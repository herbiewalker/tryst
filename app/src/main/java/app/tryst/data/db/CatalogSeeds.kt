package app.tryst.data.db

import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * The starter catalog entries a fresh install (and an upgrading install) begins with. Since Tryst
 * ships **no compiled-in category catalogs** (FDP-5 / D-41, F-Droid content policy), these neutral,
 * non-explicit starters are inserted as ordinary **user-owned rows** — exactly like anything the user
 * adds — so they show up on the management pages and can be renamed or removed like any other entry.
 *
 * These are the only predefined category labels that ship in the APK, and they're deliberately
 * innocuous. `kinks`, `positions`, and `toys` seed **nothing** (empty on first run).
 *
 * Seeding is idempotent (`INSERT OR IGNORE`): a stable id (its old enum name) means a re-run — or an
 * existing row an upgrade already adopted from logged data — is skipped, and the unique-label index
 * prevents a duplicate under a different id. Run on fresh install ([TrystDatabaseFactory] `onCreate`)
 * and on upgrade (`MIGRATION_11_12`, after adoption).
 */
object CatalogSeeds {

    /** table → list of (stable id, label). Only non-explicit starters; the rest of each catalog is user data. */
    private val SEEDS: Map<String, List<Pair<String, String>>> = mapOf(
        "acts" to listOf("KISSING" to "Kissing", "CUDDLING" to "Cuddling"),
        "occasions" to listOf("DATE_NIGHT" to "Date night", "ANNIVERSARY" to "Anniversary"),
        "ejaculation_locations" to listOf("NONE" to "Didn't finish", "IN_CONDOM" to "In condom"),
    )

    fun seed(db: SupportSQLiteDatabase) {
        for ((table, rows) in SEEDS) {
            for ((id, label) in rows) {
                db.execSQL("INSERT OR IGNORE INTO $table (id, label, isBuiltIn) VALUES (?, ?, 0)", arrayOf(id, label))
            }
        }
    }
}
