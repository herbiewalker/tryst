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
    fun migrate1To2_preservesRowsAndAddsColumns() {
        helper.createDatabase(dbName, 1).use { db ->
            db.execSQL(
                "INSERT INTO encounters (id, startAt, protectionUsed, createdAt, updatedAt) " +
                    "VALUES ('e1', 1000, 'CONDOM', 1, 1)",
            )
        }

        // Applies MIGRATION_1_2 and validates the resulting schema equals the exported v2 schema.
        helper.runMigrationsAndValidate(dbName, 2, true, MIGRATION_1_2).use { db ->
            db.query(
                "SELECT id, orgasmCountSelf, ejaculationLocations FROM encounters WHERE id = 'e1'",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("e1", cursor.getString(0))
                assertTrue("new column should be NULL for migrated row", cursor.isNull(1))
                assertTrue("new column should be NULL for migrated row", cursor.isNull(2))
            }
        }
    }
}
