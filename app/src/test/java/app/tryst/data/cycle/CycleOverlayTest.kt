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
import app.tryst.data.repository.CycleOwner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CycleOverlayTest {

    private val a = CycleOwner(CyclePeriodEntity.OWNER_PARTNER, "a")
    private val b = CycleOwner(CyclePeriodEntity.OWNER_PROFILE, "self")

    private fun period(owner: CycleOwner, start: Long, length: Int = 5) = CyclePeriodEntity(
        id = "${owner.id}-$start",
        ownerKind = owner.kind,
        ownerId = owner.id,
        startEpochDay = start,
        endEpochDay = start + length - 1,
        endEstimated = false,
        createdAt = 0,
        updatedAt = 0,
    )

    @Test
    fun loggedPeriodsMarkEveryDayInclusive() {
        val marks = CycleOverlay.build(listOf(period(a, 10, 3)), todayEpochDay = 100, predict = false, owners = null)
        assertEquals(setOf(10L, 11L, 12L), marks.keys)
        assertTrue(marks.values.flatten().none { it.predicted })
    }

    @Test
    fun predictedRangeIsMarkedFromTodayOn() {
        // Cycles of 28: next likely day 84 + 28 = 112, range 110..114.
        val periods = listOf(0L, 28L, 56L, 84L).map { period(a, it) }
        val marks = CycleOverlay.build(periods, todayEpochDay = 112, predict = true, owners = null)
        val predicted = marks.filterValues { list -> list.any { it.predicted } }.keys
        assertEquals((112L..114L).toSet(), predicted)
    }

    @Test
    fun noPredictionWhenSwitchedOff() {
        val periods = listOf(0L, 28L, 56L, 84L).map { period(a, it) }
        val marks = CycleOverlay.build(periods, todayEpochDay = 90, predict = false, owners = null)
        assertTrue(marks.values.flatten().none { it.predicted })
    }

    @Test
    fun aLoggedDayIsNotAlsoPredictedForTheSamePerson() {
        // A period logged inside the predicted window wins over the guess.
        val periods = listOf(0L, 28L, 56L, 84L).map { period(a, it) }
        val marks = CycleOverlay.build(periods, todayEpochDay = 90, predict = true, owners = null)
        marks.values.forEach { list -> assertEquals(list.size, list.map { it.owner }.toSet().size) }
    }

    @Test
    fun ownerFilterHidesOtherPeople() {
        val periods = listOf(period(a, 10), period(b, 10))
        val marks = CycleOverlay.build(periods, todayEpochDay = 100, predict = false, owners = setOf(b))
        assertEquals(listOf(b), marks.getValue(10L).map { it.owner })
    }

    @Test
    fun twoPeopleOnTheSameDayKeepAStableOrder() {
        val periods = listOf(period(b, 10), period(a, 10))
        val marks = CycleOverlay.build(periods, todayEpochDay = 100, predict = false, owners = null)
        assertEquals(listOf(a, b), marks.getValue(10L).map { it.owner })
    }

    @Test
    fun noPicksMeansEveryone() {
        assertEquals(setOf(a, b), CycleOverlay.visibleOwners(emptySet(), setOf(a, b)))
    }

    @Test
    fun picksThatStillExistAreKept() {
        assertEquals(setOf(b), CycleOverlay.visibleOwners(setOf(CycleOverlay.key(b)), setOf(a, b)))
    }

    @Test
    fun picksThatAllWentStaleFallBackToEveryone() {
        // After a restore or "Delete all data", the stored ids can point at people who no longer exist.
        assertEquals(setOf(a, b), CycleOverlay.visibleOwners(setOf("partner:gone", "junk"), setOf(a, b)))
    }

    @Test
    fun aMixOfStaleAndLivePicksKeepsOnlyTheLiveOnes() {
        assertEquals(setOf(a), CycleOverlay.visibleOwners(setOf("partner:gone", CycleOverlay.key(a)), setOf(a, b)))
    }

    @Test
    fun keysRoundTrip() {
        assertEquals(a, CycleOverlay.ownerFromKey(CycleOverlay.key(a)))
        assertNull(CycleOverlay.ownerFromKey("nokey"))
        assertNull(CycleOverlay.ownerFromKey("kind:"))
    }
}

// ⏣ HW ⏣ · code · tools · homelab · ⏣ HW ⏣
