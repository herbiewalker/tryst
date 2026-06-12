package app.tryst.core.crypto

import com.google.crypto.tink.subtle.AesGcmHkdfStreaming
import java.io.InputStream
import java.io.OutputStream

/**
 * Streaming authenticated encryption for the backup container (AES-256-GCM-HKDF, Tink). The key is
 * derived from the user's backup password (PBKDF2; see [app.tryst.data.backup.BackupManager]).
 * Streaming so large backups (many photos) never have to fit in memory. A wrong password fails as
 * an authentication error on first read.
 */
object BackupCrypto {

    private const val KEY_SIZE_BYTES = 32 // AES-256
    private const val SEGMENT_BYTES = 1 shl 20 // 1 MiB segments
    private val ASSOCIATED_DATA = "tryst-backup-v1".toByteArray()

    fun encryptingStream(key: ByteArray, dest: OutputStream): OutputStream = streaming(key).newEncryptingStream(dest, ASSOCIATED_DATA)

    fun decryptingStream(key: ByteArray, source: InputStream): InputStream = streaming(key).newDecryptingStream(source, ASSOCIATED_DATA)

    private fun streaming(key: ByteArray) = AesGcmHkdfStreaming(key, "HmacSha256", KEY_SIZE_BYTES, SEGMENT_BYTES, 0)
}
