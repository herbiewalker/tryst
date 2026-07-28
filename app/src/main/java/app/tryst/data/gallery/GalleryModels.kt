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
    ;

    /** The grid layouts honour the density (column-count) preference; the feed is intrinsically one column. */
    val usesColumns: Boolean get() = this != FEED
}

/** Photo order within the gallery. */
enum class GallerySort { NEWEST, OLDEST }

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
 * One encounter photo lifted into the gallery, carrying the context of the tryst it belongs to so the
 * gallery can group/caption it and jump back to the tryst — without re-reading the DB. Derived from
 * [app.tryst.data.db.relation.EncounterWithDetails]; the gallery is a **view over the `media` table**,
 * so there is no schema change.
 */
data class GalleryPhoto(
    val media: MediaEntity,
    val encounterId: String,
    /** The tryst's start time — photos cluster by their tryst's date, and this drives sort + month grouping. */
    val takenAt: Long,
    val partners: List<GalleryPartner>,
    val rating: Int?,
) {
    val id: String get() = media.id

    /** Non-blank partner display names, for the feed caption. */
    val partnerNames: List<String> get() = partners.mapNotNull { it.name?.takeIf(String::isNotBlank) }
}
