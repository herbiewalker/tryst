// SPDX-License-Identifier: GPL-3.0-or-later
package app.tryst.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import app.tryst.data.db.entity.ActEntity
import app.tryst.data.db.entity.EjaculationLocationEntity
import app.tryst.data.db.entity.KinkEntity
import app.tryst.data.db.entity.LocationEntity
import app.tryst.data.db.entity.MediaEntity
import app.tryst.data.db.entity.OccasionEntity
import app.tryst.data.db.entity.PersonPhotoEntity
import app.tryst.data.db.entity.PositionEntity
import app.tryst.data.db.entity.RecentSearchEntity
import app.tryst.data.db.entity.TagEntity
import app.tryst.data.db.entity.ToyEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaDao {
    @Upsert
    suspend fun upsert(media: MediaEntity)

    @Query("SELECT * FROM media WHERE encounterId = :encounterId ORDER BY createdAt")
    suspend fun getForEncounter(encounterId: String): List<MediaEntity>

    @Query("UPDATE media SET favorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: String, favorite: Boolean)

    @Query("UPDATE media SET favorite = :favorite WHERE id IN (:ids)")
    suspend fun setFavorite(ids: List<String>, favorite: Boolean)

    /** Sets or clears the caption on one photo (CAP-1). Empty/blank captions are stored as NULL. */
    @Query("UPDATE media SET caption = :caption WHERE id = :id")
    suspend fun setCaption(id: String, caption: String?)

    /** Moves photos to a different tryst (bulk reassign, GAL-4). The gallery re-derives, so they relocate. */
    @Query("UPDATE media SET encounterId = :encounterId WHERE id IN (:ids)")
    suspend fun reassign(ids: List<String>, encounterId: String)

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
interface OccasionDao {
    @Upsert
    suspend fun upsert(occasion: OccasionEntity)

    @Query("SELECT * FROM occasions WHERE isBuiltIn = 0 ORDER BY label COLLATE NOCASE")
    fun observeCustom(): Flow<List<OccasionEntity>>

    @Query("UPDATE occasions SET label = :label WHERE id = :id")
    suspend fun rename(id: String, label: String)

    @Query("DELETE FROM occasions WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface EjaculationLocationDao {
    @Upsert
    suspend fun upsert(location: EjaculationLocationEntity)

    @Query("SELECT * FROM ejaculation_locations WHERE isBuiltIn = 0 ORDER BY label COLLATE NOCASE")
    fun observeCustom(): Flow<List<EjaculationLocationEntity>>

    @Query("UPDATE ejaculation_locations SET label = :label WHERE id = :id")
    suspend fun rename(id: String, label: String)

    @Query("DELETE FROM ejaculation_locations WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface LocationDao {
    @Upsert
    suspend fun upsert(location: LocationEntity)

    @Query("SELECT * FROM locations ORDER BY label COLLATE NOCASE")
    fun observeAll(): Flow<List<LocationEntity>>
}

@Dao
interface PersonPhotoDao {
    @Upsert
    suspend fun upsert(photo: PersonPhotoEntity)

    @Query("SELECT * FROM person_photo WHERE ownerKind = :kind AND ownerId = :id ORDER BY addedAt DESC")
    fun observeForOwner(kind: String, id: String): Flow<List<PersonPhotoEntity>>

    @Query("SELECT * FROM person_photo WHERE ownerKind = :kind AND ownerId = :id ORDER BY addedAt DESC")
    suspend fun getForOwner(kind: String, id: String): List<PersonPhotoEntity>

    @Query("SELECT * FROM person_photo ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<PersonPhotoEntity>>

    @Query("SELECT * FROM person_photo WHERE mediaBlobId = :blobId LIMIT 1")
    suspend fun getByBlobId(blobId: String): PersonPhotoEntity?

    @Query("DELETE FROM person_photo WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM person_photo WHERE ownerKind = :kind AND ownerId = :id")
    suspend fun deleteForOwner(kind: String, id: String)
}

@Dao
interface RecentSearchDao {
    /** Re-searching an existing term bumps its timestamp rather than adding a duplicate (query is the PK). */
    @Upsert
    suspend fun upsert(search: RecentSearchEntity)

    @Query("SELECT * FROM recent_searches ORDER BY lastUsedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<RecentSearchEntity>>

    @Query("DELETE FROM recent_searches WHERE `query` = :query")
    suspend fun delete(query: String)

    @Query("DELETE FROM recent_searches")
    suspend fun clear()

    /** Keeps the table bounded: drops everything outside the newest [keep] rows. */
    @Query("DELETE FROM recent_searches WHERE `query` NOT IN (SELECT `query` FROM recent_searches ORDER BY lastUsedAt DESC LIMIT :keep)")
    suspend fun trimTo(keep: Int)
}
