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
package app.tryst.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import app.tryst.data.db.entity.DisplayLabel

/**
 * A labelled **optional** single-select chip row: tapping the selected chip again clears it. Shared by
 * the partner editor, the self-profile editor, and the demographic fields so they read identically.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> OptionalChips(
    label: String,
    options: List<T>,
    selected: T?,
    onSelect: (T?) -> Unit,
) where T : Enum<T>, T : DisplayLabel {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { option ->
                FilterChip(
                    selected = option == selected,
                    onClick = { onSelect(if (option == selected) null else option) },
                    label = { Text(option.label) },
                )
            }
        }
    }
}

/**
 * [OptionalChips] for a user-defined catalog (DEMO-1 orientation): options are `id → label` pairs and the
 * selection is an id. With no labels defined yet it shows [emptyHint] instead of an empty chip row.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CatalogChips(
    label: String,
    options: List<Pair<String, String>>,
    selected: String?,
    emptyHint: String,
    onSelect: (String?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        if (options.isEmpty()) {
            Text(emptyHint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEach { (id, text) ->
                    FilterChip(
                        selected = id == selected,
                        onClick = { onSelect(if (id == selected) null else id) },
                        label = { Text(text) },
                    )
                }
            }
        }
    }
}

// ⏣ HW ⏣ · code · tools · homelab · ⏣ HW ⏣
