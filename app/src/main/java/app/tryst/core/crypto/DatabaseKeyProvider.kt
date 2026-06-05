package app.tryst.core.crypto

/**
 * Source of the secrets used to encrypt data at rest. Kept behind an interface so the
 * storage layer never knows *how* the key is obtained.
 *
 * M1 ships [InsecureDevKeyProvider] (a placeholder). M2 will replace the binding with a
 * real implementation: a passphrase-derived (Argon2id) key unlocked via the Android
 * Keystore / biometrics. See docs/SECURITY_DESIGN.md §1.
 */
interface DatabaseKeyProvider {
    /** Raw key/passphrase bytes for the SQLCipher database. */
    fun databaseKey(): ByteArray

    /** Input key material for media-file encryption (Tink AES-GCM-HKDF streaming). */
    fun mediaKeyMaterial(): ByteArray
}
