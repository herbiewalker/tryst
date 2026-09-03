// SPDX-License-Identifier: GPL-3.0-or-later
package app.tryst.ui.gallery

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tryst.core.prefs.GalleryPreferences
import app.tryst.data.db.entity.Initiator
import app.tryst.data.db.entity.Mood
import app.tryst.data.db.entity.PartnerEntity
import app.tryst.data.db.entity.Place
import app.tryst.data.db.entity.ProfileEntity
import app.tryst.data.db.entity.Protection
import app.tryst.data.filter.DateRange
import app.tryst.data.filter.DateScope
import app.tryst.data.filter.EncounterFilter
import app.tryst.data.filter.TimeOfDay
import app.tryst.data.gallery.GalleryLayout
import app.tryst.data.gallery.GalleryPhoto
import app.tryst.data.gallery.GalleryPhotos
import app.tryst.data.gallery.GallerySection
import app.tryst.data.gallery.GallerySort
import app.tryst.data.media.FractionalRect
import app.tryst.data.media.PhotoMeta
import app.tryst.data.media.PhotoMetadata
import app.tryst.data.media.PhotoTransforms
import app.tryst.data.repository.ActRepository
import app.tryst.data.repository.EncounterRepository
import app.tryst.data.repository.KinkRepository
import app.tryst.data.repository.OccasionRepository
import app.tryst.data.repository.PartnerRepository
import app.tryst.data.repository.PersonPhotoRepository
import app.tryst.data.repository.PositionRepository
import app.tryst.data.repository.ProfileRepository
import app.tryst.data.repository.ToyRepository
import app.tryst.data.search.CatalogLabels
import app.tryst.ui.common.MediaImages
import app.tryst.ui.search.RatingFilter
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A tryst a bulk-selected set of photos can be moved to (GAL-4): its id, date, and partner names for the label. */
data class ReassignTarget(val id: String, val date: Long, val partnerNames: List<String>)

/** Everything the gallery renders: the grouped [sections], the flat [photos] the viewer pages, and the look. */
data class GalleryUiState(
    val sections: List<GallerySection> = emptyList(),
    val photos: List<GalleryPhoto> = emptyList(),
    val layout: GalleryLayout = GalleryPreferences.DEFAULT_LAYOUT,
    val columns: Int = GalleryPreferences.DEFAULT_COLUMNS,
    val criteriaActive: Boolean = false,
) {
    val totalCount: Int get() = photos.size
}

/**
 * Photos gallery (GAL-1) — the third consumer of the FILT-1 layer. Reuses Search's structured-filter
 * machinery (date scope, rating, partners, and the full "More filters" [EncounterFilter] via [advanced])
 * plus a free-text query, and combines them with the persisted [GalleryPreferences] look. The heavy
 * filter/group work runs off the main thread; the result is [GalleryPhotos.build].
 *
 * Beyond browsing it also owns the gallery's edit surface: a favourites mark (GAL-3), multi-select with
 * bulk delete / favourite / reassign (GAL-4), and "set as partner avatar".
 */
