// SPDX-License-Identifier: GPL-3.0-or-later
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

    /** Bring an archived partner back into the active list — mirrors [archive]. */
    @Query("UPDATE partners SET archivedAt = NULL, updatedAt = :timestamp WHERE id = :id")
    suspend fun unarchive(id: String, timestamp: Long)

    @Query("SELECT * FROM partners WHERE archivedAt IS NOT NULL ORDER BY displayName IS NULL, displayName COLLATE NOCASE")
    fun observeArchived(): Flow<List<PartnerEntity>>

    @Delete
    suspend fun delete(partner: PartnerEntity)
}
