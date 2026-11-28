// SPDX-License-Identifier: GPL-3.0-or-later
// ╭──────────────────────────────────────────────────────────────────╮
// │  ┌●───●───●───●───●───●───●───●───●───●───●───●───●───●───●───┐  │
// │  │                                                            │  │
// │  ●   ██╗  ██╗    ██╗    ██╗                                   ●  │
// │  │   ██║  ██║    ██║    ██║           herbiewalker            │  │
// │  │   ███████║    ██║ █╗ ██║──●──●──●──┐                       │  │
// │  │   ██╔══██║    ██║███╗██║           │                       │  │
// │  ●   ██║  ██║    ╚███╔███╔╝           ●───⏣  code · tools     ●  │
// │  │   ╚═╝  ╚═╝     ╚══╝╚══╝                    homelab         │  │
// │  │                                                            │  │
// │  └●───●───●───●───●───●───●───●───●───●───●───●───●───●───●───┘  │
// ╰──────────────────────────────────────────────────────────────────╯
package app.tryst.ui.encounter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.tryst.R
import app.tryst.ui.common.EjaculationOption

/** One list inside the combined orgasm row: its options, the inline suggestions and what is picked. */
data class OrgasmChipSection(
    val title: String,
    val all: List<EjaculationOption>,
    val common: List<EjaculationOption>,
    val selectedIds: Set<String>,
    val onToggle: (String) -> Unit,
    val onAddNew: (String) -> Unit,
)

/**
 * One chip row per orgasm (ORG-1) mixing orgasm types and finish locations, like the old single
 * Finish row. Each [sections] entry keeps its own list, so picks still save and chart separately.
 * Inline, a section shows its picks once it has any, otherwise its suggestions; "More…" opens every
 * section in full, each with its own add field. A null [label] means a lone orgasm, with no heading.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OrgasmChipsField(label: String?, sections: List<OrgasmChipSection>) {
    var showAll by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (label != null) Text(label, style = MaterialTheme.typography.labelLarge)
        FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            sections.forEach { section ->
                val picked = section.all.filter { it.id in section.selectedIds }
                val inline = (picked.ifEmpty { section.common }).distinct().sortedBy { it.label.lowercase() }
                inline.forEach { option ->
                    FilterChip(
                        selected = option.id in section.selectedIds,
                        onClick = { section.onToggle(option.id) },
                        label = { Text(option.label) },
                    )
                }
            }
            AssistChip(onClick = { showAll = true }, label = { Text(stringResource(R.string.action_more)) })
        }
    }

    if (showAll) {
        AlertDialog(
            onDismissRequest = { showAll = false },
            title = { Text(label ?: stringResource(R.string.encounter_orgasm_details)) },
            text = {
                Column(
                    Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    sections.forEach { SectionInDialog(it) }
                }
            },
            confirmButton = { TextButton(onClick = { showAll = false }) { Text(stringResource(R.string.action_done)) } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SectionInDialog(section: OrgasmChipSection) {
    var newLabel by remember { mutableStateOf("") }
    val submit = {
        val trimmed = newLabel.trim()
        if (trimmed.isNotEmpty()) {
            section.onAddNew(trimmed)
            newLabel = ""
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(section.title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            section.all.sortedBy { it.label.lowercase() }.forEach { option ->
                FilterChip(
                    selected = option.id in section.selectedIds,
                    onClick = { section.onToggle(option.id) },
                    label = { Text(option.label) },
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = newLabel,
                onValueChange = { newLabel = it },
                placeholder = { Text(stringResource(R.string.action_add_new_placeholder)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = submit, enabled = newLabel.isNotBlank()) {
                Text(stringResource(R.string.action_add))
            }
        }
    }
}

// ⏣ HW ⏣ · code · tools · homelab · ⏣ HW ⏣
