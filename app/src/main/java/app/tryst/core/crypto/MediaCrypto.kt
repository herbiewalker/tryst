package app.tryst.core.crypto

import com.google.crypto.tink.StreamingAead
import com.google.crypto.tink.subtle.AesGcmHkdfStreaming
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Streaming authenticated encryption for media (photos), built directly on Tink's
 * AES-256-GCM-HKDF streaming primitive from the media key material. Streaming means large
 * files never have to be fully buffered in memory, and authentication (the GCM tag) protects
 * against tampering. The encounter/media id is passed as associated data so ciphertext is
 * bound to the record it belongs to.
 *
 * No Tink keyset / Keystore management here on purpose — that is M2's concern. We construct
 * the primitive from raw key material supplied by [DatabaseKeyProvider].
 */
@Singleton
class MediaCrypto @Inject constructor(keyProvider: DatabaseKeyProvider) {

    private val ikm: ByteArray = keyProvider.mediaKeyMaterial()

    private fun streaming(): StreamingAead =
        AesGcmHkdfStreaming(ikm, "HmacSha256", KEY_SIZE_BYTES, SEGMENT_BYTES, 0)

    /** Encrypts [source] into [dest]. Closes the encrypting wrapper (not [dest]) when done. */
    fun encrypt(source: InputStream, dest: OutputStream, associatedData: ByteArray) {
        streaming().newEncryptingStream(dest, associatedData).use { encrypting ->
            source.copyTo(encrypting)
        }
    }

    /** Returns a stream that decrypts [source] on the fly. Caller closes it. */
    fun decryptingStream(source: InputStream, associatedData: ByteArray): InputStream =
        streaming().newDecryptingStream(source, associatedData)

    private companion object {
        const val KEY_SIZE_BYTES = 32 // AES-256
        const val SEGMENT_BYTES = 1 shl 20 // 1 MiB ciphertext segments
    }
}
