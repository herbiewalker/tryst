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

/** All migrations, in order. */
val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
