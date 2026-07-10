package app.tryst.ui.search

import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tryst.data.db.entity.MediaEntity
import app.tryst.data.db.entity.PartnerEntity
import app.tryst.data.filter.DateRange
import app.tryst.data.filter.DateScope
import app.tryst.data.filter.EncounterFilter
import app.tryst.data.repository.ActRepository
import app.tryst.data.repository.EncounterRepository
import app.tryst.data.repository.KinkRepository
import app.tryst.data.repository.OccasionRepository
import app.tryst.data.repository.PartnerRepository
import app.tryst.data.repository.PositionRepository
import app.tryst.data.repository.RecentSearchRepository
import app.tryst.data.repository.ToyRepository
import app.tryst.data.search.CatalogLabels
import app.tryst.data.search.EncounterSearch
import app.tryst.data.search.SearchHit
import app.tryst.data.search.SearchableEncounter
import app.tryst.ui.common.MediaImages
import dagger.hilt.android.lifecycle.HiltViewModel
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The rating shortcuts. [ANY] means no rating constraint. */
enum class RatingFilter(val label: String, val range: IntRange?) {
    ANY("Any rating", null),
    THREE_PLUS("★ 3+", 3..5),
    FOUR_PLUS("★ 4+", 4..5),
    FIVE("★ 5", 5..5),
}

/**
 * How results are ordered. Only the date orders group into day sections in the UI — ordering by rating
 * or duration would scatter those headers, so those render as one flat run.
 */
enum class SortOrder(val label: String) {
    NEWEST("Newest first"),
    OLDEST("Oldest first"),
    RATING("Highest rated"),
    DURATION("Longest"),
    ;

    val isChronological: Boolean get() = this == NEWEST || this == OLDEST

    fun sort(hits: List<SearchHit>): List<SearchHit> {
        // Unrated / untimed entries sink to the bottom rather than being treated as zero-and-tied.
        fun rating(h: SearchHit) = h.encounter.encounter.satisfactionRating ?: -1
        fun duration(h: SearchHit) = h.encounter.encounter.durationMin ?: -1
        fun startAt(h: SearchHit) = h.encounter.encounter.startAt
        return when (this) {
            NEWEST -> hits.sortedByDescending(::startAt)
            OLDEST -> hits.sortedBy(::startAt)
            RATING -> hits.sortedWith(compareByDescending(::rating).thenByDescending(::startAt))
            DURATION -> hits.sortedWith(compareByDescending(::duration).thenByDescending(::startAt))
        }
    }
}

/**
 * One self-consistent snapshot of the results section: the [hits], the [tokens] they were matched
 * against (so highlighting can never belong to a different query), and whether anything narrows the log.
 */
data class SearchUiState(
    val tokens: List<String> = emptyList(),
    val hits: List<SearchHit> = emptyList(),
    val criteriaActive: Boolean = false,
)

/**
 * Search (SRCH-1) — the first consumer of the FILT-1 layer.
 *
 * Structured chips drive an [EncounterFilter] (dates, partners, rating, photos); the typed query drives
 * [EncounterSearch] over the entry's visible text. The searchable text is **indexed once per (log,
 * catalog) change**, not per keystroke, so typing only re-tokenizes and scans.
 *
 * Recent queries are recorded on submit into the **encrypted DB** (D-42) — never prefs, never backups.
 */
