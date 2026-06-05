package app.tryst.data.media

import android.content.Context
import app.tryst.core.crypto.MediaCrypto
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stores media as encrypted blobs under the app's private storage. Files are never written
 * to MediaStore / shared storage, so they never appear in the system gallery. The media id
 * is used as associated data so a blob can't be swapped between records.
 */
@Singleton
class EncryptedMediaStore @Inject constructor(
    @ApplicationContext context: Context,
    private val crypto: MediaCrypto,
) {
    private val dir: File = File(context.filesDir, "media").apply { if (!exists()) mkdirs() }

    fun fileFor(id: String): File = File(dir, "$id.enc")

    /** Encrypts [source] to a blob for [id] and returns the file. */
    fun save(id: String, source: InputStream): File {
        val file = fileFor(id)
        file.outputStream().use { out -> crypto.encrypt(source, out, id.toByteArray()) }
        return file
    }

    /** Returns a decrypting stream for the blob; the caller must close it. */
    fun open(id: String): InputStream = crypto.decryptingStream(fileFor(id).inputStream(), id.toByteArray())

    fun delete(id: String): Boolean = fileFor(id).delete()
}
