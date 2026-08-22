// SPDX-License-Identifier: GPL-3.0-or-later
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
        // The media dir is created in the constructor, but it can be removed later (deleteAllData wipes
        // it) while this singleton lives on with a stale reference. Recreate it so a restore that runs
        // after a wipe — the standard "delete all data, then restore" migration — doesn't fail.
        if (!dir.exists()) dir.mkdirs()
        file.outputStream().use { out -> MediaCrypto.encrypt(session.mediaKey(), source, out, id.toByteArray()) }
        return file
    }

    fun open(id: String): InputStream = MediaCrypto.decryptingStream(session.mediaKey(), fileFor(id).inputStream(), id.toByteArray())

    fun delete(id: String): Boolean = fileFor(id).delete()

    // --- Staging (Bundle-C N5: atomic restore) --------------------------------------------------
    //
    // A restore needs to write every media blob to disk BEFORE the DB is committed — otherwise a
    // mid-loop failure leaves the DB pointing at blobs that don't exist ("silent lossy round-trip").
    // The staging API lets the caller write each blob to a suffixed path, then atomically move
    // every staged file into place after the whole set has landed successfully. On failure, the
    // caller drops staged files and the on-disk media/ dir stays untouched.

    /** Path a staged blob for [id] is written to before promotion. Same Zip-Slip validation as [fileFor]. */
    fun stagingFileFor(id: String): File = File(fileFor(id).parentFile, "${fileFor(id).name}.staging")

    /** Encrypts [source] into the staging path for [id]. Never overwrites the live blob at [fileFor]. */
    fun saveStaged(id: String, source: InputStream): File {
        val file = stagingFileFor(id)
        if (!dir.exists()) dir.mkdirs()
        file.outputStream().use { out -> MediaCrypto.encrypt(session.mediaKey(), source, out, id.toByteArray()) }
        return file
    }

    /**
     * Move the staged blob for [id] into place, overwriting any existing blob. Returns `true` if the
     * promotion happened. `File.renameTo` on Android generally atomically replaces on the same
     * filesystem, but is best-effort: if it fails, fall back to copy-delete so the caller doesn't
     * get partial state.
     */
    fun promoteStaged(id: String): Boolean {
        val staged = stagingFileFor(id)
        if (!staged.exists()) return false
        val target = fileFor(id)
        if (target.exists()) target.delete()
        if (staged.renameTo(target)) return true
        // Cross-fs or filesystem quirk — fall back to a full copy, then drop the staged source.
        staged.inputStream().use { input -> target.outputStream().use { output -> input.copyTo(output) } }
        staged.delete()
        return true
    }

    /** Best-effort cleanup of a single staged blob. Safe to call on a blob that was never staged. */
    fun clearStaged(id: String): Boolean = stagingFileFor(id).let { if (it.exists()) it.delete() else false }

    /** Sweep every `<id>.enc.staging` file left in the media dir. Called on restore-failure paths. */
    fun clearAllStaged() {
        dir.listFiles { _, name -> name.endsWith(".enc.staging") }?.forEach { it.delete() }
    }

    /**
     * Delete every live blob file whose id is NOT in [keep]. Staging files are ignored — this is
     * intended for the restore-over-existing wipe path (Bundle-E Q1): after the DB has been wiped
     * and the backup rows restored, orphan blobs are the previous user's photos that no row
     * points at any more.
     */
    fun deleteOrphans(keep: Set<String>) {
        dir.listFiles { _, name -> name.endsWith(".enc") && !name.endsWith(".enc.staging") }?.forEach { file ->
            val id = file.name.removeSuffix(".enc")
            if (id !in keep) file.delete()
        }
    }
}
