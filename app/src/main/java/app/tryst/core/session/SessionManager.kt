package app.tryst.core.session

import android.content.Context
import app.tryst.core.prefs.GeneralPreferences
import app.tryst.core.security.BiometricVault
import app.tryst.core.security.SessionKeys
import app.tryst.core.security.Vault
import app.tryst.core.security.VaultWipedException
import app.tryst.data.db.TrystDatabase
import app.tryst.data.db.TrystDatabaseFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.crypto.Cipher
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Owns the unlock lifecycle and the in-memory key/database. While locked, the DEK is not in
 * memory and the database does not exist. Unlock derives the keys, opens the DB, and flips to
 * [LockState.Unlocked]; [lock] closes the DB and zeroes the DEK.
 */
@Singleton
class SessionManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val vault: Vault,
    private val databaseFactory: TrystDatabaseFactory,
    private val biometricVault: BiometricVault,
    private val generalPreferences: GeneralPreferences,
) {
    // Drives the delayed auto-lock when a non-zero timeout is configured. Process-scoped so the timer
    // survives the app being backgrounded (and dies with the process, which loses the keys anyway).
    private val lockScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile private var pendingLock: Job? = null
    private val _state = MutableStateFlow(initialState())
    val state: StateFlow<LockState> = _state.asStateFlow()

    @Volatile private var dek: ByteArray? = null

    @Volatile private var db: TrystDatabase? = null

    /** When >now, the next background event is NOT auto-locked (a photo picker/camera is up). */
    @Volatile private var autoLockSuppressedUntil = 0L

    /** The open database. Throws if accessed while locked. */
    fun database(): TrystDatabase = db ?: error("Database accessed while locked")

    /** Media encryption key for the current session. Throws if locked. */
    fun mediaKey(): ByteArray = SessionKeys.mediaKey(dek ?: error("Locked"))

    /** First-run: create the vault with [pin] and open a session. */
    suspend fun setupPin(pin: String) = withContext(Dispatchers.IO) {
        // Remove any stale DB / biometric blob left by a previously wiped vault.
        databaseFactory.deleteDatabase()
        biometricVault.disable()
        openSession(vault.setup(pin))
    }

    /** Unlock with [pin]. Throws WrongPinException / VaultWipedException on failure. */
    suspend fun unlockWithPin(pin: String) = withContext(Dispatchers.IO) {
        val unlocked = try {
            vault.unlock(pin)
        } catch (e: VaultWipedException) {
            biometricVault.disable() // the wrapped DEK is gone; biometric is now meaningless
            _state.value = LockState.NeedsSetup
            throw e
        }
        openSession(unlocked)
    }

    // --- Biometric unlock (see BiometricVault) -----------------------------------------

    fun isBiometricEnabled(): Boolean = biometricVault.isEnabled()

    fun canUseBiometrics(): Boolean = biometricVault.canUseBiometrics()

    /** Cipher to feed BiometricPrompt when enabling biometric unlock. */
    fun biometricEncryptCipher(): Cipher = biometricVault.encryptCipher()

    /** Cipher to feed BiometricPrompt when unlocking with biometrics. */
    fun biometricDecryptCipher(): Cipher = biometricVault.decryptCipher()

    /** Enable biometric unlock for the current (unlocked) session. */
    fun enableBiometric(authenticatedCipher: Cipher) {
        val currentDek = dek ?: error("Cannot enable biometric while locked")
        biometricVault.store(currentDek, authenticatedCipher)
    }

    fun disableBiometric() = biometricVault.disable()

    /** Verify the current PIN without affecting the self-wipe counter (re-auth for change-PIN). */
    fun verifyCurrentPin(pin: String): Boolean = vault.verifyPin(pin)

    /**
     * Change the app PIN while unlocked: verify [current] (no wipe on a wrong PIN), then re-wrap the
     * **in-memory** DEK under [new]. The session/DB and the biometric wrap (DEK-based, PIN-independent)
     * are untouched. Returns false if the current PIN is wrong or the session isn't unlocked.
     */
    fun changePin(current: String, new: String): Boolean {
        if (!vault.verifyPin(current)) return false
        val currentDek = dek ?: return false
        vault.reprotect(currentDek, new)
        return true
    }

    /** Irreversibly destroy ALL app data: keys, database, and encrypted media. Returns to setup. */
    @Synchronized
    fun deleteAllData() {
        db?.close()
        db = null
        dek?.fill(0)
        dek = null
        databaseFactory.deleteDatabase()
        biometricVault.disable()
        vault.wipe()
        File(context.filesDir, "media").deleteRecursively()
        clearCaptureCache()
        _state.value = LockState.NeedsSetup
    }

    /** Unlock using a BiometricPrompt-authenticated decrypt cipher. */
    suspend fun unlockWithBiometric(authenticatedCipher: Cipher) = withContext(Dispatchers.IO) {
        openSession(biometricVault.recover(authenticatedCipher))
    }

    /**
     * Suppress the very next background auto-lock, for ~2 min, while we hand off to the OS photo
     * picker or camera (which unavoidably backgrounds us). Consumed on first use; the time window
     * means a failed launch can't leave auto-lock disabled.
     */
    fun suppressNextAutoLock() {
        autoLockSuppressedUntil = System.currentTimeMillis() + AUTO_LOCK_GRACE_MS
    }

    /**
     * Called when the app goes to background. Locks immediately by default; if the user configured a
     * non-zero auto-lock timeout (Settings → General), schedules the lock after that delay instead —
     * cancelled by [onAppForegrounded] if they return first. A picker/camera launch suppresses it.
     */
    fun onAppBackgrounded() {
        if (System.currentTimeMillis() < autoLockSuppressedUntil) {
            autoLockSuppressedUntil = 0L
            return
        }
        val timeout = generalPreferences.autoLockTimeoutMs.value
        if (timeout <= 0L) {
            lock()
        } else {
            pendingLock?.cancel()
            pendingLock = lockScope.launch {
                delay(timeout)
                lock()
            }
        }
    }

    /** Called when the app returns to the foreground: cancel any pending delayed auto-lock. */
    fun onAppForegrounded() {
        pendingLock?.cancel()
        pendingLock = null
    }

    /** Clear all in-memory secrets and lock the app. */
    @Synchronized
    fun lock() {
        db?.close()
        db = null
        dek?.fill(0)
        dek = null
        _state.value = if (vault.isInitialized()) LockState.Locked else LockState.NeedsSetup
    }

    @Synchronized
    private fun openSession(newDek: ByteArray) {
        val database = databaseFactory.create(SessionKeys.databaseKey(newDek))
        database.openHelper.writableDatabase // force-open so a bad key fails here, not later
        dek = newDek
        db = database
        // A camera capture keeps the session open across the OS handoff (auto-lock is suppressed),
        // so any plaintext temp here at unlock time is an orphan from a killed prior session — sweep it.
        clearCaptureCache()
        _state.value = LockState.Unlocked
    }

    /** Delete any plaintext camera captures left in cache (see [app.tryst.ui.common.rememberCameraCapture]). */
    private fun clearCaptureCache() {
        File(context.cacheDir, "captures").deleteRecursively()
    }

    private fun initialState(): LockState = if (vault.isInitialized()) LockState.Locked else LockState.NeedsSetup

    private companion object {
        const val AUTO_LOCK_GRACE_MS = 120_000L
    }
}
