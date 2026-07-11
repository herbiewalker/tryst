package app.tryst.ui.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tryst.R
import app.tryst.data.db.entity.MediaEntity
import app.tryst.data.db.entity.PartnerEntity
import app.tryst.data.filter.DateScope
import app.tryst.data.search.EncounterSearch
import app.tryst.data.search.SearchField
import app.tryst.data.search.SearchHit
import app.tryst.ui.common.CARD_THUMB_PX
import app.tryst.ui.common.CheckableItem
import app.tryst.ui.common.DateHeader
import app.tryst.ui.common.DateRangePickerDialog
import app.tryst.ui.common.DateScopeChips
import app.tryst.ui.common.DecodedImage
import app.tryst.ui.common.EncounterCard
import app.tryst.ui.common.Format
import app.tryst.ui.common.MenuChip

private const val DETAIL_THUMB_PX = 220

/**
 * Search (SRCH-1): a free-text query over each tryst's visible text, combined with structured filter
 * chips (date window, partners, rating, photos) and a sort order. Both halves run through the shared
 * FILT-1 layer in [SearchViewModel] — this screen is its controls and results.
 *
 * A result expands **in place** to show the whole encounter, so scanning matches never costs a round
 * trip through the editor. Because search matches fields the card doesn't show (kinks, positions, …),
 * each result also reports which field the query hit.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onOpenEncounter: (String) -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val partners by viewModel.partners.collectAsStateWithLifecycle()
    val availableYears by viewModel.availableYears.collectAsStateWithLifecycle()
    val dateScope by viewModel.dateScope.collectAsStateWithLifecycle()
    val rating by viewModel.rating.collectAsStateWithLifecycle()
    val partnerIds by viewModel.partnerIds.collectAsStateWithLifecycle()
    val photosOnly by viewModel.photosOnly.collectAsStateWithLifecycle()
    val sortOrder by viewModel.sortOrder.collectAsStateWithLifecycle()
    val recents by viewModel.recentSearches.collectAsStateWithLifecycle()
    val advanced by viewModel.advanced.collectAsStateWithLifecycle()
    val catalogLabels by viewModel.catalogLabels.collectAsStateWithLifecycle()
    val advancedCount by viewModel.activeAdvancedCount.collectAsStateWithLifecycle()

    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    // Land with the cursor in the field — the whole point of the screen is to type.
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    var showRangePicker by remember { mutableStateOf(false) }
    var showFilters by remember { mutableStateOf(false) }
    var expandedId by rememberSaveable { mutableStateOf<String?>(null) }
    // A row expanded in one search must not stay open in the next — the result set underneath it changed.
    LaunchedEffect(query, dateScope, rating, partnerIds, photosOnly, sortOrder, advanced) {
        expandedId = null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                title = {
                    TextField(
                        value = query,
                        onValueChange = viewModel::setQuery,
                        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                        placeholder = { Text(stringResource(R.string.search_hint)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = {
                                // Only an explicit submit becomes a recent search — never every keystroke.
                                viewModel.submitQuery()
                                keyboard?.hide()
                            },
                        ),
                        // Blend into the app bar: no container fill, no indicator line.
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent,
                        ),
                    )
                },
                actions = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setQuery("") }) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.search_clear_query))
                        }
                    }
                    SortMenu(sortOrder, viewModel::setSortOrder)
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            FilterChipRow(
                dateScope = dateScope,
                availableYears = availableYears,
                rating = rating,
                partners = partners,
                partnerIds = partnerIds,
                photosOnly = photosOnly,
                advancedCount = advancedCount,
                criteriaActive = ui.criteriaActive,
                onDateScope = viewModel::setDateScope,
                onCustomRange = { showRangePicker = true },
                onRating = viewModel::setRating,
                onTogglePartner = viewModel::togglePartner,
                onPhotosOnly = viewModel::setPhotosOnly,
                onMoreFilters = { showFilters = true },
                onClearAll = viewModel::clearAll,
            )

            when {
                // Nothing typed and no chips set: offer recents rather than dumping the whole log.
                !ui.criteriaActive -> IdleState(
                    recents = recents,
                    onApply = viewModel::applyRecent,
                    onDelete = viewModel::deleteRecent,
                    onClearRecents = viewModel::clearRecents,
                )

                ui.hits.isEmpty() -> NoResults(
                    dateScope = dateScope,
                    rating = rating,
                    partnerIds = partnerIds,
                    photosOnly = photosOnly,
                    advancedActive = advancedCount > 0,
                    onDateScope = viewModel::setDateScope,
                    onRating = viewModel::setRating,
                    onClearPartners = { partnerIds.forEach(viewModel::togglePartner) },
                    onPhotosOnly = viewModel::setPhotosOnly,
                    onClearAdvanced = viewModel::clearAdvanced,
                )

                else -> ResultList(
                    results = ui.hits,
                    tokens = ui.tokens,
                    grouped = sortOrder.isChronological,
                    expandedId = expandedId,
                    onToggleExpand = { id -> expandedId = if (expandedId == id) null else id },
                    onOpenEncounter = onOpenEncounter,
                    onLoadThumb = viewModel::decode,
                )
            }
        }
    }

    if (showRangePicker) {
        DateRangePickerDialog(
            initial = (dateScope as? DateScope.Custom)?.range,
            onDismiss = { showRangePicker = false },
            onConfirm = { start, end ->
                viewModel.setCustomRange(start, end)
                showRangePicker = false
            },
        )
    }

    if (showFilters) {
        MoreFiltersSheet(
            advanced = advanced,
            catalogLabels = catalogLabels,
            actions = remember(viewModel) {
                MoreFiltersActions(
                    toggleAct = viewModel::toggleAct,
                    togglePosition = viewModel::togglePosition,
                    toggleKink = viewModel::toggleKink,
                    toggleToy = viewModel::toggleToy,
                    toggleOccasion = viewModel::toggleOccasion,
                    togglePlace = viewModel::togglePlace,
                    toggleProtection = viewModel::toggleProtection,
                    toggleMood = viewModel::toggleMood,
                    toggleInitiator = viewModel::toggleInitiator,
                    toggleWeekday = viewModel::toggleWeekday,
                    toggleTimeOfDay = viewModel::toggleTimeOfDay,
                    setDuration = viewModel::setDurationRange,
                    setHasNote = viewModel::setHasNote,
                    setIncludeSolo = viewModel::setIncludeSolo,
                    reset = viewModel::clearAdvanced,
                )
            },
            resultCount = ui.hits.size,
            onDismiss = { showFilters = false },
        )
    }
}

// --- results -------------------------------------------------------------

@Composable
private fun ResultList(
    results: List<SearchHit>,
    tokens: List<String>,
    grouped: Boolean,
    expandedId: String?,
    onToggleExpand: (String) -> Unit,
    onOpenEncounter: (String) -> Unit,
    onLoadThumb: suspend (MediaEntity, Int) -> androidx.compose.ui.graphics.ImageBitmap?,
) {
    // Day headers only make sense when results are in date order; rating/duration sorts render flat.
    val sections = remember(results, grouped) {
        if (grouped) results.groupBy { Format.relativeDay(it.encounter.encounter.startAt) } else mapOf("" to results)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "count") {
            Text(
                text = pluralStringResource(R.plurals.search_result_count, results.size, results.size),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        sections.forEach { (label, hits) ->
            if (label.isNotEmpty()) item(key = label) { DateHeader(Modifier.animateItem(), label) }
            items(hits, key = { it.encounter.encounter.id }) { hit ->
                val id = hit.encounter.encounter.id
                ResultItem(
                    hit = hit,
                    tokens = tokens,
                    expanded = expandedId == id,
                    onToggle = { onToggleExpand(id) },
                    onOpen = { onOpenEncounter(id) },
                    onLoadThumb = onLoadThumb,
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

/** One result: the standard card (tap to expand), a "matched in" line, and an in-place detail panel. */
@Composable
private fun ResultItem(
    hit: SearchHit,
    tokens: List<String>,
    expanded: Boolean,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    onLoadThumb: suspend (MediaEntity, Int) -> androidx.compose.ui.graphics.ImageBitmap?,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        EncounterCard(
            item = hit.encounter,
            onLoadThumb = { onLoadThumb(it, CARD_THUMB_PX) },
            onClick = onToggle,
            // No shared-element transform here: tapping expands rather than navigating, and the Trysts
            // list already owns this encounter's shared key.
            sharedScope = null,
            animatedScope = null,
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp, start = 4.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (hit.matchedFields.isNotEmpty()) {
                Text(
                    text = stringResource(
                        R.string.search_matched_in,
                        hit.matchedFields.sortedBy { it.ordinal }.joinToString(" · ") { it.label },
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
            IconButton(onClick = onToggle, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = stringResource(if (expanded) R.string.cd_search_collapse else R.string.cd_search_expand),
                )
            }
        }

        AnimatedVisibility(visible = expanded) {
            DetailPanel(hit = hit, tokens = tokens, onOpen = onOpen, onLoadThumb = onLoadThumb)
        }
    }
}

/** Everything the encounter holds, so a match can be judged without opening the editor. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailPanel(
    hit: SearchHit,
    tokens: List<String>,
    onOpen: () -> Unit,
    onLoadThumb: suspend (MediaEntity, Int) -> androidx.compose.ui.graphics.ImageBitmap?,
) {
    val values = hit.searchable.values
    val media = hit.encounter.media
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            values[SearchField.NOTE]?.firstOrNull()?.let { note ->
                Text(
                    text = highlight(note, tokens, MaterialTheme.colorScheme.primary),
                    style = MaterialTheme.typography.bodyMedium,
                )
                HorizontalDivider()
            }

            // Partners and the note already headline the card; the rest is what search can match on.
            values.forEach { (field, list) ->
                if (field == SearchField.NOTE || field == SearchField.PARTNER) return@forEach
                Row(Modifier.fillMaxWidth()) {
                    Text(
                        text = field.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(0.32f),
                    )
                    Text(
                        text = highlight(list.joinToString(", "), tokens, MaterialTheme.colorScheme.primary),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(0.68f),
                    )
                }
            }

            if (media.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(media, key = { it.id }) { item ->
                        DecodedImage(
                            model = item.id,
                            contentDescription = stringResource(R.string.cd_photo),
                            modifier = Modifier.size(84.dp).clip(RoundedCornerShape(10.dp)),
                            contentScale = ContentScale.Crop,
                            load = { onLoadThumb(item, DETAIL_THUMB_PX) },
                        )
                    }
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onOpen) { Text(stringResource(R.string.search_open)) }
            }
        }
    }
}

/** Bolds every occurrence of [tokens] in [text]; offsets survive folding (see `EncounterSearch.fold`). */
@Composable
private fun highlight(text: String, tokens: List<String>, color: Color): AnnotatedString {
    val spans = remember(text, tokens) { EncounterSearch.highlightRanges(text, tokens) }
    return remember(text, spans, color) {
        buildAnnotatedString {
            append(text)
            spans.forEach { addStyle(SpanStyle(fontWeight = FontWeight.Bold, color = color), it.start, it.end) }
        }
    }
}

// --- empty states --------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IdleState(
    recents: List<String>,
    onApply: (String) -> Unit,
    onDelete: (String) -> Unit,
    onClearRecents: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        if (recents.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.search_recent_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                TextButton(onClick = onClearRecents) { Text(stringResource(R.string.action_clear)) }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                recents.forEach { recent ->
                    AssistChip(
                        onClick = { onApply(recent) },
                        label = { Text(recent) },
                        trailingIcon = {
                            IconButton(onClick = { onDelete(recent) }, modifier = Modifier.size(24.dp)) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = stringResource(R.string.cd_search_remove_recent),
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        },
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }
        Box(Modifier.fillMaxSize().padding(bottom = 48.dp), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.search_prompt),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** No matches: name the active constraints and offer to relax each, rather than a dead end. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NoResults(
    dateScope: DateScope,
    rating: RatingFilter,
    partnerIds: Set<String>,
    photosOnly: Boolean,
    advancedActive: Boolean,
    onDateScope: (DateScope) -> Unit,
    onRating: (RatingFilter) -> Unit,
    onClearPartners: () -> Unit,
    onPhotosOnly: (Boolean) -> Unit,
    onClearAdvanced: () -> Unit,
) {
    val allTime = stringResource(R.string.date_scope_all_time)
    val clearMore = stringResource(R.string.search_clear_more_filters)
    val relaxable = buildList {
        if (dateScope != DateScope.AllTime) add(allTime to { onDateScope(DateScope.AllTime) })
        if (rating != RatingFilter.ANY) add(RatingFilter.ANY.label to { onRating(RatingFilter.ANY) })
        if (partnerIds.isNotEmpty()) add("All partners" to onClearPartners)
        if (photosOnly) add("Any photo" to { onPhotosOnly(false) })
        if (advancedActive) add(clearMore to onClearAdvanced)
    }

    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = stringResource(R.string.search_no_results),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (relaxable.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.search_no_results_hint),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    relaxable.forEach { (label, relax) ->
                        AssistChip(onClick = relax, label = { Text(label) })
                    }
                }
            }
        }
    }
}

