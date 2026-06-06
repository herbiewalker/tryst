package app.tryst.ui.history

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
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
import androidx.annotation.DrawableRes
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tryst.data.db.relation.EncounterWithDetails
import app.tryst.ui.common.Format
import app.tryst.ui.common.PracticeVisuals
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onAddEncounter: () -> Unit,
    onOpenEncounter: (String) -> Unit,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val encounters by viewModel.encounters.collectAsStateWithLifecycle()
    var calendarMode by remember { mutableStateOf(false) }
    // Already sorted by startAt DESC; groupBy keeps that order, one section per day.
    val grouped = remember(encounters) {
        encounters.groupBy { Format.relativeDay(it.encounter.startAt) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Trysts") },
                actions = {
                    IconButton(onClick = { calendarMode = !calendarMode }) {
                        if (calendarMode) {
                            Icon(Icons.AutoMirrored.Filled.List, contentDescription = "List view")
                        } else {
                            Icon(Icons.Filled.DateRange, contentDescription = "Calendar view")
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddEncounter) {
                Text("+", style = MaterialTheme.typography.headlineMedium)
            }
        },
    ) { padding ->
        when {
            encounters.isEmpty() -> EmptyState(Modifier.padding(padding))
            calendarMode -> CalendarView(
                modifier = Modifier.padding(padding),
                items = encounters,
                onOpenEncounter = onOpenEncounter,
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                grouped.forEach { (label, items) ->
                    item(key = label) { DateHeader(label) }
                    items(items, key = { it.encounter.id }) { item ->
                        EncounterCard(item, onClick = { onOpenEncounter(item.encounter.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun DateHeader(label: String) {
    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EncounterCard(item: EncounterWithDetails, onClick: () -> Unit) {
    val e = item.encounter
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(modifier = Modifier.padding(12.dp)) {
            DateBadge(e.startAt)
            Column(
                modifier = Modifier.padding(start = 14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PracticeBadge(
                        PracticeVisuals.icon(
                            PracticeVisuals.primaryPractice(e.practicesPerformed, e.practicesReceived),
                        ),
                    )
                    Text(
                        text = if (item.partners.isEmpty()) "Solo" else item.partners.joinToString(", ") { Format.partnerName(it) },
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                Text(
                    text = Format.time(e.startAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                val pills = buildList {
                    e.satisfactionRating?.let { add("★ $it") }
                    e.durationMin?.let { add("$it min") }
                    e.mood?.let { add(it.label) }
                    val orgasms = (e.orgasmCountSelf ?: 0) + (e.orgasmCountPartner ?: 0)
                    if (orgasms > 0) add("✨ $orgasms")
                }
                if (pills.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        pills.forEach { Pill(it) }
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
private fun PracticeBadge(@DrawableRes icon: Int) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = CircleShape,
        modifier = Modifier.size(34.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun Pill(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = RoundedCornerShape(50),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

private val selectedDayFormatter = DateTimeFormatter.ofPattern("EEEE, MMM d")

@Composable
private fun CalendarView(
    modifier: Modifier = Modifier,
    items: List<EncounterWithDetails>,
    onOpenEncounter: (String) -> Unit,
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
                onPrev = { month = month.minusMonths(1); selectedDay = null },
                onNext = { month = month.plusMonths(1); selectedDay = null },
            )
        }
        item(key = "weekday-labels") { WeekdayLabels() }
        item(key = "month-grid") {
            MonthGrid(
                month = month,
                dayIcons = dayIcons,
                selected = selectedDay,
                onSelect = { selectedDay = if (it == selectedDay) null else it },
            )
        }
        selectedDay?.let { day ->
            item(key = "day-header") {
                DateHeader(day.format(selectedDayFormatter))
            }
            if (dayItems.isEmpty()) {
                item(key = "day-empty") {
                    Text(
                        "No trysts on this day.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(dayItems, key = { it.encounter.id }) { item ->
                    EncounterCard(item, onClick = { onOpenEncounter(item.encounter.id) })
                }
            }
        }
    }
}

@Composable
private fun MonthHeader(month: YearMonth, onPrev: () -> Unit, onNext: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrev) {
            Icon(Icons.Filled.KeyboardArrowLeft, contentDescription = "Previous month")
        }
        Text(
            text = "${month.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${month.year}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        IconButton(onClick = onNext) {
            Icon(Icons.Filled.KeyboardArrowRight, contentDescription = "Next month")
        }
    }
}

@Composable
private fun WeekdayLabels() {
    // Sunday-first column order.
    val days = listOf(
        DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY,
    )
    Row(Modifier.fillMaxWidth()) {
        days.forEach { dow ->
            Text(
                text = dow.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun MonthGrid(
    month: YearMonth,
    dayIcons: Map<LocalDate, Int>,
    selected: LocalDate?,
    onSelect: (LocalDate) -> Unit,
) {
    val daysInMonth = month.lengthOfMonth()
    // Sunday=0 .. Saturday=6 offset for the 1st of the month.
    val leadingBlanks = month.atDay(1).dayOfWeek.value % 7
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
    val content = when {
        selected -> MaterialTheme.colorScheme.onPrimary
        isToday -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
        contentColor = content,
        modifier = Modifier.fillMaxSize(),
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
            "No trysts yet.\nTap + to log one.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
