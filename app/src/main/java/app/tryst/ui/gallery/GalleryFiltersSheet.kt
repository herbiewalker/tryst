package app.tryst.ui.gallery

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.tryst.R
import app.tryst.data.db.entity.PartnerEntity
import app.tryst.data.filter.DateScope
import app.tryst.data.filter.EncounterFilter
import app.tryst.data.search.CatalogLabels
import app.tryst.ui.common.DateScopeChips
import app.tryst.ui.common.Format
import app.tryst.ui.search.FilterSection
import app.tryst.ui.search.MoreFiltersActions
import app.tryst.ui.search.MoreFiltersColumn
import app.tryst.ui.search.RatingFilter
import app.tryst.ui.search.SelectChip

/**
 * The gallery's one filter surface, reached from the app-bar Filters button. Holds the base narrowing
 * (date window, rating, partners) plus the full advanced [MoreFiltersColumn] shared with Search. Layout,
 * density, and sort are **not** here — those are persistent look preferences, set in Settings → Gallery.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryFiltersSheet(
    dateScope: DateScope,
    availableYears: List<Int>,
    rating: RatingFilter,
    partners: List<PartnerEntity>,
    partnerIds: Set<String>,
    filtersActive: Boolean,
    advanced: EncounterFilter,
    catalogLabels: CatalogLabels,
    actions: MoreFiltersActions,
    resultCount: Int,
    onDateScope: (DateScope) -> Unit,
    onCustomRange: () -> Unit,
    onRating: (RatingFilter) -> Unit,
    onTogglePartner: (String) -> Unit,
    onClearAll: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxHeight(0.9f)) {
            Text(
                text = stringResource(R.string.search_filters_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
            )

            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
            ) {
                FilterSection(stringResource(R.string.gallery_filter_date)) {
                    DateScopeChips(scope = dateScope, availableYears = availableYears, onSelect = onDateScope, onCustomRange = onCustomRange)
                }
                FilterSection(stringResource(R.string.gallery_filter_rating)) {
                    RatingFilter.entries.forEach { option ->
                        SelectChip(option.label, option == rating, { onRating(option) })
                    }
                }
                if (partners.isNotEmpty()) {
                    FilterSection(stringResource(R.string.search_chip_partners)) {
                        partners.forEach { partner ->
                            SelectChip(Format.partnerName(partner), partner.id in partnerIds, { onTogglePartner(partner.id) })
                        }
                    }
                }

                MoreFiltersColumn(advanced = advanced, catalogLabels = catalogLabels, actions = actions)
            }

            HorizontalDivider()
            Row(
                Modifier.fillMaxWidth().padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (filtersActive) {
                    TextButton(onClick = onClearAll) { Text(stringResource(R.string.search_clear_all)) }
                }
                Spacer(Modifier.weight(1f))
                Button(onClick = onDismiss) {
                    Text(pluralStringResource(R.plurals.search_show_results, resultCount, resultCount))
                }
            }
        }
    }
}
