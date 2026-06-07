package app.tryst.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import app.tryst.data.db.entity.PartnerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PartnerDao {

    @Upsert
    suspend fun upsert(partner: PartnerEntity)

    @Query("SELECT * FROM partners WHERE archivedAt IS NULL ORDER BY displayName IS NULL, displayName COLLATE NOCASE")
    fun observeActive(): Flow<List<PartnerEntity>>

    @Query("SELECT * FROM partners ORDER BY displayName IS NULL, displayName COLLATE NOCASE")
    fun observeAll(): Flow<List<PartnerEntity>>

    @Query("SELECT * FROM partners WHERE id = :id")
    suspend fun getById(id: String): PartnerEntity?

    @Query("SELECT * FROM partners")
    suspend fun getAll(): List<PartnerEntity>

    @Query("UPDATE partners SET archivedAt = :timestamp, updatedAt = :timestamp WHERE id = :id")
    suspend fun archive(id: String, timestamp: Long)

    @Delete
    suspend fun delete(partner: PartnerEntity)
}
