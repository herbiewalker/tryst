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

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.center
import androidx.compose.ui.test.click
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.width
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.tryst.core.prefs.CaptionEntryPoint
import app.tryst.data.gallery.GalleryPhoto
import app.tryst.data.media.PhotoMeta
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Gesture and action tests for the full-screen photo viewer. The headline regression guard: a
 * one-finger swipe on an un-zoomed photo must reach the pager (the zoom detector used to consume every
 * drag, so swiping between photos did nothing). The photo list is state-backed so a delete shrinks it
 * the way the real gallery does.
 */
@RunWith(AndroidJUnit4::class)
class PhotoViewerTest {

    @get:Rule
    val compose = createComposeRule()

    private val photos = mutableStateOf(
        (1..3).map {
            GalleryPhoto(
                blobId = "b$it",
                mimeType = "image/jpeg",
                takenAt = it.toLong(),
                partners = emptyList(),
                rating = null,
                favorite = false,
                source = GalleryPhoto.Source.Person(ownerKind = "partner", ownerId = "p1", personPhotoId = "pp$it"),
            )
        },
    )

    private var closed = false
    private val deleted = mutableListOf<String>()

    private fun show() {
        compose.setContent {
            PhotoViewer(
                photos = photos.value,
                initialIndex = 0,
                onClose = { closed = true },
                onOpenEncounter = {},
                onLoad = { _, _ -> null },
                onLoadMeta = { PhotoMeta() },
                onToggleFavorite = {},
                onSetAvatar = { _, _ -> },
                assignablePeople = emptyList(),
                onAddToPerson = { _, _, _ -> },
                slideshowIntervalSeconds = 5,
                slideshowShuffle = false,
                captionEntryPoint = CaptionEntryPoint.INFO_PANEL,
                onSetCaption = { _, _ -> },
                bustKey = { it },
                onRotate = { _, _ -> },
                onCrop = { _, _ -> },
                onDelete = { photo ->
                    deleted += photo.blobId
                    photos.value = photos.value.filterNot { it.blobId == photo.blobId }
                },
            )
        }
    }

    private fun counter(text: String) = compose.onNodeWithText(text)

    // --- paging ---------------------------------------------------------------------------

    @Test
    fun swipingLeftThenRightMovesBetweenPhotos() {
        show()
        counter("1 / 3").assertExists()

        compose.onRoot().performTouchInput { swipeLeft() }
        compose.waitForIdle()
        counter("2 / 3").assertExists()

        compose.onRoot().performTouchInput { swipeRight() }
        compose.waitForIdle()
        counter("1 / 3").assertExists()
    }

    @Test
    fun swipingPastTheLastPhotoStaysOnTheLastPhoto() {
        show()
        repeat(4) {
            compose.onRoot().performTouchInput { swipeLeft() }
            compose.waitForIdle()
        }
        counter("3 / 3").assertExists()
    }

    // --- zoom -----------------------------------------------------------------------------

    @Test
    fun panningAZoomedPhotoDoesNotChangePage() {
        show()
        compose.onRoot().performTouchInput { doubleClick() }
        compose.waitForIdle()

        // A short drag on the 2x photo pans it; the page must not change.
        compose.onRoot().performTouchInput {
            swipe(start = center, end = center.copy(x = center.x - width * 0.15f))
        }
        compose.waitForIdle()
        counter("1 / 3").assertExists()
    }

    @Test
    fun swipingAtTheEdgeOfAZoomedPhotoHandsOverToThePager() {
        show()
        compose.onRoot().performTouchInput { doubleClick() }
        compose.waitForIdle()

        // The first swipe pans the zoomed photo to its edge; once it can't move further a swipe must fall
        // through to the pager instead of being swallowed. (Zoom resets on the new page, so stop at the
        // first page change rather than swiping a fixed number of times.)
        var swipes = 0
        while (swipes < 4 && compose.onAllNodesWithText("1 / 3").fetchSemanticsNodes().isNotEmpty()) {
            compose.onRoot().performTouchInput { swipeLeft() }
            compose.waitForIdle()
            swipes++
        }
        counter("1 / 3").assertDoesNotExist()
        assertTrue("should take more than one swipe: the first only pans", swipes > 1)
    }

    @Test
    fun doubleTapTwiceReturnsToFitAndSwipingPagesAgain() {
        show()
        compose.onRoot().performTouchInput { doubleClick() }
        compose.waitForIdle()
        compose.onRoot().performTouchInput { doubleClick() }
        compose.waitForIdle()

        compose.onRoot().performTouchInput { swipeLeft() }
        compose.waitForIdle()
        counter("2 / 3").assertExists()
    }

    // --- dismiss --------------------------------------------------------------------------

    @Test
    fun draggingDownFarEnoughClosesTheViewer() {
        show()
        compose.onRoot().performTouchInput {
            swipe(start = center, end = center.copy(y = center.y + height * 0.4f))
        }
        compose.waitForIdle()
        assertTrue("viewer should close", closed)
    }

    @Test
    fun shortDownDragSnapsBackAndStaysOpen() {
        show()
        compose.onRoot().performTouchInput {
            swipe(start = center, end = center.copy(y = center.y + 30f))
        }
        compose.waitForIdle()
        assertFalse("a small drag must not close", closed)
        counter("1 / 3").assertExists()
    }

    @Test
    fun draggingDownOnAZoomedPhotoDoesNotClose() {
        show()
        compose.onRoot().performTouchInput { doubleClick() }
        compose.waitForIdle()
        compose.onRoot().performTouchInput {
            swipe(start = center, end = center.copy(y = center.y + height * 0.4f))
        }
        compose.waitForIdle()
        assertFalse("a zoomed pan must not dismiss", closed)
    }

    // --- chrome ---------------------------------------------------------------------------

    @Test
    fun tappingThePhotoTogglesTheChrome() {
        show()
        counter("1 / 3").assertExists()

        compose.onRoot().performTouchInput { click(center) }
        // onTap waits out the double-tap window before firing.
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        counter("1 / 3").assertDoesNotExist()

        compose.onRoot().performTouchInput { click(center) }
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        counter("1 / 3").assertExists()
    }

    // --- delete ---------------------------------------------------------------------------

    @Test
    fun deleteRemovesTheCurrentPhotoWithoutAConfirmDialog() {
        show()
        compose.onNodeWithContentDescription("Delete").performClick()
        compose.waitForIdle()
        assertEquals(listOf("b1"), deleted)
        counter("1 / 2").assertExists()
    }

    @Test
    fun deletingTheLastPhotoInTheSetLandsOnThePreviousOne() {
        show()
        repeat(2) {
            compose.onRoot().performTouchInput { swipeLeft() }
            compose.waitForIdle()
        }
        counter("3 / 3").assertExists()

        compose.onNodeWithContentDescription("Delete").performClick()
        compose.waitForIdle()

        assertEquals(listOf("b3"), deleted)
        counter("2 / 2").assertExists()
    }

    // --- overflow menu --------------------------------------------------------------------

    @Test
    fun overflowMenuDisablesActionsThatDoNotApply() {
        show()
        compose.onNodeWithContentDescription("More…").performClick()
        // A portrait with no partners, no assignable people and no owning tryst.
        compose.onNodeWithText("Add to person’s photos").assertIsNotEnabled()
        compose.onNodeWithText("Set as partner photo").assertIsNotEnabled()
        compose.onNodeWithText("Open tryst").assertIsNotEnabled()
    }
}

// ⏣ HW ⏣ · code · tools · homelab · ⏣ HW ⏣
