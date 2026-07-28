package app.tryst.ui.gallery

import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tryst.core.prefs.GalleryPreferences
import app.tryst.data.db.entity.Initiator
import app.tryst.data.db.entity.MediaEntity
import app.tryst.data.db.entity.Mood
import app.tryst.data.db.entity.PartnerEntity
import app.tryst.data.db.entity.Place
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
import app.tryst.data.repository.ToyRepository
import app.tryst.data.search.CatalogLabels
import app.tryst.ui.common.MediaImages
import app.tryst.ui.search.RatingFilter
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
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
import kotlinx.coroutines.withContext

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
 */
@HiltViewModel
@Suppress("LongParameterList") // Hilt-injected repositories; each is a distinct catalog/data source.
class GalleryViewModel @Inject constructor(
    private val encounterRepository: EncounterRepository,
    private val galleryPreferences: GalleryPreferences,
    actRepository: ActRepository,
    positionRepository: PositionRepository,
    kinkRepository: KinkRepository,
    toyRepository: ToyRepository,
    occasionRepository: OccasionRepository,
    private val partnerRepository: PartnerRepository,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _dateScope = MutableStateFlow<DateScope>(DateScope.AllTime)
    val dateScope: StateFlow<DateScope> = _dateScope.asStateFlow()

    private val _rating = MutableStateFlow(RatingFilter.ANY)
    val rating: StateFlow<RatingFilter> = _rating.asStateFlow()

    private val _partnerIds = MutableStateFlow<Set<String>>(emptySet())
    val partnerIds: StateFlow<Set<String>> = _partnerIds.asStateFlow()

    /** The "More filters" sheet's dimensions — everything beyond the base chips (see [SearchViewModel]). */
    private val _advanced = MutableStateFlow(EncounterFilter())
    val advanced: StateFlow<EncounterFilter> = _advanced.asStateFlow()

    val activeAdvancedCount: StateFlow<Int> = _advanced
        .map { it.advancedCount() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** Whether the Photos tab should open blurred behind a tap-to-reveal (SEC-2). */
    val blurUntilRevealed: StateFlow<Boolean> = galleryPreferences.blurUntilRevealed

    val partners: StateFlow<List<PartnerEntity>> = partnerRepository.observeActive()
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

    /** Base chips + the advanced sheet, one filter. `hasPhoto` is implicit — the gallery only shows photos. */
    private val filter: Flow<EncounterFilter> =
        combine(_dateScope, _rating, _partnerIds, _advanced) { scope, rating, partnerIds, advanced ->
            advanced.copy(
                dateRanges = listOfNotNull(scope.range()),
                ratingRange = rating.range,
                partnerIds = partnerIds,
            )
        }

    private val prefs: Flow<Triple<GalleryLayout, Int, GallerySort>> =
        combine(galleryPreferences.layout, galleryPreferences.columns, galleryPreferences.sort) { layout, columns, sort ->
            Triple(layout, columns, sort)
        }

    private val filterAndQuery: Flow<Pair<EncounterFilter, String>> =
        combine(filter, _query) { f, q -> f to q }

    val uiState: StateFlow<GalleryUiState> =
        combine(encounterRepository.observeAll(), catalogLabels, filterAndQuery, prefs) { encounters, labels, (filter, query), (layout, columns, sort) ->
            withContext(Dispatchers.Default) {
                val result = GalleryPhotos.build(encounters, filter, query, labels, layout, sort)
                GalleryUiState(
                    sections = result.sections,
                    photos = result.photos,
                    layout = layout,
                    columns = columns,
                    criteriaActive = query.isNotBlank() || filter.isActive,
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

    fun clearAll() {
        _query.value = ""
        _dateScope.value = DateScope.AllTime
        _rating.value = RatingFilter.ANY
        _partnerIds.value = emptySet()
        _advanced.value = EncounterFilter()
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

    // Layout/density/sort are set in Settings → Gallery (GallerySettingsViewModel); the gallery only reads
    // them (via galleryPreferences in uiState).

    suspend fun decode(media: MediaEntity, reqPx: Int): ImageBitmap? = MediaImages.decodeSampled(reqPx) { runCatching { encounterRepository.openMedia(media) }.getOrNull() }

    /** Decodes a partner's avatar blob (its `photoMediaId`) for the by-partner section headers. */
    suspend fun decodePartnerPhoto(photoMediaId: String, reqPx: Int): ImageBitmap? = MediaImages.decodeSampled(reqPx) { runCatching { partnerRepository.openPhoto(photoMediaId) }.getOrNull() }

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
