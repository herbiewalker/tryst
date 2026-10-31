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
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

private val THUMB_HEIGHT = 56.dp
private val THUMB_WIDTH = 6.dp
private val GRIP_WIDTH = 36.dp
private val GRIP_SLOP = 16.dp
private const val LINGER_MS = 1_200L

/** What the fast scroller needs from a lazy container, so grid, mosaic and feed share one implementation. */
interface ScrollMetrics {
    val firstVisibleIndex: Int
    val visibleCount: Int
    val totalCount: Int
    val isScrolling: Boolean

    /**
     * Jumps so [index] is at the start of the viewport on the next measure. Deliberately the non-suspending
     * request, not `scrollToItem`: that takes the scroll mutex at default priority and is cancelled if the
     * list is mid-fling (its touch handling holds a higher-priority mutation), so grabbing the thumb during
     * a fling would silently do nothing.
     */
    fun jumpTo(index: Int)
}

private class GridMetrics(private val state: LazyGridState) : ScrollMetrics {
    override val firstVisibleIndex get() = state.firstVisibleItemIndex
    override val visibleCount get() = state.layoutInfo.visibleItemsInfo.size
    override val totalCount get() = state.layoutInfo.totalItemsCount
    override val isScrolling get() = state.isScrollInProgress
    override fun jumpTo(index: Int) = state.requestScrollToItem(index)
}

private class ListMetrics(private val state: LazyListState) : ScrollMetrics {
    override val firstVisibleIndex get() = state.firstVisibleItemIndex
    override val visibleCount get() = state.layoutInfo.visibleItemsInfo.size
    override val totalCount get() = state.layoutInfo.totalItemsCount
    override val isScrolling get() = state.isScrollInProgress
    override fun jumpTo(index: Int) = state.requestScrollToItem(index)
}

/**
 * State for one fast scroller: whether it is showing, where the thumb is, and an in-progress drag. The
 * gesture ([fastScrollGesture]) and the drawing ([FastScrollOverlay]) are separate so the overlay never
 * sits in the hit-test path and blocks normal scrolling at the screen edge.
 */
@Stable
class FastScrollState internal constructor(
    private val metrics: ScrollMetrics,
) {
    /** Maps a lazy-item index to the bubble text for that point in the timeline. */
    var labelAt: (Int) -> String? = { null }

    var shown by mutableStateOf(false)
    var dragging by mutableStateOf(false)
        private set
    private var dragFraction by mutableFloatStateOf(0f)

    val scrolling: Boolean get() = metrics.isScrolling

    /** Short lists don't need a scroller. */
    val worthShowing: Boolean get() = metrics.visibleCount > 0 && metrics.totalCount > metrics.visibleCount * 2

    val fraction: Float
        get() = if (dragging) {
            dragFraction
        } else {
            (metrics.firstVisibleIndex.toFloat() / max(1, metrics.totalCount - metrics.visibleCount)).coerceIn(0f, 1f)
        }

    val label: String? get() = labelAt(indexFor(fraction))

    private fun indexFor(f: Float): Int = (f * (metrics.totalCount - 1)).roundToInt().coerceIn(0, max(0, metrics.totalCount - 1))

    internal fun beginDrag(f: Float) {
        dragFraction = f
        dragging = true
    }

    internal fun dragTo(f: Float) {
        dragFraction = f.coerceIn(0f, 1f)
        metrics.jumpTo(indexFor(dragFraction))
    }

    internal fun endDrag() {
        dragging = false
    }
}

@Composable
fun rememberFastScroll(state: LazyGridState, labelAt: (Int) -> String?): FastScrollState = rememberFastScroll(remember(state) { GridMetrics(state) }, labelAt)

@Composable
fun rememberFastScroll(state: LazyListState, labelAt: (Int) -> String?): FastScrollState = rememberFastScroll(remember(state) { ListMetrics(state) }, labelAt)

@Composable
private fun rememberFastScroll(metrics: ScrollMetrics, labelAt: (Int) -> String?): FastScrollState {
    val fast = remember(metrics) { FastScrollState(metrics) }
    SideEffect { fast.labelAt = labelAt }
    // Visible while scrolling or dragging, then lingers briefly so the thumb can be grabbed.
    LaunchedEffect(fast.scrolling, fast.dragging) {
        if (fast.scrolling || fast.dragging) {
            fast.shown = true
        } else {
            delay(LINGER_MS)
            fast.shown = false
        }
    }
    return fast
}

/**
 * Grab the thumb at the right edge and drag to jump through the whole timeline. Handled on the container
 * on the Initial pass, and only when the press starts on the visible thumb, so every other touch (including
 * ones at the very edge) scrolls the list exactly as before.
 */
@Composable
fun Modifier.fastScrollGesture(fast: FastScrollState): Modifier {
    val density = LocalDensity.current
    val thumbPx = with(density) { THUMB_HEIGHT.toPx() }
    val gripPx = with(density) { GRIP_WIDTH.toPx() }
    val slopPx = with(density) { GRIP_SLOP.toPx() }
    return pointerInput(fast) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val room = size.height - thumbPx
            val top = fast.fraction * room
            val onThumb = fast.shown &&
                fast.worthShowing &&
                room > 0f &&
                down.position.x >= size.width - gripPx &&
                down.position.y in (top - slopPx)..(top + thumbPx + slopPx)
            if (!onThumb) return@awaitEachGesture

            down.consume()
            val grab = down.position.y - top
            fast.beginDrag(fast.fraction)
            try {
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (change.pressed) fast.dragTo((change.position.y - grab) / room)
                    change.consume()
                } while (change.pressed)
            } finally {
                fast.endDrag()
            }
        }
    }
}

/** The thumb and, while dragging, the month bubble. Pure drawing; takes no touches. */
@Composable
fun FastScrollOverlay(fast: FastScrollState, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = fast.shown && fast.worthShowing,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val density = LocalDensity.current
            val thumbPx = with(density) { THUMB_HEIGHT.toPx() }
            val top = (fast.fraction * (constraints.maxHeight - thumbPx)).roundToInt()
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset { IntOffset(0, top) }
                    .padding(end = 3.dp)
                    .width(THUMB_WIDTH)
                    .height(THUMB_HEIGHT)
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = if (fast.dragging) 1f else 0.7f),
                        RoundedCornerShape(THUMB_WIDTH / 2),
                    ),
            )
            if (fast.dragging) {
                fast.label?.let { text ->
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset { IntOffset(-with(density) { 20.dp.roundToPx() }, top + (thumbPx / 2).roundToInt() - with(density) { 18.dp.roundToPx() }) },
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        shadowElevation = 4.dp,
                    ) {
                        Text(text, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
                    }
                }
            }
        }
    }
}

/** "October 2026" for a photo's timestamp, in the device's locale and zone. Null when there is none. */
fun monthYearLabel(takenAt: Long?, locale: Locale = Locale.getDefault()): String? = takenAt?.let {
    YearMonth.from(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()))
        .format(DateTimeFormatter.ofPattern("LLLL yyyy", locale))
}

// ⏣ HW ⏣ · code · tools · homelab · ⏣ HW ⏣
