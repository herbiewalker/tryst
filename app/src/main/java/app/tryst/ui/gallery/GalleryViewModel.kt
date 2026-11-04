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
package app.tryst.ui.gallery

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tryst.R
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
import app.tryst.data.gallery.DateGrouping
import app.tryst.data.gallery.GalleryLayout
import app.tryst.data.gallery.GalleryPhoto
import app.tryst.data.gallery.GalleryPhotos
import app.tryst.data.gallery.GallerySection
import app.tryst.data.gallery.GallerySort
import app.tryst.data.media.ExifOrientation
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
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
    val grouping: DateGrouping = GalleryPreferences.DEFAULT_GROUPING,
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
    private val authState: GalleryAuthState,
    private val deepLink: GalleryDeepLink,
    actRepository: ActRepository,
    positionRepository: PositionRepository,
    kinkRepository: KinkRepository,
    toyRepository: ToyRepository,
    occasionRepository: OccasionRepository,
    private val partnerRepository: PartnerRepository,
    private val personPhotoRepository: PersonPhotoRepository,
    profileRepository: ProfileRepository,
    private val generalPreferences: app.tryst.core.prefs.GeneralPreferences,
    private val session: app.tryst.core.session.SessionManager,
    private val photoRevisions: app.tryst.data.media.PhotoRevisions,
) : ViewModel() {

    // Delete-with-undo: photos in [pendingDeletes] are hidden at once and only really removed once the
    // undo window passes, so a stray delete is recoverable. Nothing is staged on disk, so a process kill
    // inside the window just leaves the photos where they were.
    private val pendingDeletes = MutableStateFlow<List<GalleryPhoto>>(emptyList())

    // One-off notices for actions that used to fail silently (edit, add to person, set as avatar). The screen shows them.
    private val noticeFlow = MutableSharedFlow<Int>(extraBufferCapacity = 4)
    val notices: SharedFlow<Int> = noticeFlow.asSharedFlow()
    private var commitJob: Job? = null

    /** How many photos are waiting out the undo window (0 = nothing to undo). */
    val pendingDeleteCount: StateFlow<Int> = pendingDeletes.map { it.size }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

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

    /** Whether entering the Photos tab requires a re-auth (SEC-2 tier 2). */
    val requireReauthForPhotos: StateFlow<Boolean> = galleryPreferences.requireReauthForPhotos

    /** Records a successful re-auth so a quick tab switch back doesn't re-prompt. */
    fun markAuthed() = authState.markAuthed()

    /** True if the user re-authed within the configured grace window (0 disables the carry-over). */
    fun authedRecently(): Boolean = galleryPreferences.reauthGraceSeconds.value.takeIf { it > 0 }
        ?.let { authState.isWithinGrace(it * 1000L) } ?: false

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
        // Read details for photos that have none stored yet (older imports, older backups); a no-op once done.
        viewModelScope.launch(Dispatchers.IO) { runCatching { encounterRepository.backfillMediaMetadata() } }
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

    private data class GalleryLook(val layout: GalleryLayout, val columns: Int, val sort: GallerySort, val grouping: DateGrouping)

    private val prefs: Flow<GalleryLook> =
        combine(
            galleryPreferences.layout,
            galleryPreferences.columns,
            galleryPreferences.sort,
            galleryPreferences.dateGrouping,
            _drilledPartnerId,
        ) { layout, columns, sort, grouping, drilledId ->
            // While drilled from People, force a photo layout so the user actually sees their photos.
            val effectiveLayout = if (drilledId != null) GalleryLayout.JUSTIFIED_DATE else layout
            GalleryLook(effectiveLayout, columns, sort, grouping)
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
        pendingDeletes,
    ) { encounters, personPhotos, partners, profile, pending ->
        // Photos waiting out their undo window are hidden from every layout and from the viewer.
        val hidden = pending.mapTo(HashSet()) { it.blobId }
        GallerySources(
            encounters = if (hidden.isEmpty()) encounters else encounters.map { e -> e.copy(media = e.media.filter { it.id !in hidden }) },
            personPhotos = if (hidden.isEmpty()) personPhotos else personPhotos.filter { it.mediaBlobId !in hidden },
            partnerNames = partners.associate { it.id to it.displayName },
            profileName = profile?.displayName,
        )
    }

    val uiState: StateFlow<GalleryUiState> =
        combine(gallerySources, catalogLabels, query3, prefs) { src, labels, (filter, query, onlyFav), look ->
            val layout = look.layout
            val columns = look.columns
            val sort = look.sort
            val grouping = look.grouping
            withContext(Dispatchers.Default) {
                val result = GalleryPhotos.build(
                    encounters = src.encounters,
                    filter = filter,
                    query = query,
                    labels = labels,
                    layout = layout,
                    sort = sort,
                    onlyFavorites = onlyFav,
                    grouping = grouping,
                    personPhotos = src.personPhotos,
                    partnerNamesById = src.partnerNames,
                    profileDisplayName = src.profileName,
                )
                GalleryUiState(
                    sections = result.sections,
                    photos = result.photos,
                    layout = layout,
                    columns = columns,
                    grouping = grouping,
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

    /** The one-time Photos gesture tip (QOL-6): shown until dismissed. */
    val showGestureTip: StateFlow<Boolean> = generalPreferences.seenTips.map { GESTURE_TIP_ID !in it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Called before handing off to another app (e.g. Maps) so the auto-lock does not fire as we leave. */
    fun suppressAutoLock() = session.suppressNextAutoLock()

    fun dismissGestureTip() = generalPreferences.markTipSeen(GESTURE_TIP_ID)

    /** Pinch-to-zoom density: nudge the persisted column count (clamped in [GalleryPreferences]), GAL-5. */
    fun changeColumns(delta: Int) {
        val columns = galleryPreferences.columns.value
        val grouping = galleryPreferences.dateGrouping.value
        val dateLayout = galleryPreferences.layout.value.let { it == GalleryLayout.JUSTIFIED_DATE || it == GalleryLayout.MOSAIC } || _drilledPartnerId.value != null
        when {
            // Past the loosest density, keep spreading to section by month, then by year.
            dateLayout && delta > 0 && columns >= GalleryPreferences.MAX_COLUMNS -> galleryPreferences.setDateGrouping(grouping.coarser())
            // Past the tightest density, keep pinching in to section by day.
            dateLayout && delta < 0 && columns <= GalleryPreferences.MIN_COLUMNS -> galleryPreferences.setDateGrouping(grouping.finer())
            else -> galleryPreferences.setColumns(columns + delta)
        }
    }

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

    fun setSelected(id: String, selected: Boolean) = _selectedIds.update { if (selected) it + id else it - id }

    fun clearSelection() {
        _selectedIds.value = emptySet()
    }

    fun selectAll() {
        _selectedIds.value = uiState.value.photos.mapTo(LinkedHashSet()) { it.id }
    }

    /** Marks/unmarks a single photo (the viewer's heart), GAL-3; album photos too since v19. */
    fun toggleFavorite(photo: GalleryPhoto) = viewModelScope.launch {
        when (val src = photo.source) {
            is GalleryPhoto.Source.Encounter -> encounterRepository.setFavorite(photo.id, !photo.favorite)
            is GalleryPhoto.Source.Person -> personPhotoRepository.setFavorite(listOf(src.personPhotoId), !photo.favorite)
        }
    }

    /**
     * Sets or clears a photo's caption (CAP-1; album photos since v19). Blank text becomes NULL upstream so the
     * empty-caption state stays canonical.
     */
    fun setCaption(photo: GalleryPhoto, caption: String?) = viewModelScope.launch {
        when (val src = photo.source) {
            is GalleryPhoto.Source.Encounter -> encounterRepository.setCaption(photo.id, caption)
            is GalleryPhoto.Source.Person -> personPhotoRepository.setCaption(src.personPhotoId, caption)
        }
    }

    /** Bulk favourite for the selection: encounter photos and album photos alike (album photos since v19). */
    fun favoriteSelected(favorite: Boolean) = viewModelScope.launch {
        val encounterIds = encounterBlobsInSelection()
        if (encounterIds.isNotEmpty()) encounterRepository.setFavorite(encounterIds, favorite)
        val personIds = personPhotoIdsInSelection()
        if (personIds.isNotEmpty()) personPhotoRepository.setFavorite(personIds, favorite)
        clearSelection()
    }

    fun deleteSelected() {
        val ids = _selectedIds.value
        deleteWithUndo(uiState.value.photos.filter { it.id in ids })
        clearSelection()
    }

    /** Deletes one photo from the full-screen viewer (same undo window as bulk delete). */
    fun deletePhoto(photo: GalleryPhoto) = deleteWithUndo(listOf(photo))

    /** Brings back everything still inside the undo window. */
    fun undoDelete() {
        commitJob?.cancel()
        commitJob = null
        pendingDeletes.value = emptyList()
    }

    private fun deleteWithUndo(photos: List<GalleryPhoto>) {
        if (photos.isEmpty()) return
        // A second delete inside the window commits the first batch at once; only the latest is undoable.
        val earlier = pendingDeletes.value
        commitJob?.cancel()
        pendingDeletes.value = photos
        commitJob = viewModelScope.launch {
            if (earlier.isNotEmpty()) deletePhotos(earlier)
            delay(UNDO_WINDOW_MS)
            deletePhotos(photos)
            pendingDeletes.value = emptyList()
        }
    }

    override fun onCleared() {
        thumbCache.evictAll()
        // Leaving the screen inside the window still deletes: viewModelScope is gone, so finish on a short-lived scope.
        val pending = pendingDeletes.value
        if (pending.isNotEmpty()) CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { deletePhotos(pending) }
    }

    private suspend fun deletePhotos(photos: List<GalleryPhoto>) {
        val encounterMedia = photos.mapNotNull { it.media }
        val portraitBlobIds = photos.filter { it.source is GalleryPhoto.Source.Person }.map { it.blobId }
        encounterRepository.deleteMedia(encounterMedia)
        portraitBlobIds.forEach { personPhotoRepository.deleteByBlobId(it) }
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
    fun addPhotoToPerson(photo: GalleryPhoto, kind: String, ownerId: String) = viewModelScope.launch(Dispatchers.IO) {
        val done = runCatching {
            // The copy keeps the original's date, favourite mark and caption, so it lands in the same spot in the gallery
            // and not at today's date. Its capture details travel with the bytes (EXIF).
            personPhotoRepository.openBlob(photo.blobId).use {
                personPhotoRepository.add(kind, ownerId, it, now = photo.takenAt, favorite = photo.favorite, caption = photo.caption)
            }
        }
        noticeFlow.tryEmit(if (done.isSuccess) R.string.gallery_added_to_person else R.string.gallery_add_to_person_failed)
    }

    /**
     * Makes a gallery photo a partner's avatar (GAL-5) **without copying it**: the partner's avatar simply points at the photo's
     * existing encrypted blob. The photo therefore stays exactly where it is in the gallery, with its date, caption,
     * favourite mark and details intact, and nothing new appears at today's date. The previous avatar is left alone when
     * it is still a photo in an album or on a tryst; only a leftover blob that nothing else owns is cleaned up.
     */
    fun setAsPartnerAvatar(blobId: String, partnerId: String) = viewModelScope.launch(Dispatchers.IO) {
        val partner = partnerRepository.getById(partnerId) ?: return@launch
        val old = partner.photoMediaId
        partnerRepository.upsert(partner.copy(photoMediaId = blobId))
        if (old != null && old != blobId) partnerRepository.deletePhoto(old)
        noticeFlow.tryEmit(R.string.gallery_avatar_set)
    }

    // --- decoding (all blobs open through the same EncryptedMediaStore) ----------------------------

    // Small decodes (grid tiles, filmstrip) are kept in memory so scrolling back is instant and the full-screen
    // viewer can show the tile at once while the large image decodes. Plain bitmaps in RAM only, never on disk;
    // keyed by edit revision so a rotate or crop is never served stale.
    private class CachedThumb(val reqPx: Int, val bitmap: ImageBitmap)

    private val thumbCache = object : android.util.LruCache<String, CachedThumb>(THUMB_CACHE_BYTES) {
        override fun sizeOf(key: String, value: CachedThumb): Int = value.bitmap.width * value.bitmap.height * 4
    }

    /** The grid tile for [blobId] if it has been decoded already, for use as an instant placeholder. */
    fun cachedThumb(blobId: String): ImageBitmap? = thumbCache.get(photoBustKey(blobId))?.bitmap

    suspend fun decode(blobId: String, reqPx: Int): ImageBitmap? {
        val key = photoBustKey(blobId)
        val cacheable = reqPx <= THUMB_CACHE_MAX_PX
        if (cacheable) thumbCache.get(key)?.takeIf { it.reqPx >= reqPx }?.let { return it.bitmap }
        val bmp = MediaImages.decodeSampled(reqPx) { runCatching { personPhotoRepository.openBlob(blobId) }.getOrNull() }
        if (cacheable && bmp != null) thumbCache.put(key, CachedThumb(reqPx, bmp))
        return bmp
    }

    /** Decodes an avatar blob (a partner's or the profile's `photoMediaId`) — same encrypted store. */
    suspend fun decodePartnerPhoto(photoMediaId: String, reqPx: Int): ImageBitmap? = MediaImages.decodeSampled(reqPx) { runCatching { partnerRepository.openPhoto(photoMediaId) }.getOrNull() }

    /** A photo's width/height aspect ratio for the mosaic layout (GAL-1b); decoded once and cached. */
    suspend fun aspectRatio(blobId: String): Float {
        aspectCache[blobId]?.let { return it }
        val ratio = withContext(Dispatchers.IO) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            runCatching { personPhotoRepository.openBlob(blobId).use { BitmapFactory.decodeStream(it, null, bounds) } }
            val orientation = runCatching { personPhotoRepository.openBlob(blobId).use { ExifOrientation.read(it) } }.getOrDefault(ExifOrientation.NORMAL)
            val (w, h) = if (ExifOrientation.swapsDimensions(orientation)) bounds.outHeight to bounds.outWidth else bounds.outWidth to bounds.outHeight
            if (w > 0 && h > 0) w.toFloat() / h else 1f
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

    val photoRevision: StateFlow<Map<String, Int>> get() = photoRevisions.revisions

    /** Cache-busting key for loaders — pairs the blob id with the current edit revision. */
    fun photoBustKey(blobId: String): String = photoRevisions.keyFor(blobId)

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
            PhotoTransforms.decodeUpright(personPhotoRepository.openBlob(photo.blobId).use { it.readBytes() })
        }.getOrNull()
        val transformed = bmp?.let { runCatching { transform(it) }.getOrNull() }
        if (transformed == null) {
            noticeFlow.tryEmit(R.string.gallery_edit_failed)
            return
        }
        val jpeg = PhotoTransforms.encodeJpeg(transformed)
        val saved = runCatching {
            when (val src = photo.source) {
                is GalleryPhoto.Source.Encounter -> encounterRepository.replacePhotoBytes(src.media, jpeg)
                is GalleryPhoto.Source.Person -> personPhotoRepository.replaceBlobBytes(photo.blobId, jpeg)
            }
        }
        if (saved.isFailure) {
            noticeFlow.tryEmit(R.string.gallery_edit_failed)
            return
        }
        // Invalidate downstream caches keyed by the blob id — decoded ImageBitmap + aspect ratio.
        aspectCache.remove(photo.blobId)
        photoRevisions.bump(photo.blobId)
    }

    private fun encounterBlobsInSelection(): List<String> {
        val ids = _selectedIds.value
        return uiState.value.photos.filter { it.id in ids && it.media != null }.map { it.id }
    }

    private fun personPhotoIdsInSelection(): List<String> {
        val ids = _selectedIds.value
        return uiState.value.photos.filter { it.id in ids }.mapNotNull { (it.source as? GalleryPhoto.Source.Person)?.personPhotoId }
    }

    companion object {
        private const val GESTURE_TIP_ID = "gallery_gestures"
        private const val THUMB_CACHE_BYTES = 24 * 1024 * 1024
        private const val THUMB_CACHE_MAX_PX = 600

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

// ⏣ HW ⏣ · code · tools · homelab · ⏣ HW ⏣
