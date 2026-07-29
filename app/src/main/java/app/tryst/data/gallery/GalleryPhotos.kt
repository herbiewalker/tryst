package app.tryst.data.gallery

import app.tryst.data.db.entity.MediaEntity
import app.tryst.data.db.relation.EncounterWithDetails
import app.tryst.data.filter.EncounterFilter
import app.tryst.data.search.CatalogLabels
import app.tryst.data.search.EncounterSearch
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

/**
 * Builds the Photos gallery (GAL-1) from the encounter log — the gallery's third reuse of the FILT-1
 * layer, after Search and the Insights scope. Pure data (no Android/Compose), so it's JVM-tested like
 * [EncounterFilter] and [EncounterSearch].
 *
 * Pipeline: keep encounters that **have media** and match the structured [EncounterFilter], narrow by the
 * free-text [EncounterSearch] query, flatten each survivor's photos into [GalleryPhoto]s (carrying the
 * tryst's date/partners/rating), order them, then group per the chosen [GalleryLayout].
 */
object GalleryPhotos {

    /**
     * The gallery's photos, both [grouped][GalleryResult.sections] per [layout] and as the flat sorted
     * [list][GalleryResult.photos] the full-screen viewer pages through — produced in one filter/query pass.
     */
    @Suppress("LongParameterList") // Distinct query inputs — the encounter log, the two filter layers, the labels, and the look.
    fun build(
        encounters: List<EncounterWithDetails>,
        filter: EncounterFilter,
        query: String,
        labels: CatalogLabels,
        layout: GalleryLayout,
        sort: GallerySort,
        zone: ZoneId = ZoneId.systemDefault(),
        onlyFavorites: Boolean = false,
    ): GalleryResult {
        // PEOPLE draws avatars from the Partners/Profile data, not encounter photos — nothing to build here.
        if (!layout.isPhotoLayout) return GalleryResult()

        val withPhotos = encounters.filter { it.media.isNotEmpty() && filter.matches(it, zone) }
        val matched = applyQuery(withPhotos, query, labels)
        val flattened = matched.flatMap { e -> e.media.map { m -> toPhoto(e, m) } }
            .filter { !onlyFavorites || it.favorite }
        val ordered = sortPhotos(flattened, sort)

        val sections = when (layout) {
            GalleryLayout.SQUARE_GRID, GalleryLayout.FEED ->
                if (ordered.isEmpty()) emptyList() else listOf(GallerySection(GalleryGroup.Ungrouped, ordered))
            // The mosaic groups by month exactly like the date grid; only its tile layout differs (UI-side).
            GalleryLayout.JUSTIFIED_DATE, GalleryLayout.MOSAIC -> groupByMonth(ordered, zone)
            GalleryLayout.BY_PARTNER -> groupByPartner(ordered)
            GalleryLayout.PEOPLE -> emptyList() // unreachable (guarded above); keeps the when exhaustive
        }
        return GalleryResult(sections = sections, photos = ordered)
    }

    private fun applyQuery(
        encounters: List<EncounterWithDetails>,
        query: String,
        labels: CatalogLabels,
    ): List<EncounterWithDetails> {
        val tokens = EncounterSearch.tokenize(query)
        if (tokens.isEmpty()) return encounters
        val index = EncounterSearch.index(encounters, labels)
        return EncounterSearch.search(index, tokens).map { it.encounter }
    }

    private fun toPhoto(e: EncounterWithDetails, m: MediaEntity): GalleryPhoto = GalleryPhoto(
        media = m,
        encounterId = e.encounter.id,
        takenAt = e.encounter.startAt,
        partners = e.partners.map { GalleryPartner(it.id, it.displayName) },
        rating = e.encounter.satisfactionRating,
    )

    private fun sortPhotos(photos: List<GalleryPhoto>, sort: GallerySort): List<GalleryPhoto> = when (sort) {
        GallerySort.NEWEST -> photos.sortedWith(
            compareByDescending<GalleryPhoto> { it.takenAt }.thenByDescending { it.media.createdAt },
        )
        GallerySort.OLDEST -> photos.sortedWith(
            compareBy<GalleryPhoto> { it.takenAt }.thenBy { it.media.createdAt },
        )
    }

    /** Groups already-sorted photos by calendar month; the sorted input keeps months (and their photos) in order. */
    private fun groupByMonth(photos: List<GalleryPhoto>, zone: ZoneId): List<GallerySection> = photos.groupBy {
        val date = Instant.ofEpochMilli(it.takenAt).atZone(zone).toLocalDate()
        YearMonth.of(date.year, date.monthValue)
    }.map { (ym, list) -> GallerySection(GalleryGroup.Month(ym.year, ym.monthValue), list) }

    /**
     * Groups by partner: a photo appears under **every** partner its tryst involved (a threesome's photo
     * shows under each person), and photos from solo trysts fall into one trailing [GalleryGroup.Solo]
     * section. Partner sections are ordered by first appearance in the sorted input.
     */
    private fun groupByPartner(photos: List<GalleryPhoto>): List<GallerySection> {
        val byPartner = LinkedHashMap<String, MutableList<GalleryPhoto>>()
        val names = HashMap<String, String?>()
        val solo = mutableListOf<GalleryPhoto>()
        for (photo in photos) {
            if (photo.partners.isEmpty()) {
                solo += photo
                continue
            }
            for (partner in photo.partners) {
                byPartner.getOrPut(partner.id) { mutableListOf() } += photo
                names.putIfAbsent(partner.id, partner.name)
            }
        }
        val sections = byPartner.map { (id, list) -> GallerySection(GalleryGroup.Partner(id, names[id]), list) }
        return if (solo.isEmpty()) sections else sections + GallerySection(GalleryGroup.Solo, solo)
    }
}
