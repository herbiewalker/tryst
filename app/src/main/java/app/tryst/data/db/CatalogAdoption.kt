package app.tryst.data.db

import androidx.sqlite.db.SupportSQLiteDatabase
import app.tryst.data.db.entity.Act
import app.tryst.data.db.entity.EjaculationLocation
import app.tryst.data.db.entity.Kink
import app.tryst.data.db.entity.Occasion
import app.tryst.data.db.entity.Position
import app.tryst.data.db.entity.ToyType
import java.util.Locale

/**
 * Adopts **unknown built-in-style ids** found in the encounter log into the matching custom catalog
 * table — `acts`, `kinks`, `positions`, or `toys` (FDP-2 then FDP-4 / D-41). When a built-in catalog entry is removed from the shipped enums,
 * any encounter still referencing its bare enum name would resolve to nothing; this routine turns
 * each such id into a user-owned custom entry and rewrites the refs to `custom:<id>`, so the data
 * keeps its label and stays pickable/searchable — zero data loss.
 *
 * Deliberately **generic** (it adopts whatever the running binary doesn't recognize, rather than
 * shipping a hardcoded removed-id list) for two reasons:
 *  - the removed ids are themselves explicit strings that must not ship in the APK (the F-Droid
 *    policy issue this exists to fix), and
 *  - the same routine then also heals a **restore** of an older backup, which inserts raw rows
 *    without replaying migrations ([app.tryst.data.backup.BackupManager]).
 *
 * Labels are derived by [prettify] (generic enum-name → sentence case), never from shipped strings.
 * Idempotent: a second run finds no unknown bare ids and is a no-op.
 */
object CatalogAdoption {

    private const val CUSTOM_PREFIX = "custom:"

    fun adoptUnknownIds(db: SupportSQLiteDatabase) {
        // Guard by table existence: this routine is called from several migrations (v9→10, v10→11,
        // v11→12) and a category's custom table only exists from the migration that created it, so a
        // pre-existing category's ids stay bare until their table lands, then adopt then (or on restore,
        // where every table exists). Without the guard, adopting an id into a not-yet-created table
        // (e.g. `occasions`/`ejaculation_locations` during v9→10) would crash the upgrade.
        if (tableExists(db, "acts")) adopt(db, "acts", listOf("practicesPerformed", "practicesReceived"), Act.entries.mapTo(HashSet()) { it.name })
        if (tableExists(db, "kinks")) adopt(db, "kinks", listOf("kinks"), Kink.entries.mapTo(HashSet()) { it.name })
        if (tableExists(db, "positions")) adopt(db, "positions", listOf("positions"), Position.entries.mapTo(HashSet()) { it.name })
        if (tableExists(db, "toys")) adopt(db, "toys", listOf("toys"), ToyType.entries.mapTo(HashSet()) { it.name })
        if (tableExists(db, "occasions")) adopt(db, "occasions", listOf("occasions"), Occasion.entries.mapTo(HashSet()) { it.name })
        if (tableExists(db, "ejaculation_locations")) adoptEjaculation(db, EjaculationLocation.entries.mapTo(HashSet()) { it.name })
    }

    private fun tableExists(db: SupportSQLiteDatabase, name: String): Boolean = db.query("SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?", arrayOf(name)).use { it.moveToFirst() }

    /** Generic label for an adopted id: `TABLE_TOP` → `"Table top"`. */
    fun prettify(id: String): String = id.lowercase(Locale.US).replace('_', ' ').trim().replaceFirstChar { it.uppercaseChar() }

    private fun adopt(db: SupportSQLiteDatabase, table: String, columns: List<String>, known: Set<String>) {
        val isUnknown = { id: String -> id.isNotBlank() && !id.startsWith(CUSTOM_PREFIX) && id !in known }
        val unknown = collectUnknownIds(db, columns, isUnknown)
        if (unknown.isEmpty()) return
        val rowIdFor = ensureRows(db, table, unknown)
        for (col in columns) rewriteRefs(db, col, isUnknown) { CUSTOM_PREFIX + rowIdFor.getValue(it) }
    }

