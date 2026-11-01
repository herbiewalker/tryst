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

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The delete-undo bar: hidden at zero, plural wording, and Undo reaches the caller. */
@RunWith(AndroidJUnit4::class)
class UndoDeleteBarTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun showsCountAndUndoFiresCallback() {
        val count = mutableIntStateOf(2)
        var undone = 0
        compose.setContent { UndoDeleteBar(count = count.intValue, onUndo = { undone++ }) }
        compose.onNodeWithText("2 photos deleted").assertExists()
        compose.onNodeWithText("Undo").performClick()
        assertEquals(1, undone)
    }

    @Test
    fun singularWording() {
        compose.setContent { UndoDeleteBar(count = 1, onUndo = {}) }
        compose.onNodeWithText("1 photo deleted").assertExists()
    }

    @Test
    fun absentWhenNothingIsPending() {
        compose.setContent { UndoDeleteBar(count = 0, onUndo = {}) }
        compose.onNodeWithText("Undo").assertDoesNotExist()
    }
}

// ⏣ HW ⏣ · code · tools · homelab · ⏣ HW ⏣