@HiltViewModel
@Suppress("LongParameterList") // Hilt-injected repositories; each is a distinct catalog/data source.
class SearchViewModel @Inject constructor(
    private val encounterRepository: EncounterRepository,
    private val recentSearchRepository: RecentSearchRepository,
    actRepository: ActRepository,
    positionRepository: PositionRepository,
    kinkRepository: KinkRepository,
    toyRepository: ToyRepository,
    occasionRepository: OccasionRepository,
    partnerRepository: PartnerRepository,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    /** The date window, using the same year → quarter → custom vocabulary as the Insights scope. */
    private val _dateScope = MutableStateFlow<DateScope>(DateScope.AllTime)
    val dateScope: StateFlow<DateScope> = _dateScope.asStateFlow()

    private val _rating = MutableStateFlow(RatingFilter.ANY)
    val rating: StateFlow<RatingFilter> = _rating.asStateFlow()

    private val _partnerIds = MutableStateFlow<Set<String>>(emptySet())
    val partnerIds: StateFlow<Set<String>> = _partnerIds.asStateFlow()

    private val _photosOnly = MutableStateFlow(false)
    val photosOnly: StateFlow<Boolean> = _photosOnly.asStateFlow()

    private val _sortOrder = MutableStateFlow(SortOrder.NEWEST)
    val sortOrder: StateFlow<SortOrder> = _sortOrder.asStateFlow()

    /** Named partners for the partner chip's menu. */
    val partners: StateFlow<List<PartnerEntity>> = partnerRepository.observeActive()
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Years the log covers, newest first — the year chip offers exactly these. */
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

    /** Previously submitted queries, newest first. */
    val recentSearches: StateFlow<List<String>> = recentSearchRepository.observeRecent()
        .map { rows -> rows.map { it.query } }
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The chips, assembled into the shared filter model. */
    private val filter: Flow<EncounterFilter> =
        combine(_dateScope, _rating, _partnerIds, _photosOnly) { scope, rating, partnerIds, photosOnly ->
            EncounterFilter(
                dateRanges = listOfNotNull(scope.range()),
                partnerIds = partnerIds,
                ratingRange = rating.range,
                hasPhoto = true.takeIf { photosOnly },
            )
        }

    private val labels: Flow<CatalogLabels> = combine(
        actRepository.observeCustom().map { rows -> rows.associate { it.id to it.label } },
        positionRepository.observeCustom().map { rows -> rows.associate { it.id to it.label } },
        kinkRepository.observeCustom().map { rows -> rows.associate { it.id to it.label } },
        toyRepository.observeCustom().map { rows -> rows.associate { it.id to it.label } },
        occasionRepository.observeCustom().map { rows -> rows.associate { it.id to it.label } },
    ) { acts, positions, kinks, toys, occasions ->
        CatalogLabels(acts = acts, positions = positions, kinks = kinks, toys = toys, occasions = occasions)
    }

    /**
     * The searchable text of the whole log. Rebuilt only when the log or a catalog label changes — the
     * expensive part of search, deliberately kept off the per-keystroke path.
     */
    private val index: Flow<List<SearchableEncounter>> =
        combine(encounterRepository.observeAll(), labels) { encounters, catalogLabels ->
            withContext(Dispatchers.Default) { EncounterSearch.index(encounters, catalogLabels) }
        }

    /**
     * Everything the results section renders, emitted as **one value**.
     *
     * Matching runs off the main thread, so it necessarily lags the text field by a frame or two. If the
     * screen read `hits`, `tokens`, and `criteriaActive` from three separate flows it would briefly draw
     * the *previous* query's rows annotated with the *current* query's highlights and "matched in" line.
     * Computing them together means a stale frame is at least a self-consistent one: old rows, old
     * highlights, then all three flip over at once. (The text field still binds to [query] directly, so
     * typing stays instant.)
     */
    val uiState: StateFlow<SearchUiState> =
        combine(index, _query, filter, _sortOrder) { searchable, query, filter, sortOrder ->
            withContext(Dispatchers.Default) {
                val tokens = EncounterSearch.tokenize(query)
                val filtered = searchable.filter { filter.matches(it.encounter) }
                SearchUiState(
                    tokens = tokens,
                    hits = sortOrder.sort(EncounterSearch.search(filtered, tokens)),
                    criteriaActive = query.isNotBlank() || filter.isActive,
                )
            }
        }
            // The DB closes on lock; swallow the resulting error so we don't crash mid-teardown.
            .catch { emit(SearchUiState()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState())

    fun setQuery(value: String) = _query.update { value }

    /** Records the current query as a recent search. Called when the user submits, not on every keystroke. */
    fun submitQuery() {
        val current = _query.value
        viewModelScope.launch { runCatching { recentSearchRepository.record(current) } }
    }

    fun applyRecent(query: String) {
        _query.value = query
        submitQuery() // bump its timestamp so reuse floats it back to the top
    }

    fun deleteRecent(query: String) {
        viewModelScope.launch { runCatching { recentSearchRepository.delete(query) } }
    }

    fun clearRecents() {
        viewModelScope.launch { runCatching { recentSearchRepository.clear() } }
    }

    fun setDateScope(value: DateScope) = _dateScope.update { value }

    fun setCustomRange(start: LocalDate, end: LocalDate) {
        val range = if (start <= end) DateRange(start, end) else DateRange(end, start)
        _dateScope.value = DateScope.Custom(range)
    }

    fun setRating(value: RatingFilter) = _rating.update { value }

    fun togglePartner(id: String) = _partnerIds.update { if (id in it) it - id else it + id }

    fun setPhotosOnly(value: Boolean) = _photosOnly.update { value }

    fun setSortOrder(value: SortOrder) = _sortOrder.update { value }

    fun clearAll() {
        _query.value = ""
        _dateScope.value = DateScope.AllTime
        _rating.value = RatingFilter.ANY
        _partnerIds.value = emptySet()
        _photosOnly.value = false
    }

    suspend fun decode(media: MediaEntity, reqPx: Int): ImageBitmap? = MediaImages.decodeSampled(reqPx) { runCatching { encounterRepository.openMedia(media) }.getOrNull() }
}
