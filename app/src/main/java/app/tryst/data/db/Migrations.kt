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

/** All migrations, in order. */
val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2)
