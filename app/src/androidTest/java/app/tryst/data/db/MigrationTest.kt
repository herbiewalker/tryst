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
}
