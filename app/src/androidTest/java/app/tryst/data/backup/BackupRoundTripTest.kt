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
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Full backup round-trip: export (encrypted) → wipe → import → data + a photo come back intact;
 * the backup bytes are not plaintext; and a wrong password fails.
 */
@RunWith(AndroidJUnit4::class)
class BackupRoundTripTest {

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
    fun export_wipe_import_roundTrips() = runBlocking<Unit> {
        val now = System.currentTimeMillis()
        manager.database().partnerDao().upsert(
            PartnerEntity("p1", "Sam", isAnonymous = false, color = null, note = "note", archivedAt = null, createdAt = now, updatedAt = now),
        )
        encounters.save(EncounterEntity(id = "e1", startAt = now, createdAt = now, updatedAt = now), partnerIds = listOf("p1"))
        val marker = "ROUNDTRIP_SECRET_".repeat(1000).toByteArray()
        val media = encounters.attachMedia("e1", "image/jpeg", ByteArrayInputStream(marker))

        val out = ByteArrayOutputStream()
        backup.export(password, out)
        val bytes = out.toByteArray()

        // The backup must not contain the plaintext photo bytes or the partner name.
        val raw = bytes.toString(Charsets.ISO_8859_1)
        assertFalse("Backup is not encrypted!", raw.contains("ROUNDTRIP_SECRET_"))
        assertFalse("Backup leaks plaintext!", raw.contains("Sam"))

        // Wipe the data.
        val db = manager.database().openHelper.writableDatabase
        db.execSQL("DELETE FROM media")
        db.execSQL("DELETE FROM encounter_partner")
        db.execSQL("DELETE FROM encounters")
        db.execSQL("DELETE FROM partners")
        store.fileFor(media.id).delete()
        assertNull(manager.database().partnerDao().getById("p1"))

        // Restore.
        backup.import(password, ByteArrayInputStream(bytes))

        assertEquals("Sam", manager.database().partnerDao().getById("p1")?.displayName)
        val details = encounters.get("e1")
        assertNotNull(details)
        assertEquals(1, details!!.partners.size)
        assertEquals(1, details.media.size)
        val restored = store.open(media.id).use { it.readBytes() }
        assertArrayEquals(marker, restored)

        // Wrong password fails.
        assertThrows(Exception::class.java) {
            runBlocking { backup.import("wrong-password", ByteArrayInputStream(bytes)) }
        }
    }

    /**
     * Zip-Slip guard: a media id is attacker-controlled during import (it comes from the backup's ZIP
     * entry names). `fileFor` must reject any id that would resolve outside the media dir, while still
     * accepting legitimate UUID ids.
     */
    @Test
    fun fileFor_rejectsPathTraversalIds() {
        for (evil in listOf("../../databases/tryst", "..", ".", "a/b", "a\\b", "")) {
            assertThrows("Expected '$evil' to be rejected", IllegalArgumentException::class.java) {
                store.fileFor(evil)
            }
        }
        // A normal id still resolves inside the media dir.
        val ok = store.fileFor("550e8400-e29b-41d4-a716-446655440000")
        assertEquals(File(context.filesDir, "media").canonicalFile, ok.canonicalFile.parentFile)
    }

    /**
     * KDF DoS guard: the PBKDF2 iteration count is read from the (untrusted) file header. An absurd
     * value must be rejected fast, before key derivation — never used to hang the app.
     */
    @Test
    fun import_rejectsAbsurdIterationCount() {
        val header = ByteArrayOutputStream().apply {
            write("TRYSTBK1".toByteArray(Charsets.US_ASCII)) // MAGIC
            write(1) // FORMAT_VERSION
            write(ByteArray(16)) // salt
            write(byteArrayOf(0x7F, -1, -1, -1)) // iterations = Int.MAX_VALUE (big-endian)
        }.toByteArray()
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { backup.import(password, ByteArrayInputStream(header)) }
        }
    }
}
