package app.tryst.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
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

    Scaffold(
        topBar = { TopAppBar(title = { Text("History") }) },
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
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(encounters, key = { it.encounter.id }) { item ->
                    EncounterCard(item, onClick = { onOpenEncounter(item.encounter.id) })
                }
            }
        }
    }
}

@Composable
private fun EncounterCard(item: EncounterWithDetails, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(Format.dateTime(item.encounter.startAt), style = MaterialTheme.typography.titleMedium)
            if (item.partners.isNotEmpty()) {
                Text(
                    item.partners.joinToString(", ") { Format.partnerName(it) },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            item.encounter.satisfactionRating?.let { rating ->
                Text("Rating: $rating/5", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            "No encounters yet.\nTap + to log one.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
