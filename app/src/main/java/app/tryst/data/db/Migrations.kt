package app.tryst.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v1 → v2: adds per-person orgasm counts, ejaculation locations, and performed/received
 * practices to `encounters`. All additive and nullable — existing rows keep their data.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE encounters ADD COLUMN orgasmCountSelf INTEGER")
        db.execSQL("ALTER TABLE encounters ADD COLUMN orgasmCountPartner INTEGER")
        db.execSQL("ALTER TABLE encounters ADD COLUMN ejaculationLocations TEXT")
        db.execSQL("ALTER TABLE encounters ADD COLUMN practicesPerformed TEXT")
        db.execSQL("ALTER TABLE encounters ADD COLUMN practicesReceived TEXT")
    }
}

/** v2 → v3: adds the positions column to `encounters` (additive, nullable). */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE encounters ADD COLUMN positions TEXT")
    }
}

/** v3 → v4: adds kinks, contexts (setting), and toys columns to `encounters` (additive, nullable). */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE encounters ADD COLUMN kinks TEXT")
        db.execSQL("ALTER TABLE encounters ADD COLUMN contexts TEXT")
        db.execSQL("ALTER TABLE encounters ADD COLUMN toys TEXT")
    }
}

/**
 * v4 → v5: partner sex/gender/relationship/photo (M4 hook); an `occasions` column on encounters;
 * and a custom `acts` table (mirrors `positions`). All additive — existing rows keep their data.
 * The practicesPerformed/Received columns are unchanged (TEXT); only their app-side type moved
 * from a Practice-set to string ids, which the same TEXT storage already holds.
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE partners ADD COLUMN sex TEXT")
        db.execSQL("ALTER TABLE partners ADD COLUMN gender TEXT")
        db.execSQL("ALTER TABLE partners ADD COLUMN relationshipType TEXT")
        db.execSQL("ALTER TABLE partners ADD COLUMN photoMediaId TEXT")
        db.execSQL("ALTER TABLE encounters ADD COLUMN occasions TEXT")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `acts` " +
                "(`id` TEXT NOT NULL, `label` TEXT NOT NULL, `isBuiltIn` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_acts_label` ON `acts` (`label`)")
    }
}

/**
 * v5 → v6: per-partner orgasm counts on encounters (additive). The ejaculationLocations column
 * is unchanged (still TEXT) — only its app-side encoding moved from a Set to an orgasm-index→
 * location map, which the same TEXT storage holds.
 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE encounters ADD COLUMN partnerOrgasms TEXT")
    }
}

/**
 * v6 → v7: partner **demographics** (birthDate / ethnicity / height / bodyType / location, additive
 * nullable on `partners`) and a new single-row **`profile`** table for the user's own photo +
 * demographics. All additive — existing rows keep their data; the `profile` table starts empty.
 */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE partners ADD COLUMN birthDate INTEGER")
        db.execSQL("ALTER TABLE partners ADD COLUMN ethnicity TEXT")
        db.execSQL("ALTER TABLE partners ADD COLUMN height TEXT")
        db.execSQL("ALTER TABLE partners ADD COLUMN bodyType TEXT")
        db.execSQL("ALTER TABLE partners ADD COLUMN location TEXT")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `profile` (" +
                "`id` TEXT NOT NULL, `displayName` TEXT, `photoMediaId` TEXT, " +
                "`sex` TEXT, `gender` TEXT, `birthDate` INTEGER, `ethnicity` TEXT, " +
                "`height` TEXT, `bodyType` TEXT, `location` TEXT, `note` TEXT, " +
                "`updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        )
    }
}

