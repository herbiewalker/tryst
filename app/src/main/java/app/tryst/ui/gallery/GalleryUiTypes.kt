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

import androidx.compose.runtime.Immutable

/**
 * The tap/long-press behaviour shared by every photo tile (grid, feed, mosaic). When [selectionActive],
 * a tap toggles selection; otherwise it opens the viewer. A long-press always toggles selection (entering
 * selection mode on the first one). See [GalleryViewModel] for the backing state (GAL-4).
 */
@Immutable
data class TileInteraction(
    val selectionActive: Boolean,
    val selectedIds: Set<String>,
    val onClick: (String) -> Unit,
    /** Sets one tile's selection (long-press-and-drag selects or deselects a run; a long-press is the first tile). */
    val onSetSelected: (id: String, selected: Boolean) -> Unit,
    /** Where the on-screen tiles are, so the container's drag-select gesture can hit-test them. */
    val bounds: TileBounds,
)

/** A person with an avatar — a partner or the self profile — as a browsable gallery item (GAL-1a). */
data class GalleryPerson(val id: String, val photoMediaId: String, val name: String, val isSelf: Boolean)

// ⏣ HW ⏣ · code · tools · homelab · ⏣ HW ⏣
