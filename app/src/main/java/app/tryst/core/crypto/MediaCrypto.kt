package app.tryst.core.crypto

import com.google.crypto.tink.StreamingAead
import com.google.crypto.tink.subtle.AesGcmHkdfStreaming
import java.io.InputStream
import java.io.OutputStream

/**
 * Streaming authenticated encryption for media (photos), built on Tink's AES-256-GCM-HKDF
 * streaming primitive. Stateless: the per-session media key is supplied by the caller (from
 * [app.tryst.core.session.SessionManager]). The encounter/media id is passed as associated
 * data so a blob is cryptographically bound to the record it belongs to.
 */
object MediaCrypto {

    private const val KEY_SIZE_BYTES = 32 // AES-256
    private const val SEGMENT_BYTES = 1 shl 20 // 1 MiB ciphertext segments

    /** Encrypts [source] into [dest]. Closes the encrypting wrapper (not [dest]) when done. */
    fun encrypt(key: ByteArray, source: InputStream, dest: OutputStream, associatedData: ByteArray) {
        streaming(key).newEncryptingStream(dest, associatedData).use { encrypting ->
            source.copyTo(encrypting)
        }
    }

    /** Returns a stream that decrypts [source] on the fly. Caller closes it. */
    fun decryptingStream(key: ByteArray, source: InputStream, associatedData: ByteArray): InputStream =
        streaming(key).newDecryptingStream(source, associatedData)

    private fun streaming(key: ByteArray): StreamingAead =
        AesGcmHkdfStreaming(key, "HmacSha256", KEY_SIZE_BYTES, SEGMENT_BYTES, 0)
}
