package app.tryst.data.backup

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import app.tryst.core.crypto.BackupCrypto
import app.tryst.core.security.Pbkdf2
import app.tryst.core.session.SessionManager
import app.tryst.data.db.CatalogAdoption
import app.tryst.data.media.EncryptedMediaStore
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Full encrypted backup / restore (M5). Because the live data is encrypted under a device-bound
 * Keystore key (not portable), a backup **decrypts** the data while unlocked and **re-encrypts** the
 * whole container under a key derived from the user's backup password. Restore reverses it,
 * re-encrypting media under the current device's key.
 *
 * File layout: `MAGIC(8) | version(1) | salt(16) | iterations(4 BE)` in the clear, then a
 * [BackupCrypto] stream of a ZIP: `data.json` (every table, generic column dump) + `media/<id>`
 * (decrypted photo bytes). See docs/EXPORT_FORMAT.md.
 */
@Singleton
class BackupManager @Inject constructor(
    private val session: SessionManager,
    private val mediaStore: EncryptedMediaStore,
) {
    @Suppress("CyclomaticComplexMethod") // Straight-line assembly of the backup container (header + zip entries).
    suspend fun export(password: String, out: OutputStream): Unit = withContext(Dispatchers.IO) {
        val db = session.database().openHelper.writableDatabase
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        val iterations = Pbkdf2.DEFAULT_ITERATIONS

        out.write(MAGIC)
        out.write(FORMAT_VERSION)
        out.write(salt)
        out.write(ByteBuffer.allocate(4).putInt(iterations).array())
        out.flush()

        val key = Pbkdf2.derive(password, salt, iterations)
        ZipOutputStream(BackupCrypto.encryptingStream(key, out)).use { zip ->
            zip.putNextEntry(ZipEntry("data.json"))
            zip.write(dumpDatabase(db).toString().toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            // Encounter photos (rows in the media table) AND partner avatars (blobs referenced only by
            // Partner.photoMediaId, with no media-table row) — both must be backed up. They share the
            // media/<id> namespace; import saves any such entry by id, and the restored rows point back.
            val blobIds = LinkedHashSet<String>()
            db.query("SELECT id FROM media").use { c -> while (c.moveToNext()) blobIds.add(c.getString(0)) }
            db.query("SELECT photoMediaId FROM partners WHERE photoMediaId IS NOT NULL").use { c ->
                while (c.moveToNext()) blobIds.add(c.getString(0))
            }
            db.query("SELECT photoMediaId FROM profile WHERE photoMediaId IS NOT NULL").use { c ->
                while (c.moveToNext()) blobIds.add(c.getString(0))
            }
            // Person portraits (v15+): each row references its own blob; include them all so a restore
            // brings the whole portrait album back.
            db.query("SELECT mediaBlobId FROM person_photo").use { c ->
                while (c.moveToNext()) blobIds.add(c.getString(0))
            }
            for (id in blobIds) {
                if (!mediaStore.fileFor(id).exists()) continue // skip a dangling reference rather than fail
                zip.putNextEntry(ZipEntry("media/$id"))
                mediaStore.open(id).use { it.copyTo(zip) } // decrypt → re-encrypted by the container
                zip.closeEntry()
            }
        }
    }

    /**
     * Restore an encrypted backup. When [wipeFirst] is true (Bundle-E Q1), every row in the backup's
     * `TABLES` list is deleted before the backup rows are inserted — the whole wipe+restore runs in a
     * single DB transaction so a failure rolls both halves back. When false (advanced), the previous
     * `INSERT OR REPLACE` merge semantics apply (with the known cascade side-effect on cross-refs).
     */
    @Suppress("CyclomaticComplexMethod", "NestedBlockDepth", "LongMethod") // Multi-phase restore is clearer inline than split.
    suspend fun import(
        password: String,
        input: InputStream,
        wipeFirst: Boolean = false,
    ): Unit = withContext(Dispatchers.IO) {
        val magic = ByteArray(MAGIC.size).also { readFully(input, it) }
        require(magic.contentEquals(MAGIC)) { "Not a Tryst backup file" }
        require(input.read() == FORMAT_VERSION) { "Unsupported backup version" }
        val salt = ByteArray(SALT_BYTES).also { readFully(input, it) }
        val iterations = ByteBuffer.wrap(ByteArray(4).also { readFully(input, it) }).int
        // The iteration count comes from the (untrusted) file header. Bound it: a crafted value like
        // Int.MAX_VALUE would otherwise hang the app for minutes deriving the key (DoS).
        require(iterations in MIN_ITERATIONS..MAX_ITERATIONS) { "Unsupported backup version" }

        val key = Pbkdf2.derive(password, salt, iterations)
        val db = session.database().openHelper.writableDatabase
        val stagedIds = mutableListOf<String>()
        try {
            // Phase 1: drain the ZIP. Read data.json into memory; write every media blob to its
            // STAGING path (`<id>.enc.staging`) — never touching the live `<id>.enc` until after the
            // DB commit succeeds. A mid-loop failure here leaves the on-disk media/ dir and the DB
            // untouched (Bundle-C N5).
            var dataJson: JSONObject? = null
            ZipInputStream(BackupCrypto.decryptingStream(key, input)).use { zip ->
                var entry: ZipEntry? = zip.nextEntry
                while (entry != null) {
                    val name = entry.name
                    when {
                        name == "data.json" -> dataJson = JSONObject(zip.readBytes().toString(Charsets.UTF_8))
                        name.startsWith("media/") -> {
                            val id = name.removePrefix("media/")
                            mediaStore.saveStaged(id, zip)
                            stagedIds += id
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
            val root = dataJson ?: throw IOException("Backup missing data.json")

            // Phase 2: DB transaction — wipe (if requested) + restore. Both halves commit as one
            // atomic unit, so a mid-restore failure rolls the wipe back and leaves the user's data
            // exactly where it was.
            db.beginTransaction()
            try {
                db.execSQL("PRAGMA defer_foreign_keys = TRUE")
                if (wipeFirst) {
                    // Children before parents so FK order is respected even without the pragma.
                    for (table in TABLES.reversed()) db.execSQL("DELETE FROM $table")
                }
                restoreDatabaseIntoTx(db, root)
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }

            // Phase 3: promote staged blobs into their live paths. DB is now committed; the restored
            // media/person_photo/partner/profile rows expect these files to exist. Promotion happens
            // after commit so a failure here doesn't corrupt the DB rollback in Phase 2.
            for (id in stagedIds) mediaStore.promoteStaged(id)

            // Phase 4: (wipe-first only) drop any remaining blob whose id isn't in the restored set —
            // that's the previous user data's media files, orphaned now that no row references them.
            if (wipeFirst) mediaStore.deleteOrphans(stagedIds.toSet())

            // Restore inserts rows raw — it does NOT replay migrations — so a backup made before a
            // catalog trim can reintroduce since-removed built-in act/kink ids. Adopt them into the
            // custom tables exactly like MIGRATION_9_10 does; no-op for current backups (idempotent).
            CatalogAdoption.adoptUnknownIds(db)
        } finally {
            // Any staging file left over (loop-fail before Phase 3, or Phase-3 rename failure) is
            // just accumulated junk if we don't clean it. Best-effort sweep — doesn't touch live blobs.
            mediaStore.clearAllStaged()
        }
    }

    @Suppress("NestedBlockDepth")
    private fun dumpDatabase(db: androidx.sqlite.db.SupportSQLiteDatabase): JSONObject {
        val tables = JSONObject()
        for (table in TABLES) {
            val rows = JSONArray()
            db.query("SELECT * FROM $table").use { c ->
                while (c.moveToNext()) {
                    val row = JSONObject()
                    for (i in 0 until c.columnCount) {
                        val col = c.getColumnName(i)
                        when (c.getType(i)) {
                            Cursor.FIELD_TYPE_NULL -> row.put(col, JSONObject.NULL)
                            Cursor.FIELD_TYPE_INTEGER -> row.put(col, c.getLong(i))
                            Cursor.FIELD_TYPE_FLOAT -> row.put(col, c.getDouble(i))
                            else -> row.put(col, c.getString(i))
                        }
                    }
                    rows.put(row)
                }
            }
            tables.put(table, rows)
        }
        return JSONObject().put("schemaVersion", db.version).put("tables", tables)
    }

    /**
     * Insert every backup row into the DB. Runs inside a caller-owned transaction so wipe+restore
     * can commit atomically when the caller wants that (Bundle-E Q1). The caller is responsible for
     * `beginTransaction` / `setTransactionSuccessful` / `endTransaction` and for setting
     * `defer_foreign_keys` before this method starts inserting.
     */
    @Suppress("CyclomaticComplexMethod", "NestedBlockDepth")
    private fun restoreDatabaseIntoTx(db: androidx.sqlite.db.SupportSQLiteDatabase, root: JSONObject) {
        val tables = root.optJSONObject("tables") ?: return
        for (table in TABLES) {
            val rows = tables.optJSONArray(table) ?: continue
            val notNullDefaults = notNullColumnDefaults(db, table)
            for (i in 0 until rows.length()) {
                val row = rows.getJSONObject(i)
                val values = ContentValues()
                val keys = row.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    // Column names come from the untrusted backup JSON, and the framework's
                    // insert() appends them into the SQL column list unquoted — so reject
                    // anything that isn't a plain SQL identifier (real columns always match)
                    // to deny SQL injection via a crafted key. Defence-in-depth: import already
                    // requires the backup's password (AEAD-authenticated).
                    if (!COLUMN_NAME.matches(k)) continue
                    when {
                        row.isNull(k) -> values.putNull(k)
                        else -> when (val v = row.get(k)) {
                            is Int -> values.put(k, v.toLong())
                            is Long -> values.put(k, v)
                            is Double -> values.put(k, v)
                            is Boolean -> values.put(k, if (v) 1L else 0L)
                            else -> values.put(k, v.toString())
                        }
                    }
                }
                if (table == "media") {
                    // The stored path is device-specific — point it at this device's media dir.
                    values.getAsString("id")?.let { values.put("encFilePath", mediaStore.fileFor(it).absolutePath) }
                }
                // Backfill any NOT NULL column the backup lacks with the live schema's DEFAULT.
                // A pre-Vn backup naturally omits every column added in Vn+ migrations; without
                // this, the first row we try to insert blows up on the column's NOT-NULL constraint
                // on any *fresh* install whose entity forgot @ColumnInfo(defaultValue=…). Rows that
                // do carry the key stay as-is (defer_foreign_keys still governs FK ordering).
                for ((col, default) in notNullDefaults) {
                    if (values.containsKey(col)) continue
                    when (default) {
                        is Long -> values.put(col, default)
                        is Double -> values.put(col, default)
                        else -> values.put(col, default.toString())
                    }
                }
                db.insert(table, SQLiteDatabase.CONFLICT_REPLACE, values)
            }
        }
    }

    /**
     * `PRAGMA table_info(t)` rows for every NOT NULL column of [table] that carries a SQL DEFAULT,
     * mapped `column → default value` (typed to what ContentValues will accept). Columns without a
     * DEFAULT are deliberately absent — restore can't invent one safely for them, so an
     * older-backup row missing a required column without any default still fails loudly rather than
     * silently getting a fabricated zero/blank.
     */
    @Suppress("CyclomaticComplexMethod", "LoopWithTooManyJumpStatements") // Guard-clause loop over PRAGMA rows.
    private fun notNullColumnDefaults(
        db: androidx.sqlite.db.SupportSQLiteDatabase,
        table: String,
    ): Map<String, Any> {
        val defaults = mutableMapOf<String, Any>()
        db.query("PRAGMA table_info(`$table`)").use { c ->
            val nameIdx = c.getColumnIndex("name")
            val typeIdx = c.getColumnIndex("type")
            val notNullIdx = c.getColumnIndex("notnull")
            val defaultIdx = c.getColumnIndex("dflt_value")
            val pkIdx = c.getColumnIndex("pk")
            while (c.moveToNext()) {
                if (c.getInt(notNullIdx) == 0) continue
                if (c.getInt(pkIdx) != 0) continue // PKs must come from the row itself.
                if (c.isNull(defaultIdx)) continue
                val raw = c.getString(defaultIdx).trim().trim('\'') // SQLite quotes text defaults.
                val typed: Any = when (c.getString(typeIdx).uppercase()) {
                    "INTEGER" -> raw.toLongOrNull() ?: continue
                    "REAL" -> raw.toDoubleOrNull() ?: continue
                    else -> raw // TEXT / BLOB / no-affinity — pass the literal through.
                }
                defaults[c.getString(nameIdx)] = typed
            }
        }
        return defaults
    }

    private fun readFully(input: InputStream, buf: ByteArray) {
        var off = 0
        while (off < buf.size) {
            val n = input.read(buf, off, buf.size - off)
            if (n < 0) throw IOException("Truncated backup")
            off += n
        }
    }

    private companion object {
        val MAGIC = "TRYSTBK1".toByteArray(Charsets.US_ASCII) // 8 bytes
        const val FORMAT_VERSION = 1
        const val SALT_BYTES = 16

        // A plain SQL identifier — used to vet untrusted backup column names before they reach the
        // framework's (unquoted) INSERT column list.
        val COLUMN_NAME = Regex("[A-Za-z_][A-Za-z0-9_]*")

        // Sane bounds for the file-supplied PBKDF2 iteration count (default is 600k). The upper
        // bound caps key-derivation time so a malicious header can't freeze the app.
        const val MIN_ITERATIONS = 100_000
        const val MAX_ITERATIONS = 5_000_000

        // Insert order respects foreign keys (parents first); defer_foreign_keys also guards it.
        // `profile` has no FKs (single self row) — order is irrelevant for it.
        //
        // `recent_searches` is deliberately ABSENT (D-42): a backup is the one artefact that leaves the
        // device, and the user's search terms have no business travelling in it. Because this list also
        // drives restore, an import leaves the local search history untouched rather than wiping it.
        val TABLES = listOf(
            "partners", "profile", "locations", "tags", "positions", "acts", "kinks", "toys",
            "occasions", "ejaculation_locations",
            "encounters", "media", "person_photo",
            "encounter_partner", "encounter_position", "encounter_tag",
        )
    }
}
