package app.tryst.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Validates the v1 → v2 migration against the exported schemas. Uses the framework SQLite
 * helper (unencrypted): the migration is plain DDL, so this checks structural correctness;
 * the real SQLCipher path runs the same SQL on next launch of an existing install.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val dbName = "migration-test.db"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        TrystDatabase::class.java,
    )

    @Test
    fun migrate1To7_preservesRowsAndAddsColumns() {
        helper.createDatabase(dbName, 1).use { db ->
            db.execSQL(
                "INSERT INTO encounters (id, startAt, protectionUsed, createdAt, updatedAt) " +
                    "VALUES ('e1', 1000, 'CONDOM', 1, 1)",
            )
            db.execSQL(
                "INSERT INTO partners (id, displayName, isAnonymous, createdAt, updatedAt) " +
                    "VALUES ('p1', 'Alex', 0, 1, 1)",
            )
        }

        // Applies the full migration chain and validates the schema equals the exported v7 schema.
        helper.runMigrationsAndValidate(
            dbName,
            7,
            true,
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4,
            MIGRATION_4_5,
            MIGRATION_5_6,
            MIGRATION_6_7,
        ).use { db ->
            db.query(
                "SELECT id, positions, kinks, occasions, partnerOrgasms FROM encounters WHERE id = 'e1'",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("e1", cursor.getString(0))
                assertTrue("positions column should be NULL for migrated row", cursor.isNull(1))
                assertTrue("kinks column should be NULL for migrated row", cursor.isNull(2))
                assertTrue("occasions column should be NULL for migrated row", cursor.isNull(3))
                assertTrue("partnerOrgasms column should be NULL for migrated row", cursor.isNull(4))
            }
            db.query(
                "SELECT displayName, sex, relationshipType, birthDate, ethnicity, height, bodyType, location " +
                    "FROM partners WHERE id = 'p1'",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Alex", cursor.getString(0))
                assertTrue("sex column should be NULL for migrated row", cursor.isNull(1))
                assertTrue("relationshipType column should be NULL for migrated row", cursor.isNull(2))
                assertTrue("birthDate (v7) should be NULL for migrated row", cursor.isNull(3))
                assertTrue("ethnicity (v7) should be NULL for migrated row", cursor.isNull(4))
                assertTrue("height (v7) should be NULL for migrated row", cursor.isNull(5))
                assertTrue("bodyType (v7) should be NULL for migrated row", cursor.isNull(6))
                assertTrue("location (v7) should be NULL for migrated row", cursor.isNull(7))
            }
            // The new single-row profile table exists and starts empty.
            db.query("SELECT COUNT(*) FROM profile").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
            }
        }
    }

    /**
     * v7 → v8 is a **data** migration (no DDL): moved category values must rewrite the stored string
     * ids without losing any encounter data. Covers the `ORAL_69_SIDE`→`LYING_ORAL` remap, the
     * `WATCHING_PORN` act→kink move (gave + received), and that custom (`custom:<uuid>`) refs pass
     * through untouched.
     */
    @Test
    fun migrate7To8_rewritesMovedValuesAndPreservesCustom() {
        helper.createDatabase(dbName, 7).use { db ->
            db.execSQL("INSERT INTO positions (id, label, isBuiltIn) VALUES ('posCustom', 'My Custom Position', 0)")
            db.execSQL("INSERT INTO acts (id, label, isBuiltIn) VALUES ('actCustom', 'My Custom Act', 0)")

            // e1: a deleted position + a moved act (gave & received) alongside custom refs that must survive.
            db.execSQL(
                "INSERT INTO encounters (id, startAt, protectionUsed, positions, practicesPerformed, practicesReceived, createdAt, updatedAt) " +
                    "VALUES ('e1', 1000, '', 'ORAL_69_SIDE,custom:posCustom', 'WATCHING_PORN,custom:actCustom', 'WATCHING_PORN', 1, 1)",
            )
            // e2: a control row that must pass through untouched.
            db.execSQL(
                "INSERT INTO encounters (id, startAt, protectionUsed, positions, practicesPerformed, kinks, createdAt, updatedAt) " +
                    "VALUES ('e2', 2000, '', 'MISSIONARY', 'ORAL', 'SPANKING', 1, 1)",
            )
        }

        helper.runMigrationsAndValidate(dbName, 8, true, MIGRATION_7_8).use { db ->
            db.query(
                "SELECT positions, practicesPerformed, practicesReceived, kinks FROM encounters WHERE id = 'e1'",
            ).use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("LYING_ORAL,custom:posCustom", c.getString(0)) // ORAL_69_SIDE remapped; custom untouched
                assertEquals("custom:actCustom", c.getString(1)) // WATCHING_PORN stripped; custom untouched
                assertTrue("WATCHING_PORN should be stripped from received acts", c.isNull(2))
                assertEquals("WATCHING_PORN", c.getString(3)) // moved into kinks
            }
            db.query(
                "SELECT positions, practicesPerformed, kinks FROM encounters WHERE id = 'e2'",
            ).use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("MISSIONARY", c.getString(0))
                assertEquals("ORAL", c.getString(1))
                assertEquals("SPANKING", c.getString(2))
            }
            // Custom rows are preserved (no promotion/deletion in v8).
            db.query("SELECT COUNT(*) FROM positions WHERE id = 'posCustom'").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(1, c.getInt(0))
            }
            db.query("SELECT COUNT(*) FROM acts WHERE id = 'actCustom'").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(1, c.getInt(0))
            }
        }
    }

    /**
     * v8 → v9 adds the custom `kinks` table (DDL only). The `encounters.kinks` column is unchanged, so
     * existing comma-joined kink ids must pass through untouched, the new table starts empty (built-in
     * kinks live in the enum), and a custom kink row can be inserted.
     */
    @Test
    fun migrate8To9_addsKinkTableAndPreservesKinkRefs() {
        helper.createDatabase(dbName, 8).use { db ->
            db.execSQL(
                "INSERT INTO encounters (id, startAt, protectionUsed, kinks, createdAt, updatedAt) " +
                    "VALUES ('e1', 1000, '', 'SPANKING,CHOKING', 1, 1)",
            )
        }

        helper.runMigrationsAndValidate(dbName, 9, true, MIGRATION_8_9).use { db ->
            db.query("SELECT kinks FROM encounters WHERE id = 'e1'").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("SPANKING,CHOKING", c.getString(0)) // unchanged
            }
            // The new kinks table exists and starts empty.
            db.query("SELECT COUNT(*) FROM kinks").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(0, c.getInt(0))
            }
            // Custom kinks can be inserted (table writable; unique-label index present).
            db.execSQL("INSERT INTO kinks (id, label, isBuiltIn) VALUES ('k1', 'My Custom Kink', 0)")
            db.query("SELECT label FROM kinks WHERE id = 'k1'").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("My Custom Kink", c.getString(0))
            }
        }
    }

    /**
     * v9 → v10 is a **data** migration (no DDL): the built-in Act/Kink catalogs were trimmed (FDP-2)
     * and every ref to a removed built-in must be adopted into the custom tables (prettified label)
     * with the ref rewritten to `custom:<id>`. Covers: multiple removed ids in one column — including
     * a pair where one id is a substring of the other (`CREAMPIE` ⊂ `ANAL_CREAMPIE`), the classic
     * SQL-REPLACE corruption case; merge into a pre-existing custom entry whose label collides;
     * surviving built-ins and `custom:` refs passing through untouched; and idempotence of the
     * underlying [CatalogAdoption] (also run post-restore by BackupManager).
     */
    @Test
    fun migrate9To10_adoptsRemovedBuiltInsAsCustom() {
        helper.createDatabase(dbName, 9).use { db ->
            db.execSQL("INSERT INTO acts (id, label, isBuiltIn) VALUES ('actCustom', 'My Custom Act', 0)")
            // Label collides with prettify("FOOT_PLAY") → refs must merge into this row, no new row.
            db.execSQL("INSERT INTO acts (id, label, isBuiltIn) VALUES ('preexisting', 'Foot play', 0)")
            db.execSQL("INSERT INTO kinks (id, label, isBuiltIn) VALUES ('kCustom', 'My Custom Kink', 0)")

            // e1: removed ids (incl. the substring pair CREAMPIE ⊂ ANAL_CREAMPIE) + a custom ref, each
            // handled independently. No built-ins ship any more (FDP-5), so bare ids all adopt.
            db.execSQL(
                "INSERT INTO encounters (id, startAt, protectionUsed, practicesPerformed, practicesReceived, createdAt, updatedAt) " +
                    "VALUES ('e1', 1000, '', 'CREAMPIE,ANAL_CREAMPIE,custom:actCustom', 'FOOT_PLAY', 1, 1)",
            )
            // e2: control — a custom ref must pass through untouched.
            db.execSQL(
                "INSERT INTO encounters (id, startAt, protectionUsed, practicesPerformed, createdAt, updatedAt) " +
                    "VALUES ('e2', 2000, '', 'custom:actCustom', 1, 1)",
            )
            // e3: removed kinks beside a custom ref.
            db.execSQL(
                "INSERT INTO encounters (id, startAt, protectionUsed, kinks, createdAt, updatedAt) " +
                    "VALUES ('e3', 3000, '', 'CHOKING,GAGGING,custom:kCustom', 1, 1)",
            )
        }

        helper.runMigrationsAndValidate(dbName, 10, true, MIGRATION_9_10).use { db ->
            db.query("SELECT practicesPerformed, practicesReceived FROM encounters WHERE id = 'e1'").use { c ->
                assertTrue(c.moveToFirst())
                // Order preserved; each removed id independently rewritten (no substring bleed).
                assertEquals("custom:CREAMPIE,custom:ANAL_CREAMPIE,custom:actCustom", c.getString(0))
                assertEquals("custom:preexisting", c.getString(1)) // merged into the colliding label's row
            }
            db.query("SELECT practicesPerformed FROM encounters WHERE id = 'e2'").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("custom:actCustom", c.getString(0)) // custom untouched
            }
            db.query("SELECT kinks FROM encounters WHERE id = 'e3'").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("custom:CHOKING,custom:GAGGING,custom:kCustom", c.getString(0))
            }
            // Adopted rows exist with prettified labels; the merged id did NOT create a new row.
            db.query("SELECT label, isBuiltIn FROM acts WHERE id = 'CREAMPIE'").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("Creampie", c.getString(0))
                assertEquals(0, c.getInt(1))
            }
            db.query("SELECT label FROM acts WHERE id = 'ANAL_CREAMPIE'").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("Anal creampie", c.getString(0))
            }
            db.query("SELECT COUNT(*) FROM acts WHERE id = 'FOOT_PLAY'").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(0, c.getInt(0))
            }
            db.query("SELECT label FROM kinks WHERE id = 'CHOKING'").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("Choking", c.getString(0))
            }
            // Idempotence: a second adoption pass finds nothing bare/unknown and changes nothing.
            CatalogAdoption.adoptUnknownIds(db)
            db.query("SELECT practicesPerformed FROM encounters WHERE id = 'e1'").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("custom:CREAMPIE,custom:ANAL_CREAMPIE,custom:actCustom", c.getString(0))
            }
            db.query("SELECT COUNT(*) FROM acts").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(4, c.getInt(0)) // actCustom, preexisting, CREAMPIE, ANAL_CREAMPIE
            }
        }
    }

    /**
     * v10 → v11 (FDP-4): creates the `toys` table and adopts removed built-in position and toy
     * ids into custom rows, mirroring the v9→v10 acts/kinks adoption. Surviving ids pass through
     * untouched, so nothing logged is lost when the built-in catalogs are trimmed.
     */
    @Test
    fun migrate10To11_addsToyTableAndAdoptsRemovedPositionsAndToys() {
        helper.createDatabase(dbName, 10).use { db ->
            db.execSQL("INSERT INTO positions (id, label, isBuiltIn) VALUES ('posCtrl', 'My Position', 0)")

            // e1: removed built-in positions/toys (none of these ship any more) → all adopt.
            db.execSQL(
                "INSERT INTO encounters (id, startAt, protectionUsed, positions, toys, createdAt, updatedAt) " +
                    "VALUES ('e1', 1000, '', 'FACE_SITTING,MISSIONARY', 'BUTT_PLUG,VIBRATOR', 1, 1)",
            )
            // e2: control — a custom position ref passes through untouched (toys table is created by
            // this migration, so a bare toy id here would adopt; the custom ref is the stable control).
            db.execSQL(
                "INSERT INTO encounters (id, startAt, protectionUsed, positions, createdAt, updatedAt) " +
                    "VALUES ('e2', 2000, '', 'custom:posCtrl', 1, 1)",
            )
        }

        helper.runMigrationsAndValidate(dbName, 11, true, MIGRATION_10_11).use { db ->
            db.query("SELECT positions, toys FROM encounters WHERE id = 'e1'").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("custom:FACE_SITTING,custom:MISSIONARY", c.getString(0))
                assertEquals("custom:BUTT_PLUG,custom:VIBRATOR", c.getString(1))
            }
            db.query("SELECT positions FROM encounters WHERE id = 'e2'").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("custom:posCtrl", c.getString(0)) // custom ref untouched
            }
            // Adopted custom rows exist with generically prettified labels.
            db.query("SELECT label, isBuiltIn FROM positions WHERE id = 'FACE_SITTING'").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("Face sitting", c.getString(0))
                assertEquals(0, c.getInt(1))
            }
            db.query("SELECT label, isBuiltIn FROM toys WHERE id = 'BUTT_PLUG'").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("Butt plug", c.getString(0))
                assertEquals(0, c.getInt(1))
            }
        }
    }

    /**
     * v11 → v12 (FDP-5): creates the `occasions` and `ejaculation_locations` tables, **seeds** the
     * neutral starter rows, then adopts every ref (including the per-orgasm **map-encoded**
     * `ejaculationLocations` column `idx=ID1|ID2,…`, which the generic comma-set adopter can't handle)
     * into a custom row. Since no built-ins ship any more, seed ids (DATE_NIGHT, NONE) also become
     * `custom:` refs — but they resolve to the **seed** row (seeding runs first), so a used starter keeps
     * its nice label rather than a generic prettified one. `custom:` refs pass through untouched.
     */
    @Test
    fun migrate11To12_addsTablesSeedsAndAdopts() {
        helper.createDatabase(dbName, 11).use { db ->
            // e1: a removed occasion (QUICKIE) + a seed id (DATE_NIGHT); a map-encoded finish column with
            // a removed id (ON_CHEST) + a seed id (NONE = "Didn't finish") across two orgasm rows.
            db.execSQL(
                "INSERT INTO encounters (id, startAt, protectionUsed, occasions, ejaculationLocations, createdAt, updatedAt) " +
                    "VALUES ('e1', 1000, '', 'QUICKIE,DATE_NIGHT', '0=ON_CHEST|NONE,1=ON_CHEST', 1, 1)",
            )
        }

        helper.runMigrationsAndValidate(dbName, 12, true, MIGRATION_11_12).use { db ->
            db.query("SELECT occasions, ejaculationLocations FROM encounters WHERE id = 'e1'").use { c ->
                assertTrue(c.moveToFirst())
                // Every id is now custom-prefixed (seed ids reuse the seeded row; QUICKIE/ON_CHEST adopt).
                assertEquals("custom:QUICKIE,custom:DATE_NIGHT", c.getString(0))
                assertEquals("0=custom:ON_CHEST|custom:NONE,1=custom:ON_CHEST", c.getString(1))
            }
            // Adopted rows get a generically prettified label.
            db.query("SELECT label, isBuiltIn FROM occasions WHERE id = 'QUICKIE'").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("Quickie", c.getString(0))
                assertEquals(0, c.getInt(1))
            }
            db.query("SELECT label FROM ejaculation_locations WHERE id = 'ON_CHEST'").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("On chest", c.getString(0))
            }
            // Seed rows exist with their nice labels (seeded, editable, isBuiltIn = 0); the used NONE seed
            // kept "Didn't finish" (seeding-before-adoption), not the prettified "None".
            db.query("SELECT label, isBuiltIn FROM occasions WHERE id = 'DATE_NIGHT'").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("Date night", c.getString(0))
                assertEquals(0, c.getInt(1))
            }
            db.query("SELECT label FROM occasions WHERE id = 'ANNIVERSARY'").use { c ->
                assertTrue("ANNIVERSARY seed row missing", c.moveToFirst())
                assertEquals("Anniversary", c.getString(0))
            }
            db.query("SELECT label FROM ejaculation_locations WHERE id = 'NONE'").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("Didn't finish", c.getString(0))
            }
            db.query("SELECT COUNT(*) FROM ejaculation_locations WHERE id = 'IN_CONDOM'").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(1, c.getInt(0)) // seeded even though unused
            }
            // Idempotence: a second pass finds nothing bare/unknown and changes nothing.
            CatalogAdoption.adoptUnknownIds(db)
            db.query("SELECT ejaculationLocations FROM encounters WHERE id = 'e1'").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("0=custom:ON_CHEST|custom:NONE,1=custom:ON_CHEST", c.getString(0))
            }
        }
    }

    @Test
    fun migrate12To13_addsRecentSearchesAndPreservesData() {
        helper.createDatabase(dbName, 12).use { db ->
            db.execSQL("INSERT INTO encounters (id, startAt, protectionUsed, note, createdAt, updatedAt) VALUES ('e1', 1000, '', 'keep me', 1, 1)")
        }

        helper.runMigrationsAndValidate(dbName, 13, true, MIGRATION_12_13).use { db ->
            // The new table exists and starts empty (additive, pure DDL).
            db.query("SELECT COUNT(*) FROM recent_searches").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(0, c.getInt(0))
            }
            // It round-trips a query (`query` is both PK and a reserved word — must stay quoted).
            db.execSQL("INSERT INTO recent_searches (`query`, lastUsedAt) VALUES ('hotel', 42)")
            db.query("SELECT `query`, lastUsedAt FROM recent_searches").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("hotel", c.getString(0))
                assertEquals(42, c.getInt(1))
            }
            // Existing rows are untouched.
            db.query("SELECT note FROM encounters WHERE id = 'e1'").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("keep me", c.getString(0))
            }
        }
    }
}
