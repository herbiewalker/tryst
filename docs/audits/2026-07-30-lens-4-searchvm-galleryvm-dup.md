# Lens 4 — SearchVM / GalleryVM filter-plumbing duplication

Date: 2026-07-30. Base: `main` @ `6706500`. Scope: `ui/search/SearchViewModel.kt`,
`ui/gallery/GalleryViewModel.kt`, `data/filter/EncounterFilter.kt`.

**Status: REFAC-TICKET-ONLY.** No drift bugs found. Every shared field, default,
mutator, and helper is byte-for-byte identical between the two VMs.

## Field / method inventory (shared shape in both files)

| Symbol | Same shape? | Same default / semantics? | Notes |
| --- | --- | --- | --- |
| `_query` / `query` / `setQuery` | yes | `""` | identical |
| `_dateScope` / `dateScope` / `setDateScope` / `setCustomRange` | yes | `DateScope.AllTime`; swap-start/end guard identical | identical |
| `_rating` / `rating` / `setRating` | yes | `RatingFilter.ANY` | identical |
| `_partnerIds` / `partnerIds` / `togglePartner` | yes | `emptySet()`, add/remove toggle | identical |
| `_advanced` / `advanced` | yes | `EncounterFilter()` | identical |
| `activeAdvancedCount` | yes | `WhileSubscribed(5_000)`, initial `0` | identical |
| `toggleAct/Position/Kink/Toy/Occasion/Place/Protection/Mood/Initiator/Weekday/TimeOfDay` (11) | yes | all use the `Set<T>.toggled(v)` helper | identical |
| `setDurationRange` / `setHasNote` / `setIncludeSolo` | yes | pass-through into `_advanced.copy` | identical |
| `clearAdvanced` | yes | reassigns `_advanced.value = EncounterFilter()` | identical |
| `advancedCount()` private ext | yes | 14-item list; counts `.isNotEmpty()` / non-null / true | identical line-by-line |
| `Set<T>.toggled(v)` private ext | yes | `if (v in this) this - v else this + v` | identical |
| `catalogLabels` | yes | 5-arg combine, `WhileSubscribed(5_000)`, fallback `CatalogLabels.EMPTY` | identical |
| `availableYears` | yes | derive year, distinct, sortedDescending | identical |
| `partners` | yes | `observeActive()` with empty fallback | identical |
| `clearAll` | *different by design* | Search clears `_photosOnly`; Gallery clears `_onlyFavorites` **and** `_drilledPartnerId` | correct per VM (each covers its own extras); not drift |
| base-filter merge | *different by design* | Search: `combine(base, adv)`, base built from date+rating+partners+photosOnly; Gallery: `combine(..., _drilledPartnerId)` overrides partners+includeSolo when drilled | correct per VM |

Everything on rows 1–14 is a candidate for extraction; the last two rows are the
per-VM tail.

## Drift bugs

None. Cross-checked:

- Defaults (`""`, `AllTime`, `ANY`, `emptySet()`, `EncounterFilter()`) match.
- `setCustomRange` normalises `start > end` the same way in both VMs.
- `togglePartner` implementation identical (`- id` vs `+ id`); no set-vs-single-select divergence.
- `advancedCount()` counts exactly the same 14 dimensions in the same order —
  `includeSolo` counted in both, `noteContains` counted in neither (matches
  what's exposed by the sheet).
- `Set<T>.toggled` uses the same immutable minus/plus in both; no ordering trap
  because `EncounterFilter` uses `Set`, not `List`.
- Neither merger copies `noteContains` — but the sheet doesn't expose it either,
  so both are consistent with the sheet contract, not a divergence between VMs.
- `_advanced` seeds and clears identically. `clearAdvanced` is byte-identical.

## REFAC ticket spec

Extract the base-chip filter plumbing into a shared abstract superclass rather
than a holder — every field wants to be a first-class `StateFlow` on the VM
that Compose collects, and Hilt already constructs both VMs.

### Proposed shape

```kotlin
abstract class BaseFilterViewModel : ViewModel() {
    protected val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    protected val _dateScope = MutableStateFlow<DateScope>(DateScope.AllTime)
    val dateScope: StateFlow<DateScope> = _dateScope.asStateFlow()

    protected val _rating = MutableStateFlow(RatingFilter.ANY)
    val rating: StateFlow<RatingFilter> = _rating.asStateFlow()

    protected val _partnerIds = MutableStateFlow<Set<String>>(emptySet())
    val partnerIds: StateFlow<Set<String>> = _partnerIds.asStateFlow()

    protected val _advanced = MutableStateFlow(EncounterFilter())
    val advanced: StateFlow<EncounterFilter> = _advanced.asStateFlow()

    val activeAdvancedCount: StateFlow<Int> = _advanced
        .map { it.advancedCount() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun setQuery(v: String) = _query.update { v }
    fun setDateScope(v: DateScope) = _dateScope.update { v }
    fun setCustomRange(a: LocalDate, b: LocalDate) { … }
    fun setRating(v: RatingFilter) = _rating.update { v }
    fun togglePartner(id: String) = _partnerIds.update { if (id in it) it - id else it + id }

    // All 11 toggle*(id/value) + setDurationRange/setHasNote/setIncludeSolo + clearAdvanced.

    /** Base chips assembled — subclasses call and then merge with anything extra. */
    protected fun baseChipFilter(hasPhoto: Boolean? = null): Flow<EncounterFilter> =
        combine(_dateScope, _rating, _partnerIds) { s, r, p ->
            EncounterFilter(
                dateRanges = listOfNotNull(s.range()),
                partnerIds = p, ratingRange = r.range, hasPhoto = hasPhoto,
            )
        }

    protected open fun clearBase() {
        _query.value = ""; _dateScope.value = DateScope.AllTime
        _rating.value = RatingFilter.ANY; _partnerIds.value = emptySet()
        _advanced.value = EncounterFilter()
    }
}
```

Move `Set<T>.toggled` and `EncounterFilter.advancedCount()` to
`data/filter/EncounterFilter.kt` as top-level internal extensions so both VMs
consume the same source.

### Touch points

**SearchViewModel** — delete the 6 backing flows, all 14 mutators, both private
extensions, `activeAdvancedCount`, `advancedCount`. Rebuild its `baseFilter`
via `baseChipFilter(hasPhoto = true.takeIf { photosOnly.value })` fed by
`_photosOnly` (still combined). `clearAll` becomes `clearBase(); _photosOnly.value = false`.

**GalleryViewModel** — delete the same 6 flows + 14 mutators + both extensions +
`activeAdvancedCount`. Keep its own `filter` combine (drill logic + `_drilledPartnerId`
override); it can still read `_dateScope`/`_rating`/`_partnerIds`/`_advanced`
directly since they're `protected`. `clearAll` becomes
`clearBase(); _onlyFavorites.value = false; _drilledPartnerId.value = null`.

Net delete: ~55 lines duplicated across the two files. No behaviour change; all
drift risk is retired at the source.
