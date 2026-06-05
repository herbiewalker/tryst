package app.tryst.core.crypto

import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * !!! INSECURE PLACEHOLDER — M1 ONLY. DO NOT SHIP. !!!
 *
 * Returns deterministic keys derived from hard-coded constants, so the encrypted database
 * and media files can be opened across runs while we build out the storage layer. This
 * provides NO real protection: anyone with the app can derive the same keys.
 *
 * M2 replaces this binding with a passphrase-derived (Argon2id) key unlocked via the
 * Android Keystore / biometrics (see docs/SECURITY_DESIGN.md §1). The rest of the codebase
 * depends only on [DatabaseKeyProvider], so that swap touches nothing else.
 */
@Singleton
class InsecureDevKeyProvider @Inject constructor() : DatabaseKeyProvider {

    override fun databaseKey(): ByteArray =
        sha256("tryst.dev.db.key.INSECURE.PLACEHOLDER.replace.in.M2")

    override fun mediaKeyMaterial(): ByteArray =
        sha256("tryst.dev.media.key.INSECURE.PLACEHOLDER.replace.in.M2")

    private fun sha256(input: String): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
}
