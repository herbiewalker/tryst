package app.tryst.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import app.tryst.data.db.entity.ActEntity
import app.tryst.data.db.entity.KinkEntity
import app.tryst.data.db.entity.LocationEntity
import app.tryst.data.db.entity.MediaEntity
import app.tryst.data.db.entity.PositionEntity
import app.tryst.data.db.entity.TagEntity
import app.tryst.data.db.entity.ToyEntity
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

    @Query("SELECT * FROM positions WHERE isBuiltIn = 0 ORDER BY label COLLATE NOCASE")
    fun observeCustom(): Flow<List<PositionEntity>>

    @Query("UPDATE positions SET label = :label WHERE id = :id")
    suspend fun rename(id: String, label: String)

    @Query("DELETE FROM positions WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface ActDao {
    @Upsert
    suspend fun upsert(act: ActEntity)

    @Query("SELECT * FROM acts WHERE isBuiltIn = 0 ORDER BY label COLLATE NOCASE")
    fun observeCustom(): Flow<List<ActEntity>>

    @Query("UPDATE acts SET label = :label WHERE id = :id")
    suspend fun rename(id: String, label: String)

    @Query("DELETE FROM acts WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface KinkDao {
    @Upsert
    suspend fun upsert(kink: KinkEntity)

    @Query("SELECT * FROM kinks WHERE isBuiltIn = 0 ORDER BY label COLLATE NOCASE")
    fun observeCustom(): Flow<List<KinkEntity>>

    @Query("UPDATE kinks SET label = :label WHERE id = :id")
    suspend fun rename(id: String, label: String)

    @Query("DELETE FROM kinks WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface ToyDao {
    @Upsert
    suspend fun upsert(toy: ToyEntity)

    @Query("SELECT * FROM toys WHERE isBuiltIn = 0 ORDER BY label COLLATE NOCASE")
    fun observeCustom(): Flow<List<ToyEntity>>

    @Query("UPDATE toys SET label = :label WHERE id = :id")
    suspend fun rename(id: String, label: String)

    @Query("DELETE FROM toys WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface LocationDao {
    @Upsert
    suspend fun upsert(location: LocationEntity)

    @Query("SELECT * FROM locations ORDER BY label COLLATE NOCASE")
    fun observeAll(): Flow<List<LocationEntity>>
}
