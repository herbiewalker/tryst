package app.tryst.ui.insights

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.zIndex
import app.tryst.R
import app.tryst.ui.common.rememberHaptics

/**
 * A small long-press-to-drag reorderable column for a short, fixed-height list (the Insights
 * Overview tiles in edit mode). Rows must be [itemHeight] tall so the drag threshold lines up.
 * [onMove] is called as the dragged row crosses each neighbour; the caller owns the list state.
 *
 * Deliberately lightweight (no LazyColumn, no third-party reorder lib) — there are only a dozen
 * tiles, and it keeps the project dependency-free.
 */
@Composable
fun <T> ReorderableColumn(
    items: List<T>,
    key: (T) -> Any,
    itemHeight: Dp,
    spacing: Dp,
    onMove: (from: Int, to: Int) -> Unit,
    modifier: Modifier = Modifier,
    row: @Composable (item: T, dragging: Boolean, handle: Modifier) -> Unit,
) {
    val slotPx = with(LocalDensity.current) { (itemHeight + spacing).toPx() }
    var draggingKey by remember { mutableStateOf<Any?>(null) }
    var accum by remember { mutableFloatStateOf(0f) }
    val currentItems by rememberUpdatedState(items)
    val haptics = rememberHaptics()

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(spacing)) {
        items.forEach { item ->
            val k = key(item)
            val dragging = k == draggingKey
            // The lifted row scales up a touch so it reads as picked up off the list.
            val scale by animateFloatAsState(if (dragging) 1.03f else 1f, label = "dragScale")
            val handle = Modifier.pointerInput(k) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { draggingKey = k; accum = 0f; haptics.pickUp() },
                    onDragEnd = { draggingKey = null; accum = 0f },
                    onDragCancel = { draggingKey = null; accum = 0f },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        accum += dragAmount.y
                        val idx = currentItems.indexOfFirst { key(it) == k }
                        if (idx < 0) return@detectDragGesturesAfterLongPress
                        when {
                            accum > slotPx / 2 && idx < currentItems.lastIndex -> {
                                onMove(idx, idx + 1); accum -= slotPx; haptics.tick()
                            }
                            accum < -slotPx / 2 && idx > 0 -> {
                                onMove(idx, idx - 1); accum += slotPx; haptics.tick()
                            }
                        }
                    },
                )
            }
            // Long-press drag is invisible to TalkBack; expose Move up / Move down as custom actions.
            val index = items.indexOfFirst { key(it) == k }
            val moveUpLabel = stringResource(R.string.cd_move_up)
            val moveDownLabel = stringResource(R.string.cd_move_down)
            Box(
                Modifier
                    .fillMaxWidth()
                    .semantics {
                        customActions = buildList {
                            if (index > 0) {
                                add(CustomAccessibilityAction(moveUpLabel) { onMove(index, index - 1); true })
                            }
                            if (index in 0 until items.lastIndex) {
                                add(CustomAccessibilityAction(moveDownLabel) { onMove(index, index + 1); true })
                            }
                        }
                    }
                    .then(
                        if (dragging) {
                            Modifier.zIndex(1f).graphicsLayer {
                                translationY = accum
                                scaleX = scale
                                scaleY = scale
                            }
                        } else {
                            Modifier.graphicsLayer { scaleX = scale; scaleY = scale }
                        },
                    ),
            ) {
                row(item, dragging, handle)
            }
        }
    }
}
