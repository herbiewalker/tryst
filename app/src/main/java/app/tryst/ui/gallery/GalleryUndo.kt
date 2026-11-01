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
package app.tryst.ui.gallery

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.tryst.R

/** How long a deleted photo stays recoverable before it is really removed. */
internal const val UNDO_WINDOW_MS = 6_000L

/**
 * A snackbar-style "Photo deleted · Undo" bar. A plain composable rather than a Scaffold snackbar so it
 * can sit above the full-screen viewer as well as the gallery grid. Shows only while [count] > 0.
 */
@Composable
fun UndoDeleteBar(count: Int, onUndo: () -> Unit, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = count > 0,
        modifier = modifier,
        enter = fadeIn() + slideInVertically { it },
        exit = fadeOut() + slideOutVertically { it },
    ) {
        Surface(
            modifier = Modifier.navigationBarsPadding().padding(16.dp).widthIn(max = 480.dp),
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.inverseSurface,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            tonalElevation = 6.dp,
        ) {
            Row(
                modifier = Modifier.padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // `count` is 0 while the exit animation runs; coerce so the plural never reads "0 photos".
                val shown = count.coerceAtLeast(1)
                Text(
                    text = pluralStringResource(R.plurals.gallery_deleted_undo, shown, shown),
                    modifier = Modifier.padding(end = 8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
                TextButton(onClick = onUndo) { Text(stringResource(R.string.action_undo)) }
            }
        }
    }
}

// ⏣ HW ⏣ · code · tools · homelab · ⏣ HW ⏣
