package app.tryst.ui.gallery

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tryst.R
import app.tryst.data.db.entity.MediaEntity
import app.tryst.data.db.entity.PartnerEntity
import app.tryst.data.filter.DateScope
import app.tryst.data.gallery.GalleryGroup
import app.tryst.data.gallery.GalleryLayout
import app.tryst.data.gallery.GalleryPhoto
import app.tryst.data.gallery.GallerySection
import app.tryst.ui.common.CheckableItem
import app.tryst.ui.common.DateRangePickerDialog
import app.tryst.ui.common.DateScopeChips
import app.tryst.ui.common.DecodedImage
import app.tryst.ui.common.Format
import app.tryst.ui.common.MenuChip
import app.tryst.ui.search.MoreFiltersActions
import app.tryst.ui.search.MoreFiltersSheet
import app.tryst.ui.search.RatingFilter
import java.time.YearMonth
import java.time.format.DateTimeFormatter

private const val THUMB_PX = 420
private const val FEED_PX = 900

/**
 * The Photos gallery (GAL-1): every encounter photo in one browsable surface. The user's chosen
 * [GalleryLayout] (date grid / flat grid / by partner / feed), column density, and sort come from
 * [app.tryst.core.prefs.GalleryPreferences]; a search field plus the same date/rating/partner chips and
 * "More filters" sheet as Search narrow which photos show. Tapping a photo opens the full-screen [PhotoViewer].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    onOpenEncounter: (String) -> Unit,
    viewModel: GalleryViewModel = hiltViewModel(),
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val dateScope by viewModel.dateScope.collectAsStateWithLifecycle()
    val rating by viewModel.rating.collectAsStateWithLifecycle()
    val partnerIds by viewModel.partnerIds.collectAsStateWithLifecycle()
    val partners by viewModel.partners.collectAsStateWithLifecycle()
    val availableYears by viewModel.availableYears.collectAsStateWithLifecycle()
    val advanced by viewModel.advanced.collectAsStateWithLifecycle()
    val catalogLabels by viewModel.catalogLabels.collectAsStateWithLifecycle()
    val advancedCount by viewModel.activeAdvancedCount.collectAsStateWithLifecycle()
    val sort by viewModel.sort.collectAsStateWithLifecycle()

    var searching by remember { mutableStateOf(false) }
    var showFilters by remember { mutableStateOf(false) }
    var showRangePicker by remember { mutableStateOf(false) }
    var showOptions by remember { mutableStateOf(false) }
    var viewerIndex by remember { mutableIntStateOf(-1) }

    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(searching) { if (searching) focusRequester.requestFocus() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (searching) {
                        TextField(
                            value = query,
                            onValueChange = viewModel::setQuery,
                            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                            placeholder = { Text(stringResource(R.string.gallery_search_hint)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                            ),
                        )
                    } else {
                        Text(stringResource(R.string.nav_photos))
                    }
                },
                actions = {
                    if (searching) {
                        IconButton(onClick = {
                            viewModel.setQuery("")
                            searching = false
                        }) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.search_clear_query))
                        }
                    } else {
                        IconButton(onClick = { searching = true }) {
                            Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.gallery_search_hint))
                        }
                        IconButton(onClick = { showOptions = true }) {
                            Icon(Icons.Filled.Tune, contentDescription = stringResource(R.string.gallery_options))
                        }
                    }
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
                advancedCount = advancedCount,
                criteriaActive = ui.criteriaActive,
                onDateScope = viewModel::setDateScope,
                onCustomRange = { showRangePicker = true },
                onRating = viewModel::setRating,
                onTogglePartner = viewModel::togglePartner,
                onMoreFilters = { showFilters = true },
                onClearAll = viewModel::clearAll,
            )

            when {
                ui.photos.isEmpty() -> EmptyState(criteriaActive = ui.criteriaActive)
                ui.layout == GalleryLayout.FEED -> GalleryFeed(
                    photos = ui.photos,
                    onOpen = { id -> viewerIndex = ui.photos.indexOfFirst { it.id == id } },
                    onLoad = viewModel::decode,
                )
                else -> GalleryGrid(
                    sections = ui.sections,
                    columns = ui.columns,
                    onOpen = { id -> viewerIndex = ui.photos.indexOfFirst { it.id == id } },
                    onLoad = viewModel::decode,
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
            resultCount = ui.totalCount,
            onDismiss = { showFilters = false },
        )
    }

    if (showOptions) {
        GalleryOptionsSheet(
            layout = ui.layout,
            columns = ui.columns,
            sort = sort,
            onLayout = viewModel::setLayout,
            onColumns = viewModel::setColumns,
            onSort = viewModel::setSort,
            onDismiss = { showOptions = false },
        )
    }

    if (viewerIndex in ui.photos.indices) {
        PhotoViewer(
            photos = ui.photos,
            initialIndex = viewerIndex,
            onClose = { viewerIndex = -1 },
            onOpenEncounter = { id ->
                viewerIndex = -1
                onOpenEncounter(id)
            },
            onLoad = viewModel::decode,
        )
    }
}

// --- grid + feed ---------------------------------------------------------------------------------

@Composable
private fun GalleryGrid(
    sections: List<GallerySection>,
    columns: Int,
    onOpen: (String) -> Unit,
    onLoad: suspend (media: MediaEntity, reqPx: Int) -> ImageBitmap?,
) {
    // Resolve headers here — label lookup is @Composable and can't run inside LazyGridScope.
    val labeled = sections.map { it to sectionLabelOrNull(it.group) }
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(2.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        labeled.forEach { (section, label) ->
            val key = sectionKey(section.group)
            if (label != null) {
                item(key = "hdr:$key", span = { GridItemSpan(maxLineSpan) }) {
                    SectionHeader(label)
                }
            }
            items(section.photos, key = { "$key:${it.id}" }) { photo ->
                PhotoTile(photo = photo, onClick = { onOpen(photo.id) }, onLoad = onLoad)
            }
        }
    }
}

@Composable
private fun GalleryFeed(
    photos: List<GalleryPhoto>,
    onOpen: (String) -> Unit,
    onLoad: suspend (media: MediaEntity, reqPx: Int) -> ImageBitmap?,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(1),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(photos, key = { it.id }) { photo ->
            Column(Modifier.clickable { onOpen(photo.id) }) {
                DecodedImage(
                    model = photo.id,
                    contentDescription = stringResource(R.string.cd_photo),
                    modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop,
                    load = { onLoad(photo.media, FEED_PX) },
                )
                FeedCaption(photo)
            }
        }
    }
}

@Composable
private fun PhotoTile(
    photo: GalleryPhoto,
    onClick: () -> Unit,
    onLoad: suspend (media: MediaEntity, reqPx: Int) -> ImageBitmap?,
) {
    DecodedImage(
        model = photo.id,
        contentDescription = stringResource(R.string.cd_photo),
        modifier = Modifier.aspectRatio(1f).clip(RoundedCornerShape(4.dp)).clickable(onClick = onClick),
        contentScale = ContentScale.Crop,
        load = { onLoad(photo.media, THUMB_PX) },
    )
}

@Composable
private fun FeedCaption(photo: GalleryPhoto) {
    Row(
        Modifier.fillMaxWidth().padding(top = 6.dp, start = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = Format.date(photo.takenAt),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (photo.partnerNames.isNotEmpty()) {
            Text(
                text = photo.partnerNames.joinToString(", "),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        photo.rating?.let { r ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Star, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                Text(" $r", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SectionHeader(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth().padding(start = 6.dp, top = 14.dp, bottom = 4.dp),
    )
}

@Composable
private fun EmptyState(criteriaActive: Boolean) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(if (criteriaActive) R.string.gallery_empty_filtered else R.string.gallery_empty),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// --- filter chips --------------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterChipRow(
    dateScope: DateScope,
    availableYears: List<Int>,
    rating: RatingFilter,
    partners: List<PartnerEntity>,
    partnerIds: Set<String>,
    advancedCount: Int,
    criteriaActive: Boolean,
    onDateScope: (DateScope) -> Unit,
    onCustomRange: () -> Unit,
    onRating: (RatingFilter) -> Unit,
    onTogglePartner: (String) -> Unit,
    onMoreFilters: () -> Unit,
    onClearAll: () -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DateScopeChips(scope = dateScope, availableYears = availableYears, onSelect = onDateScope, onCustomRange = onCustomRange)

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
            partnerIds.size == 1 -> partners.firstOrNull { it.id in partnerIds }?.let { Format.partnerName(it) }
                ?: stringResource(R.string.search_chip_partners)
            else -> stringResource(R.string.search_chip_partners_count, partnerIds.size)
        }
        MenuChip(label = partnerLabel, selected = partnerIds.isNotEmpty()) {
            partners.forEach { partner ->
                CheckableItem(Format.partnerName(partner), checked = partner.id in partnerIds) {
                    onTogglePartner(partner.id)
                }
            }
        }

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

// --- helpers -------------------------------------------------------------------------------------

private fun sectionKey(group: GalleryGroup): String = when (group) {
    GalleryGroup.Ungrouped -> "all"
    GalleryGroup.Solo -> "solo"
    is GalleryGroup.Month -> "m${group.year}-${group.month}"
    is GalleryGroup.Partner -> "p${group.id}"
}

@Composable
private fun sectionLabelOrNull(group: GalleryGroup): String? = when (group) {
    GalleryGroup.Ungrouped -> null
    GalleryGroup.Solo -> stringResource(R.string.gallery_group_solo)
    is GalleryGroup.Partner -> group.name?.takeIf { it.isNotBlank() } ?: stringResource(R.string.gallery_group_anonymous)
    is GalleryGroup.Month -> {
        val locale = LocalConfiguration.current.locales[0]
        YearMonth.of(group.year, group.month).format(DateTimeFormatter.ofPattern("LLLL yyyy", locale))
    }
}
