package app.tryst.ui.gallery

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tryst.core.prefs.GalleryPreferences
import app.tryst.data.db.entity.Initiator
import app.tryst.data.db.entity.MediaEntity
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
import app.tryst.data.media.PhotoMeta
import app.tryst.data.media.PhotoMetadata
import app.tryst.data.repository.ActRepository
import app.tryst.data.repository.EncounterRepository
import app.tryst.data.repository.KinkRepository
import app.tryst.data.repository.OccasionRepository
import app.tryst.data.repository.PartnerRepository
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

    /** Show only favourited photos (GAL-3). */
    private val _onlyFavorites = MutableStateFlow(false)
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

    /** The active partner's row when drilled — for the "Photos of {name}" top-bar title. */
    val drilledPartner: StateFlow<PartnerEntity?> =
        combine(_drilledPartnerId, partnerRepository.observeActive()) { id, list -> list.firstOrNull { it.id == id } }
            .catch { emit(null) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun drillIntoPerson(partnerId: String) {
        _drilledPartnerId.value = partnerId
    }

    fun exitDrill() {
        _drilledPartnerId.value = null
    }

    val activeAdvancedCount: StateFlow<Int> = _advanced
        .map { it.advancedCount() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** Whether the Photos tab should open blurred behind a tap-to-reveal (SEC-2). */
    val blurUntilRevealed: StateFlow<Boolean> = galleryPreferences.blurUntilRevealed

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
            val effectivePartnerIds = drilledId?.let { setOf(it) } ?: partnerIds
            advanced.copy(
                dateRanges = listOfNotNull(scope.range()),
                ratingRange = rating.range,
                partnerIds = effectivePartnerIds,
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

    val uiState: StateFlow<GalleryUiState> =
        combine(encounterRepository.observeAll(), catalogLabels, query3, prefs) { encounters, labels, (filter, query, onlyFav), (layout, columns, sort) ->
            withContext(Dispatchers.Default) {
                val result = GalleryPhotos.build(encounters, filter, query, labels, layout, sort, onlyFavorites = onlyFav)
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

    /** Marks/unmarks a single photo (the viewer's star), GAL-3. */
    fun toggleFavorite(photo: GalleryPhoto) = viewModelScope.launch {
        encounterRepository.setFavorite(photo.id, !photo.favorite)
    }

    fun favoriteSelected(favorite: Boolean) = viewModelScope.launch {
        encounterRepository.setFavorite(_selectedIds.value.toList(), favorite)
        clearSelection()
    }

    fun deleteSelected() = viewModelScope.launch {
        val ids = _selectedIds.value
        val media = uiState.value.photos.filter { it.id in ids }.map { it.media }
        encounterRepository.deleteMedia(media)
        clearSelection()
    }

    fun reassignSelected(encounterId: String) = viewModelScope.launch {
        encounterRepository.reassignMedia(_selectedIds.value.toList(), encounterId)
        clearSelection()
    }

    /**
     * Copies a gallery photo into a partner's avatar (GAL-5). The photo's encrypted bytes are re-encrypted
     * as a fresh partner-photo blob; the partner's previous avatar blob (if any) is removed after the swap.
     */
    fun setAsPartnerAvatar(media: MediaEntity, partnerId: String) = viewModelScope.launch(Dispatchers.IO) {
        val partner = partnerRepository.getById(partnerId) ?: return@launch
        val newId = runCatching { partnerRepository.savePhoto(encounterRepository.openMedia(media)) }.getOrNull() ?: return@launch
        val old = partner.photoMediaId
        partnerRepository.upsert(partner.copy(photoMediaId = newId))
        if (old != null && old != newId) partnerRepository.deletePhoto(old)
    }

    // --- decoding -------------------------------------------------------------------------------------

    suspend fun decode(media: MediaEntity, reqPx: Int): ImageBitmap? = MediaImages.decodeSampled(reqPx) { runCatching { encounterRepository.openMedia(media) }.getOrNull() }

    /** Decodes an avatar blob (a partner's or the profile's `photoMediaId`) — same encrypted store. */
    suspend fun decodePartnerPhoto(photoMediaId: String, reqPx: Int): ImageBitmap? = MediaImages.decodeSampled(reqPx) { runCatching { partnerRepository.openPhoto(photoMediaId) }.getOrNull() }

    /** A photo's width/height aspect ratio for the mosaic layout (GAL-1b); decoded once and cached. */
    suspend fun aspectRatio(media: MediaEntity): Float {
        aspectCache[media.id]?.let { return it }
        val ratio = withContext(Dispatchers.IO) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            runCatching { encounterRepository.openMedia(media).use { BitmapFactory.decodeStream(it, null, bounds) } }
            if (bounds.outWidth > 0 && bounds.outHeight > 0) bounds.outWidth.toFloat() / bounds.outHeight else 1f
        }
        aspectCache[media.id] = ratio
        return ratio
    }

    private val aspectCache = ConcurrentHashMap<String, Float>()

    /** Reads a photo's embedded metadata (date/dimensions/camera/location) for the viewer info panel (META-1). */
    suspend fun readMeta(media: MediaEntity): PhotoMeta = withContext(Dispatchers.IO) {
        PhotoMetadata.read { runCatching { encounterRepository.openMedia(media) }.getOrNull() }
    }

    private fun <T> Set<T>.toggled(value: T): Set<T> = if (value in this) this - value else this + value

    private fun EncounterFilter.advancedCount(): Int = listOf(
        actIds.isNotEmpty(), positionIds.isNotEmpty(), kinkIds.isNotEmpty(), toyIds.isNotEmpty(),
        occasionIds.isNotEmpty(), places.isNotEmpty(), protection.isNotEmpty(), moods.isNotEmpty(),
        initiators.isNotEmpty(), weekdays.isNotEmpty(), timesOfDay.isNotEmpty(),
        durationRange != null, hasNote != null, includeSolo,
    ).count { it }
}
