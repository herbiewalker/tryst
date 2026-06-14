package app.tryst.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import app.tryst.data.db.entity.ProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {

    @Upsert
    suspend fun upsert(profile: ProfileEntity)

    @Query("SELECT * FROM profile WHERE id = :id")
    fun observe(id: String): Flow<ProfileEntity?>

    @Query("SELECT * FROM profile WHERE id = :id")
    suspend fun get(id: String): ProfileEntity?
}
