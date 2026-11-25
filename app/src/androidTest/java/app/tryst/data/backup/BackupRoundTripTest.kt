// SPDX-License-Identifier: GPL-3.0-or-later
// ╭──────────────────────────────────────────────────────────────────╮
// │  ┌●───●───●───●───●───●───●───●───●───●───●───●───●───●───●───┐  │
// │  │                                                            │  │
// │  ●   ██╗  ██╗    ██╗    ██╗                                   ●  │
// │  │   ██║  ██║    ██║    ██║           herbiewalker            │  │
// │  │   ███████║    ██║ █╗ ██║──●──●──●──┐                       │  │
// │  │   ██╔══██║    ██║███╗██║           │                       │  │
// │  ●   ██║  ██║    ╚███╔███╔╝           ●───⏣  code · tools     ●  │
// │  │   ╚═╝  ╚═╝     ╚══╝╚══╝                    homelab         │  │
// │  │                                                            │  │
// │  └●───●───●───●───●───●───●───●───●───●───●───●───●───●───●───┘  │
// ╰──────────────────────────────────────────────────────────────────╯
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
import app.tryst.data.db.entity.OrientationEntity
import app.tryst.data.db.entity.PartnerEntity
import app.tryst.data.db.entity.ProfileEntity
import app.tryst.data.media.EncryptedMediaStore
import app.tryst.data.repository.EncounterRepository
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
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
        backup = BackupManager(context, manager, store)
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

    /** DEMO-1: the orientation catalog and a partner's chosen label survive export → wipe → import. */
    @Test
    fun export_wipe_import_roundTripsOrientation() = runBlocking<Unit> {
        val now = System.currentTimeMillis()
        val db = manager.database()
        db.orientationDao().upsert(OrientationEntity(id = "o1", label = "Label A"))
        db.partnerDao().upsert(
            PartnerEntity("p1", "Sam", isAnonymous = false, color = null, note = null, orientation = "o1", archivedAt = null, createdAt = now, updatedAt = now),
        )

        val out = ByteArrayOutputStream()
        backup.export(password, out)

        val raw = db.openHelper.writableDatabase
        raw.execSQL("DELETE FROM partners")
        raw.execSQL("DELETE FROM orientations")
        assertNull(db.partnerDao().getById("p1"))

        backup.import(password, ByteArrayInputStream(out.toByteArray()))

        assertEquals("o1", db.partnerDao().getById("p1")?.orientation)
        assertEquals(listOf("Label A"), db.orientationDao().observeAll().first().map { it.label })
    }

    /** CYC-1: periods and their per-day flow survive export → wipe-first import, estimated flags included. */
    @Test
    fun export_wipe_import_roundTripsCycleData() = runBlocking<Unit> {
        val cycles = app.tryst.data.repository.CycleRepository(manager)
        val owner = app.tryst.data.repository.CycleOwner(app.tryst.data.db.entity.CyclePeriodEntity.OWNER_PROFILE, ProfileEntity.SELF_ID)
        val id = checkNotNull(cycles.startPeriod(owner, startEpochDay = 20_000))
        val period = checkNotNull(cycles.getPeriod(id))
        val days = cycles.getDays(id).toMutableList()
        days[1] = days[1].copy(flow = app.tryst.data.db.entity.CycleFlow.HEAVY.name, estimated = false)
        cycles.save(period.copy(note = "n"), days)

        val bytes = exportedBytes()
        cycles.deleteAll()
        assertNull(cycles.getPeriod(id))

        backup.import(password, ByteArrayInputStream(bytes), wipeFirst = true)

        val restored = checkNotNull(cycles.getPeriod(id))
        assertEquals(period.startEpochDay, restored.startEpochDay)
        assertEquals(period.endEpochDay, restored.endEpochDay)
        assertEquals(true, restored.endEstimated)
        assertEquals("n", restored.note)
        val restoredDays = cycles.getDays(id)
        assertEquals(days.map { it.epochDay to it.flow }, restoredDays.map { it.epochDay to it.flow })
        assertEquals(days.map { it.estimated }, restoredDays.map { it.estimated })
    }

    /** DEMO-1: deleting a label clears it from partners and the profile instead of leaving a dangling id. */
    @Test
    fun deletingOrientation_clearsPartnerAndProfileRefs() = runBlocking<Unit> {
        val now = System.currentTimeMillis()
        val db = manager.database()
        db.orientationDao().upsert(OrientationEntity(id = "o1", label = "Label A"))
        db.orientationDao().upsert(OrientationEntity(id = "o2", label = "Label B"))
        db.partnerDao().upsert(
            PartnerEntity("p1", "Sam", isAnonymous = false, color = null, note = null, orientation = "o1", archivedAt = null, createdAt = now, updatedAt = now),
        )
        db.partnerDao().upsert(
            PartnerEntity("p2", "Alex", isAnonymous = false, color = null, note = null, orientation = "o2", archivedAt = null, createdAt = now, updatedAt = now),
        )
        db.profileDao().upsert(ProfileEntity(orientation = "o1", updatedAt = now))

        db.orientationDao().delete("o1")

        assertNull(db.partnerDao().getById("p1")?.orientation)
        assertEquals("o2", db.partnerDao().getById("p2")?.orientation)
        assertNull(db.profileDao().get(ProfileEntity.SELF_ID)?.orientation)
        assertEquals(listOf("o2"), db.orientationDao().observeAll().first().map { it.id })
    }

    /** QOL-5: settings ride the backup and restore onto a different device's (simulated) prefs. */
    @Test
    fun export_import_roundTripsSettings() = runBlocking<Unit> {
        val theme = context.getSharedPreferences("tryst_appearance", Context.MODE_PRIVATE)
        theme.edit().putString("theme_mode", "DARK").putBoolean("dynamic_color", true).apply()
        val insights = context.getSharedPreferences("tryst_insights", Context.MODE_PRIVATE)
        insights.edit().putStringSet("section_hidden", setOf("streaks", "achievements")).apply()

        val out = ByteArrayOutputStream()
        backup.export(password, out)

        // Simulate a fresh device: clear the local prefs before restoring.
        theme.edit().clear().apply()
        insights.edit().clear().apply()

        backup.import(password, ByteArrayInputStream(out.toByteArray()))

        assertEquals("DARK", theme.getString("theme_mode", null))
        assertTrue(theme.getBoolean("dynamic_color", false))
        assertEquals(setOf("streaks", "achievements"), insights.getStringSet("section_hidden", emptySet()))
    }

    /** The export-dialog toggle (Bundle QOL-5): unchecking "include settings" must leave local prefs alone on restore. */
    @Test
    fun export_withSettingsExcluded_restoreLeavesLocalPrefsUntouched() = runBlocking<Unit> {
        val theme = context.getSharedPreferences("tryst_appearance", Context.MODE_PRIVATE)
        theme.edit().putString("theme_mode", "LIGHT").apply()

        val out = ByteArrayOutputStream()
        backup.export(password, out, includeSettings = false)

        theme.edit().putString("theme_mode", "DARK").apply() // local change after the export
        backup.import(password, ByteArrayInputStream(out.toByteArray()))

        assertEquals("DARK", theme.getString("theme_mode", null)) // untouched by the settings-less restore
    }

    /**
     * A pre-QOL-5 (v1) backup has no settings.json and a version byte of 1, not 2. The version byte
     * sits in the cleartext header (index 8, right after the 8-byte MAGIC), outside the AEAD stream,
     * so rewriting it to simulate an old export doesn't disturb decryption.
     */
    @Test
    fun import_ofV1BackupWithNoSettings_succeeds() = runBlocking<Unit> {
        val out = ByteArrayOutputStream()
        backup.export(password, out, includeSettings = false)
        val bytes = out.toByteArray()
        bytes[8] = 1
        backup.import(password, ByteArrayInputStream(bytes))
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
        val e = assertThrows(BackupException::class.java) {
            runBlocking { backup.import(password, ByteArrayInputStream(header)) }
        }
        assertEquals(BackupError.NOT_A_BACKUP, e.kind)
    }

    /** D-67: a partner's and the profile's picked colors survive export → wipe-first import. */
    @Test
    fun export_wipe_import_roundTripsPersonColors() = runBlocking<Unit> {
        val now = System.currentTimeMillis()
        val db = manager.database()
        db.partnerDao().upsert(
            PartnerEntity("p1", "Sam", isAnonymous = false, color = "#2D9CFF", note = null, archivedAt = null, createdAt = now, updatedAt = now),
        )
        db.profileDao().upsert(ProfileEntity(displayName = "Me", color = "#FF2D6F", updatedAt = now))

        val bytes = exportedBytes()
        val raw = db.openHelper.writableDatabase
        raw.execSQL("DELETE FROM partners")
        raw.execSQL("DELETE FROM profile")

        backup.import(password, ByteArrayInputStream(bytes), wipeFirst = true)

        assertEquals("#2D9CFF", db.partnerDao().getById("p1")?.color)
        assertEquals("#FF2D6F", db.profileDao().get(ProfileEntity.SELF_ID)?.color)
    }

    /** CYC-1: the cycle switches and the "whose periods" picks ride the settings backup, and reload() picks them up. */
    @Test
    fun export_import_roundTripsCycleSettings_andReloadAppliesThem() = runBlocking<Unit> {
        val general = GeneralPreferences(context)
        general.setCycleEnabled(true)
        general.setCyclePredictions(false)
        general.setCycleCalendar(true)
        general.setCycleCalendarOwners(setOf("partner:p1", "profile:self"))

        val bytes = exportedBytes()
        general.resetToDefaults() // a fresh device: everything back to defaults
        assertFalse(general.cycleEnabled.value)

        backup.import(password, ByteArrayInputStream(bytes))

        // The store holds the restored values, but this live object still shows the old ones until reloaded.
        assertFalse(general.cycleEnabled.value)
        general.reload()
        assertTrue(general.cycleEnabled.value)
        assertFalse(general.cyclePredictions.value)
        assertTrue(general.cycleCalendar.value)
        assertEquals(setOf("partner:p1", "profile:self"), general.cycleCalendarOwners.value)
        general.resetToDefaults()
    }

    /**
     * A backup made by a newer Tryst (higher schemaVersion in data.json) is refused as NEWER_VERSION before
     * anything is written, even with "replace my data" on. Built by hand: the real header and key, but a
     * data.json that claims a schema from the future.
     */
    @Test
    fun import_ofANewerSchema_isRefused_andChangesNothing() = runBlocking<Unit> {
        seedOneTryst()
        val future = org.json.JSONObject()
            .put("schemaVersion", dbVersion() + 1)
            .put("tables", org.json.JSONObject())
            .put("meta", org.json.JSONObject().put("appVersion", "9.9.9"))
        val file = handMadeBackup(future)

        val e = assertThrows(BackupException::class.java) {
            runBlocking { backup.import(password, ByteArrayInputStream(file), wipeFirst = true) }
        }
        assertEquals("the refusal names the version it needs", "9.9.9", e.madeWith)
        assertEquals(BackupError.NEWER_VERSION, e.kind)
        assertNotNull("existing data must be untouched", encounters.get("e1"))
    }

    private fun dbVersion(): Int = manager.database().openHelper.writableDatabase.version

    /** A real, password-encrypted backup container whose data.json is [root]: the header comes from a fresh export. */
    private suspend fun handMadeBackup(root: org.json.JSONObject): ByteArray {
        val header = exportedBytes().copyOfRange(0, HEADER_BYTES)
        val salt = header.copyOfRange(MAGIC_BYTES + 1, MAGIC_BYTES + 1 + SALT_BYTES)
        val iterations = java.nio.ByteBuffer.wrap(header, MAGIC_BYTES + 1 + SALT_BYTES, 4).int
        val key = app.tryst.core.security.Pbkdf2.derive(password, salt, iterations)
        val out = ByteArrayOutputStream().apply { write(header) }
        java.util.zip.ZipOutputStream(app.tryst.core.crypto.BackupCrypto.encryptingStream(key, out)).use { zip ->
            zip.putNextEntry(java.util.zip.ZipEntry("data.json"))
            zip.write(root.toString().toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
        return out.toByteArray()
    }

    /** BKP-3: a fresh export describes itself, and a quick look at it finds nothing to warn about. */
    @Test
    fun inspect_describesAFreshExport() = runBlocking<Unit> {
        seedOneTryst()
        val summary = backup.inspect(password, ByteArrayInputStream(exportedBytes()))
        assertEquals(dbVersion(), summary.schemaVersion)
        assertEquals(1, summary.trysts)
        assertEquals(1, summary.partners)
        assertEquals(1, summary.photos)
        assertNotNull("new backups record when they were made", summary.createdAt)
        assertFalse(summary.newerThanApp)
        assertFalse(summary.olderThanApp)
        assertTrue(summary.replaceWouldRemove.isEmpty())
        assertNull("a quick look doesn't read the photos", summary.photosFound)
    }

    /** BKP-3: "Check a backup" reads every byte and counts the photos it actually holds. */
    @Test
    fun inspect_fullCheck_readsEveryPhoto_andCatchesACutShortFile() = runBlocking<Unit> {
        seedOneTryst()
        val bytes = exportedBytes()
        assertEquals(1, backup.inspect(password, ByteArrayInputStream(bytes), fullCheck = true).photosFound)

        val cut = bytes.copyOf(bytes.size - 200)
        val e = assertThrows(BackupException::class.java) {
            runBlocking { backup.inspect(password, ByteArrayInputStream(cut), fullCheck = true) }
        }
        // A small backup is one encrypted segment, so the cut shows up on the first read (reported as a wrong key).
        assertTrue(e.kind == BackupError.DAMAGED || e.kind == BackupError.WRONG_PASSWORD)
    }

    /**
     * BKP-3: an older backup with no cycle tables at all, looked at on a phone that has periods, says a replace
     * would delete them. This is the data loss the restore preview warns about.
     */
    @Test
    fun inspect_ofAnOlderBackup_saysReplaceWouldDeletePeriods() = runBlocking<Unit> {
        val cycles = app.tryst.data.repository.CycleRepository(manager)
        val owner = app.tryst.data.repository.CycleOwner(app.tryst.data.db.entity.CyclePeriodEntity.OWNER_PROFILE, ProfileEntity.SELF_ID)
        checkNotNull(cycles.startPeriod(owner, startEpochDay = 20_000))
        // Two user-made kinks; the backup's one tryst uses the first, so a restore re-adopts it and only the
        // second would really be lost.
        manager.database().openHelper.writableDatabase.execSQL(
            "INSERT INTO kinks (id, label, isBuiltIn) VALUES ('K_USED', 'Used', 0), ('K_UNUSED', 'Unused', 0)",
        )
        val tryst = org.json.JSONObject().put("id", "e1").put("kinks", "custom:K_USED")
        val old = org.json.JSONObject()
            .put("schemaVersion", 19)
            .put("tables", org.json.JSONObject().put("encounters", org.json.JSONArray().put(tryst)).put("partners", org.json.JSONArray()))

        val summary = backup.inspect(password, ByteArrayInputStream(handMadeBackup(old)))

        assertTrue(summary.olderThanApp)
        assertNull("pre-0.7.2 backups don't say who made them", summary.appVersion)
        assertEquals(1, summary.replaceWouldRemove["cycle_period"])
        assertEquals("a kink the backup's trysts use comes back; the other doesn't", 1, summary.replaceWouldRemove["kinks"])
    }

    /** BKP-3: the restore result counts periods too. */
    @Test
    fun import_reportsPeriods() = runBlocking<Unit> {
        val cycles = app.tryst.data.repository.CycleRepository(manager)
        val owner = app.tryst.data.repository.CycleOwner(app.tryst.data.db.entity.CyclePeriodEntity.OWNER_PROFILE, ProfileEntity.SELF_ID)
        checkNotNull(cycles.startPeriod(owner, startEpochDay = 20_000))
        val result = backup.import(password, ByteArrayInputStream(exportedBytes()), wipeFirst = true)
        assertEquals(1, result.periods)
    }

    private companion object {
        const val MAGIC_BYTES = 8
        const val SALT_BYTES = 16
        const val HEADER_BYTES = MAGIC_BYTES + 1 + SALT_BYTES + 4
    }

    private suspend fun exportedBytes(): ByteArray = ByteArrayOutputStream().also { backup.export(password, it) }.toByteArray()

    private suspend fun seedOneTryst(): Long {
        val now = System.currentTimeMillis()
        manager.database().partnerDao().upsert(
            PartnerEntity("p1", "Sam", isAnonymous = false, color = null, note = null, archivedAt = null, createdAt = now, updatedAt = now),
        )
        encounters.save(EncounterEntity(id = "e1", startAt = now, createdAt = now, updatedAt = now), partnerIds = listOf("p1"))
        encounters.attachMedia("e1", "image/jpeg", ByteArrayInputStream("photo-bytes".repeat(500).toByteArray()))
        return now
    }

    @Test
    fun import_wrongPassword_isReportedAsWrongPassword_andChangesNothing() = runBlocking<Unit> {
        seedOneTryst()
        val bytes = exportedBytes()
        val e = assertThrows(BackupException::class.java) { runBlocking { backup.import("not-it", ByteArrayInputStream(bytes), wipeFirst = true) } }
        assertEquals(BackupError.WRONG_PASSWORD, e.kind)
        assertNotNull("existing data must survive a failed restore", encounters.get("e1"))
    }

    @Test
    fun import_ofANonBackupFile_isReportedAsNotABackup() {
        val e = assertThrows(BackupException::class.java) { runBlocking { backup.import(password, ByteArrayInputStream("hello world this is not a backup".toByteArray())) } }
        assertEquals(BackupError.NOT_A_BACKUP, e.kind)
    }

    @Test
    fun import_ofANewerFormat_isReportedAsNewerVersion() = runBlocking<Unit> {
        seedOneTryst()
        val bytes = exportedBytes()
        bytes[8] = 99 // a format version this build has never heard of
        val e = assertThrows(BackupException::class.java) { runBlocking { backup.import(password, ByteArrayInputStream(bytes)) } }
        assertEquals(BackupError.NEWER_VERSION, e.kind)
    }

    @Test
    fun import_ofATruncatedFile_isReportedAsDamaged_orWrongKey_andChangesNothing() = runBlocking<Unit> {
        seedOneTryst()
        val bytes = exportedBytes()
        val cut = bytes.copyOf(bytes.size / 2)
        val e = assertThrows(BackupException::class.java) { runBlocking { backup.import(password, ByteArrayInputStream(cut), wipeFirst = true) } }
        assertTrue("a cut-off file is damaged (or unreadable), never OTHER", e.kind == BackupError.DAMAGED || e.kind == BackupError.WRONG_PASSWORD)
        assertNotNull(encounters.get("e1"))
    }

    @Test
    fun export_andImport_reportCountsAndProgress() = runBlocking<Unit> {
        seedOneTryst()
        val exportStages = mutableListOf<BackupStage>()
        val out = ByteArrayOutputStream()
        val exported = backup.export(password, out) { exportStages += it.stage }
        assertEquals(1, exported.trysts)
        assertEquals(1, exported.partners)
        assertEquals(1, exported.photos)
        assertEquals("the reported size is the real size of the file", out.size().toLong(), exported.bytes)
        assertTrue(exportStages.containsAll(listOf(BackupStage.PREPARING, BackupStage.DATABASE, BackupStage.PHOTOS, BackupStage.FINISHING)))

        val importStages = mutableListOf<BackupStage>()
        val imported = backup.import(password, ByteArrayInputStream(out.toByteArray()), wipeFirst = true) { importStages += it.stage }
        assertEquals(1, imported.trysts)
        assertEquals(1, imported.partners)
        assertEquals(1, imported.photos)
        assertTrue(imported.withSettings)
        assertTrue(importStages.containsAll(listOf(BackupStage.PREPARING, BackupStage.DATABASE, BackupStage.PHOTOS, BackupStage.FINISHING)))
    }
}

// ⏣ HW ⏣ · code · tools · homelab · ⏣ HW ⏣
