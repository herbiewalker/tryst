package app.tryst.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import app.tryst.data.db.entity.EncounterEntity
import app.tryst.data.db.entity.EncounterPartnerCrossRef
import app.tryst.data.db.entity.EncounterPositionCrossRef
import app.tryst.data.db.entity.EncounterTagCrossRef
import app.tryst.data.db.relation.EncounterWithDetails
import kotlinx.coroutines.flow.Flow

@Dao
abstract class EncounterDao {

    @Upsert
    abstract suspend fun upsert(encounter: EncounterEntity)

    @Delete
    abstract suspend fun delete(encounter: EncounterEntity)

    @Transaction
    @Query("SELECT * FROM encounters WHERE id = :id")
    abstract suspend fun getWithDetails(id: String): EncounterWithDetails?

    @Transaction
    @Query("SELECT * FROM encounters ORDER BY startAt DESC")
    abstract fun observeAllWithDetails(): Flow<List<EncounterWithDetails>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertPartnerRefs(refs: List<EncounterPartnerCrossRef>)

    @Query("DELETE FROM encounter_partner WHERE encounterId = :encounterId")
    abstract suspend fun clearPartnerRefs(encounterId: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertPositionRefs(refs: List<EncounterPositionCrossRef>)

    @Query("DELETE FROM encounter_position WHERE encounterId = :encounterId")
    abstract suspend fun clearPositionRefs(encounterId: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertTagRefs(refs: List<EncounterTagCrossRef>)

    @Query("DELETE FROM encounter_tag WHERE encounterId = :encounterId")
    abstract suspend fun clearTagRefs(encounterId: String)

    /** Upserts an encounter and replaces all of its partner/position/tag links atomically. */
    @Transaction
    open suspend fun upsertWithRelations(
        encounter: EncounterEntity,
        partnerIds: List<String>,
        positionIds: List<String>,
        tagIds: List<String>,
    ) {
        upsert(encounter)

        clearPartnerRefs(encounter.id)
        insertPartnerRefs(partnerIds.map { EncounterPartnerCrossRef(encounter.id, it) })

        clearPositionRefs(encounter.id)
        insertPositionRefs(positionIds.map { EncounterPositionCrossRef(encounter.id, it) })

        clearTagRefs(encounter.id)
        insertTagRefs(tagIds.map { EncounterTagCrossRef(encounter.id, it) })
    }
}
