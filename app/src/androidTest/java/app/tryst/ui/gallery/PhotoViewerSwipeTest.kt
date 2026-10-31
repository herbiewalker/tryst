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

import androidx.compose.ui.test.center
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.createComposeRule
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
 * The viewer's pager must see a one-finger swipe on an un-zoomed photo. Regression guard: the zoom
 * detector used to consume every drag, so swiping between photos did nothing.
 */
@RunWith(AndroidJUnit4::class)
class PhotoViewerSwipeTest {

    @get:Rule
    val compose = createComposeRule()

    private val photos = (1..3).map {
        GalleryPhoto(
            blobId = "b$it",
            mimeType = "image/jpeg",
            takenAt = it.toLong(),
            partners = emptyList(),
            rating = null,
            favorite = false,
            source = GalleryPhoto.Source.Person(ownerKind = "partner", ownerId = "p1", personPhotoId = "pp$it"),
        )
    }

    private var closed = false
    private val deleted = mutableListOf<String>()

    private fun show() {
        compose.setContent {
            PhotoViewer(
                photos = photos,
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
                onDelete = { deleted += it.blobId },
            )
        }
    }

    @Test
    fun swipingLeftThenRightMovesBetweenPhotos() {
        show()
        compose.onNodeWithText("1 / 3").assertExists()

        compose.onRoot().performTouchInput { swipeLeft() }
        compose.waitForIdle()
        compose.onNodeWithText("2 / 3").assertExists()

        compose.onRoot().performTouchInput { swipeRight() }
        compose.waitForIdle()
        compose.onNodeWithText("1 / 3").assertExists()
    }

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
        compose.onNodeWithText("1 / 3").assertExists()
    }

    @Test
    fun deleteAsksForConfirmationThenDeletesTheCurrentPhoto() {
        show()
        compose.onNodeWithContentDescription("Delete").performClick()
        assertTrue("nothing deleted before confirming", deleted.isEmpty())
        compose.onNodeWithText("Delete 1 photo?").assertExists()
        compose.onNodeWithText("Delete").performClick()
        compose.waitForIdle()
        assertEquals(listOf("b1"), deleted)
    }

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
        compose.onNodeWithText("1 / 3").assertExists()
    }
}

// ⏣ HW ⏣ · code · tools · homelab · ⏣ HW ⏣
