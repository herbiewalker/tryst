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
     * v7 → v8 is a **data** migration (no DDL): renamed/moved/promoted category values must rewrite
     * the stored string ids without losing any encounter data. Covers FIX-3 (ORAL_69_SIDE→LYING_ORAL),
     * FIX-4 (WATCHING_PORN act→kink), and FIX-5/6 (custom→built-in promotion + non-promoted custom
     * survival).
     */
    @Test
    fun migrate7To8_rewritesMovedAndPromotedValues() {
        helper.createDatabase(dbName, 7).use { db ->
            // Custom rows use the user's REAL stored labels (verified from their backup) so this proves
            // the actual mismatched names promote: one promoted, one left alone (no match).
            db.execSQL("INSERT INTO positions (id, label, isBuiltIn) VALUES ('posPromote', 'Reverse cowgirl - legs under', 0)")
            db.execSQL("INSERT INTO positions (id, label, isBuiltIn) VALUES ('posKeep', 'Keepme Custom', 0)")
            db.execSQL("INSERT INTO acts (id, label, isBuiltIn) VALUES ('actPromote', 'Lick after sex', 0)")

            // e1 exercises every rewrite: deleted position, moved act (gave+received), promoted custom act+position.
            db.execSQL(
                "INSERT INTO encounters (id, startAt, protectionUsed, positions, practicesPerformed, practicesReceived, createdAt, updatedAt) " +
                    "VALUES ('e1', 1000, '', 'ORAL_69_SIDE,custom:posPromote', 'WATCHING_PORN,custom:actPromote', 'WATCHING_PORN', 1, 1)",
            )
            // e2 is a control: built-in values + a non-promoted custom position must pass through untouched.
            db.execSQL(
                "INSERT INTO encounters (id, startAt, protectionUsed, positions, practicesPerformed, kinks, createdAt, updatedAt) " +
                    "VALUES ('e2', 2000, '', 'MISSIONARY,custom:posKeep', 'ORAL', 'SPANKING', 1, 1)",
            )
        }

        helper.runMigrationsAndValidate(dbName, 8, true, MIGRATION_7_8).use { db ->
            db.query(
                "SELECT positions, practicesPerformed, practicesReceived, kinks FROM encounters WHERE id = 'e1'",
            ).use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("LYING_ORAL,REVERSE_COWGIRL_MODIFIED", c.getString(0)) // ORAL_69_SIDE→LYING_ORAL + custom→built-in
                assertEquals("LICK_PUSSY_AFTER", c.getString(1)) // WATCHING_PORN stripped, custom act promoted
                assertTrue("WATCHING_PORN should be stripped from received acts", c.isNull(2))
                assertEquals("WATCHING_PORN", c.getString(3)) // moved into kinks
            }
            db.query(
                "SELECT positions, practicesPerformed, kinks FROM encounters WHERE id = 'e2'",
            ).use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("MISSIONARY,custom:posKeep", c.getString(0)) // non-promoted custom survives
                assertEquals("ORAL", c.getString(1))
                assertEquals("SPANKING", c.getString(2))
            }
            // The promoted custom rows are gone; the non-promoted one remains.
            db.query("SELECT COUNT(*) FROM positions WHERE id = 'posPromote'").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(0, c.getInt(0))
            }
            db.query("SELECT COUNT(*) FROM acts WHERE id = 'actPromote'").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(0, c.getInt(0))
            }
            db.query("SELECT COUNT(*) FROM positions WHERE id = 'posKeep'").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(1, c.getInt(0))
            }
        }
    }
}
