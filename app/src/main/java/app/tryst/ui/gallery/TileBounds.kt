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

import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Where each on-screen photo tile currently is, in window coordinates, so one gesture on the scrolling
 * container can work out which tile a finger is over — in the grid, the mosaic and the feed alike.
 *
 * Keyed by a per-tile token rather than the photo id: the by-partner grouping can show the same photo
 * in two sections, and one tile leaving composition must not erase the other's bounds.
 */
class TileBounds {
    private val rects = HashMap<Any, Pair<String, Rect>>()

    fun update(token: Any, photoId: String, bounds: Rect) {
        rects[token] = photoId to bounds
    }

    fun remove(token: Any) {
        rects.remove(token)
    }

    /** The photo id under [point] (window coordinates), or null when the point is in a gap or off any tile. */
    fun idAt(point: Offset): String? = rects.values.firstOrNull { it.second.contains(point) }?.first
}

private val EDGE_ZONE = 72.dp
private const val MAX_SCROLL_PX_PER_TICK = 28f
private const val SCROLL_TICK_MS = 16L
private val HIT_STEP = 12.dp

/**
 * Long-press a tile, then drag across others to select (or, if the first one was selected, deselect) a
 * run of photos — the Google Photos gesture. Works with one finger and without lifting: the long-press
 * itself still selects the first tile, so this replaces the per-tile long-click rather than adding to it.
 *
 * Runs on the Initial pass so it sees the drag before the container's own scroll detector and can claim it
 * once the long-press fires; a plain scroll never reaches the long-press timeout, so it is untouched.
 * Dragging into the top or bottom edge zone auto-scrolls [scrollState], extending the selection as it goes.
 */
@Composable
fun Modifier.dragSelect(interaction: TileInteraction, scrollState: ScrollableState): Modifier {
    val latest = rememberUpdatedState(interaction)
    val origin = remember { floatArrayOf(0f, 0f, 0f) } // window x, window y, container height
    val density = LocalDensity.current
    val edgePx = with(density) { EDGE_ZONE.toPx() }
    val hitStepPx = with(density) { HIT_STEP.toPx() }

    return this
        .onGloballyPositioned {
            val p = it.positionInWindow()
            origin[0] = p.x
            origin[1] = p.y
            origin[2] = it.size.height.toFloat()
        }
        .pointerInput(scrollState) {
            coroutineScope {
                val scope = this
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = true, pass = PointerEventPass.Initial)
                    val cancelled = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull true
                            val moved = (change.position - down.position).getDistance() > viewConfiguration.touchSlop
                            if (event.changes.size > 1 || !change.pressed || moved) return@withTimeoutOrNull true
                        }
                        @Suppress("UNREACHABLE_CODE")
                        false
                    }
                    if (cancelled == true) return@awaitEachGesture // a tap, scroll or pinch — not ours

                    // Long-press fired: this gesture is now a selection drag.
                    fun windowOf(local: Offset) = Offset(local.x + origin[0], local.y + origin[1])
                    val visited = HashSet<String>()
                    var adding = true
                    var last = down.position

                    fun apply(local: Offset) {
                        val id = latest.value.bounds.idAt(windowOf(local)) ?: return
                        if (visited.add(id)) latest.value.onSetSelected(id, adding)
                    }

                    val first = latest.value.bounds.idAt(windowOf(down.position))
                    if (first != null) {
                        adding = first !in latest.value.selectedIds
                        visited += first
                        latest.value.onSetSelected(first, adding)
                    }

                    val scroller = scope.launch {
                        while (isActive) {
                            val y = last.y
                            val height = origin[2]
                            val push = when {
                                y < edgePx -> -(1f - y / edgePx).coerceIn(0f, 1f)
                                y > height - edgePx -> ((y - (height - edgePx)) / edgePx).coerceIn(0f, 1f)
                                else -> 0f
                            }
                            if (push != 0f) {
                                scrollState.scrollBy(push * MAX_SCROLL_PX_PER_TICK)
                                apply(last) // the content moved under a still finger
                            }
                            delay(SCROLL_TICK_MS)
                        }
                    }

                    try {
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (change.pressed) {
                                // Sample along the move so a fast drag can't skip over tiles between events.
                                val delta = change.position - last
                                val steps = max(1, (delta.getDistance() / hitStepPx).toInt())
                                for (i in 1..steps) apply(last + delta * (i / steps.toFloat()))
                                last = change.position
                            }
                            event.changes.forEach { it.consume() } // claim the drag and the lift (no tap, no scroll)
                        } while (change.pressed)
                    } finally {
                        scroller.cancel()
                    }
                }
            }
        }
}

// ⏣ HW ⏣ · code · tools · homelab · ⏣ HW ⏣
