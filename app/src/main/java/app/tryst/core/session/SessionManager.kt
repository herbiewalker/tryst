package app.tryst.core.session

import app.tryst.core.security.SessionKeys
import app.tryst.core.security.Vault
import app.tryst.core.security.VaultWipedException
import app.tryst.data.db.TrystDatabase
import app.tryst.data.db.TrystDatabaseFactory
import kotlinx.coroutines.Dispatchers
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
    private val vault: Vault,
    private val databaseFactory: TrystDatabaseFactory,
) {
    private val _state = MutableStateFlow(initialState())
    val state: StateFlow<LockState> = _state.asStateFlow()

    @Volatile private var dek: ByteArray? = null
    @Volatile private var db: TrystDatabase? = null

    /** The open database. Throws if accessed while locked. */
    fun database(): TrystDatabase =
        db ?: throw IllegalStateException("Database accessed while locked")

    /** Media encryption key for the current session. Throws if locked. */
    fun mediaKey(): ByteArray =
        SessionKeys.mediaKey(dek ?: throw IllegalStateException("Locked"))

    /** First-run: create the vault with [pin] and open a session. */
    suspend fun setupPin(pin: String) = withContext(Dispatchers.IO) {
        // Remove any stale DB left by a previously wiped vault (its key is gone).
        databaseFactory.deleteDatabase()
        openSession(vault.setup(pin))
    }

    /** Unlock with [pin]. Throws WrongPinException / VaultWipedException on failure. */
    suspend fun unlockWithPin(pin: String) = withContext(Dispatchers.IO) {
        val unlocked = try {
            vault.unlock(pin)
        } catch (e: VaultWipedException) {
            _state.value = LockState.NeedsSetup
            throw e
        }
        openSession(unlocked)
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
}
