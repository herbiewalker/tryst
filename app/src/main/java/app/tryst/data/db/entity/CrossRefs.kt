package app.tryst.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "encounter_partner",
    primaryKeys = ["encounterId", "partnerId"],
    foreignKeys = [
        ForeignKey(EncounterEntity::class, ["id"], ["encounterId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(PartnerEntity::class, ["id"], ["partnerId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("partnerId")],
)
data class EncounterPartnerCrossRef(
    val encounterId: String,
    val partnerId: String,
)

@Entity(
    tableName = "encounter_position",
    primaryKeys = ["encounterId", "positionId"],
    foreignKeys = [
        ForeignKey(EncounterEntity::class, ["id"], ["encounterId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(PositionEntity::class, ["id"], ["positionId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("positionId")],
)
data class EncounterPositionCrossRef(
    val encounterId: String,
    val positionId: String,
)

@Entity(
    tableName = "encounter_tag",
    primaryKeys = ["encounterId", "tagId"],
    foreignKeys = [
        ForeignKey(EncounterEntity::class, ["id"], ["encounterId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(TagEntity::class, ["id"], ["tagId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("tagId")],
)
data class EncounterTagCrossRef(
    val encounterId: String,
    val tagId: String,
)
