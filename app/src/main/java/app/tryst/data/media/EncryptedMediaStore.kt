package app.tryst.data.media

import android.content.Context
import app.tryst.core.crypto.MediaCrypto
import app.tryst.core.session.SessionManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stores media as encrypted blobs under the app's private storage. Files are never written to
 * MediaStore / shared storage, so they never appear in the system gallery. The media key comes
 * from the unlocked session; the media id is used as associated data.
 */
@Singleton
class EncryptedMediaStore @Inject constructor(
    @ApplicationContext context: Context,
    private val session: SessionManager,
) {
    private val dir: File = File(context.filesDir, "media").apply { if (!exists()) mkdirs() }

    fun fileFor(id: String): File = File(dir, "$id.enc")

    fun save(id: String, source: InputStream): File {
        val file = fileFor(id)
        file.outputStream().use { out -> MediaCrypto.encrypt(session.mediaKey(), source, out, id.toByteArray()) }
        return file
    }

    fun open(id: String): InputStream =
        MediaCrypto.decryptingStream(session.mediaKey(), fileFor(id).inputStream(), id.toByteArray())

    fun delete(id: String): Boolean = fileFor(id).delete()
}