// --- controls ------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterChipRow(
    dateScope: DateScope,
    availableYears: List<Int>,
    rating: RatingFilter,
    partners: List<PartnerEntity>,
    partnerIds: Set<String>,
    photosOnly: Boolean,
    advancedCount: Int,
    criteriaActive: Boolean,
    onDateScope: (DateScope) -> Unit,
    onCustomRange: () -> Unit,
    onRating: (RatingFilter) -> Unit,
    onTogglePartner: (String) -> Unit,
    onPhotosOnly: (Boolean) -> Unit,
    onMoreFilters: () -> Unit,
    onClearAll: () -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Same year -> quarter -> custom narrowing as the Insights time scope.
        DateScopeChips(
            scope = dateScope,
            availableYears = availableYears,
            onSelect = onDateScope,
            onCustomRange = onCustomRange,
        )

        MenuChip(label = rating.label, selected = rating != RatingFilter.ANY) { dismiss ->
            RatingFilter.entries.forEach { option ->
                CheckableItem(option.label, checked = option == rating) {
                    onRating(option)
                    dismiss()
                }
            }
        }

        val partnerLabel = when {
            partnerIds.isEmpty() -> stringResource(R.string.search_chip_partners)
            partnerIds.size == 1 -> partners.firstOrNull { it.id in partnerIds }
                ?.let { Format.partnerName(it) }
                ?: stringResource(R.string.search_chip_partners)
            else -> stringResource(R.string.search_chip_partners_count, partnerIds.size)
        }
        MenuChip(label = partnerLabel, selected = partnerIds.isNotEmpty()) {
            // Multi-select: the menu stays open so several partners can be picked in one go.
            partners.forEach { partner ->
                CheckableItem(Format.partnerName(partner), checked = partner.id in partnerIds) {
                    onTogglePartner(partner.id)
                }
            }
        }

        FilterChip(
            selected = photosOnly,
            onClick = { onPhotosOnly(!photosOnly) },
            label = { Text(stringResource(R.string.search_chip_photos)) },
        )

        // Everything else in FILT-1 (acts, kinks, places, mood, duration, …) lives behind this sheet.
        val moreLabel = if (advancedCount > 0) {
            stringResource(R.string.search_more_filters_count, advancedCount)
        } else {
            stringResource(R.string.search_more_filters)
        }
        FilterChip(
            selected = advancedCount > 0,
            onClick = onMoreFilters,
            label = { Text(moreLabel) },
            leadingIcon = { Icon(Icons.Filled.Tune, contentDescription = null, modifier = Modifier.size(18.dp)) },
        )

        if (criteriaActive) {
            TextButton(onClick = onClearAll) { Text(stringResource(R.string.search_clear_all)) }
        }
    }
}

@Composable
private fun SortMenu(current: SortOrder, onSelect: (SortOrder) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = current.label)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SortOrder.entries.forEach { option ->
                CheckableItem(option.label, checked = option == current) {
                    onSelect(option)
                    expanded = false
                }
            }
        }
    }
}
