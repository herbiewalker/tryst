package app.tryst.core.security

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Derives purpose-specific subkeys from the single master DEK, so the database key and the
 * media key are cryptographically separate (domain separation) rather than reusing one key
 * for two schemes. HMAC-SHA256(DEK, label) → 32-byte subkey.
 */
object SessionKeys {
    fun databaseKey(dek: ByteArray): ByteArray = hmac(dek, "tryst-db-key-v1")

    fun mediaKey(dek: ByteArray): ByteArray = hmac(dek, "tryst-media-key-v1")

    private fun hmac(key: ByteArray, label: String): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(label.toByteArray(Charsets.UTF_8))
    }
}
