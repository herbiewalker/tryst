package app.tryst.data.db.relation

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation
import app.tryst.data.db.entity.EncounterEntity
import app.tryst.data.db.entity.EncounterPartnerCrossRef
import app.tryst.data.db.entity.EncounterPositionCrossRef
import app.tryst.data.db.entity.EncounterTagCrossRef
import app.tryst.data.db.entity.LocationEntity
import app.tryst.data.db.entity.MediaEntity
import app.tryst.data.db.entity.PartnerEntity
import app.tryst.data.db.entity.PositionEntity
import app.tryst.data.db.entity.TagEntity

/** An encounter with all of its related rows resolved. */
data class EncounterWithDetails(
    @Embedded val encounter: EncounterEntity,

    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = EncounterPartnerCrossRef::class,
            parentColumn = "encounterId",
            entityColumn = "partnerId",
        ),
    )
    val partners: List<PartnerEntity>,

    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = EncounterPositionCrossRef::class,
            parentColumn = "encounterId",
            entityColumn = "positionId",
        ),
    )
    val positions: List<PositionEntity>,

    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = EncounterTagCrossRef::class,
            parentColumn = "encounterId",
            entityColumn = "tagId",
        ),
    )
    val tags: List<TagEntity>,

    @Relation(parentColumn = "id", entityColumn = "encounterId")
    val media: List<MediaEntity>,

    @Relation(parentColumn = "locationId", entityColumn = "id")
    val location: LocationEntity?,
)
