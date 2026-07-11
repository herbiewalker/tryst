package app.tryst.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.tryst.R
import app.tryst.data.db.entity.DisplayLabel
import app.tryst.data.db.entity.Initiator
import app.tryst.data.db.entity.Mood
import app.tryst.data.db.entity.Place
import app.tryst.data.db.entity.Protection
import app.tryst.data.filter.EncounterFilter
import app.tryst.data.filter.TimeOfDay
import app.tryst.data.search.CatalogLabels
import java.time.DayOfWeek
import java.time.format.TextStyle
import kotlin.math.roundToInt

/** How stored catalog refs (`custom:<uuid>`) are formed from a catalog row's bare id. */
private const val CUSTOM_PREFIX = "custom:"

/** Longest duration the slider offers, in minutes; the top thumb reads "180+ min" there. */
private const val DURATION_MAX = 180

/**
 * The lambdas the "More filters" sheet drives. Bundled so [MoreFiltersSheet] keeps a small parameter
 * list; each maps one-to-one to a [SearchViewModel] mutator.
 */
@Suppress("LongParameterList") // A callback bundle, one lambda per FILT-1 dimension — the point is to shrink the sheet's own list.
class MoreFiltersActions(
    val toggleAct: (String) -> Unit,
    val togglePosition: (String) -> Unit,
    val toggleKink: (String) -> Unit,
    val toggleToy: (String) -> Unit,
    val toggleOccasion: (String) -> Unit,
    val togglePlace: (Place) -> Unit,
    val toggleProtection: (Protection) -> Unit,
    val toggleMood: (Mood) -> Unit,
    val toggleInitiator: (Initiator) -> Unit,
    val toggleWeekday: (DayOfWeek) -> Unit,
    val toggleTimeOfDay: (TimeOfDay) -> Unit,
    val setDuration: (IntRange?) -> Unit,
    val setHasNote: (Boolean?) -> Unit,
    val setIncludeSolo: (Boolean) -> Unit,
    val reset: () -> Unit,
)

/**
 * The full FILT-1 filter surface that the base Search chips don't cover, as a bottom sheet. Every control
 * is **live** — a tap updates results behind the scrim immediately (like the base chips), so the bottom
 * bar's "Show N results" button only needs to dismiss. Catalog sections hide themselves when the user has
 * no entries in that category, since an empty category can only ever match nothing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreFiltersSheet(
    advanced: EncounterFilter,
    catalogLabels: CatalogLabels,
    actions: MoreFiltersActions,
    resultCount: Int,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        // A bounded height lets the scrollable body take the slack while the action bar stays pinned.
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
                CatalogSection(stringResource(R.string.search_section_acts), catalogLabels.acts, advanced.actIds, actions.toggleAct)
                CatalogSection(stringResource(R.string.search_section_positions), catalogLabels.positions, advanced.positionIds, actions.togglePosition)
                CatalogSection(stringResource(R.string.search_section_kinks), catalogLabels.kinks, advanced.kinkIds, actions.toggleKink)
                CatalogSection(stringResource(R.string.search_section_toys), catalogLabels.toys, advanced.toyIds, actions.toggleToy)
                CatalogSection(stringResource(R.string.search_section_occasions), catalogLabels.occasions, advanced.occasionIds, actions.toggleOccasion)

                EnumSection(stringResource(R.string.search_section_places), Place.entries, advanced.places, actions.togglePlace)
                EnumSection(stringResource(R.string.search_section_protection), Protection.entries, advanced.protection, actions.toggleProtection)
                EnumSection(stringResource(R.string.search_section_mood), Mood.entries, advanced.moods, actions.toggleMood)
                EnumSection(stringResource(R.string.search_section_initiator), Initiator.entries, advanced.initiators, actions.toggleInitiator)

                WeekdaySection(advanced.weekdays, actions.toggleWeekday)
                EnumSection(stringResource(R.string.search_section_timeofday), TimeOfDay.entries, advanced.timesOfDay, actions.toggleTimeOfDay)

                DurationSection(advanced.durationRange, actions.setDuration)
                NoteSection(advanced.hasNote, actions.setHasNote)

                FilterSection(stringResource(R.string.search_section_solo)) {
                    SelectChip(
                        label = stringResource(R.string.search_include_solo),
                        selected = advanced.includeSolo,
                        onClick = { actions.setIncludeSolo(!advanced.includeSolo) },
                    )
                }
                Spacer(Modifier.padding(bottom = 8.dp))
            }

            HorizontalDivider()
            Row(
                Modifier.fillMaxWidth().padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (advanced.isActive) {
                    TextButton(onClick = actions.reset) { Text(stringResource(R.string.search_reset_filters)) }
                }
                Spacer(Modifier.weight(1f))
                Button(onClick = onDismiss) {
                    Text(pluralStringResource(R.plurals.search_show_results, resultCount, resultCount))
                }
            }
        }
    }
}

// --- sections ----------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterSection(title: String, content: @Composable FlowRowScope.() -> Unit) {
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

@Composable
private fun SelectChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}

/** A user-catalog section: chip value = `custom:<bareId>` to match stored refs. Hidden when empty. */
@Composable
private fun CatalogSection(
    title: String,
    options: Map<String, String>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
) {
    if (options.isEmpty()) return
    FilterSection(title) {
        options.entries.sortedBy { it.value.lowercase() }.forEach { (bareId, label) ->
            val value = CUSTOM_PREFIX + bareId
            SelectChip(label, value in selected, { onToggle(value) })
        }
    }
}

