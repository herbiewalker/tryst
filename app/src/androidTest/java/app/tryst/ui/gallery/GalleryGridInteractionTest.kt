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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.tryst.data.gallery.GalleryPhoto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Drag-to-select and the fast scroller, exercised on a real lazy grid of [SelectablePhotoTile]s so the
 * hit-testing, the Initial-pass gesture claim and the "a plain scroll is untouched" guarantees are all
 * the production code paths, not mocks.
 */
@RunWith(AndroidJUnit4::class)
class GalleryGridInteractionTest {

    @get:Rule
    val compose = createComposeRule()

    private var selected by mutableStateOf(emptySet<String>())
    private var clicked = mutableListOf<String>()
    private lateinit var gridState: LazyGridState
    private lateinit var fastState: FastScrollState

    private fun photo(n: Int) = GalleryPhoto(
        blobId = "b$n",
        mimeType = "image/jpeg",
        takenAt = n.toLong(),
        partners = emptyList(),
        rating = null,
        favorite = false,
        source = GalleryPhoto.Source.Person(ownerKind = "partner", ownerId = "p1", personPhotoId = "pp$n"),
    )

    private fun show(count: Int, withFastScroll: Boolean = false) {
        compose.setContent {
            val photos = remember { (1..count).map(::photo) }
            val bounds = remember { TileBounds() }
            val interaction = TileInteraction(
                selectionActive = selected.isNotEmpty(),
                selectedIds = selected,
                onClick = { clicked += it },
                onSetSelected = { id, on -> selected = if (on) selected + id else selected - id },
                bounds = bounds,
            )
            gridState = rememberLazyGridState()
            val fast = rememberFastScroll(gridState) { i -> "Item $i" }
            fastState = fast
            Box(Modifier.fillMaxSize()) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    state = gridState,
                    modifier = Modifier.fillMaxSize().fastScrollGesture(fast).dragSelect(interaction, gridState),
                ) {
                    items(photos, key = { it.id }) { p ->
                        SelectablePhotoTile(
                            photo = p,
                            reqPx = 64,
                            onLoad = { _, _ -> null },
                            interaction = interaction,
                            modifier = Modifier.aspectRatio(1f).testTag("tile-${p.id}"),
                        )
                    }
                }
                if (withFastScroll) FastScrollOverlay(fast, Modifier.fillMaxSize())
            }
        }
    }

    private fun centreOf(tag: String): Offset = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.center

    // --- drag select ----------------------------------------------------------------------

    @Test
    fun longPressSelectsTheTile() {
        show(30)
        val at = centreOf("tile-b1")
        compose.onRoot().performTouchInput {
            down(at)
            advanceEventTime(700)
            up()
        }
        compose.waitForIdle()
        assertEquals(setOf("b1"), selected)
    }

    @Test
    fun longPressThenDragSelectsTheRunOfTilesUnderTheFinger() {
        show(30)
        val from = centreOf("tile-b1")
        val to = centreOf("tile-b3") // same row: b1, b2, b3
        compose.onRoot().performTouchInput {
            down(from)
            advanceEventTime(700)
            moveTo(to)
            up()
        }
        compose.waitForIdle()
        assertEquals(setOf("b1", "b2", "b3"), selected)
    }

    @Test
    fun dragStartedOnASelectedTileDeselectsTheRun() {
        show(30)
        selected = setOf("b1", "b2", "b3")
        compose.waitForIdle()
        val from = centreOf("tile-b1")
        val to = centreOf("tile-b3")
        compose.onRoot().performTouchInput {
            down(from)
            advanceEventTime(700)
            moveTo(to)
            up()
        }
        compose.waitForIdle()
        assertTrue("run should be deselected, was $selected", selected.isEmpty())
    }

    @Test
    fun aFastDragStillSelectsTilesItJumpedOver() {
        show(30)
        val from = centreOf("tile-b1")
        val to = centreOf("tile-b9") // bottom-right of a 3x3 block, one big jump
        compose.onRoot().performTouchInput {
            down(from)
            advanceEventTime(700)
            moveTo(to)
            up()
        }
        compose.waitForIdle()
        assertTrue("start tile selected", "b1" in selected)
        assertTrue("end tile selected", "b9" in selected)
        assertTrue("a tile in between selected, was $selected", "b5" in selected)
    }

    @Test
    fun aPlainScrollSelectsNothingAndStillScrolls() {
        show(300)
        compose.onRoot().performTouchInput { swipeUp() }
        compose.waitForIdle()
        assertTrue("grid should have scrolled", gridState.firstVisibleItemIndex > 0)
        assertTrue("scrolling must not select, was $selected", selected.isEmpty())
    }

    @Test
    fun aTapStillClicksTheTileAndDoesNotSelect() {
        show(30)
        val at = centreOf("tile-b2")
        compose.onRoot().performTouchInput {
            down(at)
            advanceEventTime(50)
            up()
        }
        compose.waitForIdle()
        assertEquals(listOf("b2"), clicked)
        assertTrue(selected.isEmpty())
    }

    // --- fast scroller --------------------------------------------------------------------

    @Test
    fun draggingTheThumbJumpsFarThroughTheTimelineAndNamesTheMonth() {
        show(600, withFastScroll = true)
        compose.mainClock.autoAdvance = false
        compose.onRoot().performTouchInput { swipeUp(durationMillis = 100) }
        compose.mainClock.advanceTimeBy(150) // thumb is showing; stay inside its linger window
        assertTrue("thumb should be showing after a scroll (shown=${fastState.shown} worth=${fastState.worthShowing} scrolling=${fastState.scrolling})", fastState.shown)
        val bounds = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val x = bounds.right - 8f
        val thumbPx = with(compose.density) { 56.dp.toPx() }
        val thumbCentreY = bounds.top + fastState.fraction * (bounds.height - thumbPx) + thumbPx / 2f

        compose.onRoot().performTouchInput {
            down(Offset(x, thumbCentreY)) // on the thumb
            moveTo(Offset(x, bounds.top + bounds.height / 2f))
        }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertTrue("mid-drag index=${gridState.firstVisibleItemIndex} frac=${fastState.fraction} dragging=${fastState.dragging} thumbCentreY=$thumbCentreY h=${bounds.height}", gridState.firstVisibleItemIndex > 150)
        assertTrue("thumb should be grabbed (frac=${fastState.fraction} dragging=${fastState.dragging})", fastState.dragging)
        compose.onNodeWithText("Item", substring = true).assertExists()
        compose.onRoot().performTouchInput { up() }
        compose.waitForIdle()

        assertTrue(
            "thumb drag to mid-track should land deep in the list, was ${gridState.firstVisibleItemIndex}",
            gridState.firstVisibleItemIndex > 150,
        )
        assertTrue("dragging the thumb must not select", selected.isEmpty())
    }

    @Test
    fun touchingTheRightEdgeWithoutTheThumbStillScrollsNormally() {
        show(300, withFastScroll = true)
        val bounds = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val x = bounds.right - 8f
        compose.onRoot().performTouchInput {
            swipe(Offset(x, bounds.bottom - 40f), Offset(x, bounds.top + 40f), durationMillis = 150)
        }
        compose.waitForIdle()
        assertTrue("an edge swipe away from the thumb should scroll the list", gridState.firstVisibleItemIndex > 0)
    }
}

// ⏣ HW ⏣ · code · tools · homelab · ⏣ HW ⏣
