package app.tryst.core.session

import android.content.Context
import app.tryst.core.security.BiometricVault
import app.tryst.core.security.SessionKeys
import app.tryst.core.security.Vault
import app.tryst.core.security.VaultWipedException
import app.tryst.data.db.TrystDatabase
import app.tryst.data.db.TrystDatabaseFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import java.io.File
import javax.crypto.Cipher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

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
) {
    private val _state = MutableStateFlow(initialState())
    val state: StateFlow<LockState> = _state.asStateFlow()

    @Volatile private var dek: ByteArray? = null
    @Volatile private var db: TrystDatabase? = null

    /** When >now, the next background event is NOT auto-locked (a photo picker/camera is up). */
    @Volatile private var autoLockSuppressedUntil = 0L

    /** The open database. Throws if accessed while locked. */
    fun database(): TrystDatabase =
        db ?: throw IllegalStateException("Database accessed while locked")

    /** Media encryption key for the current session. Throws if locked. */
    fun mediaKey(): ByteArray =
        SessionKeys.mediaKey(dek ?: throw IllegalStateException("Locked"))

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
        val currentDek = dek ?: throw IllegalStateException("Cannot enable biometric while locked")
        biometricVault.store(currentDek, authenticatedCipher)
    }

    fun disableBiometric() = biometricVault.disable()

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

    /** Called when the app goes to background: lock, unless a picker/camera launch just suppressed it. */
    fun onAppBackgrounded() {
        if (System.currentTimeMillis() < autoLockSuppressedUntil) {
            autoLockSuppressedUntil = 0L
            return
        }
        lock()
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
        _state.value = LockState.Unlocked
    }

    private fun initialState(): LockState =
        if (vault.isInitialized()) LockState.Locked else LockState.NeedsSetup

    private companion object {
        const val AUTO_LOCK_GRACE_MS = 120_000L
    }
}
