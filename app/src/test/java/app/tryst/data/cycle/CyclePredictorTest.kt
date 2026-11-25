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
package app.tryst.data.cycle

import app.tryst.data.db.entity.CyclePeriodEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CyclePredictorTest {

    private fun starts(vararg days: Long, length: Int = 5, estimated: Boolean = false) = days.mapIndexed { i, d ->
        CyclePeriodEntity(
            id = "p$i",
            ownerKind = CyclePeriodEntity.OWNER_PARTNER,
            ownerId = "x",
            startEpochDay = d,
            endEpochDay = d + length - 1,
            endEstimated = estimated,
            createdAt = 0,
            updatedAt = 0,
        )
    }

    @Test
    fun cyclesAreGapsBetweenStartsNewestFirst() {
        assertEquals(listOf(30, 27, 28), CyclePredictor.cycleLengths(starts(0, 28, 55, 85)))
    }

    @Test
    fun aVeryLongGapIsABreakInLoggingNotACycle() {
        assertEquals(listOf(28), CyclePredictor.cycleLengths(starts(0, 200, 228)))
    }

    @Test
    fun onlyTheLastSixCyclesCount() {
        val many = starts(*LongArray(10) { it * 30L })
        assertEquals(6, CyclePredictor.cycleLengths(many).size)
    }

    @Test
    fun noPredictionUntilThreeCycles() {
        assertEquals(CyclePrediction.NotEnoughData(2, 3), CyclePredictor.predict(starts(0, 28, 56), 60))
        val s = CyclePredictor.stats(starts(0, 28, 56))
        assertNull(s.usualCycle)
    }

    @Test
    fun regularCyclesGiveTheMedianAndARange() {
        // Cycles 28, 30, 29: median 29, range covers 28..31 (the margin widens the top end).
        val p = CyclePredictor.predict(starts(0, 29, 59, 87), todayEpochDay = 100)
        assertEquals(CyclePrediction.Next(earliest = 87 + 27, likely = 87 + 29, latest = 87 + 31, lateByDays = 0), p)
    }

    @Test
    fun todayPastTheRangeCountsAsLate() {
        val p = CyclePredictor.predict(starts(0, 28, 56, 84), todayEpochDay = 84 + 30 + 4) as CyclePrediction.Next
        assertEquals(84L + 30, p.latest)
        assertEquals(4, p.lateByDays)
    }

    @Test
    fun irregularCyclesHideThePrediction() {
        // Cycles 22, 35, 28: spread 13.
        assertEquals(CyclePrediction.Irregular(13), CyclePredictor.predict(starts(0, 28, 63, 85), 90))
    }

    @Test
    fun estimatedEndsStillCountForCyclesButNotForPeriodLength() {
        val periods = starts(0, 28, 56, 84, estimated = true)
        val s = CyclePredictor.stats(periods)
        assertEquals(28, s.usualCycle)
        assertNull("no real ends yet", s.usualPeriod)
    }

    @Test
    fun statsReportShortestLongestAndUsualPeriod() {
        val s = CyclePredictor.stats(starts(0, 26, 56, 84, length = 4))
        assertEquals(listOf(28, 30, 26), s.cycleLengths)
        assertEquals(28, s.usualCycle)
        assertEquals(26, s.shortestCycle)
        assertEquals(30, s.longestCycle)
        assertEquals(4, s.usualPeriod)
    }
}

// ⏣ HW ⏣ · code · tools · homelab · ⏣ HW ⏣
