package app.tryst.ui.history

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.Crossfade
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tryst.R
import app.tryst.core.prefs.WeekStart
import app.tryst.data.db.entity.MediaEntity
import app.tryst.data.db.relation.EncounterWithDetails
import app.tryst.ui.common.ActVisuals
import app.tryst.ui.common.CARD_THUMB_PX
import app.tryst.ui.common.DateHeader
import app.tryst.ui.common.EncounterCard
import app.tryst.ui.common.Format
import app.tryst.ui.common.encounterSharedKey
import app.tryst.ui.common.rememberHaptics
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun HistoryScreen(
    onAddEncounter: () -> Unit,
    onOpenEncounter: (String) -> Unit,
    onOpenSearch: () -> Unit,
    sharedScope: SharedTransitionScope,
    animatedScope: AnimatedVisibilityScope,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val encounters by viewModel.encounters.collectAsStateWithLifecycle()
    val weekStart by viewModel.weekStart.collectAsStateWithLifecycle()
    val defaultToCalendar by viewModel.defaultToCalendar.collectAsStateWithLifecycle()
    val haptics = rememberHaptics()
    // Initial view follows the user's "default to calendar" setting; keyed on it so changing the
    // setting (then returning here) re-applies the default. The in-session toggle still overrides it.
    var calendarMode by remember(defaultToCalendar) { mutableStateOf(defaultToCalendar) }
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
                        onOpenSearch()
                    }) {
                        Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.cd_search))
                    }
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
            ActVisuals.icon(ActVisuals.primaryAct(gave, received))
        }
    }
    // Encounter count per day — drives the heatmap fill intensity.
    val dayCounts = remember(byDay) { byDay.mapValues { it.value.size } }

    var view by remember { mutableStateOf(CalView.MONTH) }
    var month by remember { mutableStateOf(YearMonth.now()) }
    var weekAnchor by remember { mutableStateOf(LocalDate.now()) }
    var selectedDay by remember { mutableStateOf<LocalDate?>(null) }
    val dayItems = selectedDay?.let { byDay[it] }.orEmpty()
    val onSelect: (LocalDate) -> Unit = { selectedDay = if (it == selectedDay) null else it }
    val goPrev = {
        if (view == CalView.MONTH) month = month.minusMonths(1) else weekAnchor = weekAnchor.minusWeeks(1)
        selectedDay = null
    }
    val goNext = {
        if (view == CalView.MONTH) month = month.plusMonths(1) else weekAnchor = weekAnchor.plusWeeks(1)
        selectedDay = null
    }

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(8.dp))
        CalendarHeader(
            view = view,
            month = month,
            weekAnchor = weekAnchor,
            weekStart = weekStart,
            onPrev = goPrev,
            onNext = goNext,
            onSelectView = {
                view = it
                if (it == CalView.WEEK) weekAnchor = selectedDay ?: LocalDate.now()
            },
        )
        Spacer(Modifier.height(8.dp))
        WeekdayLabels(weekStart)
        Spacer(Modifier.height(6.dp))
        if (view == CalView.MONTH) {
            MonthGrid(
                modifier = Modifier.fillMaxWidth().horizontalSwipe(view, goPrev, goNext),
                month = month,
                dayIcons = dayIcons,
                dayCounts = dayCounts,
                selected = selectedDay,
                weekStart = weekStart,
                onSelect = onSelect,
            )
            SelectedDayEntries(
                modifier = Modifier.weight(1f),
                selectedDay = selectedDay,
                dayItems = dayItems,
                onLoadThumb = onLoadThumb,
                onOpenEncounter = onOpenEncounter,
                sharedScope = sharedScope,
                animatedScope = animatedScope,
            )
        } else {
            WeekRow(
                modifier = Modifier.fillMaxWidth().height(116.dp).horizontalSwipe(view, goPrev, goNext),
                anchor = weekAnchor,
                dayIcons = dayIcons,
                dayCounts = dayCounts,
                selected = selectedDay,
                weekStart = weekStart,
                onSelect = onSelect,
            )
            Spacer(Modifier.height(6.dp))
            SelectedDayEntries(
                modifier = Modifier.weight(1f),
                selectedDay = selectedDay,
                dayItems = dayItems,
                onLoadThumb = onLoadThumb,
                onOpenEncounter = onOpenEncounter,
                sharedScope = sharedScope,
                animatedScope = animatedScope,
            )
        }
    }
}

private enum class CalView { MONTH, WEEK }

/** First day of the week containing [date], honouring the user's week-start preference. */
private fun startOfWeek(date: LocalDate, weekStart: WeekStart): LocalDate {
    val firstDow = if (weekStart == WeekStart.MONDAY) DayOfWeek.MONDAY else DayOfWeek.SUNDAY
    var d = date
    while (d.dayOfWeek != firstDow) d = d.minusDays(1)
    return d
}

private fun weekRangeLabel(anchor: LocalDate, weekStart: WeekStart, locale: Locale): String {
    val start = startOfWeek(anchor, weekStart)
    val end = start.plusDays(6)
    val fmt = DateTimeFormatter.ofPattern("MMM d", locale)
    return "${start.format(fmt)} – ${end.format(fmt)}"
}