@HiltViewModel
@Suppress("LongParameterList", "TooManyFunctions") // Hilt-injected repositories + the gallery's several actions.
class GalleryViewModel @Inject constructor(
    private val encounterRepository: EncounterRepository,
    private val galleryPreferences: GalleryPreferences,
    private val revealState: GalleryRevealState,
    private val deepLink: GalleryDeepLink,
    actRepository: ActRepository,
    positionRepository: PositionRepository,
    kinkRepository: KinkRepository,
    toyRepository: ToyRepository,
    occasionRepository: OccasionRepository,
    private val partnerRepository: PartnerRepository,
    private val personPhotoRepository: PersonPhotoRepository,
    profileRepository: ProfileRepository,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _dateScope = MutableStateFlow<DateScope>(DateScope.AllTime)
    val dateScope: StateFlow<DateScope> = _dateScope.asStateFlow()

    private val _rating = MutableStateFlow(RatingFilter.ANY)
    val rating: StateFlow<RatingFilter> = _rating.asStateFlow()

    private val _partnerIds = MutableStateFlow<Set<String>>(emptySet())
    val partnerIds: StateFlow<Set<String>> = _partnerIds.asStateFlow()

    /**
     * Show only favourited photos (GAL-3). Seeded from [GalleryPreferences.defaultToFavoritesOnly] so a
     * user who's chosen "open with favourites only" always lands filtered on fresh VM construction;
     * flipping the app-bar heart during a session is transient and doesn't touch the pref.
     */
    private val _onlyFavorites = MutableStateFlow(galleryPreferences.defaultToFavoritesOnly.value)
    val onlyFavorites: StateFlow<Boolean> = _onlyFavorites.asStateFlow()

    /** The "More filters" sheet's dimensions — everything beyond the base chips (see [SearchViewModel]). */
    private val _advanced = MutableStateFlow(EncounterFilter())
    val advanced: StateFlow<EncounterFilter> = _advanced.asStateFlow()

    /** Ids of the currently multi-selected photos; empty = not in selection mode (GAL-4). */
    private val _selectedIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedIds: StateFlow<Set<String>> = _selectedIds.asStateFlow()

    /**
     * People-layout drill (GAL-1a): tapping an avatar in the People grid sets this to that partner's id
     * and the gallery switches to a filtered By-date view of their photos. Exit clears it and the People
     * layout comes back. Transient (not persisted) — a fresh app launch always starts un-drilled.
     */
    private val _drilledPartnerId = MutableStateFlow<String?>(null)
    val drilledPartnerId: StateFlow<String?> = _drilledPartnerId.asStateFlow()

    /** The active partner's row when drilled — for the "Photos of {name}" top-bar title. Null during self drill. */
    val drilledPartner: StateFlow<PartnerEntity?> =
        combine(_drilledPartnerId, partnerRepository.observeActive()) { id, list -> list.firstOrNull { it.id == id } }
            .catch { emit(null) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** True when the current drill is the self profile (owner id "self"). */
    val drilledIntoSelf: StateFlow<Boolean> = _drilledPartnerId
        .map { it == SELF_OWNER_ID }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun drillIntoPerson(partnerId: String) {
        _drilledPartnerId.value = partnerId
    }

    /** People-self-avatar drill: filter to the self "partner" id (aka [ProfileEntity.SELF_ID]). */
    fun drillIntoSelf() {
        _drilledPartnerId.value = SELF_OWNER_ID
    }

    fun exitDrill() {
        _drilledPartnerId.value = null
    }

    val activeAdvancedCount: StateFlow<Int> = _advanced
        .map { it.advancedCount() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** Whether the Photos tab should open blurred behind a tap-to-reveal (SEC-2). */
    val blurUntilRevealed: StateFlow<Boolean> = galleryPreferences.blurUntilRevealed

    /** Slideshow speed in seconds (user-picked in Settings → Gallery). */
    val slideshowIntervalSeconds: StateFlow<Int> = galleryPreferences.slideshowIntervalSeconds

    /** Whether the slideshow plays photos in a shuffled order. */
    val slideshowShuffle: StateFlow<Boolean> = galleryPreferences.slideshowShuffle

    /** How tiles are spaced in grid/mosaic layouts (user-picked in Settings → Gallery). */
    val gridSpacing: StateFlow<app.tryst.data.gallery.GridSpacing> = galleryPreferences.gridSpacing

    /** Whether to draw a small date · partner caption under each grid tile. */
    val showTileCaptions: StateFlow<Boolean> = galleryPreferences.showTileCaptions

    /** Where to expose the per-photo caption editor in the viewer (CAP-1, D-55). */
    val captionEntryPoint: StateFlow<app.tryst.core.prefs.CaptionEntryPoint> = galleryPreferences.captionEntryPoint

    /** Records a reveal so a quick tab switch back doesn't re-blur (see [GalleryRevealState]). */
    fun markRevealed() = revealState.markRevealed()

    /** True if the gallery was revealed within the user-configured grace window (0 disables the carry-over). */
    fun revealedRecently(): Boolean = galleryPreferences.blurGraceSeconds.value.takeIf { it > 0 }?.let { revealState.isWithinGrace(it * 1000L) } ?: false

    val partners: StateFlow<List<PartnerEntity>> = partnerRepository.observeActive()
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The self-profile row (for the People layout's "You" avatar), GAL-1a. */
    val profile: StateFlow<ProfileEntity?> = profileRepository.observe()
        .catch { emit(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Candidate trysts to move selected photos into (bulk reassign, GAL-4), newest first. */
    val reassignTargets: StateFlow<List<ReassignTarget>> = encounterRepository.observeAll()
        .map { list ->
            withContext(Dispatchers.Default) {
                list.map { e ->
                    ReassignTarget(
                        id = e.encounter.id,
                        date = e.encounter.startAt,
                        partnerNames = e.partners.mapNotNull { it.displayName?.takeIf(String::isNotBlank) },
                    )
                }.sortedByDescending { it.date }
            }
        }
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val availableYears: StateFlow<List<Int>> = encounterRepository.observeAll()
        .map { list ->
            withContext(Dispatchers.Default) {
                list.map { Instant.ofEpochMilli(it.encounter.startAt).atZone(ZoneId.systemDefault()).year }
                    .distinct()
                    .sortedDescending()
            }
        }
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val catalogLabels: StateFlow<CatalogLabels> = combine(
        actRepository.observeCustom().map { rows -> rows.associate { it.id to it.label } },
        positionRepository.observeCustom().map { rows -> rows.associate { it.id to it.label } },
        kinkRepository.observeCustom().map { rows -> rows.associate { it.id to it.label } },
        toyRepository.observeCustom().map { rows -> rows.associate { it.id to it.label } },
        occasionRepository.observeCustom().map { rows -> rows.associate { it.id to it.label } },
    ) { acts, positions, kinks, toys, occasions ->
        CatalogLabels(acts = acts, positions = positions, kinks = kinks, toys = toys, occasions = occasions)
    }
        .catch { emit(CatalogLabels.EMPTY) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CatalogLabels.EMPTY)

    init {
        // Consume a pending Partners → gallery deep link (open pre-filtered to that partner), GAL-5.
        viewModelScope.launch {
            deepLink.pendingPartnerId.collect { id ->
                if (id != null) {
                    _partnerIds.value = setOf(id)
                    deepLink.consume()
                }
            }
        }
    }

    /** Base chips + the advanced sheet + drill, one filter. `hasPhoto` is implicit — the gallery only shows photos. */
    private val filter: Flow<EncounterFilter> =
        combine(_dateScope, _rating, _partnerIds, _advanced, _drilledPartnerId) { scope, rating, partnerIds, advanced, drilledId ->
            // Drilled-in-from-People wins over the persistent partner filter so you always see just that person's photos.
            // "self" isn't a real partner id in the encounters table, so a drill into You becomes
            // "include solo encounters, no partner constraint" — that surfaces every solo encounter's
            // photos alongside the self-profile portrait album.
            val (effectivePartnerIds, effectiveIncludeSolo) = when (drilledId) {
                null -> partnerIds to advanced.includeSolo
                SELF_OWNER_ID -> emptySet<String>() to true
                else -> setOf(drilledId) to false
            }
            advanced.copy(
                dateRanges = listOfNotNull(scope.range()),
                ratingRange = rating.range,
                partnerIds = effectivePartnerIds,
                includeSolo = effectiveIncludeSolo,
            )
        }

    private val prefs: Flow<Triple<GalleryLayout, Int, GallerySort>> =
        combine(galleryPreferences.layout, galleryPreferences.columns, galleryPreferences.sort, _drilledPartnerId) { layout, columns, sort, drilledId ->
            // While drilled from People, force a photo layout so the user actually sees their photos.
            val effectiveLayout = if (drilledId != null) GalleryLayout.JUSTIFIED_DATE else layout
            Triple(effectiveLayout, columns, sort)
        }

    /** The filter, the free-text query, and the favourites-only toggle — the three query inputs. */
    private val query3: Flow<Triple<EncounterFilter, String, Boolean>> =
        combine(filter, _query, _onlyFavorites) { f, q, onlyFav -> Triple(f, q, onlyFav) }

    /** Every partner + self-profile portrait, folded into the gallery pipeline (v15+). */
    private val allPersonPhotos = personPhotoRepository.observeAll()
        .catch { emit(emptyList()) }

    // Bundle the four "gallery input" flows so uiState's combine stays a 4-arg call (arity cap).
    private data class GallerySources(
        val encounters: List<app.tryst.data.db.relation.EncounterWithDetails>,
        val personPhotos: List<app.tryst.data.db.entity.PersonPhotoEntity>,
        val partnerNames: Map<String, String?>,
        val profileName: String?,
    )

    private val gallerySources: Flow<GallerySources> = combine(
        encounterRepository.observeAll(),
        allPersonPhotos,
        partners,
        profile,
    ) { encounters, personPhotos, partners, profile ->
        GallerySources(
            encounters = encounters,
            personPhotos = personPhotos,
            partnerNames = partners.associate { it.id to it.displayName },
            profileName = profile?.displayName,
        )
    }

    val uiState: StateFlow<GalleryUiState> =
        combine(gallerySources, catalogLabels, query3, prefs) { src, labels, (filter, query, onlyFav), (layout, columns, sort) ->
            withContext(Dispatchers.Default) {
                val result = GalleryPhotos.build(
                    encounters = src.encounters,
                    filter = filter,
                    query = query,
                    labels = labels,
                    layout = layout,
                    sort = sort,
                    onlyFavorites = onlyFav,
                    personPhotos = src.personPhotos,
                    partnerNamesById = src.partnerNames,
                    profileDisplayName = src.profileName,
                )
                GalleryUiState(
                    sections = result.sections,
                    photos = result.photos,
                    layout = layout,
                    columns = columns,
                    criteriaActive = query.isNotBlank() || filter.isActive || onlyFav,
                )
            }
        }
            .catch { emit(GalleryUiState()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GalleryUiState())

    // --- query + base chips ---------------------------------------------------------------------------

    fun setQuery(value: String) = _query.update { value }

    fun setDateScope(value: DateScope) = _dateScope.update { value }

    fun setCustomRange(start: LocalDate, end: LocalDate) {
        val range = if (start <= end) DateRange(start, end) else DateRange(end, start)
        _dateScope.value = DateScope.Custom(range)
    }

    fun setRating(value: RatingFilter) = _rating.update { value }

    fun togglePartner(id: String) = _partnerIds.update { if (id in it) it - id else it + id }

    fun setOnlyFavorites(value: Boolean) = _onlyFavorites.update { value }

    /** Pinch-to-zoom density: nudge the persisted column count (clamped in [GalleryPreferences]), GAL-5. */
    fun changeColumns(delta: Int) = galleryPreferences.setColumns(galleryPreferences.columns.value + delta)

    fun clearAll() {
        _query.value = ""
        _dateScope.value = DateScope.AllTime
        _rating.value = RatingFilter.ANY
        _partnerIds.value = emptySet()
        _onlyFavorites.value = false
        _advanced.value = EncounterFilter()
        _drilledPartnerId.value = null
    }

    // --- advanced filters (the "More filters" sheet) --------------------------------------------------

    fun toggleAct(id: String) = _advanced.update { it.copy(actIds = it.actIds.toggled(id)) }
    fun togglePosition(id: String) = _advanced.update { it.copy(positionIds = it.positionIds.toggled(id)) }
    fun toggleKink(id: String) = _advanced.update { it.copy(kinkIds = it.kinkIds.toggled(id)) }
    fun toggleToy(id: String) = _advanced.update { it.copy(toyIds = it.toyIds.toggled(id)) }
    fun toggleOccasion(id: String) = _advanced.update { it.copy(occasionIds = it.occasionIds.toggled(id)) }
    fun togglePlace(value: Place) = _advanced.update { it.copy(places = it.places.toggled(value)) }
    fun toggleProtection(value: Protection) = _advanced.update { it.copy(protection = it.protection.toggled(value)) }
    fun toggleMood(value: Mood) = _advanced.update { it.copy(moods = it.moods.toggled(value)) }
    fun toggleInitiator(value: Initiator) = _advanced.update { it.copy(initiators = it.initiators.toggled(value)) }
    fun toggleWeekday(value: DayOfWeek) = _advanced.update { it.copy(weekdays = it.weekdays.toggled(value)) }
    fun toggleTimeOfDay(value: TimeOfDay) = _advanced.update { it.copy(timesOfDay = it.timesOfDay.toggled(value)) }

    fun setDurationRange(range: IntRange?) = _advanced.update { it.copy(durationRange = range) }
    fun setHasNote(value: Boolean?) = _advanced.update { it.copy(hasNote = value) }
    fun setIncludeSolo(value: Boolean) = _advanced.update { it.copy(includeSolo = value) }

    fun clearAdvanced() {
        _advanced.value = EncounterFilter()
    }

    // --- selection + bulk actions (GAL-4) -------------------------------------------------------------

    fun toggleSelected(id: String) = _selectedIds.update { if (id in it) it - id else it + id }

    fun clearSelection() {
        _selectedIds.value = emptySet()
    }

    fun selectAll() {
        _selectedIds.value = uiState.value.photos.mapTo(LinkedHashSet()) { it.id }
    }

    /** Marks/unmarks a single photo (the viewer's star), GAL-3. Only encounter photos are favouritable. */
    fun toggleFavorite(photo: GalleryPhoto) = viewModelScope.launch {
        if (photo.media != null) encounterRepository.setFavorite(photo.id, !photo.favorite)
    }

    /**
     * Sets or clears a photo's caption (CAP-1). Only encounter photos carry captions — person portraits
     * (which have no `media` row) silently no-op. Blank text becomes NULL upstream so the empty-caption
     * state stays canonical.
     */
    fun setCaption(photo: GalleryPhoto, caption: String?) = viewModelScope.launch {
        if (photo.media != null) encounterRepository.setCaption(photo.id, caption)
    }

    /** Bulk favourite: only the encounter photos in the selection qualify — portraits carry no favourite mark. */
    fun favoriteSelected(favorite: Boolean) = viewModelScope.launch {
        val encounterIds = encounterBlobsInSelection()
        if (encounterIds.isNotEmpty()) encounterRepository.setFavorite(encounterIds, favorite)
        clearSelection()
    }

    fun deleteSelected() = viewModelScope.launch {
        val ids = _selectedIds.value
        val selected = uiState.value.photos.filter { it.id in ids }
        val encounterMedia = selected.mapNotNull { it.media }
        val portraitBlobIds = selected.filter { it.source is GalleryPhoto.Source.Person }.map { it.blobId }
        encounterRepository.deleteMedia(encounterMedia)
        portraitBlobIds.forEach { personPhotoRepository.deleteByBlobId(it) }
        clearSelection()
    }

    /** Reassign only applies to encounter photos — portraits belong to a person, not a tryst. */
    fun reassignSelected(encounterId: String) = viewModelScope.launch {
        val encounterIds = encounterBlobsInSelection()
        if (encounterIds.isNotEmpty()) encounterRepository.reassignMedia(encounterIds, encounterId)
        clearSelection()
    }

    /**
     * Every person the viewer can tag a photo onto — active partners + You. Composed from the
     * observed partner list and profile, so it stays in sync with adds/renames/archives.
     */
    val assignablePeople: StateFlow<List<AssignablePerson>> =
        combine(partners, profile) { list, self ->
            val selfName = self?.displayName?.takeIf { it.isNotBlank() }
            listOf(AssignablePerson(PersonPhotoRepository.KIND_PROFILE, SELF_OWNER_ID, selfName)) +
                list.map { p -> AssignablePerson(PersonPhotoRepository.KIND_PARTNER, p.id, p.displayName) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Copies a gallery photo (encounter or portrait — both are just encrypted blobs) into a person's
     * portrait album. The source stays put; a fresh `person_photo` row + re-encrypted blob is created.
     * Cheap way to fix a mis-attributed photo (e.g. Alex was on the shot but not listed on the tryst)
     * without touching the encounter's partner list.
     */
    fun addPhotoToPerson(blobId: String, kind: String, ownerId: String) = viewModelScope.launch(Dispatchers.IO) {
        runCatching {
            personPhotoRepository.openBlob(blobId).use { personPhotoRepository.add(kind, ownerId, it) }
        }
    }

    /**
     * Copies a gallery photo into a partner's avatar (GAL-5). Works for encounter photos AND portraits —
     * both are just encrypted blobs. The fresh copy is added to the partner's **portrait album** (not
     * just to `partners.photoMediaId`) so PersonPhotoStrip can render the checkmark on the current
     * avatar and the user can delete the copy from the strip later. Cleans up the previous avatar's
     * blob (and its portrait row if it had one) so we don't accumulate orphan encrypted files.
     */
    fun setAsPartnerAvatar(blobId: String, partnerId: String) = viewModelScope.launch(Dispatchers.IO) {
        val partner = partnerRepository.getById(partnerId) ?: return@launch
        val newPortrait = runCatching {
            personPhotoRepository.openBlob(blobId).use { input ->
                personPhotoRepository.add(PersonPhotoRepository.KIND_PARTNER, partnerId, input)
            }
        }.getOrNull() ?: return@launch
        val newId = newPortrait.mediaBlobId
        val old = partner.photoMediaId
        partnerRepository.upsert(partner.copy(photoMediaId = newId))
        if (old != null && old != newId) {
            // If old was a portrait, deleteByBlobId removes both the row and the blob; if it wasn't
            // tracked (legacy pre-v15 avatar), fall back to the raw-blob delete.
            if (!personPhotoRepository.deleteByBlobId(old)) partnerRepository.deletePhoto(old)
        }
    }

    // --- decoding (all blobs open through the same EncryptedMediaStore) ----------------------------

    suspend fun decode(blobId: String, reqPx: Int): ImageBitmap? = MediaImages.decodeSampled(reqPx) { runCatching { personPhotoRepository.openBlob(blobId) }.getOrNull() }

    /** Decodes an avatar blob (a partner's or the profile's `photoMediaId`) — same encrypted store. */
    suspend fun decodePartnerPhoto(photoMediaId: String, reqPx: Int): ImageBitmap? = MediaImages.decodeSampled(reqPx) { runCatching { partnerRepository.openPhoto(photoMediaId) }.getOrNull() }

    /** A photo's width/height aspect ratio for the mosaic layout (GAL-1b); decoded once and cached. */
    suspend fun aspectRatio(blobId: String): Float {
        aspectCache[blobId]?.let { return it }
        val ratio = withContext(Dispatchers.IO) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            runCatching { personPhotoRepository.openBlob(blobId).use { BitmapFactory.decodeStream(it, null, bounds) } }
            if (bounds.outWidth > 0 && bounds.outHeight > 0) bounds.outWidth.toFloat() / bounds.outHeight else 1f
        }
        aspectCache[blobId] = ratio
        return ratio
    }

    private val aspectCache = ConcurrentHashMap<String, Float>()

    /** Reads a photo's embedded metadata (date/dimensions/camera/location) for the viewer info panel (META-1). */
    suspend fun readMeta(blobId: String): PhotoMeta = withContext(Dispatchers.IO) {
        PhotoMetadata.read { runCatching { personPhotoRepository.openBlob(blobId) }.getOrNull() }
    }

    // --- editing (EDIT-1) --------------------------------------------------------------------------
    //
    // Blob bytes are replaced in place (same id), so nothing that references the photo has to be
    // re-threaded. The `photoRevision` map bumps per edited blob so caching consumers (DecodedImage
    // keyed by `photoBustKey(blobId)`, the aspect cache below) know to re-load.

    private val _photoRevision = MutableStateFlow<Map<String, Int>>(emptyMap())
    val photoRevision: StateFlow<Map<String, Int>> = _photoRevision.asStateFlow()

    /** Cache-busting key for loaders — pairs the blob id with the current edit revision. */
    fun photoBustKey(blobId: String): String = "$blobId#${_photoRevision.value[blobId] ?: 0}"

    /** Rotates a photo by [degrees] (typically ±90) and re-encrypts it in place (EDIT-1). */
    fun rotate(photo: GalleryPhoto, degrees: Int) = viewModelScope.launch(Dispatchers.IO) {
        transformInPlace(photo) { bmp -> PhotoTransforms.rotate(bmp, degrees) }
    }

    /** Crops a photo to [rect] (fractional coords over the original image) and re-encrypts in place. */
    fun crop(photo: GalleryPhoto, rect: FractionalRect) = viewModelScope.launch(Dispatchers.IO) {
        transformInPlace(photo) { bmp -> PhotoTransforms.crop(bmp, rect) }
    }

    private suspend fun transformInPlace(photo: GalleryPhoto, transform: (android.graphics.Bitmap) -> android.graphics.Bitmap) {
        val bmp = runCatching {
            personPhotoRepository.openBlob(photo.blobId).use { PhotoTransforms.decode(it) }
        }.getOrNull() ?: return
        val transformed = runCatching { transform(bmp) }.getOrNull() ?: return
        val jpeg = PhotoTransforms.encodeJpeg(transformed)
        when (val src = photo.source) {
            is GalleryPhoto.Source.Encounter -> encounterRepository.replacePhotoBytes(src.media, jpeg)
            is GalleryPhoto.Source.Person -> personPhotoRepository.replaceBlobBytes(photo.blobId, jpeg)
        }
        // Invalidate downstream caches keyed by the blob id — decoded ImageBitmap + aspect ratio.
        aspectCache.remove(photo.blobId)
        _photoRevision.update { current -> current + (photo.blobId to ((current[photo.blobId] ?: 0) + 1)) }
    }

    private fun encounterBlobsInSelection(): List<String> {
        val ids = _selectedIds.value
        return uiState.value.photos.filter { it.id in ids && it.media != null }.map { it.id }
    }

    companion object {
        /** Matches [app.tryst.data.db.entity.ProfileEntity.SELF_ID]; using it as a partner-id filter selects portraits owned by the self profile. */
        const val SELF_OWNER_ID = "self"
    }

    private fun <T> Set<T>.toggled(value: T): Set<T> = if (value in this) this - value else this + value

    private fun EncounterFilter.advancedCount(): Int = listOf(
        actIds.isNotEmpty(), positionIds.isNotEmpty(), kinkIds.isNotEmpty(), toyIds.isNotEmpty(),
        occasionIds.isNotEmpty(), places.isNotEmpty(), protection.isNotEmpty(), moods.isNotEmpty(),
        initiators.isNotEmpty(), weekdays.isNotEmpty(), timesOfDay.isNotEmpty(),
        durationRange != null, hasNote != null, includeSolo,
    ).count { it }
}
