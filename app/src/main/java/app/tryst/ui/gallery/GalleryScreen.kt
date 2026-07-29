package app.tryst.ui.gallery

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items as lazyListItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
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
import app.tryst.ui.common.DateRangePickerDialog
import app.tryst.ui.common.DecodedImage
import app.tryst.ui.common.Format
import app.tryst.ui.search.MoreFiltersActions
import app.tryst.ui.search.RatingFilter
import java.time.YearMonth
import java.time.format.DateTimeFormatter

private const val THUMB_PX = 420
private const val FEED_PX = 900

// Pinch factors that step the grid density one column looser/tighter (GAL-5).
private const val PINCH_IN = 0.72f
private const val PINCH_OUT = 1.4f

/**
 * The Photos gallery (GAL-1): every encounter photo in one browsable surface, plus its edit surface —
 * favourites (GAL-3), multi-select with bulk delete / favourite / reassign (GAL-4), a People (avatars)
 * layout (GAL-1a), a justified mosaic (GAL-1b), and pinch-to-zoom density. The user's chosen layout,
 * density, and sort come from [app.tryst.core.prefs.GalleryPreferences]; a search field plus the same
 * date/rating/partner chips and "More filters" sheet as Search narrow which photos show. Tapping a photo
 * opens the full-screen [PhotoViewer].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("LongMethod", "CyclomaticComplexMethod") // The gallery's single orchestration surface.
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
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val availableYears by viewModel.availableYears.collectAsStateWithLifecycle()
    val advanced by viewModel.advanced.collectAsStateWithLifecycle()
    val catalogLabels by viewModel.catalogLabels.collectAsStateWithLifecycle()
    val advancedCount by viewModel.activeAdvancedCount.collectAsStateWithLifecycle()
    val onlyFavorites by viewModel.onlyFavorites.collectAsStateWithLifecycle()
    val blurUntilRevealed by viewModel.blurUntilRevealed.collectAsStateWithLifecycle()
    val selectedIds by viewModel.selectedIds.collectAsStateWithLifecycle()
    val drilledPartner by viewModel.drilledPartner.collectAsStateWithLifecycle()
    val gridSpacing by viewModel.gridSpacing.collectAsStateWithLifecycle()
    val showTileCaptions by viewModel.showTileCaptions.collectAsStateWithLifecycle()
    val slideshowInterval by viewModel.slideshowIntervalSeconds.collectAsStateWithLifecycle()
    val slideshowShuffle by viewModel.slideshowShuffle.collectAsStateWithLifecycle()

    val selectionActive = selectedIds.isNotEmpty()
    val drilled = drilledPartner != null
    val isPeople = ui.layout == GalleryLayout.PEOPLE

    var searching by remember { mutableStateOf(false) }
    var showFilters by remember { mutableStateOf(false) }
    var showRangePicker by remember { mutableStateOf(false) }
    var showReassign by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var viewerIndex by remember { mutableIntStateOf(-1) }
    var revealed by remember { mutableStateOf(viewModel.revealedRecently()) }
    DisposableEffect(Unit) {
        onDispose { if (revealed) viewModel.markRevealed() }
    }

    // Back exits selection mode first, then a People-drill, before leaving the tab.
    BackHandler(enabled = selectionActive) { viewModel.clearSelection() }
    BackHandler(enabled = drilled && !selectionActive) { viewModel.exitDrill() }

    val filtersActive = advancedCount > 0 ||
        rating != RatingFilter.ANY ||
        partnerIds.isNotEmpty() ||
        dateScope != DateScope.AllTime

    // Tap opens the viewer (or toggles selection when selecting); long-press selects.
    val interaction = remember(selectionActive, selectedIds) {
        TileInteraction(
            selectionActive = selectionActive,
            selectedIds = selectedIds,
            onClick = { id ->
                if (selectionActive) {
                    viewModel.toggleSelected(id)
                } else {
                    viewerIndex = viewModel.uiState.value.photos.indexOfFirst { it.id == id }
                }
            },
            onLongPress = { id -> viewModel.toggleSelected(id) },
        )
    }

    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(searching) { if (searching) focusRequester.requestFocus() }

    Scaffold(
        topBar = {
            when {
                selectionActive -> SelectionBar(
                    count = selectedIds.size,
                    onClose = viewModel::clearSelection,
                    onFavorite = { viewModel.favoriteSelected(true) },
                    onUnfavorite = { viewModel.favoriteSelected(false) },
                    onReassign = { showReassign = true },
                    onDelete = { showDeleteConfirm = true },
                )
                drilled -> DrillBar(
                    partnerName = drilledPartner?.let { Format.partnerName(it) }
                        ?: stringResource(R.string.gallery_group_anonymous),
                    onBack = viewModel::exitDrill,
                )
                else -> TopAppBar(
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
                        when {
                            searching -> IconButton(onClick = {
                                viewModel.setQuery("")
                                searching = false
                            }) {
                                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.search_clear_query))
                            }
                            // People (avatars) don't participate in the photo filters.
                            isPeople -> Unit
                            else -> {
                                IconButton(onClick = { viewModel.setOnlyFavorites(!onlyFavorites) }) {
                                    Icon(
                                        if (onlyFavorites) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                        contentDescription = stringResource(R.string.gallery_only_favorites),
                                        tint = if (onlyFavorites) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                                    )
                                }
                                IconButton(onClick = { searching = true }) {
                                    Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.gallery_search_hint))
                                }
                                IconButton(onClick = { showFilters = true }) {
                                    Icon(
                                        Icons.Filled.Tune,
                                        contentDescription = stringResource(R.string.search_more_filters),
                                        tint = if (filtersActive) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                                    )
                                }
                            }
                        }
                    },
                )
            }
        },
    ) { padding ->
        val gated = blurUntilRevealed && !revealed && ui.photos.isNotEmpty()
        Box(Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.fillMaxSize().then(if (gated) Modifier.blur(28.dp) else Modifier)) {
                when {
                    isPeople -> GalleryPeople(
                        profile = profile,
                        partners = partners,
                        columns = ui.columns,
                        onLoadAvatar = viewModel::decodePartnerPhoto,
                        onPersonClick = viewModel::drillIntoPerson,
                    )
                    ui.photos.isEmpty() -> EmptyState(criteriaActive = ui.criteriaActive)
                    ui.layout == GalleryLayout.FEED -> GalleryFeed(
                        photos = ui.photos,
                        interaction = interaction,
                        onLoad = viewModel::decode,
                    )
                    ui.layout == GalleryLayout.MOSAIC -> GalleryMosaic(
                        sections = ui.sections,
                        spacing = gridSpacing,
                        onLoad = viewModel::decode,
                        aspectOf = viewModel::aspectRatio,
                        interaction = interaction,
                    )
                    else -> GalleryGrid(
                        sections = ui.sections,
                        columns = ui.columns,
                        partners = partners,
                        spacing = gridSpacing,
                        showTileCaptions = showTileCaptions,
                        interaction = interaction,
                        onLoad = viewModel::decode,
                        onLoadPartnerPhoto = viewModel::decodePartnerPhoto,
                        onPinch = viewModel::changeColumns,
                    )
                }
            }
            if (gated) {
                BlurGate(onReveal = {
                    viewModel.markRevealed()
                    revealed = true
                })
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
        GalleryFiltersSheet(
            dateScope = dateScope,
            availableYears = availableYears,
            rating = rating,
            partners = partners,
            partnerIds = partnerIds,
            filtersActive = filtersActive,
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
            onDateScope = viewModel::setDateScope,
            onCustomRange = { showRangePicker = true },
            onRating = viewModel::setRating,
            onTogglePartner = viewModel::togglePartner,
            onClearAll = viewModel::clearAll,
            onDismiss = { showFilters = false },
        )
    }

    if (showReassign) {
        ReassignDialog(
            viewModel = viewModel,
            count = selectedIds.size,
            onDismiss = { showReassign = false },
            onPicked = { targetId ->
                viewModel.reassignSelected(targetId)
                showReassign = false
            },
        )
    }

    if (showDeleteConfirm) {
        val n = selectedIds.size
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(pluralStringResource(R.plurals.gallery_delete_title, n, n)) },
            text = { Text(stringResource(R.string.gallery_delete_body)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteSelected()
                    showDeleteConfirm = false
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text(stringResource(R.string.action_cancel)) }
            },
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
            onLoadMeta = viewModel::readMeta,
            onToggleFavorite = viewModel::toggleFavorite,
            onSetAvatar = viewModel::setAsPartnerAvatar,
            slideshowIntervalSeconds = slideshowInterval,
            slideshowShuffle = slideshowShuffle,
        )
    }
}

// --- drill bar (People → their photos) -----------------------------------------------------------

/** Header shown while drilled into a person's photos from the People layout — title + back. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DrillBar(partnerName: String, onBack: () -> Unit) {
    TopAppBar(
        title = { Text(stringResource(R.string.gallery_drill_title, partnerName)) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
            }
        },
    )
}

// --- selection bar + dialogs ---------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionBar(
    count: Int,
    onClose: () -> Unit,
    onFavorite: () -> Unit,
    onUnfavorite: () -> Unit,
    onReassign: () -> Unit,
    onDelete: () -> Unit,
) {
    TopAppBar(
        title = { Text(pluralStringResource(R.plurals.gallery_selected_count, count, count)) },
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_close))
            }
        },
        actions = {
            IconButton(onClick = onFavorite) {
                Icon(Icons.Filled.Favorite, contentDescription = stringResource(R.string.gallery_favorite_selected))
            }
            IconButton(onClick = onUnfavorite) {
                Icon(Icons.Filled.FavoriteBorder, contentDescription = stringResource(R.string.gallery_unfavorite_selected))
            }
            IconButton(onClick = onReassign) {
                Icon(Icons.Filled.SwapHoriz, contentDescription = stringResource(R.string.gallery_reassign))
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.gallery_delete_selected))
            }
        },
    )
}

/** Picks a tryst to move the selected photos into (GAL-4). */
@Composable
private fun ReassignDialog(
    viewModel: GalleryViewModel,
    count: Int,
    onDismiss: () -> Unit,
    onPicked: (String) -> Unit,
) {
    val targets by viewModel.reassignTargets.collectAsStateWithLifecycle()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(pluralStringResource(R.plurals.gallery_reassign_title, count, count)) },
        text = {
            androidx.compose.foundation.lazy.LazyColumn(Modifier.heightIn(max = 360.dp)) {
                lazyListItems(targets, key = { it.id }) { target ->
                    val partnerLabel = target.partnerNames.takeIf { it.isNotEmpty() }?.joinToString(", ")
                        ?: stringResource(R.string.gallery_group_solo)
                    Column(
                        Modifier.fillMaxWidth().clickable { onPicked(target.id) }.padding(vertical = 10.dp),
                    ) {
                        Text(Format.dateTime(target.date), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            partnerLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

// --- grid + feed ---------------------------------------------------------------------------------

@Composable
@Suppress("LongParameterList") // Distinct look/behaviour inputs threaded from the VM.
private fun GalleryGrid(
    sections: List<GallerySection>,
    columns: Int,
    partners: List<PartnerEntity>,
    spacing: app.tryst.data.gallery.GridSpacing,
    showTileCaptions: Boolean,
    interaction: TileInteraction,
    onLoad: suspend (media: MediaEntity, reqPx: Int) -> ImageBitmap?,
    onLoadPartnerPhoto: suspend (photoMediaId: String, reqPx: Int) -> ImageBitmap?,
    onPinch: (delta: Int) -> Unit,
) {
    val gap = spacingGapDp(spacing)
    val edge = spacingEdgeDp(spacing)
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = Modifier.fillMaxSize().pinchColumns(onPinch),
        contentPadding = PaddingValues(edge),
        verticalArrangement = Arrangement.spacedBy(gap),
        horizontalArrangement = Arrangement.spacedBy(gap),
    ) {
        sections.forEach { section ->
            val key = sectionKey(section.group)
            if (section.group != GalleryGroup.Ungrouped) {
                item(key = "hdr:$key", span = { GridItemSpan(maxLineSpan) }) {
                    SectionHeader(section.group, partners, onLoadPartnerPhoto)
                }
            }
            items(section.photos, key = { "$key:${it.id}" }) { photo ->
                Column {
                    SelectablePhotoTile(
                        photo = photo,
                        reqPx = THUMB_PX,
                        onLoad = onLoad,
                        interaction = interaction,
                        modifier = Modifier.aspectRatio(1f),
                    )
                    if (showTileCaptions) TileCaption(photo)
                }
            }
        }
    }
}

private fun spacingGapDp(spacing: app.tryst.data.gallery.GridSpacing): androidx.compose.ui.unit.Dp = when (spacing) {
    app.tryst.data.gallery.GridSpacing.COMPACT -> 3.dp
    app.tryst.data.gallery.GridSpacing.NORMAL -> 8.dp
}

private fun spacingEdgeDp(spacing: app.tryst.data.gallery.GridSpacing): androidx.compose.ui.unit.Dp = when (spacing) {
    app.tryst.data.gallery.GridSpacing.COMPACT -> 2.dp
    app.tryst.data.gallery.GridSpacing.NORMAL -> 8.dp
}

@Composable
private fun GalleryFeed(
    photos: List<GalleryPhoto>,
    interaction: TileInteraction,
    onLoad: suspend (media: MediaEntity, reqPx: Int) -> ImageBitmap?,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(1),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(photos, key = { it.id }) { photo ->
            Column {
                SelectablePhotoTile(
                    photo = photo,
                    reqPx = FEED_PX,
                    onLoad = onLoad,
                    interaction = interaction,
                    modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f),
                    shape = RoundedCornerShape(12.dp),
                )
                FeedCaption(photo)
            }
        }
    }
}

/** A pinch that steps the grid density one column at a time (GAL-5), without swallowing single-finger scroll. */
private fun Modifier.pinchColumns(onChange: (delta: Int) -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        var zoom = 1f
        do {
            val event = awaitPointerEvent()
            if (event.changes.size >= 2) {
                zoom *= event.calculateZoom()
                if (zoom < PINCH_IN) {
                    onChange(-1) // pinch in → bigger tiles → fewer columns
                    zoom = 1f
                    event.changes.forEach { it.consume() }
                } else if (zoom > PINCH_OUT) {
                    onChange(+1) // spread → smaller tiles → more columns
                    zoom = 1f
                    event.changes.forEach { it.consume() }
                }
            }
        } while (event.changes.any { it.pressed })
    }
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

/** A section header. Partner sections show the partner's avatar + name (from the Partners data). */
@Composable
private fun SectionHeader(
    group: GalleryGroup,
    partners: List<PartnerEntity>,
    onLoadPartnerPhoto: suspend (photoMediaId: String, reqPx: Int) -> ImageBitmap?,
) {
    when (group) {
        GalleryGroup.Ungrouped -> Unit
        GalleryGroup.Solo -> SectionHeaderText(stringResource(R.string.gallery_group_solo))
        is GalleryGroup.Month -> {
            val locale = LocalConfiguration.current.locales[0]
            SectionHeaderText(YearMonth.of(group.year, group.month).format(DateTimeFormatter.ofPattern("LLLL yyyy", locale)))
        }
        is GalleryGroup.Partner -> {
            val partner = partners.firstOrNull { it.id == group.id }
            val name = partner?.let { Format.partnerName(it) }
                ?: group.name?.takeIf { it.isNotBlank() }
                ?: stringResource(R.string.gallery_group_anonymous)
            Row(
                Modifier.fillMaxWidth().padding(start = 6.dp, top = 14.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PartnerAvatar(photoMediaId = partner?.photoMediaId, name = name, onLoadPartnerPhoto = onLoadPartnerPhoto)
                Text(text = name, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun SectionHeaderText(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth().padding(start = 6.dp, top = 14.dp, bottom = 4.dp),
    )
}

private const val AVATAR_PX = 96

/** The partner's photo as a small circle; falls back to their initial on a tinted circle when there's none. */
@Composable
private fun PartnerAvatar(
    photoMediaId: String?,
    name: String,
    onLoadPartnerPhoto: suspend (photoMediaId: String, reqPx: Int) -> ImageBitmap?,
) {
    val shape = CircleShape
    if (photoMediaId != null) {
        DecodedImage(
            model = photoMediaId,
            contentDescription = null,
            modifier = Modifier.size(28.dp).clip(shape),
            contentScale = ContentScale.Crop,
            load = { onLoadPartnerPhoto(photoMediaId, AVATAR_PX) },
        )
    } else {
        Box(
            Modifier.size(28.dp).clip(shape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = name.firstOrNull()?.uppercase() ?: "?",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

/** The tap-to-reveal cover shown over a blurred gallery when the blur setting is on (SEC-2). */
@Composable
private fun BlurGate(onReveal: () -> Unit) {
    Box(
        Modifier.fillMaxSize().clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = {}),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                Icons.Filled.VisibilityOff,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.gallery_blur_title),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onReveal) { Text(stringResource(R.string.gallery_blur_show)) }
        }
    }
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

// --- helpers -------------------------------------------------------------------------------------

private fun sectionKey(group: GalleryGroup): String = when (group) {
    GalleryGroup.Ungrouped -> "all"
    GalleryGroup.Solo -> "solo"
    is GalleryGroup.Month -> "m${group.year}-${group.month}"
    is GalleryGroup.Partner -> "p${group.id}"
}