/** Swipe left → next month/week, swipe right → previous, mirroring the header arrows. */
private fun Modifier.horizontalSwipe(key: Any?, onPrev: () -> Unit, onNext: () -> Unit): Modifier = pointerInput(key) {
    val threshold = 48.dp.toPx()
    var dx = 0f
    detectHorizontalDragGestures(
        onDragStart = { dx = 0f },
        onDragCancel = { dx = 0f },
        onDragEnd = {
            when {
                dx <= -threshold -> onNext()
                dx >= threshold -> onPrev()
            }
        },
    ) { change, amount ->
        dx += amount
        change.consume()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalendarHeader(
    view: CalView,
    month: YearMonth,
    weekAnchor: LocalDate,
    weekStart: WeekStart,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onSelectView: (CalView) -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onPrev) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = stringResource(if (view == CalView.MONTH) R.string.cd_prev_month else R.string.cd_prev_week),
                )
            }
            Text(
                text = if (view == CalView.MONTH) {
                    "${month.month.getDisplayName(TextStyle.FULL, locale)} ${month.year}"
                } else {
                    weekRangeLabel(weekAnchor, weekStart, locale)
                },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            IconButton(onClick = onNext) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = stringResource(if (view == CalView.MONTH) R.string.cd_next_month else R.string.cd_next_week),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            val options = listOf(CalView.MONTH to R.string.cal_view_month, CalView.WEEK to R.string.cal_view_week)
            options.forEachIndexed { index, (v, res) ->
                SegmentedButton(
                    selected = view == v,
                    onClick = { onSelectView(v) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                ) {
                    Text(stringResource(res))
                }
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun SelectedDayEntries(
    modifier: Modifier,
    selectedDay: LocalDate?,
    dayItems: List<EncounterWithDetails>,
    onLoadThumb: suspend (MediaEntity) -> ImageBitmap?,
    onOpenEncounter: (String) -> Unit,
    sharedScope: SharedTransitionScope,
    animatedScope: AnimatedVisibilityScope,
) {
    if (selectedDay == null) return
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(top = 8.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "day-header") { DateHeader(label = selectedDay.format(selectedDayFormatter)) }
        if (dayItems.isEmpty()) {
            item(key = "day-empty") {
                Text(
                    stringResource(R.string.history_no_trysts_day),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                )
            }
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
    modifier: Modifier = Modifier,
    month: YearMonth,
    dayIcons: Map<LocalDate, Int>,
    dayCounts: Map<LocalDate, Int>,
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
        while (size % 7 != 0) add(null)
    }
    val today = LocalDate.now()

    // Fixed row height so the grid is a constant, "normal" size — same whether or not a day is
    // selected (the day's trysts fill the flexible area below, which scrolls).
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        cells.chunked(7).forEach { week ->
            Row(
                Modifier.fillMaxWidth().height(72.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                week.forEach { date ->
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        if (date != null) {
                            DayCell(
                                date = date,
                                icon = dayIcons[date],
                                count = dayCounts[date] ?: 0,
                                selected = date == selected,
                                isToday = date == today,
                                onClick = { onSelect(date) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekRow(
    modifier: Modifier = Modifier,
    anchor: LocalDate,
    dayIcons: Map<LocalDate, Int>,
    dayCounts: Map<LocalDate, Int>,
    selected: LocalDate?,
    weekStart: WeekStart,
    onSelect: (LocalDate) -> Unit,
) {
    val start = startOfWeek(anchor, weekStart)
    val today = LocalDate.now()
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (i in 0..6) {
            val date = start.plusDays(i.toLong())
            Box(Modifier.weight(1f).fillMaxHeight()) {
                DayCell(
                    date = date,
                    icon = dayIcons[date],
                    count = dayCounts[date] ?: 0,
                    selected = date == selected,
                    isToday = date == today,
                    onClick = { onSelect(date) },
                )
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    @DrawableRes icon: Int?,
    count: Int,
    selected: Boolean,
    isToday: Boolean,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    // Tonal chip base; days with encounters fill toward primary by how many they hold (heatmap).
    // Most days have a single encounter, so even one reads clearly; 2+ is rare and pops hard.
    val targetContainer = when {
        selected -> cs.primary
        count >= 3 -> lerp(cs.surfaceContainerHigh, cs.primary, 0.85f)
        count == 2 -> lerp(cs.surfaceContainerHigh, cs.primary, 0.65f)
        count == 1 -> lerp(cs.surfaceContainerHigh, cs.primary, 0.42f)
        else -> cs.surfaceContainerHigh
    }
    // On the strong (2+) and selected fills, flip text/icon to onPrimary for contrast.
    val targetContent = if (selected || count >= 2) cs.onPrimary else cs.onSurface
    val container by animateColorAsState(targetContainer, animationSpec = tween(200), label = "dayCellContainer")
    val content by animateColorAsState(targetContent, animationSpec = tween(200), label = "dayCellContent")
    val iconTint = if (count == 0 && !selected) cs.primary else content
    // The day number + entry icon are visual only; give TalkBack a full spoken label and state.
    val locale = LocalConfiguration.current.locales[0]
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
        shape = RoundedCornerShape(14.dp),
        color = container,
        contentColor = content,
        // Today gets an outline ring so it stands out from logged days; when today is the
        // selected day, the filled selection pill takes over instead.
        border = if (isToday && !selected) {
            BorderStroke(2.dp, cs.primary)
        } else {
            null
        },
        modifier = Modifier
            .fillMaxSize()
            .semantics(mergeDescendants = true) {
                contentDescription = cellDescription
                this.selected = selected
            },
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                date.dayOfMonth.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            if (icon != null) {
                Spacer(Modifier.height(2.dp))
                Icon(
                    painterResource(icon),
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(30.dp),
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
