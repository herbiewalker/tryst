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

import app.tryst.data.cycle.CycleDayInfo
import app.tryst.data.cycle.CycleDays
import app.tryst.data.db.entity.CycleDayEntity
import app.tryst.data.db.entity.CycleFlow
import app.tryst.data.db.entity.CyclePeriodEntity
import app.tryst.data.db.entity.CycleSymptomLogEntity
import app.tryst.data.db.relation.EncounterWithDetails
import app.tryst.data.filter.DateRange
import app.tryst.data.repository.CycleOwner
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** One person's figures for the Insights Cycle section (CYC-1, 0.7.6, D-75). */
data class CycleStats(
    /** Days from each period's start to the next one's, oldest first, newest [CycleStatsEngine.MAX_BARS]. */
    val cycleLengths: List<Bucket> = emptyList(),
    /** Each period's length in days, oldest first, newest [CycleStatsEngine.MAX_BARS]. */
    val periodLengths: List<Bucket> = emptyList(),
    /** Logged days per flow level, lightest first; levels never logged are left out. */
    val flowMix: List<Pair<CycleFlow, Int>> = emptyList(),
    /** How many period days each symptom was logged on, most first. */
    val symptoms: List<Tally> = emptyList(),
    /** Trysts with this person (every tryst, for the user) that fell on one of their periods. */
    val trystsOnPeriod: Int = 0,
    val trystsNotOnPeriod: Int = 0,
) {
    val hasData: Boolean
        get() = cycleLengths.isNotEmpty() ||
            periodLengths.isNotEmpty() ||
            flowMix.isNotEmpty() ||
            symptoms.isNotEmpty() ||
            trystsOnPeriod + trystsNotOnPeriod > 0

    companion object {
        val EMPTY = CycleStats()
    }
}

/**
 * Builds [CycleStats] for one person. Pure so it's JVM-tested, like [InsightsEngine].
 *
 * With a [scope], a period counts when it starts inside the window, its days and symptoms come along
 * with it, and a tryst counts when it happened inside the window. A cycle is measured from one start to
 * the next even when the next falls outside the window, so the last cycle in a year still has a length.
 * Gaps longer than [CycleDays.MAX_CYCLE_DAY] days are left out: they mean periods weren't logged.
 */
object CycleStatsEngine {

    /** Trend charts show at most this many of the newest cycles or periods, so the bars stay readable. */
    const val MAX_BARS = 12

    private val MONTH = DateTimeFormatter.ofPattern("MMM ''yy")

    @Suppress("LongParameterList")
    fun compute(
        owner: CycleOwner,
        periods: List<CyclePeriodEntity>,
        days: List<CycleDayEntity>,
        symptomLogs: List<CycleSymptomLogEntity>,
        symptomLabels: Map<String, String>,
        encounters: List<EncounterWithDetails>,
        scope: DateRange? = null,
        zone: ZoneId = ZoneId.systemDefault(),
    ): CycleStats {
        val own = periods.filter { it.ownerKind == owner.kind && it.ownerId == owner.id }.sortedBy { it.startEpochDay }
        fun inScope(epochDay: Long) = scope == null || LocalDate.ofEpochDay(epochDay) in scope
        val scoped = own.filter { inScope(it.startEpochDay) }
        val scopedIds = scoped.mapTo(HashSet()) { it.id }

        val cycleLengths = own.zipWithNext()
            .filter { (a, _) -> a.id in scopedIds }
            .map { (a, b) -> a to (b.startEpochDay - a.startEpochDay).toInt() }
            .filter { (_, length) -> length in 1..CycleDays.MAX_CYCLE_DAY }
            .takeLast(MAX_BARS)
            .map { (a, length) -> Bucket(label(a), length) }

        val periodLengths = scoped.takeLast(MAX_BARS).map { Bucket(label(it), it.lengthDays) }

        val flowCounts = days.filter { it.periodId in scopedIds }
            .mapNotNull { it.flowLevel }
            .groupingBy { it }.eachCount()
        val flowMix = CycleFlow.entries.mapNotNull { flow -> flowCounts[flow]?.let { flow to it } }

        val symptoms = symptomLogs.filter { it.periodId in scopedIds }
            .groupingBy { it.symptomId }.eachCount()
            .mapNotNull { (id, count) -> symptomLabels[id]?.let { Tally(it, count) } }
            .sortedWith(compareByDescending<Tally> { it.count }.thenBy { it.label.lowercase() })

        val cycle = CycleDays.from(own)
        val trystDays = encounters
            .filter { e -> owner.isSelf || e.partners.any { it.id == owner.id } }
            .map { Instant.ofEpochMilli(it.encounter.startAt).atZone(zone).toLocalDate().toEpochDay() }
            .filter(::inScope)
        val on = trystDays.count { cycle.on(owner, it) is CycleDayInfo.OnPeriod }

        return CycleStats(cycleLengths, periodLengths, flowMix, symptoms, on, trystDays.size - on)
    }

    private fun label(period: CyclePeriodEntity): String = LocalDate.ofEpochDay(period.startEpochDay).format(MONTH)
}

// ⏣ HW ⏣ · code · tools · homelab · ⏣ HW ⏣
