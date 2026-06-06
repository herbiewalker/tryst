package app.tryst.core.security

import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Slow key-derivation for the PIN layer. PBKDF2-HMAC-SHA256 is used (no native dependency);
 * abstracted here so it can be swapped for Argon2id later without touching the vault. See
 * docs/SECURITY_DESIGN.md §1.
 */
object Pbkdf2 {
    // OWASP-recommended minimum for PBKDF2-HMAC-SHA256 (2023+). The count is stored per-vault
    // (the `iter` field), so raising it only affects vaults created/re-keyed after the bump;
    // existing vaults keep unlocking with their original count.
    const val DEFAULT_ITERATIONS = 600_000
    private const val KEY_BITS = 256
    private const val ALGORITHM = "PBKDF2WithHmacSHA256"

    fun derive(pin: String, salt: ByteArray, iterations: Int = DEFAULT_ITERATIONS): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, iterations, KEY_BITS)
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }
}
