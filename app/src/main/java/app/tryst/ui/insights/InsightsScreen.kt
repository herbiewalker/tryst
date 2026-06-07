package app.tryst.ui.insights

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tryst.core.prefs.ChartStyle
import app.tryst.data.stats.Bucket
import app.tryst.data.stats.Insights
import app.tryst.data.stats.Tally

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsightsScreen(
    startInEditMode: Boolean = false,
    // Non-null when opened as a sub-screen (Settings → Customize Insights): shows a back arrow
    // and makes "Done" return instead of toggling edit mode in place.
    onBack: (() -> Unit)? = null,
    viewModel: InsightsViewModel = hiltViewModel(),
) {
    val insights by viewModel.insights.collectAsStateWithLifecycle()
    val statOrder by viewModel.statOrder.collectAsStateWithLifecycle()
    val hiddenStats by viewModel.hiddenStats.collectAsStateWithLifecycle()
    val sectionOrder by viewModel.sectionOrder.collectAsStateWithLifecycle()
    val hiddenSections by viewModel.hiddenSections.collectAsStateWithLifecycle()
    val sectionStyles by viewModel.sectionStyles.collectAsStateWithLifecycle()
    var editMode by remember { mutableStateOf(startInEditMode) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (editMode) "Customize Insights" else "Insights") },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Settings")
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { if (onBack != null) onBack() else editMode = !editMode }) {
                        if (editMode) {
                            Icon(Icons.Filled.Check, contentDescription = "Done")
                        } else {
                            Icon(Icons.Filled.Tune, contentDescription = "Customize")
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (insights.isEmpty && !editMode) {
            EmptyState(Modifier.padding(padding))
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (editMode) {
                item(key = "sections-editor") {
                    SectionsEditor(
                        order = InsightSections.ordered(sectionOrder).map { it.id },
                        hidden = hiddenSections,
                        styles = sectionStyles,
                        onMove = viewModel::moveSection,
                        onToggleHidden = viewModel::setSectionHidden,
                        onSetStyle = viewModel::setSectionStyle,
                    )
                }
                item(key = "stats-editor") {
                    StatEditor(
                        insights = insights,
                        order = StatTiles.ordered(statOrder).map { it.id },
                        hidden = hiddenStats,
                        onMove = viewModel::moveStat,
                        onToggleHidden = viewModel::setStatHidden,
                    )
                }
                item(key = "reset") {
                    Column {
                        TextButton(onClick = viewModel::resetLayout) { Text("Reset to default layout") }
                        Box(Modifier.height(48.dp))
                    }
                }
            } else {
                item(key = "overview") { OverviewGrid(insights, statOrder, hiddenStats) }

                val sections = InsightSections.ordered(sectionOrder)
                    .filter { it.id !in hiddenSections && it.hasData(insights) }
                items(sections, key = { it.id }) { section ->
                    SectionCard(section.title) {
                        SectionContent(section.id, insights, sectionStyles[section.id] ?: ChartStyle.BARS)
                    }
                }
                item(key = "footer-space") { Box(Modifier.height(64.dp)) }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------
// View mode: overview grid + section content
// ---------------------------------------------------------------------------------------

private const val STAT_COLUMNS = 3

/** A uniform, evenly-sized grid (fixed columns, equal-height cells) so the tiles line up cleanly. */
@Composable
private fun OverviewGrid(insights: Insights, order: List<String>, hidden: Set<String>) {
    val tiles = StatTiles.ordered(order)
        .filter { it.id !in hidden }
        .mapNotNull { tile -> tile.value(insights)?.let { tile to it } }
    if (tiles.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        tiles.chunked(STAT_COLUMNS).forEach { rowTiles ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                rowTiles.forEach { (tile, value) -> StatCard(tile.label, value, Modifier.weight(1f)) }
                repeat(STAT_COLUMNS - rowTiles.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = MaterialTheme.shapes.large,
        modifier = modifier.height(92.dp),
    ) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** A chart in a section: either an ordered trend (bars/line) or a categorical breakdown (bars/donut). */
private sealed interface Sub {
    val label: String
}
private data class TrendSub(override val label: String, val data: List<Bucket>) : Sub
private data class BreakSub(override val label: String, val data: List<Tally>) : Sub

@Composable
private fun SectionContent(id: String, insights: Insights, style: ChartStyle) {
    when (id) {
        InsightSections.ACTIVITY -> GroupedSubCharts(
            style,
            listOf(
                TrendSub("Per month", insights.monthly),
                TrendSub("By day of week", insights.byWeekday),
            ),
        )
        InsightSections.SATISFACTION -> GroupedSubCharts(
            style,
            listOf(TrendSub("", insights.ratingHistogram.mapIndexed { i, c -> Bucket("${i + 1}★", c) })),
        )
        InsightSections.PEOPLE -> GroupedSubCharts(style, listOf(BreakSub("", insights.topPartners)))
        InsightSections.ACTS_POSITIONS -> GroupedSubCharts(
            style,
            listOf(
                BreakSub("Acts", insights.topActs),
                BreakSub("Positions", insights.topPositions),
            ),
        )
        InsightSections.VIBE -> GroupedSubCharts(
            style,
            listOf(
                BreakSub("Moods", insights.topMoods),
                BreakSub("Kinks", insights.topKinks),
                BreakSub("Places", insights.topSettings),
                BreakSub("Occasions", insights.topOccasions),
            ),
        )
        InsightSections.INITIATOR -> GroupedSubCharts(style, listOf(BreakSub("", insights.topInitiators)))
        InsightSections.ORGASMS -> {
            val yourVsPartner = buildList {
                if (insights.totalSelfOrgasms > 0) add(Tally("You", insights.totalSelfOrgasms))
                if (insights.totalPartnerOrgasms > 0) add(Tally("Partners", insights.totalPartnerOrgasms))
            }
            GroupedSubCharts(
                style,
                listOf(
                    BreakSub("You vs partners", yourVsPartner),
                    BreakSub("Orgasms per partner", insights.orgasmsPerPartner),
                    TrendSub("Over time", insights.orgasmsMonthly),
                    BreakSub("Finish / ejaculation", insights.topEjaculation),
                ),
            )
        }
        InsightSections.DETAILS -> GroupedSubCharts(
            style,
            listOf(
                BreakSub("Toys", insights.topToys),
                BreakSub("Protection", insights.topProtection),
            ),
        )
    }
}

/** Renders each non-empty sub-chart, separated by a divider + accent sub-label. */
@Composable
private fun GroupedSubCharts(style: ChartStyle, subs: List<Sub>) {
    val shown = subs.filter {
        when (it) {
            is TrendSub -> it.data.any { b -> b.count > 0 }
            is BreakSub -> it.data.isNotEmpty()
        }
    }
    if (shown.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        shown.forEachIndexed { index, sub ->
            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (sub.label.isNotEmpty()) {
                    Text(
                        sub.label,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
                when (sub) {
                    is TrendSub -> TrendChart(style, sub.data)
                    is BreakSub -> BreakdownChart(style, sub.data)
                }
            }
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

// ---------------------------------------------------------------------------------------
// Edit mode: section editor (reorder / hide / per-card style) + stat-tile editor
// ---------------------------------------------------------------------------------------

@Composable
private fun SectionsEditor(
    order: List<String>,
    hidden: Set<String>,
    styles: Map<String, ChartStyle>,
    onMove: (order: List<String>, from: Int, to: Int) -> Unit,
    onToggleHidden: (id: String, hidden: Boolean) -> Unit,
    onSetStyle: (id: String, style: ChartStyle) -> Unit,
) {
    val sections = remember(order) { InsightSections.ordered(order) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Sections", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Text(
            "Drag to reorder. Tap the eye to show or hide a section, and pick its chart style.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ReorderableColumn(
            items = sections,
            key = { it.id },
            itemHeight = 112.dp,
            spacing = 10.dp,
            onMove = { from, to -> onMove(order, from, to) },
        ) { section, dragging, handle ->
            val isHidden = section.id in hidden
            val style = styles[section.id] ?: ChartStyle.BARS
            Card(
                modifier = Modifier.fillMaxWidth().height(112.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (dragging) {
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    } else {
                        MaterialTheme.colorScheme.surfaceContainer
                    },
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = if (dragging) 8.dp else 0.dp),
            ) {
                Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.DragHandle,
                            contentDescription = "Drag to reorder",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = handle.size(28.dp),
                        )
                        Text(
                            section.title,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f).padding(start = 12.dp),
                            color = if (isHidden) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        )
                        IconButton(onClick = { onToggleHidden(section.id, !isHidden) }) {
                            Icon(
                                if (isHidden) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (isHidden) "Show" else "Hide",
                                tint = if (isHidden) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.padding(start = 40.dp, top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ChartStyle.entries.forEach { s ->
                            FilterChip(
                                selected = style == s,
                                onClick = { onSetStyle(section.id, s) },
                                label = { Text(styleLabel(s)) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatEditor(
    insights: Insights,
    order: List<String>,
    hidden: Set<String>,
    onMove: (order: List<String>, from: Int, to: Int) -> Unit,
    onToggleHidden: (id: String, hidden: Boolean) -> Unit,
) {
    val tiles = remember(order) { StatTiles.ordered(order) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Overview stats", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Text(
            "Drag to reorder. Tap the eye to show or hide a stat on the overview.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ReorderableColumn(
            items = tiles,
            key = { it.id },
            itemHeight = 60.dp,
            spacing = 10.dp,
            onMove = { from, to -> onMove(order, from, to) },
        ) { tile, dragging, handle ->
            val isHidden = tile.id in hidden
            Card(
                modifier = Modifier.fillMaxWidth().height(60.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (dragging) {
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    } else {
                        MaterialTheme.colorScheme.surfaceContainer
                    },
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = if (dragging) 8.dp else 0.dp),
            ) {
                Row(
                    Modifier.fillMaxSize().padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.DragHandle,
                        contentDescription = "Drag to reorder",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = handle.size(28.dp),
                    )
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(
                            tile.label,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (isHidden) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            tile.value(insights) ?: "—",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { onToggleHidden(tile.id, !isHidden) }) {
                        Icon(
                            if (isHidden) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = if (isHidden) "Show" else "Hide",
                            tint = if (isHidden) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

private fun styleLabel(style: ChartStyle): String = when (style) {
    ChartStyle.BARS -> "Bars"
    ChartStyle.LINE -> "Line"
    ChartStyle.DONUT -> "Donut"
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            "No insights yet.\nLog a few trysts to see your stats.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
