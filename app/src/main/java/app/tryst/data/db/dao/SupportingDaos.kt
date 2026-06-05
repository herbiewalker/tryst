package app.tryst.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import app.tryst.data.db.entity.LocationEntity
import app.tryst.data.db.entity.MediaEntity
import app.tryst.data.db.entity.PositionEntity
import app.tryst.data.db.entity.TagEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaDao {
    @Upsert
    suspend fun upsert(media: MediaEntity)

    @Query("SELECT * FROM media WHERE encounterId = :encounterId ORDER BY createdAt")
    suspend fun getForEncounter(encounterId: String): List<MediaEntity>

    @Delete
    suspend fun delete(media: MediaEntity)
}

@Dao
interface TagDao {
    @Upsert
    suspend fun upsert(tag: TagEntity)

    @Query("SELECT * FROM tags ORDER BY label COLLATE NOCASE")
    fun observeAll(): Flow<List<TagEntity>>
}

@Dao
interface PositionDao {
    @Upsert
    suspend fun upsert(position: PositionEntity)

    @Query("SELECT * FROM positions ORDER BY label COLLATE NOCASE")
    fun observeAll(): Flow<List<PositionEntity>>
}

@Dao
interface LocationDao {
    @Upsert
    suspend fun upsert(location: LocationEntity)

    @Query("SELECT * FROM locations ORDER BY label COLLATE NOCASE")
    fun observeAll(): Flow<List<LocationEntity>>
}
