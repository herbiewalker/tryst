package app.tryst.data.backup

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import app.tryst.core.crypto.BackupCrypto
import app.tryst.core.security.Pbkdf2
import app.tryst.core.session.SessionManager
import app.tryst.data.media.EncryptedMediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
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
            db.query("SELECT id FROM media").use { c ->
                while (c.moveToNext()) {
                    val id = c.getString(0)
                    zip.putNextEntry(ZipEntry("media/$id"))
                    mediaStore.open(id).use { it.copyTo(zip) } // decrypt → re-encrypted by the container
                    zip.closeEntry()
                }
            }
        }
    }

    suspend fun import(password: String, input: InputStream): Unit = withContext(Dispatchers.IO) {
        val magic = ByteArray(MAGIC.size).also { readFully(input, it) }
        require(magic.contentEquals(MAGIC)) { "Not a Tryst backup file" }
        require(input.read() == FORMAT_VERSION) { "Unsupported backup version" }
        val salt = ByteArray(SALT_BYTES).also { readFully(input, it) }
        val iterations = ByteBuffer.wrap(ByteArray(4).also { readFully(input, it) }).int

        val key = Pbkdf2.derive(password, salt, iterations)
        val db = session.database().openHelper.writableDatabase
        ZipInputStream(BackupCrypto.decryptingStream(key, input)).use { zip ->
            var entry: ZipEntry? = zip.nextEntry
            while (entry != null) {
                val name = entry.name
                when {
                    name == "data.json" -> restoreDatabase(db, JSONObject(zip.readBytes().toString(Charsets.UTF_8)))
                    name.startsWith("media/") -> mediaStore.save(name.removePrefix("media/"), zip) // re-encrypts for this device
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
    }

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

    private fun restoreDatabase(db: androidx.sqlite.db.SupportSQLiteDatabase, root: JSONObject) {
        val tables = root.optJSONObject("tables") ?: return
        db.beginTransaction()
        try {
            db.execSQL("PRAGMA defer_foreign_keys = TRUE")
            for (table in TABLES) {
                val rows = tables.optJSONArray(table) ?: continue
                for (i in 0 until rows.length()) {
                    val row = rows.getJSONObject(i)
                    val values = ContentValues()
                    val keys = row.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        if (row.isNull(k)) { values.putNull(k); continue }
                        when (val v = row.get(k)) {
                            is Int -> values.put(k, v.toLong())
                            is Long -> values.put(k, v)
                            is Double -> values.put(k, v)
                            is Boolean -> values.put(k, if (v) 1L else 0L)
                            else -> values.put(k, v.toString())
                        }
                    }
                    if (table == "media") {
                        // The stored path is device-specific — point it at this device's media dir.
                        values.getAsString("id")?.let { values.put("encFilePath", mediaStore.fileFor(it).absolutePath) }
                    }
                    db.insert(table, SQLiteDatabase.CONFLICT_REPLACE, values)
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
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
        // Insert order respects foreign keys (parents first); defer_foreign_keys also guards it.
        val TABLES = listOf(
            "partners", "locations", "tags", "positions", "acts",
            "encounters", "media", "encounter_partner", "encounter_position", "encounter_tag",
        )
    }
}
