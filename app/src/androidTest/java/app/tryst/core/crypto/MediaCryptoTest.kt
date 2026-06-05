package app.tryst.core.crypto

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class MediaCryptoTest {

    private fun fixedKeyProvider() = object : DatabaseKeyProvider {
        override fun databaseKey() = ByteArray(32) { 1 }
        override fun mediaKeyMaterial() = ByteArray(32) { 7 }
    }

    private val plaintext = ("a sensitive photo's bytes ".repeat(5000)).toByteArray()

    @Test
    fun encryptDecrypt_roundTrips() {
        val crypto = MediaCrypto(fixedKeyProvider())
        val aad = "media-1".toByteArray()

        val cipher = ByteArrayOutputStream()
        crypto.encrypt(ByteArrayInputStream(plaintext), cipher, aad)
        val cipherBytes = cipher.toByteArray()

        assertFalse("Ciphertext equals plaintext!", cipherBytes.contentEquals(plaintext))

        val decrypted = crypto.decryptingStream(ByteArrayInputStream(cipherBytes), aad).use { it.readBytes() }
        assertArrayEquals(plaintext, decrypted)
    }

    @Test
    fun decrypt_withWrongAssociatedData_fails() {
        val crypto = MediaCrypto(fixedKeyProvider())

        val cipher = ByteArrayOutputStream()
        crypto.encrypt(ByteArrayInputStream(plaintext), cipher, "media-1".toByteArray())
        val cipherBytes = cipher.toByteArray()

        assertThrows(IOException::class.java) {
            crypto.decryptingStream(ByteArrayInputStream(cipherBytes), "media-2".toByteArray()).use { it.readBytes() }
        }
    }
}
