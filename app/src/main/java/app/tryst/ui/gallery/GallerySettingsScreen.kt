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
import app.tryst.ui.search.FilterSection
import app.tryst.ui.search.SelectChip

/** Settings → Gallery: the gallery's persistent look — layout, column density, and sort order (GAL-1). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GallerySettingsScreen(
    onBack: () -> Unit,
    viewModel: GallerySettingsViewModel = hiltViewModel(),
) {
    val layout by viewModel.layout.collectAsStateWithLifecycle()
    val columns by viewModel.columns.collectAsStateWithLifecycle()
    val sort by viewModel.sort.collectAsStateWithLifecycle()
    val blur by viewModel.blurUntilRevealed.collectAsStateWithLifecycle()

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

            Text(stringResource(R.string.gallery_blur_setting), style = MaterialTheme.typography.titleMedium)
            Row(
                Modifier
                    .fillMaxWidth()
                    .toggleable(value = blur, role = Role.Switch, onValueChange = viewModel::setBlurUntilRevealed)
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Switch(checked = blur, onCheckedChange = null)
                Text(stringResource(R.string.gallery_blur_setting), style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                text = stringResource(R.string.gallery_blur_setting_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
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
