package app.tryst.data.gallery

import app.tryst.data.db.entity.MediaEntity

/**
 * How the Photos gallery lays its tiles out — a user preference (GAL-1). Each value picks both a
 * grouping and a tile style; density (column count) and [GallerySort] are chosen alongside it.
 */
enum class GalleryLayout {
    /** A square grid under month headers (the default). */
    JUSTIFIED_DATE,

    /** One flat, uniform square-cropped grid; densest and calmest. */
    SQUARE_GRID,

    /** A section per partner, each a square grid; leads with the "who". */
    BY_PARTNER,

    /** One large photo per row with its tryst's date/partner/rating beneath. */
    FEED,

    /** Aspect-preserving justified rows under month headers (Google-Photos style), GAL-1b. */
    MOSAIC,

    /**
     * People, not encounter photos: a browsable grid of partner (and profile) avatars, GAL-1a. Rendered
     * from the Partners/Profile data, not the photo pipeline — so [GalleryPhotos.build] yields nothing for it.
     */
    PEOPLE,
    ;

    /**
     * The uniform-tile grids honour the density (column-count) preference. The feed is one column; the
     * mosaic computes justified rows from aspect ratios rather than a fixed column count.
     */
    val usesColumns: Boolean get() = this != FEED && this != MOSAIC

    /** Whether this layout is derived from encounter photos (vs. [PEOPLE], which draws avatars). */
    val isPhotoLayout: Boolean get() = this != PEOPLE
}

/** Photo order within the gallery. */
enum class GallerySort { NEWEST, OLDEST }

/**
 * How much room to leave between tiles in the grid and mosaic layouts. Compact preserves the app's
 * original tight look; Normal breathes more like Google Photos. Feed is unaffected (already spaced).
 */
enum class GridSpacing {
    COMPACT, // 2dp content padding, 3dp gaps — the original look
    NORMAL, // 8dp content padding, 8dp gaps — more room to breathe
}

/**
 * What a [GallerySection] is grouped by — the header the UI draws (label resolution, which is locale- and
 * string-resource-bound, stays in the UI; this pure layer only carries the identifying data).
 */
sealed interface GalleryGroup {
    /** No header — a single flat run of tiles ([GalleryLayout.SQUARE_GRID], [GalleryLayout.FEED]). */
    data object Ungrouped : GalleryGroup

    /** A calendar month; [month] is 1..12. */
    data class Month(val year: Int, val month: Int) : GalleryGroup

    /** A named or anonymous partner; [name] is null/blank for anonymous. */
    data class Partner(val id: String, val name: String?) : GalleryGroup

    /** Photos from solo trysts (no partners). */
    data object Solo : GalleryGroup
}

/** A run of photos under one [group] header. */
data class GallerySection(val group: GalleryGroup, val photos: List<GalleryPhoto>)

/**
 * The gallery's photos in both shapes: [sections] as grouped for display, and [photos] as the flat,
 * de-duplicated, sorted list the full-screen viewer pages through and the tile count comes from.
 */
data class GalleryResult(
    val sections: List<GallerySection> = emptyList(),
    val photos: List<GalleryPhoto> = emptyList(),
)

/** A partner a photo's tryst involved; [name] is null/blank for an anonymous partner. */
data class GalleryPartner(val id: String, val name: String?)

/**
 * One photo lifted into the gallery. Two flavours share this shape:
 *
 * - **Encounter photos** (the original GAL-1 case): [source] is [Source.Encounter], `takenAt` is the
 *   tryst's start time, `partners` are the tryst's partners, `favorite` reflects `media.favorite`.
 * - **Person portraits** (v15+): [source] is [Source.Person], `takenAt` is when the portrait was
 *   attached, `partners` is a synthetic singleton for the owning person, `favorite` is always false
 *   (portraits aren't favouritable — the whole album is already curated), and there's no owning tryst
 *   so "Open tryst" doesn't apply.
 *
 * Both flavours open through [EncryptedMediaStore] with the same blob id, so the gallery pipeline
 * (decode, viewer, filmstrip) treats them identically once built.
 */
data class GalleryPhoto(
    /** The encrypted blob id — feeds `EncryptedMediaStore.open`. Also the tile's stable key. */
    val blobId: String,
    val mimeType: String,
    val takenAt: Long,
    val partners: List<GalleryPartner>,
    val rating: Int?,
    val favorite: Boolean,
    val source: Source,
) {
    val id: String get() = blobId

    /** Non-blank partner display names, for the feed caption. */
    val partnerNames: List<String> get() = partners.mapNotNull { it.name?.takeIf(String::isNotBlank) }

    /** The owning encounter's id when this photo is an encounter photo; null for person portraits. */
    val encounterId: String? get() = (source as? Source.Encounter)?.encounterId

    /** The underlying media row when this is an encounter photo (needed for reassign / favourite / metadata). */
    val media: MediaEntity? get() = (source as? Source.Encounter)?.media

    sealed interface Source {
        data class Encounter(val encounterId: String, val media: MediaEntity) : Source
        data class Person(val ownerKind: String, val ownerId: String, val personPhotoId: String) : Source
    }
}
