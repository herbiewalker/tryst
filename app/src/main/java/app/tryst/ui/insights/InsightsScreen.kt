package app.tryst.ui.insights

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.EmojiEvents
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tryst.R
import app.tryst.core.prefs.ChartStyle
import app.tryst.data.stats.Bucket
import app.tryst.data.stats.Insights
import app.tryst.data.stats.Tally
import app.tryst.ui.achievements.AchievementsTeaser
import app.tryst.ui.common.adaptiveContentWidth
import app.tryst.ui.common.rememberHaptics

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsightsScreen(
    startInEditMode: Boolean = false,
    // Non-null when opened as a sub-screen (Settings → Customize Insights): shows a back arrow
    // and makes "Done" return instead of toggling edit mode in place.
    onBack: (() -> Unit)? = null,
    onOpenAchievements: () -> Unit = {},
    viewModel: InsightsViewModel = hiltViewModel(),
) {
    val insights by viewModel.insights.collectAsStateWithLifecycle()
    val statOrder by viewModel.statOrder.collectAsStateWithLifecycle()
    val hiddenStats by viewModel.hiddenStats.collectAsStateWithLifecycle()
    val sectionOrder by viewModel.sectionOrder.collectAsStateWithLifecycle()
    val hiddenSections by viewModel.hiddenSections.collectAsStateWithLifecycle()
    val sectionStyles by viewModel.sectionStyles.collectAsStateWithLifecycle()
    val haptics = rememberHaptics()
    var editMode by remember { mutableStateOf(startInEditMode) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (editMode) R.string.settings_customize_insights else R.string.insights_title)) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back_to_settings))
                        }
                    }
                },
                actions = {
                    if (onBack == null && !editMode) {
                        IconButton(onClick = onOpenAchievements) {
                            Icon(Icons.Filled.EmojiEvents, contentDescription = stringResource(R.string.achievements_title))
                        }
                    }
                    IconButton(onClick = {
                        haptics.tick()
                        if (onBack != null) onBack() else editMode = !editMode
                    }) {
                        if (editMode) {
                            Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.action_done))
                        } else {
                            Icon(Icons.Filled.Tune, contentDescription = stringResource(R.string.cd_customize))
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
            // Cap + centre on wide windows so the stacked cards don't stretch (Pass 5); no-op on phones.
            modifier = Modifier.fillMaxSize().padding(padding).wrapContentWidth().adaptiveContentWidth(),
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
                        TextButton(onClick = viewModel::resetLayout) { Text(stringResource(R.string.insights_reset)) }
                        Box(Modifier.height(48.dp))
                    }
                }
            } else {
                val sections = InsightSections.ordered(sectionOrder)
                    .filter { it.id !in hiddenSections && it.hasData(insights) }
                items(sections, key = { it.id }) { section ->
                    Box(Modifier.animateItem()) {
                        when (section.id) {
                            InsightSections.OVERVIEW -> OverviewGrid(insights, statOrder, hiddenStats)
                            // Self-contained summary card (its own header + ViewModel), not a chart section.
                            InsightSections.ACHIEVEMENTS -> AchievementsTeaser(onSeeAll = onOpenAchievements)
                            else -> SectionCard(section.title) {
                                SectionContent(section.id, insights, sectionStyles[section.id] ?: ChartStyle.BARS)
                            }
                        }
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
        // Min (not fixed) height so the tile grows rather than clipping at large font scales.
        modifier = modifier.heightIn(min = 92.dp),
    ) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 12.dp).semantics(mergeDescendants = true) {},
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                TrendSub(stringResource(R.string.insights_sub_per_month), insights.monthly),
                TrendSub(stringResource(R.string.insights_sub_by_weekday), insights.byWeekday),
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
                BreakSub(stringResource(R.string.insights_sub_acts), insights.topActs),
                BreakSub(stringResource(R.string.insights_sub_positions), insights.topPositions),
            ),
        )
        InsightSections.VIBE -> GroupedSubCharts(
            style,
            listOf(
                BreakSub(stringResource(R.string.insights_sub_moods), insights.topMoods),
                BreakSub(stringResource(R.string.insights_sub_kinks), insights.topKinks),
                BreakSub(stringResource(R.string.insights_sub_places), insights.topSettings),
                BreakSub(stringResource(R.string.insights_sub_occasions), insights.topOccasions),
            ),
        )
        InsightSections.INITIATOR -> GroupedSubCharts(style, listOf(BreakSub("", insights.topInitiators)))
        InsightSections.ORGASMS -> {
            // Tally labels ("You"/"Partners") are TypeColors identity keys — left as English (deferred).
            val yourVsPartner = buildList {
                if (insights.totalSelfOrgasms > 0) add(Tally("You", insights.totalSelfOrgasms))
                if (insights.totalPartnerOrgasms > 0) add(Tally("Partners", insights.totalPartnerOrgasms))
            }
            GroupedSubCharts(
                style,
                listOf(
                    BreakSub(stringResource(R.string.insights_sub_you_vs_partners), yourVsPartner),
                    BreakSub(stringResource(R.string.insights_sub_orgasms_per_partner), insights.orgasmsPerPartner),
                    TrendSub(stringResource(R.string.insights_sub_over_time), insights.orgasmsMonthly),
                    BreakSub(stringResource(R.string.insights_sub_finish), insights.topEjaculation),
                ),
            )
        }
        InsightSections.DETAILS -> GroupedSubCharts(
            style,
            listOf(
                BreakSub(stringResource(R.string.insights_sub_toys), insights.topToys),
                BreakSub(stringResource(R.string.insights_sub_protection), insights.topProtection),
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
        Text(stringResource(R.string.insights_sections_header), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Text(
            stringResource(R.string.insights_sections_desc),
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
                            contentDescription = stringResource(R.string.cd_drag_reorder),
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
                                contentDescription = stringResource(if (isHidden) R.string.cd_show else R.string.cd_hide),
                                tint = if (isHidden) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    if (section.hasChart) {
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
                    } else if (section.editorNote != null) {
                        Text(
                            section.editorNote,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 40.dp, top = 8.dp),
                        )
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
        Text(stringResource(R.string.insights_overview_header), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Text(
            stringResource(R.string.insights_stats_desc),
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
                        contentDescription = stringResource(R.string.cd_drag_reorder),
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
                            contentDescription = stringResource(if (isHidden) R.string.cd_show else R.string.cd_hide),
                            tint = if (isHidden) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun styleLabel(style: ChartStyle): String = stringResource(
    when (style) {
        ChartStyle.BARS -> R.string.chart_style_bars
        ChartStyle.LINE -> R.string.chart_style_line
        ChartStyle.DONUT -> R.string.chart_style_donut
    },
)

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            stringResource(R.string.insights_empty),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
