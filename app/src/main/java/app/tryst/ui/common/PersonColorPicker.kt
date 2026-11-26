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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.tryst.R
import app.tryst.data.color.ColorOwner
import app.tryst.ui.insights.NamedColor
import app.tryst.ui.insights.TypeColors

/**
 * A person's color for the calendar and charts (D-70), as one compact row that opens the swatches in a pop-up.
 * Everyone gets their own color automatically; this is where
 * it's changed by hand. Swatches someone else already has show that person's initial and stay pickable, so two
 * people only share a color when the user picks it on purpose. [ownerId] is the person being edited (null for a
 * partner not saved yet), so their own color never counts as taken. A stored color that isn't in the palette (from
 * an import, say) shows as its own selected "Current color" swatch, so the picker matches what the calendar draws.
 */
@Composable
fun PersonColorPicker(
    selectedHex: String?,
    ownerId: String?,
    owners: Map<String, List<ColorOwner>>,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val picked = TypeColors.palette.firstOrNull { it.hex.equals(selectedHex, ignoreCase = true) }
    val custom = if (picked == null) TypeColors.parse(selectedHex) else null
    val sharedWith = picked?.let { othersUsing(it.hex, ownerId, owners) }.orEmpty()
    val current = picked?.name ?: stringResource(R.string.person_color_custom)
    val summary = if (sharedWith.isEmpty()) current else stringResource(R.string.person_color_used_by, current, sharedWith.joinToString())
    var open by rememberSaveable { mutableStateOf(false) }
    // One compact row in the form; the swatches only appear in the pop-up.
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.person_color_change)) { open = true }
            .padding(vertical = 8.dp),
    ) {
        Box(Modifier.size(28.dp).clip(CircleShape).background(picked?.color ?: custom ?: MaterialTheme.colorScheme.outline))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.person_color_label), style = MaterialTheme.typography.bodyLarge)
            Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(stringResource(R.string.person_color_change), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(stringResource(R.string.person_color_label)) },
            text = {
                ColorGrid(picked, custom, ownerId, owners) { hex ->
                    onPick(hex)
                    open = false
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorGrid(
    picked: NamedColor?,
    custom: Color?,
    ownerId: String?,
    owners: Map<String, List<ColorOwner>>,
    onPick: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
        Text(
            stringResource(R.string.person_color_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (custom != null) {
                Swatch(custom, selected = true, label = stringResource(R.string.person_color_current), badge = null, onClick = {})
            }
            TypeColors.palette.forEach { option ->
                val names = othersUsing(option.hex, ownerId, owners)
                Swatch(
                    color = option.color,
                    selected = option == picked,
                    label = if (names.isEmpty()) {
                        option.name
                    } else {
                        stringResource(R.string.person_color_used_by, option.name, names.joinToString())
                    },
                    badge = names.firstOrNull()?.let { initialOf(it) },
                    onClick = { onPick(option.hex) },
                )
            }
        }
        CustomShade(start = custom, onPick = onPick)
    }
}

/**
 * Any hue on the wheel, for when the palette isn't enough. Saturation and brightness are fixed so every custom shade
 * stays as readable as the palette; the hue slider sits on a rainbow strip so it reads like a color wheel laid flat.
 */
@Composable
private fun CustomShade(start: Color?, onPick: (String) -> Unit) {
    var hue by rememberSaveable { mutableFloatStateOf(start?.let(::hueOf) ?: 0f) }
    val shade = Color.hsv(hue, CUSTOM_SATURATION, CUSTOM_VALUE)
    val hueLabel = stringResource(R.string.person_color_custom_hue)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.person_color_custom), style = MaterialTheme.typography.titleSmall)
        // The rainbow is the slider's track: the slider's own tracks are see-through, so only the thumb shows on top.
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(CircleShape)
                    .background(Brush.horizontalGradient(RAINBOW)),
            )
            Slider(
                value = hue,
                onValueChange = { hue = it },
                valueRange = 0f..MAX_HUE,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.onSurface,
                    activeTrackColor = Color.Transparent,
                    inactiveTrackColor = Color.Transparent,
                    activeTickColor = Color.Transparent,
                    inactiveTickColor = Color.Transparent,
                ),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = hueLabel },
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(28.dp).clip(CircleShape).background(shade))
            Text(
                stringResource(R.string.person_color_custom_preview),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { onPick(hexOf(shade)) }) { Text(stringResource(R.string.person_color_use)) }
        }
    }
}

private fun hueOf(color: Color): Float {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(color.toArgb(), hsv)
    return hsv[0]
}

private fun hexOf(color: Color): String = String.format(java.util.Locale.ROOT, "#%06X", color.toArgb() and RGB_MASK)

private const val MAX_HUE = 359f
private const val CUSTOM_SATURATION = 0.62f
private const val CUSTOM_VALUE = 0.9f
private const val RGB_MASK = 0xFFFFFF
private val RAINBOW = (0..6).map { Color.hsv(it * 60f % 360f, CUSTOM_SATURATION, CUSTOM_VALUE) }

/** Display names of the people other than [ownerId] who have [hex]. */
@Composable
private fun othersUsing(hex: String, ownerId: String?, owners: Map<String, List<ColorOwner>>): List<String> {
    val you = stringResource(R.string.profile_you)
    val anonymous = stringResource(R.string.person_color_anonymous)
    return owners[hex].orEmpty()
        .filter { it.id != ownerId }
        .map { o -> o.name?.trim()?.takeIf { it.isNotEmpty() } ?: if (o.isSelf) you else anonymous }
}

private fun initialOf(name: String): String = name.first().uppercase()

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
                selected -> Icon(Icons.Filled.Check, contentDescription = null, tint = mark, modifier = Modifier.size(20.dp))
                // Someone else's color: their initial, so a shared pick is never by accident.
                badge != null -> Text(badge, color = mark, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

private val SWATCH = 44.dp
private const val DARK_MARK_LUMINANCE = 0.5f

// ⏣ HW ⏣ · code · tools · homelab · ⏣ HW ⏣
