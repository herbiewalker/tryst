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

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.tryst.R
import app.tryst.ui.insights.TypeColors

/**
 * A person's color for the calendar and charts. The first swatch is "Automatic" (the stable color their
 * name already gets, shown as [autoColor]); the rest are the shared palette. [selectedHex] null means
 * Automatic. A stored color that isn't in the palette (from an import, say) shows as its own selected
 * "Current color" swatch, so what the picker says matches what the calendar draws.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PersonColorPicker(selectedHex: String?, autoColor: Color, onPick: (String?) -> Unit, modifier: Modifier = Modifier) {
    val picked = TypeColors.palette.firstOrNull { it.hex.equals(selectedHex, ignoreCase = true) }
    val custom = if (picked == null) TypeColors.parse(selectedHex) else null
    Column(modifier) {
        Text(stringResource(R.string.person_color_label), style = MaterialTheme.typography.titleSmall)
        Text(
            stringResource(R.string.person_color_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Swatch(
                color = autoColor,
                selected = picked == null && custom == null,
                label = stringResource(R.string.person_color_auto),
                badge = stringResource(R.string.person_color_auto_badge),
                onClick = { onPick(null) },
            )
            if (custom != null) {
                Swatch(
                    color = custom,
                    selected = true,
                    label = stringResource(R.string.person_color_current),
                    badge = null,
                    onClick = {},
                )
            }
            TypeColors.palette.forEach { option ->
                Swatch(
                    color = option.color,
                    selected = option == picked,
                    label = option.name,
                    badge = null,
                    onClick = { onPick(option.hex) },
                )
            }
        }
    }
}

@Composable
private fun Swatch(color: Color, selected: Boolean, label: String, badge: String?, onClick: () -> Unit) {
    val ring = MaterialTheme.colorScheme.onSurface
    val mark = if (color.luminance() > DARK_MARK_LUMINANCE) Color.Black else Color.White
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(SWATCH)
            .clip(CircleShape)
            .then(if (selected) Modifier.border(3.dp, ring, CircleShape) else Modifier)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = label },
    ) {
        Box(Modifier.size(SWATCH - 10.dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
            when {
                // Automatic keeps its "A" even when picked, so it never looks like the palette color it happens to match.
                badge != null -> Text(badge, color = mark, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                selected -> Icon(Icons.Filled.Check, contentDescription = null, tint = mark, modifier = Modifier.size(20.dp))
            }
        }
    }
}

private val SWATCH = 44.dp
private const val DARK_MARK_LUMINANCE = 0.5f

// ⏣ HW ⏣ · code · tools · homelab · ⏣ HW ⏣
