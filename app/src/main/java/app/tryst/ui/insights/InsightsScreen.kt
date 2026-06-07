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
    val chartStyle by viewModel.chartStyle.collectAsStateWithLifecycle()
    val statOrder by viewModel.statOrder.collectAsStateWithLifecycle()
    val hidden by viewModel.hiddenStats.collectAsStateWithLifecycle()
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
            item(key = "chart-style") {
                ChartStyleSelector(chartStyle, onSelect = viewModel::setChartStyle)
            }

            if (editMode) {
                item(key = "editor") {
                    StatEditor(
                        insights = insights,
                        order = StatTiles.ordered(statOrder).map { it.id },
                        hidden = hidden,
                        onMove = viewModel::moveStat,
                        onToggleHidden = viewModel::setStatHidden,
                        onReset = viewModel::resetLayout,
                    )
                }
            } else {
                item(key = "overview") { OverviewGrid(insights, statOrder, hidden) }

                section("Activity") {
                    Caption("Trysts per month and by day of week")
                    TrendChart(chartStyle, insights.monthly)
                    Box(Modifier.height(8.dp))
                    TrendChart(chartStyle, insights.byWeekday)
                }
                if (insights.ratingHistogram.any { it > 0 }) {
                    section("Satisfaction") {
                        TrendChart(
                            chartStyle,
                            insights.ratingHistogram.mapIndexed { i, c -> Bucket("${i + 1}★", c) },
                        )
                    }
                }
                breakdownSection(chartStyle, "People", insights.topPartners)
                breakdownGroup(chartStyle, "What you did", listOf("Acts" to insights.topActs, "Positions" to insights.topPositions))
                breakdownGroup(
                    chartStyle,
                    "Vibe & context",
                    listOf(
                        "Moods" to insights.topMoods,
                        "Kinks" to insights.topKinks,
                        "Places" to insights.topSettings,
                        "Occasions" to insights.topOccasions,
                    ),
                )
                breakdownGroup(
                    chartStyle,
                    "Details",
                    listOf(
                        "Toys" to insights.topToys,
                        "Protection" to insights.topProtection,
                        "Finish / ejaculation" to insights.topEjaculation,
                    ),
                )
                item(key = "footer-space") { Box(Modifier.height(64.dp)) }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------
// Chart style selector
// ---------------------------------------------------------------------------------------

@Composable
private fun ChartStyleSelector(selected: ChartStyle, onSelect: (ChartStyle) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val labels = listOf(ChartStyle.BARS to "Bars", ChartStyle.LINE to "Line", ChartStyle.DONUT to "Donut")
        labels.forEach { (style, label) ->
            FilterChip(
                selected = selected == style,
                onClick = { onSelect(style) },
                label = { Text(label) },
            )
        }
    }
}

// ---------------------------------------------------------------------------------------
// Overview (view mode): 2-column grid of visible tiles in saved order
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
                rowTiles.forEach { (tile, value) ->
                    StatCard(tile.label, value, Modifier.weight(1f))
                }
                // Pad the final short row so cells keep a consistent width.
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
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ---------------------------------------------------------------------------------------
// Editor (edit mode): reorderable + show/hide tile list
// ---------------------------------------------------------------------------------------

@Composable
private fun StatEditor(
    insights: Insights,
    order: List<String>,
    hidden: Set<String>,
    onMove: (order: List<String>, from: Int, to: Int) -> Unit,
    onToggleHidden: (id: String, hidden: Boolean) -> Unit,
    onReset: () -> Unit,
) {
    val tiles = remember(order) { StatTiles.ordered(order) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "Drag to reorder. Tap the eye to show or hide a stat on the Insights overview.",
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
        TextButton(onClick = onReset) { Text("Reset to default layout") }
    }
}

// ---------------------------------------------------------------------------------------
// Section scaffolding
// ---------------------------------------------------------------------------------------

private fun androidx.compose.foundation.lazy.LazyListScope.section(
    title: String,
    content: @Composable () -> Unit,
) {
    item(key = "section-$title") {
        SectionCard(title) { content() }
    }
}

/** A breakdown section with a single chart, only rendered when there's data. */
private fun androidx.compose.foundation.lazy.LazyListScope.breakdownSection(
    style: ChartStyle,
    title: String,
    items: List<Tally>,
) {
    if (items.isEmpty()) return
    item(key = "breakdown-$title") {
        SectionCard(title) { BreakdownChart(style, items) }
    }
}

/** A section grouping several labelled breakdowns under one header (skips empty ones). */
private fun androidx.compose.foundation.lazy.LazyListScope.breakdownGroup(
    style: ChartStyle,
    title: String,
    groups: List<Pair<String, List<Tally>>>,
) {
    val nonEmpty = groups.filter { it.second.isNotEmpty() }
    if (nonEmpty.isEmpty()) return
    item(key = "group-$title") {
        SectionCard(title) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                nonEmpty.forEachIndexed { index, (label, items) ->
                    if (index > 0) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            label,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                        BreakdownChart(style, items)
                    }
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
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            content()
        }
    }
}

@Composable
private fun Caption(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
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
