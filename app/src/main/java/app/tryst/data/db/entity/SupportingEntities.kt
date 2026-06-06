package app.tryst.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "locations")
data class LocationEntity(
    @PrimaryKey val id: String,
    /** User-typed generic label, e.g. "home", "hotel". No GPS — see docs/DATA_MODEL.md. */
    val label: String,
    val createdAt: Long,
)

@Entity(tableName = "tags", indices = [Index(value = ["label"], unique = true)])
data class TagEntity(
    @PrimaryKey val id: String,
    val label: String,
)

@Entity(tableName = "positions", indices = [Index(value = ["label"], unique = true)])
data class PositionEntity(
    @PrimaryKey val id: String,
    val label: String,
    val isBuiltIn: Boolean = false,
)

@Entity(tableName = "acts", indices = [Index(value = ["label"], unique = true)])
data class ActEntity(
    @PrimaryKey val id: String,
    val label: String,
    val isBuiltIn: Boolean = false,
)

@Entity(
    tableName = "media",
    foreignKeys = [
        ForeignKey(
            entity = EncounterEntity::class,
            parentColumns = ["id"],
            childColumns = ["encounterId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("encounterId")],
)
data class MediaEntity(
    @PrimaryKey val id: String,
    val encounterId: String,
    /** Absolute path to the AES-GCM-encrypted blob in app-internal storage. */
    val encFilePath: String,
    val mimeType: String,
    val createdAt: Long,
)
