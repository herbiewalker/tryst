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

    /**
     * Resolve the encrypted blob for [id]. Legitimate ids are [java.util.UUID]s, but during a backup
     * import the id is taken from the (untrusted) backup's ZIP entry names / `data.json`, so it must
     * be treated as hostile: a value like `../../databases/tryst` would otherwise let a crafted backup
     * write outside the media dir (Zip-Slip). Reject path separators / traversal and verify the
     * resolved file stays directly inside [dir].
     */
    fun fileFor(id: String): File {
        require(
            id.isNotEmpty() &&
                id != "." &&
                id != ".." &&
                id.none { it == '/' || it == '\\' || it == File.separatorChar },
        ) { "Invalid media id" }
        val file = File(dir, "$id.enc")
        require(file.canonicalFile.parentFile == dir.canonicalFile) { "Media id escapes storage dir" }
        return file
    }

    fun save(id: String, source: InputStream): File {
        val file = fileFor(id)
        file.outputStream().use { out -> MediaCrypto.encrypt(session.mediaKey(), source, out, id.toByteArray()) }
        return file
    }

    fun open(id: String): InputStream = MediaCrypto.decryptingStream(session.mediaKey(), fileFor(id).inputStream(), id.toByteArray())

    fun delete(id: String): Boolean = fileFor(id).delete()
}
