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

import app.tryst.data.db.entity.CycleDayEntity
import app.tryst.data.db.entity.CycleFlow
import app.tryst.data.db.entity.CycleFlow.HEAVY
import app.tryst.data.db.entity.CycleFlow.LIGHT
import app.tryst.data.db.entity.CycleFlow.MEDIUM
import app.tryst.data.db.entity.CycleFlow.SPOTTING
import app.tryst.data.db.entity.CyclePeriodEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CycleDefaultsTest {

    private fun period(id: String, start: Long, length: Int, estimatedEnd: Boolean = false) = CyclePeriodEntity(
        id = id,
        ownerKind = CyclePeriodEntity.OWNER_PROFILE,
        ownerId = "self",
        startEpochDay = start,
        endEpochDay = start + length - 1,
        endEstimated = estimatedEnd,
        createdAt = 0,
        updatedAt = 0,
    )

    private fun days(p: CyclePeriodEntity, flows: List<CycleFlow>, estimated: Boolean = false) = flows.mapIndexed { i, f ->
        CycleDayEntity(periodId = p.id, epochDay = p.startEpochDay + i, flow = f.name, estimated = estimated)
    }

    @Test
    fun noHistoryUsesTheStandardPeriod() {
        val (end, filled) = CycleDefaults.fill("new", 100, emptyList(), emptyList())
        assertEquals(104L, end)
        assertEquals(CycleDefaults.STANDARD_PATTERN.map { it.name }, filled.map { it.flow })
        assertTrue("defaults are marked estimated", filled.all { it.estimated })
        assertEquals((100L..104L).toList(), filled.map { it.epochDay })
    }

    @Test
    fun oneCompletedPeriodIsNotEnoughHistory() {
        assertEquals(CycleDefaults.STANDARD_LENGTH, CycleDefaults.usualLength(listOf(period("a", 0, 7))))
    }

    @Test
    fun usualLengthIsTheMedianOfRealEnds() {
        val history = listOf(period("a", 0, 4), period("b", 30, 6), period("c", 60, 4))
        assertEquals(4, CycleDefaults.usualLength(history))
    }

    @Test
    fun estimatedEndsDoNotCountAsHistory() {
        val history = listOf(period("a", 0, 3), period("b", 30, 9, estimatedEnd = true), period("c", 60, 9, estimatedEnd = true))
        assertEquals(CycleDefaults.STANDARD_LENGTH, CycleDefaults.usualLength(history))
    }

    @Test
    fun usualPatternFollowsEnteredFlowsAndIgnoresEstimates() {
        val a = period("a", 0, 3)
        val b = period("b", 30, 3)
        val history = listOf(a, b)
        val entered = days(a, listOf(HEAVY, HEAVY, SPOTTING)) + days(b, listOf(HEAVY, MEDIUM, SPOTTING))
        val estimatedNoise = days(period("x", 60, 3), listOf(LIGHT, LIGHT, LIGHT), estimated = true)
        val pattern = CycleDefaults.usualPattern(3, history, entered + estimatedNoise)
        // Day 2 is a HEAVY/MEDIUM tie: the heavier flow wins.
        assertEquals(listOf(HEAVY, HEAVY, SPOTTING), pattern)
    }

    @Test
    fun daysWithoutHistoryFallBackToTheStretchedStandard() {
        val a = period("a", 0, 1)
        val pattern = CycleDefaults.usualPattern(10, listOf(a), days(a, listOf(SPOTTING)))
        assertEquals(SPOTTING, pattern[0])
        assertEquals(10, pattern.size)
        assertEquals(LIGHT, pattern.last())
    }

    @Test
    fun refitKeepsEnteredDaysAndFillsNewOnes() {
        val p = period("p", 10, 3)
        val existing = days(p, listOf(HEAVY, HEAVY, HEAVY))
        val refit = CycleDefaults.refit(period("p", 9, 5), existing, CycleHistory(emptyList(), emptyList()))
        assertEquals((9L..13L).toList(), refit.map { it.epochDay })
        // The three entered days survive untouched.
        assertEquals(existing, refit.filter { !it.estimated })
        assertEquals(listOf(9L, 13L), refit.filter { it.estimated }.map { it.epochDay })
    }

    @Test
    fun refitDropsDaysOutsideTheNewRange() {
        val p = period("p", 10, 5)
        val refit = CycleDefaults.refit(period("p", 10, 2), days(p, listOf(HEAVY, HEAVY, HEAVY, HEAVY, HEAVY)), CycleHistory(emptyList(), emptyList()))
        assertEquals(listOf(10L, 11L), refit.map { it.epochDay })
    }

    @Test
    fun statusOnPeriodBetweenAndNone() {
        val p = period("p", 100, 5)
        assertEquals(CycleStatus.None, CycleStatus.of(emptyList(), 100))
        assertEquals(CycleStatus.OnPeriod(p, 3), CycleStatus.of(listOf(p), 102))
        assertEquals(CycleStatus.Between(p, 10), CycleStatus.of(listOf(p), 109))
        // A period logged in the future doesn't count yet.
        assertEquals(CycleStatus.None, CycleStatus.of(listOf(p), 99))
    }

    @Test
    fun unknownFlowNameReadsAsNotSet() {
        assertEquals(null, CycleFlow.fromName("GUSHING"))
        assertEquals(MEDIUM, CycleFlow.fromName("MEDIUM"))
    }
}

// ⏣ HW ⏣ · code · tools · homelab · ⏣ HW ⏣
