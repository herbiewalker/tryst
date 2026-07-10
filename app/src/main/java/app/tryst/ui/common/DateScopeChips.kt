package app.tryst.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import app.tryst.R
import app.tryst.data.filter.DateScope
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * The shared date-window selector, narrowest-last: **year → quarter → custom range**. Used by the
 * Insights time scope and by Search's date filter, so both narrow time the same way.
 *
 * The three chips are one selection, not three. Picking a year keeps the quarter you were on (so you can
 * step 2025 Q2 → 2024 Q2 to compare the same season across years); the quarter chip is disabled until a
 * year anchors it, because "Q2 of all time" means nothing. The custom chip opens the picker directly,
 * and choosing a year or *All time* is how you leave it.
 *
 * Emits three chips and no container — the caller supplies the `FlowRow` (Search sits them beside its
 * other filter chips).
 *
 * @param availableYears years the log actually covers, newest first, so the menu never offers an empty one.
 */
@Composable
fun DateScopeChips(
    scope: DateScope,
    availableYears: List<Int>,
    onSelect: (DateScope) -> Unit,
    onCustomRange: () -> Unit,
) {
    val thisYear = remember { LocalDate.now().year }
    val anchorYear = scope.anchorYear
    val quarter = (scope as? DateScope.Quarter)?.quarter

    // --- year ---
    MenuChip(
        label = when {
            anchorYear == null -> stringResource(R.string.date_scope_all_time)
            anchorYear == thisYear -> stringResource(R.string.date_scope_this_year)
            else -> anchorYear.toString()
        },
        selected = anchorYear != null,
        contentDescription = stringResource(R.string.cd_date_scope_year),
    ) { dismiss ->
        CheckableItem(stringResource(R.string.date_scope_all_time), checked = scope == DateScope.AllTime) {
            onSelect(DateScope.AllTime)
            dismiss()
        }
        // Switching year preserves the quarter, so you can compare the same quarter across years.
        val pickYear = { year: Int ->
            onSelect(if (quarter != null) DateScope.Quarter(year, quarter) else DateScope.Year(year))
        }
        CheckableItem(stringResource(R.string.date_scope_this_year), checked = anchorYear == thisYear) {
            pickYear(thisYear)
            dismiss()
        }
        availableYears.filter { it != thisYear }.forEach { year ->
            CheckableItem(year.toString(), checked = anchorYear == year) {
                pickYear(year)
                dismiss()
            }
        }
    }

    // --- quarter (of the chosen year) ---
    MenuChip(
        label = quarter?.let { stringResource(R.string.date_scope_quarter, it) }
            ?: stringResource(R.string.date_scope_all_year),
        selected = quarter != null,
        enabled = anchorYear != null,
        contentDescription = stringResource(R.string.cd_date_scope_quarter),
    ) { dismiss ->
        val year = anchorYear ?: thisYear
        CheckableItem(stringResource(R.string.date_scope_all_year), checked = quarter == null) {
            onSelect(DateScope.Year(year))
            dismiss()
        }
        for (q in 1..DateScope.QUARTERS) {
            CheckableItem(stringResource(R.string.date_scope_quarter, q), checked = quarter == q) {
                onSelect(DateScope.Quarter(year, q))
                dismiss()
            }
        }
    }

    // --- custom range ---
    FilterChip(
        selected = scope is DateScope.Custom,
        onClick = onCustomRange,
        label = {
            Text(
                (scope as? DateScope.Custom)?.let {
                    stringResource(R.string.date_scope_range, it.range.start.format(chipDate), it.range.end.format(chipDate))
                } ?: stringResource(R.string.date_scope_custom),
            )
        },
        leadingIcon = { Icon(Icons.Filled.DateRange, contentDescription = stringResource(R.string.cd_date_scope_custom)) },
    )
}

/** How a window is named inside a sentence ("No trysts in **Q2 2025**"). */
@Composable
fun dateScopeDescription(scope: DateScope): String = when (scope) {
    DateScope.AllTime -> stringResource(R.string.date_scope_all_time)
    is DateScope.Year -> scope.year.toString()
    is DateScope.Quarter -> stringResource(R.string.date_scope_quarter_year, scope.quarter, scope.year)
    is DateScope.Custom ->
        stringResource(R.string.date_scope_range, scope.range.start.format(messageDate), scope.range.end.format(messageDate))
}

/** Short enough to sit in a chip; the sentence form has room for [messageDate]. */
private val chipDate: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)
private val messageDate: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
