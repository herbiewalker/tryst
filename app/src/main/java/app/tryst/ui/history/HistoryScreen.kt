package app.tryst.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tryst.data.db.relation.EncounterWithDetails
import app.tryst.ui.common.Format

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onAddEncounter: () -> Unit,
    onOpenEncounter: (String) -> Unit,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val encounters by viewModel.encounters.collectAsStateWithLifecycle()
    // Already sorted by startAt DESC; groupBy keeps that order, one section per day.
    val grouped = remember(encounters) {
        encounters.groupBy { Format.relativeDay(it.encounter.startAt) }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Trysts") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddEncounter) {
                Text("+", style = MaterialTheme.typography.headlineMedium)
            }
        },
    ) { padding ->
        if (encounters.isEmpty()) {
            EmptyState(Modifier.padding(padding))
        } else {
            LazyColumn(
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
                Text(
                    text = if (item.partners.isEmpty()) "Solo" else item.partners.joinToString(", ") { Format.partnerName(it) },
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = Format.time(e.startAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                val pills = buildList {
                    e.satisfactionRating?.let { add("★ $it") }
                    e.durationMin?.let { add("$it min") }
                    e.mood?.let { add(Format.enumLabel(it)) }
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