@Composable
private fun <T : DisplayLabel> EnumSection(
    title: String,
    entries: List<T>,
    selected: Set<T>,
    onToggle: (T) -> Unit,
) {
    FilterSection(title) {
        entries.forEach { entry -> SelectChip(entry.label, entry in selected, { onToggle(entry) }) }
    }
}

@Composable
private fun WeekdaySection(selected: Set<DayOfWeek>, onToggle: (DayOfWeek) -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    FilterSection(stringResource(R.string.search_section_weekday)) {
        DayOfWeek.entries.forEach { day ->
            SelectChip(day.getDisplayName(TextStyle.SHORT, locale), day in selected, { onToggle(day) })
        }
    }
}

@Composable
private fun NoteSection(hasNote: Boolean?, onSet: (Boolean?) -> Unit) {
    FilterSection(stringResource(R.string.search_section_note)) {
        SelectChip(stringResource(R.string.search_note_any), hasNote == null, { onSet(null) })
        SelectChip(stringResource(R.string.search_note_with), hasNote == true, { onSet(true) })
        SelectChip(stringResource(R.string.search_note_without), hasNote == false, { onSet(false) })
    }
}

/**
 * Duration band. The slider stays in local state while dragging and only commits on release, so a drag
 * doesn't re-query the whole log every frame. A full-width selection (0..max) means "any", stored as null.
 */
@Composable
private fun DurationSection(range: IntRange?, onSet: (IntRange?) -> Unit) {
    val full = 0f..DURATION_MAX.toFloat()
    var value by remember(range) {
        mutableStateOf(range?.let { it.first.toFloat()..it.last.toFloat() } ?: full)
    }
    val label = range?.let { r ->
        val hi = if (r.last >= DURATION_MAX) "$DURATION_MAX+" else r.last.toString()
        stringResource(R.string.search_duration_value, r.first, hi)
    } ?: stringResource(R.string.search_duration_any)

    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.search_section_duration),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.weight(1f))
            Text(text = label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        RangeSlider(
            value = value,
            onValueChange = { value = it },
            valueRange = full,
            onValueChangeFinished = {
                val lo = value.start.roundToInt()
                val hi = value.endInclusive.roundToInt()
                onSet(if (lo <= 0 && hi >= DURATION_MAX) null else lo..hi)
            },
        )
    }
}
