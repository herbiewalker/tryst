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
package app.tryst.data.stats

import app.tryst.data.db.entity.CycleDayEntity
import app.tryst.data.db.entity.CycleFlow
import app.tryst.data.db.entity.CyclePeriodEntity
import app.tryst.data.db.entity.CycleSymptomLogEntity
import app.tryst.data.db.entity.EncounterEntity
import app.tryst.data.db.entity.PartnerEntity
import app.tryst.data.db.entity.ProfileEntity
import app.tryst.data.db.relation.EncounterWithDetails
import app.tryst.data.filter.DateRange
import app.tryst.data.repository.CycleOwner
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CycleStatsEngineTest {

    private val zone = ZoneOffset.UTC
    private val self = CycleOwner(CyclePeriodEntity.OWNER_PROFILE, ProfileEntity.SELF_ID)
    private val a = CycleOwner(CyclePeriodEntity.OWNER_PARTNER, "a")
    private val jan1 = LocalDate.of(2026, 1, 1).toEpochDay()

    private fun period(owner: CycleOwner, start: Long, length: Int = 5) = CyclePeriodEntity(
        id = "${owner.id}-$start",
        ownerKind = owner.kind,
        ownerId = owner.id,
        startEpochDay = start,
        endEpochDay = start + length - 1,
        createdAt = 0,
        updatedAt = 0,
    )

    private fun tryst(day: Long, vararg partnerIds: String) = EncounterWithDetails(
        encounter = EncounterEntity(
            id = "e$day${partnerIds.joinToString()}",
            startAt = LocalDate.ofEpochDay(day).atTime(12, 0).toInstant(zone).toEpochMilli(),
            createdAt = 0,
            updatedAt = 0,
        ),
        partners = partnerIds.map {
            PartnerEntity(it, it, isAnonymous = false, color = null, note = null, archivedAt = null, createdAt = 0, updatedAt = 0)
        },
        positions = emptyList(),
        tags = emptyList(),
        media = emptyList(),
        location = null,
    )

    private fun compute(
        owner: CycleOwner,
        periods: List<CyclePeriodEntity>,
        days: List<CycleDayEntity> = emptyList(),
        logs: List<CycleSymptomLogEntity> = emptyList(),
        labels: Map<String, String> = emptyMap(),
        encounters: List<EncounterWithDetails> = emptyList(),
        scope: DateRange? = null,
    ) = CycleStatsEngine.compute(owner, periods, days, logs, labels, encounters, scope, zone)

    @Test
    fun cycleLengthsRunStartToStartAndSkipLongGaps() {
        val periods = listOf(period(a, jan1 + 28), period(a, jan1), period(a, jan1 + 58), period(a, jan1 + 200))
        val stats = compute(a, periods)
        assertEquals(listOf(28, 30), stats.cycleLengths.map { it.count })
        assertEquals(listOf("Jan 1", "Jan 29"), stats.cycleLengths.map { it.label })
        assertEquals(2026..2026, stats.cycleYears)
        assertEquals(4, stats.periodLengths.size)
    }

    @Test
    fun onlyThatPersonsPeriodsCount() {
        val stats = compute(a, listOf(period(a, jan1), period(self, jan1 + 3), period(a, jan1 + 27)))
        assertEquals(listOf(27), stats.cycleLengths.map { it.count })
    }

    @Test
    fun trendsKeepTheNewestBars() {
        val periods = (0 until 20).map { period(a, jan1 + it * 28L) }
        val stats = compute(a, periods)
        assertEquals(CycleStatsEngine.MAX_BARS, stats.periodLengths.size)
        assertEquals(CycleStatsEngine.MAX_BARS, stats.cycleLengths.size)
        // 20 periods 28 days apart from Jan 2026: the newest 12 start in 2026 and 2027.
        assertEquals(2026..2027, stats.periodYears)
    }

    @Test
    fun scopeKeepsPeriodsStartingInsideButMeasuresToTheNextStart() {
        val periods = listOf(period(a, jan1 - 30), period(a, jan1 + 10), period(a, jan1 + 40))
        val scope = DateRange(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31))
        val stats = compute(a, periods, scope = scope)
        assertEquals(1, stats.periodLengths.size)
        assertEquals(2026..2026, stats.periodYears)
        assertEquals(listOf(30), stats.cycleLengths.map { it.count })
    }

    @Test
    fun flowMixIsInFlowOrderAndSymptomsMostFirst() {
        val p = period(a, jan1)
        val days = listOf(
            CycleDayEntity(p.id, jan1, CycleFlow.HEAVY.name),
            CycleDayEntity(p.id, jan1 + 1, CycleFlow.LIGHT.name),
            CycleDayEntity(p.id, jan1 + 2, CycleFlow.HEAVY.name),
        )
        val logs = listOf(
            CycleSymptomLogEntity(p.id, jan1, "s1", 1),
            CycleSymptomLogEntity(p.id, jan1, "s2", 2),
            CycleSymptomLogEntity(p.id, jan1 + 1, "s2", 3),
        )
        val stats = compute(a, listOf(p), days, logs, mapOf("s1" to "Cramps", "s2" to "Headache"))
        assertEquals(listOf(CycleFlow.LIGHT to 1, CycleFlow.HEAVY to 2), stats.flowMix)
        assertEquals(listOf(Tally("Headache", 2), Tally("Cramps", 1)), stats.symptoms)
    }

    @Test
    fun partnerTrystsCountOnlyTrystsWithThem() {
        val encounters = listOf(tryst(jan1 + 1, "a"), tryst(jan1 + 10, "a"), tryst(jan1 + 2, "b"), tryst(jan1 + 2))
        val stats = compute(a, listOf(period(a, jan1)), encounters = encounters)
        assertEquals(1, stats.trystsOnPeriod)
        assertEquals(1, stats.trystsNotOnPeriod)
    }

    @Test
    fun userTrystsCountEveryTryst() {
        val encounters = listOf(tryst(jan1 + 1, "a"), tryst(jan1 + 2), tryst(jan1 + 20, "b"))
        val stats = compute(self, listOf(period(self, jan1)), encounters = encounters)
        assertEquals(2, stats.trystsOnPeriod)
        assertEquals(1, stats.trystsNotOnPeriod)
    }

    @Test
    fun noPeriodsMeansNoData() {
        assertFalse(compute(a, emptyList()).hasData)
        assertTrue(compute(a, listOf(period(a, jan1))).hasData)
    }
}

// ⏣ HW ⏣ · code · tools · homelab · ⏣ HW ⏣
