package app.tryst.data.backup

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.tryst.core.prefs.GeneralPreferences
import app.tryst.core.security.BiometricVault
import app.tryst.core.security.Vault
import app.tryst.core.session.SessionManager
import app.tryst.data.db.TrystDatabase
import app.tryst.data.db.TrystDatabaseFactory
import app.tryst.data.db.entity.EncounterEntity
import app.tryst.data.db.entity.PartnerEntity
import app.tryst.data.media.EncryptedMediaStore
import app.tryst.data.repository.EncounterRepository
import app.tryst.data.repository.PartnerRepository
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Backup/restore regression coverage for real-device data-loss bugs found in Pass 12. The shipped
 * [BackupRoundTripTest] only covers restore-into-a-freshly-constructed-store, which never exercised:
 *  - restore OVER existing data (media files re-saved in place),
 *  - restore after [SessionManager.deleteAllData] removed the whole media/ dir (the standard
 *    "delete all data, then restore" migration) — used to throw FileNotFoundException in
 *    EncryptedMediaStore.save and silently lose every photo,
 *  - partner avatars, which are blobs referenced only by Partner.photoMediaId (no media-table row)
 *    and so were never written into the backup at all.
 */
@RunWith(AndroidJUnit4::class)
class BackupRestoreRegressionTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val pin = "112233"
    private val password = "correct-horse-battery"
    private lateinit var manager: SessionManager
    private lateinit var store: EncryptedMediaStore
    private lateinit var encounters: EncounterRepository
    private lateinit var backup: BackupManager

    @Before
    fun setUp() {
        reset()
        manager = SessionManager(context, Vault(context), TrystDatabaseFactory(context), BiometricVault(context), GeneralPreferences(context))
        runBlocking { manager.setupPin(pin) }
        store = EncryptedMediaStore(context, manager)
        encounters = EncounterRepository(manager, store)
        backup = BackupManager(manager, store)
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
    fun restoreOverExistingData_keepsPhoto() = runBlocking<Unit> {
        val now = System.currentTimeMillis()
        manager.database().partnerDao().upsert(
            PartnerEntity("p1", "Sam", isAnonymous = false, color = null, note = "n", archivedAt = null, createdAt = now, updatedAt = now),
        )
        encounters.save(EncounterEntity(id = "e1", startAt = now, createdAt = now, updatedAt = now), partnerIds = listOf("p1"))
        // Multi-MB random payload (like a real photo), so a partial-write/truncation bug would surface.
        val marker = ByteArray(3 * 1024 * 1024).also { java.security.SecureRandom().nextBytes(it) }
        val media = encounters.attachMedia("e1", "image/jpeg", ByteArrayInputStream(marker))

        // Round-trip through a real file (mirrors the SAF export→import), not in-memory.
        val backupFile = File(context.cacheDir, "roundtrip.tryst")
        backupFile.outputStream().use { backup.export(password, it) }

        // Import WITHOUT wiping — data still present, and the existing media file gets re-saved
        // (outputStream truncates it first).
        backupFile.inputStream().use { backup.import(password, it) }

        val details = encounters.get("e1")
        assertEquals("encounter should survive", 1, details!!.partners.size)
        assertEquals("photo must survive restore-over-existing", 1, details.media.size)
        assertArrayEquals(marker, store.open(media.id).use { it.readBytes() })
        backupFile.delete()
    }

    @Test
    fun restoreAfterMediaDirDeleted_keepsPhoto() = runBlocking<Unit> {
        val now = System.currentTimeMillis()
        manager.database().partnerDao().upsert(
            PartnerEntity("p1", "Sam", isAnonymous = false, color = null, note = "n", archivedAt = null, createdAt = now, updatedAt = now),
        )
        encounters.save(EncounterEntity(id = "e1", startAt = now, createdAt = now, updatedAt = now), partnerIds = listOf("p1"))
        val marker = "PHOTO_".repeat(5000).toByteArray()
        val media = encounters.attachMedia("e1", "image/jpeg", ByteArrayInputStream(marker))

        val out = ByteArrayOutputStream()
        backup.export(password, out)

        // Simulate deleteAllData(): wipe DB rows AND the whole media dir (not just the file).
        val db = manager.database().openHelper.writableDatabase
        db.execSQL("DELETE FROM media")
        db.execSQL("DELETE FROM encounter_partner")
        db.execSQL("DELETE FROM encounters")
        db.execSQL("DELETE FROM partners")
        File(context.filesDir, "media").deleteRecursively()

        backup.import(password, ByteArrayInputStream(out.toByteArray()))

        val details = encounters.get("e1")
        assertEquals("photo must survive restore after media-dir wipe", 1, details!!.media.size)
        assertArrayEquals(marker, store.open(media.id).use { it.readBytes() })
    }

    @Test
    fun partnerAvatar_survivesBackup() = runBlocking<Unit> {
        val now = System.currentTimeMillis()
        val avatar = "AVATAR_".repeat(3000).toByteArray()
        val photoId = PartnerRepository(manager, store).savePhoto(ByteArrayInputStream(avatar))
        manager.database().partnerDao().upsert(
            PartnerEntity("p1", "Sam", isAnonymous = false, color = null, note = "n", archivedAt = null, createdAt = now, updatedAt = now).copy(photoMediaId = photoId),
        )

        val out = ByteArrayOutputStream()
        backup.export(password, out)

        // Wipe everything (delete-all-data style) then restore.
        manager.database().openHelper.writableDatabase.execSQL("DELETE FROM partners")
        File(context.filesDir, "media").deleteRecursively()

        backup.import(password, ByteArrayInputStream(out.toByteArray()))

        assertEquals(photoId, manager.database().partnerDao().getById("p1")?.photoMediaId)
        assertArrayEquals("partner avatar must survive backup", avatar, store.open(photoId).use { it.readBytes() })
    }

    /**
     * Restore inserts rows raw (no migrations), so a backup made before the v10 catalog trim carries
     * bare ids of since-removed built-in acts/kinks. Import must run the same CatalogAdoption pass as
     * MIGRATION_9_10: adopt them as custom rows and rewrite the refs — or the removed ids would
     * resurface unlabeled and unpickable.
     */
    @Test
    fun restoreOfPreTrimBackup_adoptsRemovedIds() = runBlocking<Unit> {
        val now = System.currentTimeMillis()
        // Emulate the old backup's content via raw SQL (the app can no longer produce these ids).
        val db = manager.database().openHelper.writableDatabase
        db.execSQL(
            "INSERT INTO encounters (id, startAt, protectionUsed, practicesPerformed, kinks, createdAt, updatedAt) " +
                "VALUES ('e1', $now, '', 'FOOT_PLAY,KISSING', 'CHOKING,SPANKING', $now, $now)",
        )

        val out = ByteArrayOutputStream()
        backup.export(password, out)

        // Wipe, then restore the "old" backup.
        db.execSQL("DELETE FROM encounters")
        db.execSQL("DELETE FROM acts")
        db.execSQL("DELETE FROM kinks")

        backup.import(password, ByteArrayInputStream(out.toByteArray()))

        db.query("SELECT practicesPerformed, kinks FROM encounters WHERE id = 'e1'").use { c ->
            assertTrue("restored encounter missing", c.moveToFirst())
            assertEquals("custom:FOOT_PLAY,custom:KISSING", c.getString(0)) // all ids are custom-prefixed rows now
            assertEquals("custom:CHOKING,custom:SPANKING", c.getString(1))
        }
        db.query("SELECT label, isBuiltIn FROM acts WHERE id = 'FOOT_PLAY'").use { c ->
            assertTrue("adopted act row missing", c.moveToFirst())
            assertEquals("Foot play", c.getString(0))
            assertEquals(0, c.getInt(1))
        }
        db.query("SELECT label FROM kinks WHERE id = 'CHOKING'").use { c ->
            assertTrue("adopted kink row missing", c.moveToFirst())
            assertEquals("Choking", c.getString(0))
        }
    }
}
