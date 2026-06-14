package app.tryst.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import app.tryst.data.db.dao.ActDao
import app.tryst.data.db.dao.EncounterDao
import app.tryst.data.db.dao.LocationDao
import app.tryst.data.db.dao.MediaDao
import app.tryst.data.db.dao.PartnerDao
import app.tryst.data.db.dao.PositionDao
import app.tryst.data.db.dao.ProfileDao
import app.tryst.data.db.dao.TagDao
import app.tryst.data.db.entity.ActEntity
import app.tryst.data.db.entity.EncounterEntity
import app.tryst.data.db.entity.EncounterPartnerCrossRef
import app.tryst.data.db.entity.EncounterPositionCrossRef
import app.tryst.data.db.entity.EncounterTagCrossRef
import app.tryst.data.db.entity.LocationEntity
import app.tryst.data.db.entity.MediaEntity
import app.tryst.data.db.entity.PartnerEntity
import app.tryst.data.db.entity.PositionEntity
import app.tryst.data.db.entity.ProfileEntity
import app.tryst.data.db.entity.TagEntity

@Database(
    entities = [
        PartnerEntity::class,
        ProfileEntity::class,
        EncounterEntity::class,
        LocationEntity::class,
        TagEntity::class,
        PositionEntity::class,
        ActEntity::class,
        MediaEntity::class,
        EncounterPartnerCrossRef::class,
        EncounterPositionCrossRef::class,
        EncounterTagCrossRef::class,
    ],
    version = 7,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class TrystDatabase : RoomDatabase() {
    abstract fun partnerDao(): PartnerDao
    abstract fun profileDao(): ProfileDao
    abstract fun encounterDao(): EncounterDao
    abstract fun mediaDao(): MediaDao
    abstract fun tagDao(): TagDao
    abstract fun positionDao(): PositionDao
    abstract fun actDao(): ActDao
    abstract fun locationDao(): LocationDao

    companion object {
        const val NAME = "tryst.db"
    }
}
