package app.tryst.ui.history

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.Crossfade
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tryst.R
import app.tryst.core.prefs.WeekStart
import app.tryst.data.db.entity.MediaEntity
import app.tryst.data.db.entity.Practice
import app.tryst.data.db.relation.EncounterWithDetails
import app.tryst.ui.common.DecodedImage
import app.tryst.ui.common.Format
import app.tryst.ui.common.PracticeVisuals
import app.tryst.ui.common.encounterSharedKey
import app.tryst.ui.common.rememberHaptics
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun HistoryScreen(
    onAddEncounter: () -> Unit,
    onOpenEncounter: (String) -> Unit,
    sharedScope: SharedTransitionScope,
    animatedScope: AnimatedVisibilityScope,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val encounters by viewModel.encounters.collectAsStateWithLifecycle()
    val weekStart by viewModel.weekStart.collectAsStateWithLifecycle()
    val haptics = rememberHaptics()
    var calendarMode by remember { mutableStateOf(false) }
    // Already sorted by startAt DESC; groupBy keeps that order, one section per day.
    val grouped = remember(encounters) {
        encounters.groupBy { Format.relativeDay(it.encounter.startAt) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.history_title)) },
                actions = {
                    IconButton(onClick = {
                        haptics.tick()
                        calendarMode = !calendarMode
                    }) {
                        // Cross-fade the icon so list/calendar toggle reads as one control changing state.
                        Crossfade(targetState = calendarMode, animationSpec = tween(180), label = "viewToggleIcon") { cal ->
                            if (cal) {
                                Icon(Icons.AutoMirrored.Filled.List, contentDescription = stringResource(R.string.cd_view_list))
                            } else {
                                Icon(Icons.Filled.DateRange, contentDescription = stringResource(R.string.cd_view_calendar))
                            }
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            with(sharedScope) {
                FloatingActionButton(
                    onClick = onAddEncounter,
                    // The "+" morphs into the new-encounter editor (and back) as a container transform.
                    modifier = Modifier.sharedBounds(
                        rememberSharedContentState(encounterSharedKey(null)),
                        animatedVisibilityScope = animatedScope,
                    ),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.cd_log_encounter))
                }
            }
        },
    ) { padding ->
        // Cross-fade between the empty / calendar / list states instead of snapping.
        val mode = when {
            encounters.isEmpty() -> "empty"
            calendarMode -> "calendar"
            else -> "list"
        }
        Crossfade(targetState = mode, animationSpec = tween(200), label = "historyMode") { state ->
            when (state) {
                "empty" -> EmptyState(Modifier.padding(padding))
                "calendar" -> CalendarView(
                    modifier = Modifier.padding(padding),
                    items = encounters,
                    weekStart = weekStart,
                    onLoadThumb = { viewModel.decode(it, CARD_THUMB_PX) },
                    onOpenEncounter = onOpenEncounter,
                    sharedScope = sharedScope,
                    animatedScope = animatedScope,
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    grouped.forEach { (label, items) ->
                        item(key = label) { DateHeader(Modifier.animateItem(), label) }
                        items(items, key = { it.encounter.id }) { item ->
                            EncounterCard(
                                item,
                                onLoadThumb = { viewModel.decode(it, CARD_THUMB_PX) },
                                onClick = { onOpenEncounter(item.encounter.id) },
                                sharedScope = sharedScope,
                                animatedScope = animatedScope,
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DateHeader(modifier: Modifier = Modifier, label: String) {
    Surface(color = MaterialTheme.colorScheme.surface, modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalSharedTransitionApi::class)
@Composable
private fun EncounterCard(
    item: EncounterWithDetails,
    onLoadThumb: suspend (MediaEntity) -> ImageBitmap?,
    onClick: () -> Unit,
    sharedScope: SharedTransitionScope,
    animatedScope: AnimatedVisibilityScope,
    modifier: Modifier = Modifier,
) {
    val e = item.encounter
    Card(
        onClick = onClick,
        modifier = with(sharedScope) {
            modifier
                .fillMaxWidth()
                // This card morphs into the editor when opened (container transform).
                .sharedBounds(
                    rememberSharedContentState(encounterSharedKey(e.id)),
                    animatedVisibilityScope = animatedScope,
                )
        },
    ) {
        // Merge the whole card into a single TalkBack stop so it reads as one item with one action.
        Row(modifier = Modifier.padding(12.dp).semantics(mergeDescendants = true) {}) {
            DateBadge(e.startAt)
            Column(
                modifier = Modifier.weight(1f).padding(start = 14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val primaryActId = PracticeVisuals.primaryPractice(e.practicesPerformed, e.practicesReceived)
                    val primaryActLabel = primaryActId
                        ?.let { id -> runCatching { Practice.valueOf(id) }.getOrNull() }
                        ?.let { Format.enumLabel(it) }
                    PracticeBadge(
                        icon = PracticeVisuals.icon(primaryActId),
                        contentDescription = primaryActLabel,
                    )
                    Text(
                        text = if (item.partners.isEmpty()) stringResource(R.string.history_solo) else item.partners.joinToString(", ") { Format.partnerName(it) },
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                Text(
                    text = Format.time(e.startAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                // Each pill carries an explicit spoken label so the ★ / ✨ glyphs read meaningfully.
                val pills = buildList {
                    e.satisfactionRating?.let { add("★ $it" to stringResource(R.string.history_rating_cd, it)) }
                    e.durationMin?.let { add(stringResource(R.string.history_pill_minutes, it) to null) }
                    e.mood?.let { add(it.label to null) }
                    val orgasms = (e.orgasmCountSelf ?: 0) +
                        (e.partnerOrgasms?.values?.sum() ?: 0) +
                        (e.orgasmCountPartner ?: 0)
                    if (orgasms > 0) {
                        add("✨ $orgasms" to pluralStringResource(R.plurals.cd_orgasm_count, orgasms, orgasms))
                    }
                }
                if (pills.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        pills.forEach { (text, cd) -> Pill(text, cd) }
                    }
                }

                e.note?.takeIf { it.isNotBlank() }?.let { note ->
                    Text(
                        text = note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            item.media.firstOrNull()?.let { media ->
                DecodedImage(
                    model = media.id,
                    contentDescription = stringResource(R.string.cd_photo),
                    modifier = Modifier
                        .padding(start = 10.dp)
                        .size(48.dp)
                        .clip(RoundedCornerShape(10.dp)),
                    contentScale = ContentScale.Crop,
                    load = { onLoadThumb(media) },
                )
            }
        }
    }
}

@Composable
private fun DateBadge(epochMillis: Long) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.width(56.dp),
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(Format.dayOfMonth(epochMillis), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(Format.monthShort(epochMillis), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun PracticeBadge(@DrawableRes icon: Int, contentDescription: String?) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = CircleShape,
        modifier = Modifier.size(34.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), contentDescription = contentDescription, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun Pill(text: String, contentDescription: String? = null) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = RoundedCornerShape(50),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier
                .padding(horizontal = 10.dp, vertical = 4.dp)
                .then(
                    if (contentDescription != null) {
                        Modifier.semantics { this.contentDescription = contentDescription }
                    } else {
                        Modifier
                    },
                ),
        )
    }
}

private const val CARD_THUMB_PX = 140
private val selectedDayFormatter = DateTimeFormatter.ofPattern("EEEE, MMM d")

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun CalendarView(
    modifier: Modifier = Modifier,
    items: List<EncounterWithDetails>,
    weekStart: WeekStart,
    onLoadThumb: suspend (MediaEntity) -> ImageBitmap?,
    onOpenEncounter: (String) -> Unit,
    sharedScope: SharedTransitionScope,
    animatedScope: AnimatedVisibilityScope,
) {
    val byDay = remember(items) { items.groupBy { Format.localDate(it.encounter.startAt) } }
    // Headline act icon per day (most intense across all of that day's encounters).
    val dayIcons = remember(byDay) {
        byDay.mapValues { (_, list) ->
            val gave = list.flatMapTo(mutableSetOf()) { it.encounter.practicesPerformed ?: emptySet() }
            val received = list.flatMapTo(mutableSetOf()) { it.encounter.practicesReceived ?: emptySet() }
            PracticeVisuals.icon(PracticeVisuals.primaryPractice(gave, received))
        }
    }
    var month by remember { mutableStateOf(YearMonth.now()) }
    var selectedDay by remember { mutableStateOf<LocalDate?>(null) }
    val dayItems = selectedDay?.let { byDay[it] }.orEmpty()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "month-header") {
            MonthHeader(
                month = month,
                onPrev = {
                    month = month.minusMonths(1)
                    selectedDay = null
                },
                onNext = {
                    month = month.plusMonths(1)
                    selectedDay = null
                },
            )
        }
        item(key = "weekday-labels") { WeekdayLabels(weekStart) }
        item(key = "month-grid") {
            MonthGrid(
                month = month,
                dayIcons = dayIcons,
                selected = selectedDay,
                weekStart = weekStart,
                onSelect = { selectedDay = if (it == selectedDay) null else it },
            )
        }
        selectedDay?.let { day ->
            item(key = "day-header") {
                DateHeader(Modifier.animateItem(), label = day.format(selectedDayFormatter))
            }
            if (dayItems.isEmpty()) {
                item(key = "day-empty") {
                    Text(
                        stringResource(R.string.history_no_trysts_day),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.animateItem(),
                    )
                }
            } else {
                items(dayItems, key = { it.encounter.id }) { item ->
                    EncounterCard(
                        item,
                        onLoadThumb = onLoadThumb,
                        onClick = { onOpenEncounter(item.encounter.id) },
                        sharedScope = sharedScope,
                        animatedScope = animatedScope,
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }
}

@Composable
private fun MonthHeader(month: YearMonth, onPrev: () -> Unit, onNext: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrev) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.cd_prev_month))
        }
        Text(
            text = "${month.month.getDisplayName(TextStyle.FULL, locale)} ${month.year}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        IconButton(onClick = onNext) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.cd_next_month))
        }
    }
}

@Composable
private fun WeekdayLabels(weekStart: WeekStart) {
    val locale = LocalConfiguration.current.locales[0]
    val days = weekdayOrder(weekStart)
    Row(Modifier.fillMaxWidth()) {
        days.forEach { dow ->
            Text(
                text = dow.getDisplayName(TextStyle.NARROW, locale),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** Column order for the calendar, Sunday- or Monday-first per the user's setting. */
private fun weekdayOrder(weekStart: WeekStart): List<DayOfWeek> = when (weekStart) {
    WeekStart.MONDAY -> listOf(
        DayOfWeek.MONDAY,
        DayOfWeek.TUESDAY,
        DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY,
        DayOfWeek.FRIDAY,
        DayOfWeek.SATURDAY,
        DayOfWeek.SUNDAY,
    )
    WeekStart.SUNDAY -> listOf(
        DayOfWeek.SUNDAY,
        DayOfWeek.MONDAY,
        DayOfWeek.TUESDAY,
        DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY,
        DayOfWeek.FRIDAY,
        DayOfWeek.SATURDAY,
    )
}

@Composable
private fun MonthGrid(
    month: YearMonth,
    dayIcons: Map<LocalDate, Int>,
    selected: LocalDate?,
    weekStart: WeekStart,
    onSelect: (LocalDate) -> Unit,
) {
    val daysInMonth = month.lengthOfMonth()
    // Offset of the 1st within its week. dayOfWeek.value is Mon=1..Sun=7.
    val firstDow = month.atDay(1).dayOfWeek.value
    val leadingBlanks = if (weekStart == WeekStart.MONDAY) (firstDow + 6) % 7 else firstDow % 7
    val cells: List<LocalDate?> = buildList {
        repeat(leadingBlanks) { add(null) }
        for (d in 1..daysInMonth) add(month.atDay(d))
    }
    val today = LocalDate.now()

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                week.forEach { date ->
                    Box(Modifier.weight(1f).aspectRatio(1f), contentAlignment = Alignment.Center) {
                        if (date != null) {
                            DayCell(
                                date = date,
                                icon = dayIcons[date],
                                selected = date == selected,
                                isToday = date == today,
                                onClick = { onSelect(date) },
                            )
                        }
                    }
                }
                repeat(7 - week.size) { Box(Modifier.weight(1f)) {} }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    @DrawableRes icon: Int?,
    selected: Boolean,
    isToday: Boolean,
    onClick: () -> Unit,
) {
    val targetContent = when {
        selected -> MaterialTheme.colorScheme.onPrimary
        isToday -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }
    // Fade the selection pill in/out rather than snapping between days.
    val container by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
        animationSpec = tween(200),
        label = "dayCellContainer",
    )
    val content by animateColorAsState(targetContent, animationSpec = tween(200), label = "dayCellContent")
    // The day number + entry icon are visual only; give TalkBack a full spoken label and state.
    val locale = LocalConfiguration.current.locales[0]
    val isSelectedDay = selected
    val todaySuffix = stringResource(R.string.cd_day_today)
    val hasTrystsSuffix = stringResource(R.string.cd_day_has_trysts)
    val cellDescription = buildString {
        append(date.month.getDisplayName(TextStyle.FULL, locale))
        append(' ')
        append(date.dayOfMonth)
        if (isToday) {
            append(", ")
            append(todaySuffix)
        }
        if (icon != null) {
            append(", ")
            append(hasTrystsSuffix)
        }
    }
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = container,
        contentColor = content,
        // Today gets an outline ring so it stands out from logged days (which also use primary);
        // when today is the selected day, the filled selection pill takes over instead.
        border = if (isToday && !selected) {
            BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
        } else {
            null
        },
        modifier = Modifier
            .fillMaxSize()
            .semantics(mergeDescendants = true) {
                contentDescription = cellDescription
                this.selected = isSelectedDay
            },
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                date.dayOfMonth.toString(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isToday || selected) FontWeight.Bold else FontWeight.Normal,
            )
            if (icon != null) {
                Icon(
                    painterResource(icon),
                    contentDescription = null,
                    tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            stringResource(R.string.history_empty),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
