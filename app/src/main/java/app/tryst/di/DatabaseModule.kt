package app.tryst.di

import android.content.Context
import androidx.room.Room
import app.tryst.core.crypto.DatabaseKeyProvider
import app.tryst.data.db.SqlCipherLibrary
import app.tryst.data.db.TrystDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
        keyProvider: DatabaseKeyProvider,
    ): TrystDatabase {
        SqlCipherLibrary.ensureLoaded()
        // SQLCipher-backed open helper: every page is encrypted with the provided key.
        val factory = SupportOpenHelperFactory(keyProvider.databaseKey())
        return Room.databaseBuilder(context, TrystDatabase::class.java, TrystDatabase.NAME)
            .openHelperFactory(factory)
            .build()
    }

    @Provides fun providePartnerDao(db: TrystDatabase) = db.partnerDao()
    @Provides fun provideEncounterDao(db: TrystDatabase) = db.encounterDao()
    @Provides fun provideMediaDao(db: TrystDatabase) = db.mediaDao()
    @Provides fun provideTagDao(db: TrystDatabase) = db.tagDao()
    @Provides fun providePositionDao(db: TrystDatabase) = db.positionDao()
    @Provides fun provideLocationDao(db: TrystDatabase) = db.locationDao()
}
