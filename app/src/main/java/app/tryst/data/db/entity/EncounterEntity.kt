package app.tryst.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "encounters",
    foreignKeys = [
        ForeignKey(
            entity = LocationEntity::class,
            parentColumns = ["id"],
            childColumns = ["locationId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("locationId"), Index("startAt")],
)
data class EncounterEntity(
    @PrimaryKey val id: String,
    /** Epoch millis of when the encounter started. */
    val startAt: Long,
    val durationMin: Int?,
    val note: String?,
    /** e.g. 1..5; null if unrated. */
    val satisfactionRating: Int?,
    val orgasm: Orgasm?,
    val mood: Mood?,
    val initiator: Initiator?,
    val protectionUsed: Set<Protection>,
    val locationId: String?,
    val createdAt: Long,
    val updatedAt: Long,
)
