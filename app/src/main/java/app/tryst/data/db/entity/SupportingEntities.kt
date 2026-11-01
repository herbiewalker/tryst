// SPDX-License-Identifier: GPL-3.0-or-later
// ╭──────────────────────────────────────────────────────────────────╮
// │  ┌●───●───●───●───●───●───●───●───●───●───●───●───●───●───●───┐  │
// │  │                                                            │  │
// │  ●   ██╗  ██╗    ██╗    ██╗                                   ●  │
// │  │   ██║  ██║    ██║    ██║           herbiewalker            │  │
// │  │   ███████║    ██║ █╗ ██║──●──●──●──┐                       │  │
// │  │   ██╔══██║    ██║███╗██║           │                       │  │
// │  ●   ██║  ██║    ╚███╔███╔╝           ●───⏣  code · tools     ●  │
// │  │   ╚═╝  ╚═╝     ╚══╝╚══╝                    homelab         │  │
// │  │                                                            │  │
// │  └●───●───●───●───●───●───●───●───●───●───●───●───●───●───●───┘  │
// ╰──────────────────────────────────────────────────────────────────╯
package app.tryst.data.db.entity

import androidx.room.ColumnInfo
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

/**
 * A user-defined sexual orientation label (DEMO-1). Fully user-editable with **no built-in rows**, so the
 * APK ships zero orientation strings (D-41); `partners.orientation` / `profile.orientation` hold the
 * chosen row's [id] (null = not set). Deleting a row clears every reference to it.
 */
@Entity(tableName = "orientations", indices = [Index(value = ["label"], unique = true)])
data class OrientationEntity(
    @PrimaryKey val id: String,
    val label: String,
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
    /**
     * User "favourite" mark (GAL-3) — surfaced in the gallery's Favourites filter and starred in the viewer.
     *
     * `defaultValue = "0"` mirrors the DEFAULT clause in `MIGRATION_13_14`, so a fresh v15+ install
     * creates `media.favorite` with the same DEFAULT the upgrade path installs. Without it, restoring a
     * pre-v14 backup (which has no `favorite` key) into a fresh install fails NOT-NULL on this column.
     */
    @ColumnInfo(defaultValue = "0") val favorite: Boolean = false,
    /**
     * User-typed caption for this photo (CAP-1, v16). Nullable — the vast majority of photos won't have
     * one. Folded into the SRCH-1 search index as an extra text source per encounter, so searching by a
     * caption phrase surfaces the tryst it belongs to. Backup round-trips via the generic `SELECT *`
     * dump so no export-format change is needed (a pre-v16 backup simply has no value → NULL).
     */
    val caption: String? = null,
    /**
     * Photo details captured from the original file at import time (v18). They live in the encrypted
     * DB so they survive a rotate/crop (which re-encodes the photo and drops EXIF), so a future map can
     * query them, and so they ride the backup. All nullable; a pre-v18 backup restores them as NULL and
     * the gallery re-reads them from the blob.
     */
    val capturedAt: Long? = null,
    /** Minutes east of UTC at capture, when the camera recorded it. */
    val capturedOffsetMin: Int? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val altitudeM: Double? = null,
    val camera: String? = null,
    val lens: String? = null,
    val aperture: Double? = null,
    val shutterSeconds: Double? = null,
    val iso: Int? = null,
    val focalLengthMm: Double? = null,
    val originalName: String? = null,
    /** True once the blob has been read for the fields above (so a photo with no EXIF is not rescanned forever). */
    @ColumnInfo(defaultValue = "0") val metaScanned: Boolean = false,
)

/**
 * A photo attached to a **person** (partner or self-profile) rather than an encounter — the "portrait
 * album" concept added in v15. The encrypted blob lives in [app.tryst.data.media.EncryptedMediaStore]
 * exactly like an encounter's [MediaEntity], but this row owns it directly (no encounter FK), so a
 * user can attach several photos to a person and swap which one is the current avatar.
 *
 * A partner's/profile's `photoMediaId` still points at the *active* avatar; when the user picks a
 * different portrait as their avatar, that field is updated to the new blob id. The other portraits
 * stay attached (visible in the gallery, browsable in the editor).
 */
@Entity(tableName = "person_photo", indices = [Index(value = ["ownerKind", "ownerId"])])
data class PersonPhotoEntity(
    @PrimaryKey val id: String,
    /** "partner" for a partner-owned photo, "profile" for the self profile. */
    val ownerKind: String,
    /** The partner's row id, or the single-row profile id ([ProfileEntity.SELF_ID]) when [ownerKind]=="profile". */
    val ownerId: String,
    /** The encrypted blob's id (opens via `EncryptedMediaStore.open(mediaBlobId)`). */
    val mediaBlobId: String,
    val addedAt: Long,
)

// ⏣ HW ⏣ · code · tools · homelab · ⏣ HW ⏣
