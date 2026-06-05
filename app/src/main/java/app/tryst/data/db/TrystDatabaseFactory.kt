package app.tryst.data.db

import android.content.Context
import androidx.room.Room
import dagger.hilt.android.qualifiers.ApplicationContext
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds a SQLCipher-backed [TrystDatabase] from a database key. The DB is created on unlock
 * (not at app start) so it never exists in memory while the app is locked.
 */
@Singleton
class TrystDatabaseFactory @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun create(databaseKey: ByteArray): TrystDatabase {
        SqlCipherLibrary.ensureLoaded()
        // clearPassphrase = true → SQLCipher zeroes the key bytes after opening.
        val factory = SupportOpenHelperFactory(databaseKey, null, true)
        return Room.databaseBuilder(context, TrystDatabase::class.java, TrystDatabase.NAME)
            .openHelperFactory(factory)
            .addMigrations(*ALL_MIGRATIONS)
            .build()
    }

    /** Deletes the database file. Used before first-run setup to clear any stale DB left
     *  behind by a previously wiped vault (whose key no longer exists). */
    fun deleteDatabase() {
        context.deleteDatabase(TrystDatabase.NAME)
    }
}
