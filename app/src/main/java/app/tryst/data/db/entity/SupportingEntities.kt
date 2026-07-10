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

@Entity(tableName = "kinks", indices = [Index(value = ["label"], unique = true)])
data class KinkEntity(
    @PrimaryKey val id: String,
    val label: String,
    val isBuiltIn: Boolean = false,
)

@Entity(tableName = "toys", indices = [Index(value = ["label"], unique = true)])
data class ToyEntity(
    @PrimaryKey val id: String,
    val label: String,
    val isBuiltIn: Boolean = false,
)

@Entity(tableName = "occasions", indices = [Index(value = ["label"], unique = true)])
data class OccasionEntity(
    @PrimaryKey val id: String,
    val label: String,
    val isBuiltIn: Boolean = false,
)

@Entity(tableName = "ejaculation_locations", indices = [Index(value = ["label"], unique = true)])
data class EjaculationLocationEntity(
    @PrimaryKey val id: String,
    val label: String,
    val isBuiltIn: Boolean = false,
)

/**
 * A search query the user submitted, most-recent-first (SRCH-1). Lives **in the encrypted DB**, not in
 * the `SharedPreferences` stores: a search history is some of the most sensitive text in the app, and
 * the prefs files are the one part of Tryst that is *not* encrypted at rest (D-42).
 *
 * Deliberately **excluded from `BackupManager.TABLES`** — your queries never travel inside an export,
 * and a restore leaves your local history alone. `SessionManager.deleteAllData` drops the DB, so a
 * full wipe clears it.
 */
@Entity(tableName = "recent_searches", indices = [Index("lastUsedAt")])
data class RecentSearchEntity(
    /** The raw query text, as typed. Also the identity — re-searching a term just bumps its timestamp. */
    @PrimaryKey val query: String,
    val lastUsedAt: Long,
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
