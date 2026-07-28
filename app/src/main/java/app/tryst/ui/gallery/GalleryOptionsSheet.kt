package app.tryst.ui.gallery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.tryst.R
import app.tryst.core.prefs.GalleryPreferences
import app.tryst.data.gallery.GalleryLayout
import app.tryst.data.gallery.GallerySort

/** Live layout picker for the gallery: which [GalleryLayout], how many columns, and [GallerySort]. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GalleryOptionsSheet(
    layout: GalleryLayout,
    columns: Int,
    sort: GallerySort,
    onLayout: (GalleryLayout) -> Unit,
    onColumns: (Int) -> Unit,
    onSort: (GallerySort) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 28.dp)) {
            Text(
                text = stringResource(R.string.gallery_options),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 10.dp),
            )

            SheetSection(stringResource(R.string.gallery_layout)) {
                GalleryLayout.entries.forEach { option ->
                    FilterChip(
                        selected = option == layout,
                        onClick = { onLayout(option) },
                        label = { Text(stringResource(layoutLabel(option))) },
                    )
                }
            }

            if (layout.usesColumns) {
                SheetSection(stringResource(R.string.gallery_density)) {
                    for (n in GalleryPreferences.MIN_COLUMNS..GalleryPreferences.MAX_COLUMNS) {
                        FilterChip(
                            selected = n == columns,
                            onClick = { onColumns(n) },
                            label = { Text(stringResource(R.string.gallery_density_columns, n)) },
                        )
                    }
                }
            }

            SheetSection(stringResource(R.string.gallery_sort)) {
                GallerySort.entries.forEach { option ->
                    FilterChip(
                        selected = option == sort,
                        onClick = { onSort(option) },
                        label = { Text(stringResource(sortLabel(option))) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SheetSection(title: String, content: @Composable FlowRowScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

private fun layoutLabel(layout: GalleryLayout): Int = when (layout) {
    GalleryLayout.JUSTIFIED_DATE -> R.string.gallery_layout_date
    GalleryLayout.SQUARE_GRID -> R.string.gallery_layout_grid
    GalleryLayout.BY_PARTNER -> R.string.gallery_layout_partner
    GalleryLayout.FEED -> R.string.gallery_layout_feed
}

private fun sortLabel(sort: GallerySort): Int = when (sort) {
    GallerySort.NEWEST -> R.string.gallery_sort_newest
    GallerySort.OLDEST -> R.string.gallery_sort_oldest
}
