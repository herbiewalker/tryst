package app.tryst.ui.gallery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tryst.R
import app.tryst.core.prefs.GalleryPreferences
import app.tryst.data.gallery.GalleryLayout
import app.tryst.data.gallery.GallerySort
import app.tryst.data.gallery.GridSpacing
import app.tryst.ui.search.FilterSection
import app.tryst.ui.search.SelectChip

/** Settings → Gallery: every persistent look + behaviour preference for the Photos tab (GAL-1). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("LongMethod") // Straight-line list of setting rows; splitting adds indirection without value.
fun GallerySettingsScreen(
    onBack: () -> Unit,
    viewModel: GallerySettingsViewModel = hiltViewModel(),
) {
    val layout by viewModel.layout.collectAsStateWithLifecycle()
    val columns by viewModel.columns.collectAsStateWithLifecycle()
    val sort by viewModel.sort.collectAsStateWithLifecycle()
    val spacing by viewModel.gridSpacing.collectAsStateWithLifecycle()
    val showTileCaptions by viewModel.showTileCaptions.collectAsStateWithLifecycle()
    val defaultFavOnly by viewModel.defaultToFavoritesOnly.collectAsStateWithLifecycle()
    val slideshowInterval by viewModel.slideshowIntervalSeconds.collectAsStateWithLifecycle()
    val slideshowShuffle by viewModel.slideshowShuffle.collectAsStateWithLifecycle()
    val cameraKeepCapturing by viewModel.cameraKeepCapturing.collectAsStateWithLifecycle()
    val blur by viewModel.blurUntilRevealed.collectAsStateWithLifecycle()
    val blurGrace by viewModel.blurGraceSeconds.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_gallery)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        ) {
            // --- Layout ------------------------------------------------------------------------
            FilterSection(stringResource(R.string.gallery_layout)) {
                GalleryLayout.entries.forEach { option ->
                    SelectChip(stringResource(layoutLabel(option)), option == layout, { viewModel.setLayout(option) })
                }
            }

            if (layout.usesColumns) {
                FilterSection(stringResource(R.string.gallery_density)) {
                    for (n in GalleryPreferences.MIN_COLUMNS..GalleryPreferences.MAX_COLUMNS) {
                        SelectChip(stringResource(R.string.gallery_density_columns, n), n == columns, { viewModel.setColumns(n) })
                    }
                }
            }

            FilterSection(stringResource(R.string.gallery_sort)) {
                GallerySort.entries.forEach { option ->
                    SelectChip(stringResource(sortLabel(option)), option == sort, { viewModel.setSort(option) })
                }
            }

            Text(
                text = stringResource(R.string.settings_gallery_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp),
            )

            HorizontalDivider(Modifier.padding(vertical = 16.dp))

            // --- Tile appearance ---------------------------------------------------------------
            Text(stringResource(R.string.gallery_tiles_title), style = MaterialTheme.typography.titleMedium)
            FilterSection(stringResource(R.string.gallery_spacing)) {
                GridSpacing.entries.forEach { option ->
                    SelectChip(stringResource(spacingLabel(option)), option == spacing, { viewModel.setGridSpacing(option) })
                }
            }
            SettingsSwitchRow(
                label = stringResource(R.string.gallery_tile_captions),
                checked = showTileCaptions,
                onCheckedChange = viewModel::setShowTileCaptions,
            )
            Text(
                text = stringResource(R.string.gallery_tile_captions_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            HorizontalDivider(Modifier.padding(vertical = 16.dp))

            // --- When Photos opens -------------------------------------------------------------
            // Groups the two "initial state" prefs users see the moment they land on the tab:
            // whether it opens filtered to favourites, and whether it opens blurred.
            Text(stringResource(R.string.gallery_on_open_title), style = MaterialTheme.typography.titleMedium)
            SettingsSwitchRow(
                label = stringResource(R.string.gallery_default_favorites),
                checked = defaultFavOnly,
                onCheckedChange = viewModel::setDefaultToFavoritesOnly,
            )
            Text(
                text = stringResource(R.string.gallery_default_favorites_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SettingsSwitchRow(
                label = stringResource(R.string.gallery_blur_setting),
                checked = blur,
                onCheckedChange = viewModel::setBlurUntilRevealed,
            )
            Text(
                text = stringResource(R.string.gallery_blur_setting_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Grace only makes sense while the blur is on — hide otherwise so it doesn't look configurable to no effect.
            if (blur) {
                FilterSection(stringResource(R.string.gallery_blur_grace_title)) {
                    for (opt in BLUR_GRACE_OPTIONS) {
                        SelectChip(stringResource(blurGraceLabel(opt)), opt == blurGrace, { viewModel.setBlurGraceSeconds(opt) })
                    }
                }
                Text(
                    text = stringResource(R.string.gallery_blur_grace_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            HorizontalDivider(Modifier.padding(vertical = 16.dp))

            // --- Slideshow ---------------------------------------------------------------------
            Text(stringResource(R.string.gallery_slideshow_title), style = MaterialTheme.typography.titleMedium)
            FilterSection(stringResource(R.string.gallery_slideshow_interval)) {
                SLIDESHOW_INTERVAL_OPTIONS.forEach { opt ->
                    SelectChip(stringResource(slideshowIntervalLabel(opt)), opt == slideshowInterval, { viewModel.setSlideshowIntervalSeconds(opt) })
                }
            }
            SettingsSwitchRow(
                label = stringResource(R.string.gallery_slideshow_shuffle),
                checked = slideshowShuffle,
                onCheckedChange = viewModel::setSlideshowShuffle,
            )
            Text(
                text = stringResource(R.string.gallery_slideshow_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            HorizontalDivider(Modifier.padding(vertical = 16.dp))

            // --- Camera capture ----------------------------------------------------------------
            Text(stringResource(R.string.gallery_camera_title), style = MaterialTheme.typography.titleMedium)
            SettingsSwitchRow(
                label = stringResource(R.string.gallery_camera_keep_capturing),
                checked = cameraKeepCapturing,
                onCheckedChange = viewModel::setCameraKeepCapturing,
            )
            Text(
                text = stringResource(R.string.gallery_camera_keep_capturing_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SettingsSwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Switch(checked = checked, onCheckedChange = null)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

// The set of grace choices for the blur gate. Immediate = re-blur every time you leave the tab.
private val BLUR_GRACE_OPTIONS = listOf(0, 15, 30, 60, 300)

// Slideshow speeds — capped to a small set of round numbers people actually pick.
private val SLIDESHOW_INTERVAL_OPTIONS = listOf(3, 5, 10)

@androidx.annotation.StringRes
private fun blurGraceLabel(seconds: Int): Int = when (seconds) {
    0 -> R.string.gallery_blur_grace_immediate
    15 -> R.string.gallery_blur_grace_15s
    30 -> R.string.gallery_blur_grace_30s
    60 -> R.string.gallery_blur_grace_1m
    300 -> R.string.gallery_blur_grace_5m
    else -> R.string.gallery_blur_grace_immediate
}

@androidx.annotation.StringRes
private fun slideshowIntervalLabel(seconds: Int): Int = when (seconds) {
    3 -> R.string.gallery_slideshow_3s
    5 -> R.string.gallery_slideshow_5s
    10 -> R.string.gallery_slideshow_10s
    else -> R.string.gallery_slideshow_3s
}

@androidx.annotation.StringRes
private fun spacingLabel(value: GridSpacing): Int = when (value) {
    GridSpacing.COMPACT -> R.string.gallery_spacing_compact
    GridSpacing.NORMAL -> R.string.gallery_spacing_normal
}

internal fun layoutLabel(layout: GalleryLayout): Int = when (layout) {
    GalleryLayout.JUSTIFIED_DATE -> R.string.gallery_layout_date
    GalleryLayout.SQUARE_GRID -> R.string.gallery_layout_grid
    GalleryLayout.BY_PARTNER -> R.string.gallery_layout_partner
    GalleryLayout.FEED -> R.string.gallery_layout_feed
    GalleryLayout.MOSAIC -> R.string.gallery_layout_mosaic
    GalleryLayout.PEOPLE -> R.string.gallery_layout_people
}

internal fun sortLabel(sort: GallerySort): Int = when (sort) {
    GallerySort.NEWEST -> R.string.gallery_sort_newest
    GallerySort.OLDEST -> R.string.gallery_sort_oldest
}
