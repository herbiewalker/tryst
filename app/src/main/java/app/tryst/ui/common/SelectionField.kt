package app.tryst.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.tryst.R

/**
 * A labelled multi-select category. Shows the [common] options inline (plus anything already
 * selected), with a "More…" chip that opens a dialog listing every option alphabetically.
 * Keeps the form uncluttered while making the full set reachable.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> MultiSelectField(
    label: String,
    all: List<T>,
    common: List<T>,
    selected: Set<T>,
    labelOf: (T) -> String,
    onToggle: (T) -> Unit,
) {
    var showAll by remember { mutableStateOf(false) }
    // Before anything is chosen, show the curated common set to pick from. Once there are
    // selections, show only those (add/remove more via "More…").
    val inline = (if (selected.isEmpty()) common else selected.toList())
        .distinct()
        .sortedBy { labelOf(it).lowercase() }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            inline.forEach { option ->
                FilterChip(
                    selected = option in selected,
                    onClick = { onToggle(option) },
                    label = { Text(labelOf(option)) },
                )
            }
            AssistChip(onClick = { showAll = true }, label = { Text(stringResource(R.string.action_more)) })
        }
    }

    if (showAll) {
        val sorted = remember(all) { all.sortedBy { labelOf(it).lowercase() } }
        AlertDialog(
            onDismissRequest = { showAll = false },
            title = { Text(label) },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        sorted.forEach { option ->
                            FilterChip(
                                selected = option in selected,
                                onClick = { onToggle(option) },
                                label = { Text(labelOf(option)) },
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showAll = false }) { Text(stringResource(R.string.action_done)) } },
        )
    }
}

/**
 * Single-select variant. [common] inline (plus the current selection), full alphabetical list in
 * the "More…" dialog; picking in the dialog closes it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> SingleSelectField(
    label: String,
    all: List<T>,
    common: List<T>,
    selected: T?,
    labelOf: (T) -> String,
    onSelect: (T) -> Unit,
) {
    var showAll by remember { mutableStateOf(false) }
    // Show only the current choice once made; otherwise the curated common set.
    val inline = (if (selected == null) common else listOf(selected))
        .distinct()
        .sortedBy { labelOf(it).lowercase() }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            inline.forEach { option ->
                FilterChip(
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    label = { Text(labelOf(option)) },
                )
            }
            AssistChip(onClick = { showAll = true }, label = { Text(stringResource(R.string.action_more)) })
        }
    }

    if (showAll) {
        val sorted = remember(all) { all.sortedBy { labelOf(it).lowercase() } }
        AlertDialog(
            onDismissRequest = { showAll = false },
            title = { Text(label) },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        sorted.forEach { option ->
                            FilterChip(
                                selected = option == selected,
                                onClick = {
                                    onSelect(option)
                                    showAll = false
                                },
                                label = { Text(labelOf(option)) },
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showAll = false }) { Text(stringResource(R.string.action_close)) } },
        )
    }
}
