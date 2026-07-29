package app.tryst.data.gallery

import app.tryst.data.db.entity.MediaEntity
import app.tryst.data.db.entity.PersonPhotoEntity
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
 * Pipeline:
 *   1. Keep encounters that **have media** and match the structured [EncounterFilter]; narrow by the
 *      free-text [EncounterSearch] query; flatten survivors' photos into encounter [GalleryPhoto]s
 *      (carrying the tryst's date/partners/rating).
 *   2. Fold in **person portraits** (v15+) as their own [GalleryPhoto]s, each attributed to its
 *      owner's partner row (or "self"). Portraits use the same structured filter — a partner-ids
 *      filter picks their partner's portraits, a favourites-only filter drops them (they carry no
 *      favourite mark), etc. — so drilling from People into a partner sees encounter + portrait
 *      photos combined.
 *   3. Sort + group per the chosen [GalleryLayout].
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
        personPhotos: List<PersonPhotoEntity> = emptyList(),
        partnerNamesById: Map<String, String?> = emptyMap(),
        profileDisplayName: String? = null,
    ): GalleryResult {
        // PEOPLE draws avatars from the Partners/Profile data, not encounter photos — nothing to build here.
        if (!layout.isPhotoLayout) return GalleryResult()

        val withPhotos = encounters.filter { it.media.isNotEmpty() && filter.matches(it, zone) }
        val matched = applyQuery(withPhotos, query, labels)
        val encounterPhotos = matched.flatMap { e -> e.media.map { m -> encounterPhoto(e, m) } }
        val portraitPhotos = personPhotos.mapNotNull { pp -> portraitPhoto(pp, partnerNamesById, profileDisplayName) }
            .filter { personPhotoPassesFilter(it, filter, zone) }
        val flattened = (encounterPhotos + portraitPhotos).filter { !onlyFavorites || it.favorite }
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

    private fun encounterPhoto(e: EncounterWithDetails, m: MediaEntity): GalleryPhoto = GalleryPhoto(
        blobId = m.id,
        mimeType = m.mimeType,
        takenAt = e.encounter.startAt,
        partners = e.partners.map { GalleryPartner(it.id, it.displayName) },
        rating = e.encounter.satisfactionRating,
        favorite = m.favorite,
        source = GalleryPhoto.Source.Encounter(encounterId = e.encounter.id, media = m),
    )

    /**
     * Builds a portrait's [GalleryPhoto]. For a partner portrait, [partnerNamesById] supplies the display
     * name (the row's stored `displayName`, or null for an anonymous partner). For the self portrait
     * (owner "self"), [profileDisplayName] supplies the "You" label so the caption shows a name.
     */
    private fun portraitPhoto(
        pp: PersonPhotoEntity,
        partnerNamesById: Map<String, String?>,
        profileDisplayName: String?,
    ): GalleryPhoto? {
        val (partnerId, partnerName) = when (pp.ownerKind) {
            "partner" -> pp.ownerId to partnerNamesById[pp.ownerId]
            "profile" -> "self" to profileDisplayName
            else -> return null // unknown owner kind → skip defensively
        }
        return GalleryPhoto(
            blobId = pp.mediaBlobId,
            mimeType = "image/*",
            takenAt = pp.addedAt,
            partners = listOf(GalleryPartner(id = partnerId, name = partnerName)),
            rating = null,
            favorite = false,
            source = GalleryPhoto.Source.Person(ownerKind = pp.ownerKind, ownerId = pp.ownerId, personPhotoId = pp.id),
        )
    }

    /**
     * Portrait photos have no encounter, so only a subset of [EncounterFilter] applies to them:
     * partner selection, date range on [PersonPhotoEntity.addedAt], and has-photo (always true).
     * Everything else — acts/positions/protection/etc. — makes no sense for a portrait and is treated
     * as passthrough for portraits. Photo-only filters (has-note, note-contains, ratings, duration,
     * timeOfDay, weekday, mood, initiator, kinks, toys, occasions) filter portraits out when set.
     */
    @Suppress("CyclomaticComplexMethod", "ReturnCount") // Guard-clause per filter category — clearer than folding into one boolean.
    private fun personPhotoPassesFilter(photo: GalleryPhoto, filter: EncounterFilter, zone: ZoneId): Boolean {
        if (filter.dateRanges.isNotEmpty()) {
            val date = Instant.ofEpochMilli(photo.takenAt).atZone(zone).toLocalDate()
            if (filter.dateRanges.none { date in it }) return false
        }
        if (filter.partnerIds.isNotEmpty() || filter.includeSolo) {
            val ownerId = photo.partners.firstOrNull()?.id
            val byPartner = filter.partnerIds.isNotEmpty() && ownerId != null && ownerId in filter.partnerIds
            if (!byPartner && !filter.includeSolo) return false // portraits are never solo
        }
        // Any structured category that would exclude a photo based on encounter fields excludes portraits too
        // (they simply have no such data).
        val excludingCategorySet = filter.actIds.isNotEmpty() ||
            filter.positionIds.isNotEmpty() ||
            filter.places.isNotEmpty() ||
            filter.occasionIds.isNotEmpty() ||
            filter.kinkIds.isNotEmpty() ||
            filter.toyIds.isNotEmpty() ||
            filter.protection.isNotEmpty() ||
            filter.moods.isNotEmpty() ||
            filter.initiators.isNotEmpty() ||
            filter.weekdays.isNotEmpty() ||
            filter.timesOfDay.isNotEmpty() ||
            filter.ratingRange != null ||
            filter.durationRange != null ||
            filter.hasNote != null ||
            !filter.noteContains.isNullOrBlank()
        if (excludingCategorySet) return false
        return true
    }

    private fun sortPhotos(photos: List<GalleryPhoto>, sort: GallerySort): List<GalleryPhoto> = when (sort) {
        // Second-level: the encounter media's createdAt when present, else the takenAt itself — keeps
        // multi-photo trysts stable while treating portraits (no media row) purely by their addedAt.
        GallerySort.NEWEST -> photos.sortedWith(
            compareByDescending<GalleryPhoto> { it.takenAt }.thenByDescending { it.media?.createdAt ?: it.takenAt },
        )
        GallerySort.OLDEST -> photos.sortedWith(
            compareBy<GalleryPhoto> { it.takenAt }.thenBy { it.media?.createdAt ?: it.takenAt },
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
