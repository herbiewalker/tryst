package app.tryst.data.media

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.tryst.core.security.BiometricVault
import app.tryst.core.security.Vault
import app.tryst.core.session.SessionManager
import app.tryst.data.db.TrystDatabase
import app.tryst.data.db.TrystDatabaseFactory
import app.tryst.data.db.entity.EncounterEntity
import app.tryst.data.repository.EncounterRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.File

/**
 * End-to-end media attachment: attach a photo to an encounter, read it back decrypted, confirm the
 * on-disk blob is encrypted (plaintext bytes absent), and that delete removes the blob. Exercises
 * the real unlocked-session path (DEK → media key → EncryptedMediaStore).
 */
@RunWith(AndroidJUnit4::class)
class MediaAttachmentTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val pin = "135790"
    private lateinit var manager: SessionManager
    private lateinit var store: EncryptedMediaStore
    private lateinit var repo: EncounterRepository

    @Before
    fun setUp() {
        reset()
        manager = SessionManager(context, Vault(context), TrystDatabaseFactory(context), BiometricVault(context))
        runBlocking { manager.setupPin(pin) } // unlocks: DB open, media key available
        store = EncryptedMediaStore(context, manager)
        repo = EncounterRepository(manager, store)
    }

    @After
    fun tearDown() {
        if (::manager.isInitialized) manager.lock()
        reset()
    }

    private fun reset() {
        Vault(context).wipe()
        BiometricVault(context).disable()
        context.deleteDatabase(TrystDatabase.NAME)
        File(context.filesDir, "media").deleteRecursively()
    }

    @Test
    fun attach_readsBackDecrypted_isEncryptedOnDisk_andDeletes() = runBlocking {
        val now = System.currentTimeMillis()
        repo.save(EncounterEntity(id = "e1", startAt = now, createdAt = now, updatedAt = now))

        val marker = "PHOTO_SECRET_BYTES_".repeat(2000).toByteArray()
        val media = repo.attachMedia("e1", "image/jpeg", ByteArrayInputStream(marker))

        // Round-trips through the decrypting stream.
        val readBack = repo.openMedia(media).use { it.readBytes() }
        assertArrayEquals(marker, readBack)

        // The blob on disk is encrypted — the plaintext marker must not appear in the raw bytes.
        val raw = store.fileFor(media.id).readBytes().toString(Charsets.ISO_8859_1)
        assertFalse("Plaintext photo bytes found in blob — media is NOT encrypted at rest!", raw.contains("PHOTO_SECRET_BYTES_"))

        // Deleting the media removes the encrypted blob.
        repo.deleteMedia(media)
        assertFalse("Blob still exists after delete", store.fileFor(media.id).exists())
    }
}