/**
 * v7 → v8: **data-only** normalization — no DDL/schema change (every column already exists), so the
 * exported v8 schema is structurally identical to v7. The category enums moved, so the stored ids
 * are rewritten to match:
 *  - **FIX-3** delete Position `ORAL_69_SIDE` → remap its stored refs to `LYING_ORAL`.
 *  - **FIX-4** move `WATCHING_PORN` from acts (`Practice`) to kinks (`Kink`): add it to `kinks` where
 *    it appears in either practice column, then strip it out of both practice columns.
 *  - **FIX-5 / FIX-6** promote user **custom** positions/acts to built-ins: for each, find its custom
 *    row by label (trimmed, case-insensitive); if found, rewrite `custom:<uuid>` refs to the new enum
 *    name in the relevant `encounters` column(s) and delete the now-redundant custom row. A label that
 *    doesn't match simply leaves that item as a custom entry — **no data is ever lost**.
 *
 * Refs are comma-joined string-id sets; ids never contain commas, so substring `REPLACE` is safe and
 * any duplicate it produces is de-duped when the set is read (`Converters.stringToPositionSet`).
 * NOTE: backup *restore* inserts rows raw and does NOT replay this (`BackupManager`), so re-export
 * after upgrading to refresh a backup, or its rows keep the old ids.
 */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // FIX-3: ORAL_69_SIDE → LYING_ORAL
        db.execSQL("UPDATE encounters SET positions = REPLACE(positions, 'ORAL_69_SIDE', 'LYING_ORAL') WHERE positions LIKE '%ORAL_69_SIDE%'")

        // FIX-4: copy WATCHING_PORN into kinks where it appears in an act column...
        db.execSQL(
            "UPDATE encounters SET kinks = CASE WHEN kinks IS NULL OR kinks = '' THEN 'WATCHING_PORN' " +
                "ELSE kinks || ',WATCHING_PORN' END " +
                "WHERE (practicesPerformed LIKE '%WATCHING_PORN%' OR practicesReceived LIKE '%WATCHING_PORN%') " +
                "AND (kinks IS NULL OR kinks NOT LIKE '%WATCHING_PORN%')",
        )
        // ...then remove it from both act columns (comma-list element removal; NULL out if emptied).
        for (col in listOf("practicesPerformed", "practicesReceived")) {
            db.execSQL(
                "UPDATE encounters SET $col = NULLIF(TRIM(REPLACE(',' || $col || ',', ',WATCHING_PORN,', ','), ','), '') " +
                    "WHERE $col LIKE '%WATCHING_PORN%'",
            )
        }

        // FIX-5: custom positions → built-in Position (refs in `positions`).
        promoteCustom(
            db,
            table = "positions",
            columns = listOf("positions"),
            byLabel = mapOf(
                // Keys = the user's ACTUAL custom labels (verified against their decrypted backup),
                // matched NOCASE/trim; the fuller display name lives on the enum. The originally-described
                // names are kept as aliases so the promotion still takes if a live row was since renamed.
                "Anal - sex toy" to "ANAL_TOY",
                "Anal - sexy toy" to "ANAL_TOY",
                "Modified missionary" to "MODIFIED_MISSIONARY",
                "Oral - edge of bed" to "ORAL_EDGE_OF_BED",
                "Missionary - standing edge" to "MISSIONARY_STANDING_EDGE",
                "Missionary - Standing Edge of Bed" to "MISSIONARY_STANDING_EDGE",
                "Reverse cowgirl - legs under" to "REVERSE_COWGIRL_MODIFIED",
                "Reverse Cowgirl - Modified" to "REVERSE_COWGIRL_MODIFIED",
            ),
        )
        // FIX-6: custom acts → built-in Practice (refs in both gave/received columns).
        promoteCustom(
            db,
            table = "acts",
            columns = listOf("practicesPerformed", "practicesReceived"),
            byLabel = mapOf(
                "Eat own creampie (EOC)" to "EAT_OWN_CREAMPIE",
                "Lick after sex" to "LICK_PUSSY_AFTER",
                "Lick Pussy after Sex" to "LICK_PUSSY_AFTER",
            ),
        )
    }

    /**
     * For each [byLabel] entry, look up the custom row in [table] whose label matches (trimmed,
     * NOCASE); if found, replace `custom:<id>` with the built-in enum name across [columns] on
     * `encounters`, then delete the custom row. No match → no-op (item safely stays custom). Done in
     * Kotlin (not pure SQL) so a missing label can't produce `REPLACE(col, NULL, …)` = NULL and wipe
     * a column.
     */
    private fun promoteCustom(
        db: SupportSQLiteDatabase,
        table: String,
        columns: List<String>,
        byLabel: Map<String, String>,
    ) {
        for ((label, enumName) in byLabel) {
            val id = db.query(
                "SELECT id FROM $table WHERE TRIM(label) = ? COLLATE NOCASE LIMIT 1",
                arrayOf<Any?>(label.trim()),
            ).use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: continue

            val token = "custom:$id"
            for (col in columns) {
                db.execSQL(
                    "UPDATE encounters SET $col = REPLACE($col, ?, ?) WHERE $col LIKE ?",
                    arrayOf<Any?>(token, enumName, "%$token%"),
                )
            }
            db.execSQL("DELETE FROM $table WHERE id = ?", arrayOf<Any?>(id))
        }
    }
}

/** All migrations, in order. */
val ALL_MIGRATIONS = arrayOf(
    MIGRATION_1_2,
    MIGRATION_2_3,
    MIGRATION_3_4,
    MIGRATION_4_5,
    MIGRATION_5_6,
    MIGRATION_6_7,
    MIGRATION_7_8,
)
