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

import app.tryst.core.prefs.ChartStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InsightSectionsTest {

    @Test
    fun emptyOrderReturnsFullCatalog() {
        assertEquals(InsightSections.catalog.map { it.id }, InsightSections.ordered(emptyList()).map { it.id })
    }

    @Test
    fun keepsSavedOrderThenAppendsRest() {
        val saved = listOf(InsightSections.ORGASMS, InsightSections.PEOPLE)
        val result = InsightSections.ordered(saved).map { it.id }
        assertEquals(InsightSections.ORGASMS, result[0])
        assertEquals(InsightSections.PEOPLE, result[1])
        assertEquals(InsightSections.catalog.size, result.size)
        assertEquals(InsightSections.catalog.map { it.id }.toSet(), result.toSet())
    }

    @Test
    fun ignoresUnknownIds() {
        val result = InsightSections.ordered(listOf("nope", InsightSections.ACTIVITY)).map { it.id }
        assertEquals(InsightSections.ACTIVITY, result.first())
        assertEquals(InsightSections.catalog.size, result.size)
    }

    @Test
    fun timeSeriesSectionsNeverOfferDonut() {
        for (id in listOf(InsightSections.ACTIVITY, InsightSections.SATISFACTION)) {
            val section = InsightSections.catalog.first { it.id == id }
            assertEquals(listOf(ChartStyle.BARS, ChartStyle.LINE), section.styles)
        }
    }

    @Test
    fun breakdownSectionsNeverOfferLine() {
        val ids = listOf(
            InsightSections.PEOPLE,
            InsightSections.ACTS_POSITIONS,
            InsightSections.VIBE,
            InsightSections.INITIATOR,
            InsightSections.DETAILS,
        )
        for (id in ids) {
            val section = InsightSections.catalog.first { it.id == id }
            assertEquals(listOf(ChartStyle.BARS, ChartStyle.DONUT), section.styles)
        }
    }

    @Test
    fun orgasmsOffersEveryStyle() {
        val section = InsightSections.catalog.first { it.id == InsightSections.ORGASMS }
        assertEquals(ChartStyle.entries.toList(), section.styles)
    }

    @Test
    fun savedStyleTheSectionCannotDrawFallsBackToBars() {
        val activity = InsightSections.catalog.first { it.id == InsightSections.ACTIVITY }
        assertEquals(ChartStyle.BARS, activity.resolve(ChartStyle.DONUT))
        assertEquals(ChartStyle.LINE, activity.resolve(ChartStyle.LINE))
        assertEquals(ChartStyle.BARS, activity.resolve(null))
        val people = InsightSections.catalog.first { it.id == InsightSections.PEOPLE }
        assertEquals(ChartStyle.BARS, people.resolve(ChartStyle.LINE))
        assertEquals(ChartStyle.DONUT, people.resolve(ChartStyle.DONUT))
    }

    @Test
    fun barFractionScalesWithCount() {
        assertEquals(1f, barFraction(10, 10), 0f)
        assertEquals(0.5f, barFraction(5, 10), 0f)
        assertEquals(0f, barFraction(0, 10), 0f)
        assertEquals(0f, barFraction(3, 0), 0f)
        assertEquals(1f, barFraction(12, 10), 0f)
    }

    @Test
    fun idsAreUnique() {
        val ids = InsightSections.catalog.map { it.id }
        assertTrue(ids.size == ids.toSet().size)
    }
}

// ⏣ HW ⏣ · code · tools · homelab · ⏣ HW ⏣
