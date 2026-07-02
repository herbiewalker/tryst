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
 * from a Act-set to string ids, which the same TEXT storage already holds.
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
 * exported v8 schema is structurally identical to v7. A few category members moved, so the stored ids
 * are rewritten to match:
 *  - delete Position `ORAL_69_SIDE` → remap its stored refs to `LYING_ORAL`.
 *  - move `WATCHING_PORN` from acts (`Act`) to kinks (`Kink`): add it to `kinks` where it appears
 *    in either practice column, then strip it from both practice columns.
 *
 * v8 also adds several built-in `Position`/`Act`/`Place` values in `Enums.kt` — additive, no
 * migration needed; any pre-existing custom entry with a similar name simply stays a custom entry.
 *
 * Refs are comma-joined string-id sets; ids never contain commas, so substring `REPLACE` is safe and
 * any duplicate it produces is de-duped when the set is read (`Converters.stringToPositionSet`).
 * NOTE: backup *restore* inserts rows raw and does NOT replay this (`BackupManager`), so re-export
 * after upgrading to refresh a backup, or its rows keep the old ids.
 */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // ORAL_69_SIDE → LYING_ORAL
        db.execSQL("UPDATE encounters SET positions = REPLACE(positions, 'ORAL_69_SIDE', 'LYING_ORAL') WHERE positions LIKE '%ORAL_69_SIDE%'")

        // Move WATCHING_PORN from acts to kinks: add it to `kinks` where it appears in an act column...
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
    }
}

/**
 * v8 → v9: adds a custom **`kinks`** table (mirrors `acts`/`positions`), so kinks become
 * user-configurable string ids instead of a fixed enum. The `encounters.kinks` column is unchanged
 * (still TEXT) — only its app-side type moved from a `Kink`-set to string ids, and a built-in kink's
 * id **is** its old enum name, so existing comma-joined values stay valid with no data rewrite. The
 * table starts empty (built-in kinks live in the `Kink` enum; only user-defined kinks are stored here).
 */
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `kinks` " +
                "(`id` TEXT NOT NULL, `label` TEXT NOT NULL, `isBuiltIn` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_kinks_label` ON `kinks` (`label`)")
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
    MIGRATION_8_9,
)
