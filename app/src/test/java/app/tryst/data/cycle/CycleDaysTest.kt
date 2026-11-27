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
import app.tryst.data.db.entity.ProfileEntity
import app.tryst.data.repository.CycleOwner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CycleDaysTest {

    private val self = CycleOwner(CyclePeriodEntity.OWNER_PROFILE, ProfileEntity.SELF_ID)
    private val a = CycleOwner(CyclePeriodEntity.OWNER_PARTNER, "a")
    private val b = CycleOwner(CyclePeriodEntity.OWNER_PARTNER, "b")

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
    fun firstDayOfPeriodIsDayOne() {
        val days = CycleDays.from(listOf(period(a, 100)))
        assertEquals(CycleDayInfo.OnPeriod(1), days.on(a, 100))
        assertEquals(CycleDayInfo.OnPeriod(5), days.on(a, 104))
    }

    @Test
    fun daysAfterPeriodCountFromItsStart() {
        val days = CycleDays.from(listOf(period(a, 100)))
        assertEquals(CycleDayInfo.Between(6), days.on(a, 105))
        assertEquals(CycleDayInfo.Between(28), days.on(a, 127))
    }

    @Test
    fun usesTheLatestPeriodStartedOnOrBeforeTheDay() {
        // Logged out of order on purpose.
        val days = CycleDays.from(listOf(period(a, 128), period(a, 100)))
        assertEquals(CycleDayInfo.Between(27), days.on(a, 126))
        assertEquals(CycleDayInfo.OnPeriod(1), days.on(a, 128))
        assertEquals(CycleDayInfo.Between(10), days.on(a, 137))
    }

    @Test
    fun nothingBeforeTheFirstPeriodOrLongAfterTheLast() {
        val days = CycleDays.from(listOf(period(a, 100)))
        assertNull(days.on(a, 99))
        assertEquals(CycleDayInfo.Between(CycleDays.MAX_CYCLE_DAY), days.on(a, 100L + CycleDays.MAX_CYCLE_DAY - 1))
        assertNull(days.on(a, 100L + CycleDays.MAX_CYCLE_DAY))
    }

    @Test
    fun nobodyHasACycleDayWithNoPeriods() {
        assertNull(CycleDays.NONE.on(a, 100))
        assertTrue(CycleDays.NONE.forTryst(listOf("a"), 100).isEmpty())
    }

    @Test
    fun trystListsTheUserFirstThenPartnersWithData() {
        val days = CycleDays.from(listOf(period(b, 98), period(self, 90), period(a, 100)))
        val result = days.forTryst(listOf("a", "b", "c"), 101)
        assertEquals(listOf(self, a, b), result.map { it.first })
        assertEquals(CycleDayInfo.Between(12), result[0].second)
        assertEquals(CycleDayInfo.OnPeriod(2), result[1].second)
        assertEquals(CycleDayInfo.OnPeriod(4), result[2].second)
    }

    @Test
    fun partnersNotInTheTrystDoNotCount() {
        val days = CycleDays.from(listOf(period(a, 100)))
        assertTrue(days.forTryst(emptyList(), 101).isEmpty())
        assertFalse(days.anyOnPeriod(listOf("b"), 101))
        assertTrue(days.anyOnPeriod(listOf("b", "a"), 101))
    }

    @Test
    fun userCountsOnSoloTrysts() {
        val days = CycleDays.from(listOf(period(self, 100)))
        assertTrue(days.anyOnPeriod(emptyList(), 102))
        assertFalse(days.anyOnPeriod(emptyList(), 106))
    }
}

// ⏣ HW ⏣ · code · tools · homelab · ⏣ HW ⏣
