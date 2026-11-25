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
package app.tryst.ui.insights

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.tryst.data.stats.Bucket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Bar heights follow the counts (issue #9: every bar used to fill the full height). */
@RunWith(AndroidJUnit4::class)
class VerticalBarChartTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun barHeightsAreProportionalToCounts() {
        compose.mainClock.autoAdvance = true
        compose.setContent {
            VerticalBarChart(listOf(Bucket("A", 10), Bucket("B", 5), Bucket("C", 2)))
        }
        compose.waitForIdle()
        val heights = compose.onAllNodesWithTag(BAR_TEST_TAG).fetchSemanticsNodes().map { it.size.height }
        assertEquals(3, heights.size)
        val (a, b, c) = heights
        assertTrue("tallest bar should be tallest: $heights", a > b && b > c)
        // B is half of A; allow a few px of rounding and the label row.
        assertEquals(a / 2f, b.toFloat(), a * 0.08f)
    }
}

// ⏣ HW ⏣ · code · tools · homelab · ⏣ HW ⏣
