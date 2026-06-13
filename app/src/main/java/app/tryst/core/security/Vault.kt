package app.tryst.core.security

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONObject

/**
 * The encrypted key vault (Keystore-only model — see docs/SECURITY_DESIGN.md §1).
 *
 * A random 256-bit DEK is persisted **double-wrapped**: `Enc(Keystore, Enc(pinKey, DEK))`.
 * The outer layer is a hardware-bound Android Keystore key; the inner layer is a key derived
 * from a distinct 6-digit app PIN. Unlock returns the DEK; the caller owns its lifetime in
 * memory. After [MAX_ATTEMPTS] wrong PINs the vault self-wipes.
 *
 * This class is headless (no UI, no biometrics) — that comes in M2b.
 */
@Singleton
class Vault @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val file: File get() = File(context.filesDir, FILE_NAME)
    private val keyStore = SecureKeyStore(KEK_ALIAS)
    private val random = SecureRandom()

    fun isInitialized(): Boolean = file.exists()

    /** First-run setup: generates a random DEK protected by [pin] and returns it. */
    @Synchronized
    fun setup(pin: String): ByteArray {
        if (isInitialized()) throw VaultAlreadyInitializedException()
        val dek = ByteArray(DEK_BYTES).also { random.nextBytes(it) }
        persist(dek, pin)
        return dek
    }

    /** Unlocks with [pin], returning the DEK. */
    @Synchronized
    @Suppress("ThrowsCount") // distinct failure modes (not initialised, wrong PIN, tampered file).
    fun unlock(pin: String): ByteArray {
        if (!isInitialized()) throw VaultNotInitializedException()
        val json = JSONObject(file.readText())
        val salt = b64d(json.getString(KEY_SALT))
        val iterations = json.getInt(KEY_ITER)
        val wrapped = b64d(json.getString(KEY_WRAPPED))
        var attempts = json.getInt(KEY_ATTEMPTS)

        // Outer (hardware) layer: always succeeds on this device regardless of PIN.
        val inner = keyStore.decrypt(wrapped)

        // Inner (PIN) layer: fails with an AEAD error on a wrong PIN.
        val pinKey = Pbkdf2.derive(pin, salt, iterations)
        val dek = try {
            AesGcm.decrypt(pinKey, inner)
        } catch (_: GeneralSecurityException) {
            attempts += 1
            if (attempts >= MAX_ATTEMPTS) {
                wipe()
                throw VaultWipedException()
            }
            setAttempts(json, attempts)
            throw WrongPinException(MAX_ATTEMPTS - attempts)
        }
        if (attempts != 0) setAttempts(json, 0)
        return dek
    }

    /**
     * Checks [pin] **without** counting a failed attempt or wiping — for re-authentication while the
     * vault is already unlocked (e.g. confirming the current PIN before a change). Returns false on a
     * wrong PIN rather than throwing. Do not use for the lock screen (that must use [unlock], which
     * enforces the self-wipe).
     */
    @Synchronized
    fun verifyPin(pin: String): Boolean {
        if (!isInitialized()) return false
        val json = JSONObject(file.readText())
        val salt = b64d(json.getString(KEY_SALT))
        val iterations = json.getInt(KEY_ITER)
        val wrapped = b64d(json.getString(KEY_WRAPPED))
        val inner = keyStore.decrypt(wrapped)
        val pinKey = Pbkdf2.derive(pin, salt, iterations)
        return try {
            AesGcm.decrypt(pinKey, inner)
            true
        } catch (_: GeneralSecurityException) {
            false
        }
    }

    /**
     * Re-wraps an already-unlocked [dek] under [newPin]. The caller must have authorised the change
     * (e.g. via [verifyPin]); this does not check any old PIN, so it never touches the wipe counter
     * and can't lose data on a mistyped current PIN.
     */
    @Synchronized
    fun reprotect(dek: ByteArray, newPin: String) = persist(dek, newPin)

    /** Destroys all key material (lockout, or a future "delete all data"). */
    @Synchronized
    fun wipe() {
        if (file.exists()) file.delete()
        keyStore.deleteKey()
    }

    private fun persist(dek: ByteArray, pin: String) {
        val salt = ByteArray(SALT_BYTES).also { random.nextBytes(it) }
        val iterations = Pbkdf2.DEFAULT_ITERATIONS
        val pinKey = Pbkdf2.derive(pin, salt, iterations)
        val inner = AesGcm.encrypt(pinKey, dek)
        val wrapped = keyStore.encrypt(inner)
        val json = JSONObject()
            .put(KEY_VERSION, 1)
            .put(KEY_SALT, b64e(salt))
            .put(KEY_ITER, iterations)
            .put(KEY_WRAPPED, b64e(wrapped))
            .put(KEY_ATTEMPTS, 0)
        atomicWrite(json.toString())
    }

    private fun setAttempts(json: JSONObject, attempts: Int) {
        json.put(KEY_ATTEMPTS, attempts)
        atomicWrite(json.toString())
    }

    private fun atomicWrite(content: String) {
        val tmp = File(context.filesDir, "$FILE_NAME.tmp")
        tmp.writeText(content)
        if (!tmp.renameTo(file)) {
            file.writeText(content)
            tmp.delete()
        }
    }

    private fun b64e(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)
    private fun b64d(text: String): ByteArray = Base64.getDecoder().decode(text)

    private companion object {
        const val FILE_NAME = "vault.json"
        const val KEK_ALIAS = "tryst_vault_kek"
        const val DEK_BYTES = 32
        const val SALT_BYTES = 16
        const val MAX_ATTEMPTS = 10

        const val KEY_VERSION = "v"
        const val KEY_SALT = "salt"
        const val KEY_ITER = "iter"
        const val KEY_WRAPPED = "wrapped"
        const val KEY_ATTEMPTS = "attempts"
    }
}
