package app.tryst.ui.insights

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tryst.data.stats.Insights
import app.tryst.data.stats.Tally

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsightsScreen(viewModel: InsightsViewModel = hiltViewModel()) {
    val insights by viewModel.insights.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Insights") }) },
    ) { padding ->
        if (insights.isEmpty) {
            EmptyState(Modifier.padding(padding))
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item(key = "summary") { SummaryGrid(insights) }
                item(key = "monthly") {
                    SectionCard("Activity — last 12 months") {
                        VerticalBarChart(insights.monthly)
                    }
                }
                item(key = "weekday") {
                    SectionCard("By day of week") {
                        VerticalBarChart(insights.byWeekday)
                    }
                }
                if (insights.ratingHistogram.any { it > 0 }) {
                    item(key = "ratings") {
                        SectionCard("Ratings") {
                            VerticalBarChart(
                                insights.ratingHistogram.mapIndexed { i, c ->
                                    app.tryst.data.stats.Bucket("${i + 1}★", c)
                                },
                            )
                        }
                    }
                }

                breakdown("Partners", insights.topPartners)
                breakdown("Acts", insights.topActs)
                breakdown("Positions", insights.topPositions)
                breakdown("Moods", insights.topMoods)
                breakdown("Kinks", insights.topKinks)
                breakdown("Places", insights.topSettings)
                breakdown("Occasions", insights.topOccasions)
                breakdown("Toys", insights.topToys)
                breakdown("Protection", insights.topProtection)
                breakdown("Finish", insights.topEjaculation)

                item(key = "footer-space") { Box(Modifier.padding(bottom = 72.dp)) {} }
            }
        }
    }
}

/** Adds a ranked-bar breakdown section, but only when there's data for it. */
private fun androidx.compose.foundation.lazy.LazyListScope.breakdown(title: String, items: List<Tally>) {
    if (items.isEmpty()) return
    item(key = "breakdown-$title") {
        SectionCard(title) { RankedBars(items) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SummaryGrid(insights: Insights) {
    val stats = buildList {
        add("Total trysts" to insights.totalCount.toString())
        add("This month" to insights.thisMonthCount.toString())
        add("This year" to insights.thisYearCount.toString())
        insights.daysSinceLast?.let {
            add("Days since last" to if (it == 0L) "Today" else it.toString())
        }
        add("Avg / month" to formatDecimal(insights.avgPerMonth))
        add("Current streak" to weeksLabel(insights.currentStreakWeeks))
        add("Longest streak" to weeksLabel(insights.longestStreakWeeks))
        insights.avgRating?.let { add("Avg rating" to "★ ${formatDecimal(it)}") }
        insights.avgDurationMin?.let { add("Avg duration" to "${it.toInt()} min") }
        if (insights.totalDurationMin > 0) add("Total time" to formatDuration(insights.totalDurationMin))
        if (insights.totalSelfOrgasms > 0) add("Your orgasms" to insights.totalSelfOrgasms.toString())
        if (insights.totalPartnerOrgasms > 0) {
            add("Partner orgasms" to insights.totalPartnerOrgasms.toString())
        }
    }
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        stats.forEach { (label, value) -> StatCard(label, value, Modifier.weight(1f)) }
        // Pad a trailing slot so the last row's card doesn't stretch full-width.
        if (stats.size % 2 == 1) Box(Modifier.weight(1f)) {}
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = MaterialTheme.shapes.large,
        modifier = modifier,
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
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
private fun EmptyState(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            "No insights yet.\nLog a few trysts to see your stats.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun weeksLabel(weeks: Int): String = if (weeks == 1) "1 wk" else "$weeks wks"

private fun formatDecimal(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString()
    else String.format(java.util.Locale.getDefault(), "%.1f", value)

private fun formatDuration(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h == 0 -> "${m}m"
        m == 0 -> "${h}h"
        else -> "${h}h ${m}m"
    }
}