    /**
     * Ejaculation is stored as a per-orgasm map column (`idx=ID1|ID2,idx2=ID3`), not a flat comma set,
     * so the generic [adopt] can't scan it. Same idea though: collect unknown finish-location ids across
     * the map, adopt each into the `ejaculation_locations` table, and rewrite refs to `custom:<id>` while
     * preserving the `idx=` structure. Idempotent.
     */
    private fun adoptEjaculation(db: SupportSQLiteDatabase, known: Set<String>) {
        val col = "ejaculationLocations"
        val isUnknown = { id: String -> id.isNotBlank() && !id.startsWith(CUSTOM_PREFIX) && id !in known }
        val unknown = sortedSetOf<String>()
        db.query("SELECT $col FROM encounters WHERE $col IS NOT NULL AND $col != ''").use { c ->
            while (c.moveToNext()) ejaculationIds(c.getString(0)).filterTo(unknown) { isUnknown(it) }
        }
        if (unknown.isEmpty()) return
        val rowIdFor = ensureRows(db, "ejaculation_locations", unknown)
        val updates = ArrayList<Pair<String, String>>()
        db.query("SELECT id, $col FROM encounters WHERE $col IS NOT NULL AND $col != ''").use { c ->
            while (c.moveToNext()) {
                val old = c.getString(1)
                val new = remapEjaculation(old) { id -> if (isUnknown(id)) CUSTOM_PREFIX + rowIdFor.getValue(id) else id }
                if (new != old) updates.add(c.getString(0) to new)
            }
        }
        for ((encounterId, value) in updates) {
            db.execSQL("UPDATE encounters SET $col = ? WHERE id = ?", arrayOf(value, encounterId))
        }
    }

    /** Every finish-location id in a map-encoded value (`idx=ID1|ID2,...`). */
    private fun ejaculationIds(value: String): List<String> = value.split(',').flatMap { token ->
        val eq = token.indexOf('=')
        if (eq < 0) emptyList() else token.substring(eq + 1).split('|').filter { it.isNotBlank() }
    }

    /** Rewrites each id in a map-encoded value, keeping the `idx=` structure intact. */
    private fun remapEjaculation(value: String, map: (String) -> String): String = value.split(',').joinToString(",") { token ->
        val eq = token.indexOf('=')
        if (eq < 0) {
            token
        } else {
            val ids = token.substring(eq + 1).split('|').filter { it.isNotBlank() }.joinToString("|") { map(it) }
            token.substring(0, eq) + "=" + ids
        }
    }

    /** Every unknown bare id referenced anywhere in the log (sorted → deterministic). */
    private fun collectUnknownIds(db: SupportSQLiteDatabase, columns: List<String>, isUnknown: (String) -> Boolean): Set<String> {
        val unknown = sortedSetOf<String>()
        for (col in columns) {
            db.query("SELECT $col FROM encounters WHERE $col IS NOT NULL AND $col != ''").use { c ->
                while (c.moveToNext()) c.getString(0).split(',').filterTo(unknown) { isUnknown(it) }
            }
        }
        return unknown
    }

    /**
     * Ensures a custom row per unknown id and returns id → row id. The id doubles as the row id
     * (deterministic, idempotent); if the user already has a custom entry with the same label,
     * merge into it instead — the unique-label index forbids a second row anyway.
     */
    private fun ensureRows(db: SupportSQLiteDatabase, table: String, unknown: Set<String>): Map<String, String> {
        val rowIdFor = HashMap<String, String>()
        for (id in unknown) {
            val label = prettify(id)
            val existing = db.query("SELECT id FROM $table WHERE label = ?", arrayOf(label)).use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
            rowIdFor[id] = existing ?: id.also {
                db.execSQL("INSERT OR IGNORE INTO $table (id, label, isBuiltIn) VALUES (?, ?, 0)", arrayOf(id, label))
            }
        }
        return rowIdFor
    }

    /**
     * Rewrites matching ids in one comma-joined column, row by row in code (split → map → join).
     * No SQL string surgery: substring REPLACE has real hazards here (ids that contain other ids,
     * adjacent matches).
     */
    private fun rewriteRefs(db: SupportSQLiteDatabase, col: String, matches: (String) -> Boolean, replacement: (String) -> String) {
        val updates = ArrayList<Pair<String, String>>() // encounter id → new value
        db.query("SELECT id, $col FROM encounters WHERE $col IS NOT NULL AND $col != ''").use { c ->
            while (c.moveToNext()) {
                val old = c.getString(1)
                val new = old.split(',').joinToString(",") { id -> if (matches(id)) replacement(id) else id }
                if (new != old) updates.add(c.getString(0) to new)
            }
        }
        for ((encounterId, value) in updates) {
            db.execSQL("UPDATE encounters SET $col = ? WHERE id = ?", arrayOf(value, encounterId))
        }
    }
}
