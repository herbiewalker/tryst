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
package app.tryst.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// Softer, larger radii for a modern, friendly feel — cards and sheets read as rounded tiles.
val Shapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/**
 * Deliberately tighter than [Shapes.extraSmall] — dense photo grids (Gallery, the photo viewer's
 * filmstrip) want tiles that read as one continuous mosaic when packed edge-to-edge; the standard
 * scale's rounding would look noticeably gappy at that density. Named here (rather than a bare
 * `RoundedCornerShape(4.dp)` literal repeated per call site) so the one intentional exception to
 * the scale has a single source of truth instead of several.
 */
val GridTileShape = RoundedCornerShape(4.dp)

// ⏣ HW ⏣ · code · tools · homelab · ⏣ HW ⏣
