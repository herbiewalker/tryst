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

/** One person's derived numbers (CYC-1, 0.7.1). Null fields mean there isn't enough history yet. */
data class CycleStats(
    /** Start-to-start gaps of the recent cycles that count, newest first. */
    val cycleLengths: List<Int>,
    val usualCycle: Int?,
    val shortestCycle: Int?,
    val longestCycle: Int?,
    /** Median length of recent periods with a real end; null until [CycleDefaults.MIN_HISTORY] of them. */
    val usualPeriod: Int?,
)

/** What the Cycle screens can say about the next period. Always an estimate, never medical advice. */
sealed interface CyclePrediction {
    /** Fewer than [need] completed cycles logged. */
    data class NotEnoughData(val have: Int, val need: Int) : CyclePrediction

    /** Recent cycles vary by [spreadDays], too much for an honest guess, so none is shown. */
    data class Irregular(val spreadDays: Int) : CyclePrediction

    /**
     * The next period most likely starts on [likely], somewhere in [earliest]..[latest]. [lateByDays] is how
     * far today is past [latest] (0 when it isn't).
     */
    data class Next(val earliest: Long, val likely: Long, val latest: Long, val lateByDays: Int) : CyclePrediction
}

/**
 * Cycle stats and next-period estimates from logged period starts. Pure and stateless so it's
 * JVM-tested. A cycle is the gap between two logged starts. Only the last [LOOKBACK] count, and a gap
 * longer than [MAX_CYCLE] reads as a break in logging rather than a cycle.
 *
 * The estimate is the median cycle, shown as a range that covers the shortest and longest recent cycle
 * (at least [MIN_MARGIN] days either side). It needs [MIN_CYCLES] cycles and is withheld when they
 * differ by more than [MAX_SPREAD] days. No fertile-window or contraception estimates, on purpose.
 */
object CyclePredictor {

    const val MIN_CYCLES = 3
    const val LOOKBACK = 6
    const val MIN_CYCLE = 1
    const val MAX_CYCLE = 90
    const val MAX_SPREAD = 9
    const val MIN_MARGIN = 2

    fun cycleLengths(periods: List<CyclePeriodEntity>): List<Int> = periods
        .map { it.startEpochDay }
        .distinct()
        .sortedDescending()
        .zipWithNext { newer, older -> (newer - older).toInt() }
        .filter { it in MIN_CYCLE..MAX_CYCLE }
        .take(LOOKBACK)

    fun stats(periods: List<CyclePeriodEntity>): CycleStats {
        val cycles = cycleLengths(periods)
        val enough = cycles.size >= MIN_CYCLES
        val realPeriods = periods.count { !it.endEstimated }
        return CycleStats(
            cycleLengths = cycles,
            usualCycle = if (enough) median(cycles) else null,
            shortestCycle = if (enough) cycles.min() else null,
            longestCycle = if (enough) cycles.max() else null,
            usualPeriod = if (realPeriods >= CycleDefaults.MIN_HISTORY) CycleDefaults.usualLength(periods) else null,
        )
    }

    fun predict(periods: List<CyclePeriodEntity>, todayEpochDay: Long): CyclePrediction {
        val cycles = cycleLengths(periods)
        if (cycles.size < MIN_CYCLES) return CyclePrediction.NotEnoughData(cycles.size, MIN_CYCLES)
        val shortest = cycles.min()
        val longest = cycles.max()
        if (longest - shortest > MAX_SPREAD) return CyclePrediction.Irregular(longest - shortest)
        val usual = median(cycles)
        val last = periods.maxOf { it.startEpochDay }
        val earliest = last + minOf(shortest, usual - MIN_MARGIN)
        val latest = last + maxOf(longest, usual + MIN_MARGIN)
        val late = (todayEpochDay - latest).coerceAtLeast(0).toInt()
        return CyclePrediction.Next(earliest = earliest, likely = last + usual, latest = latest, lateByDays = late)
    }

    private fun median(values: List<Int>): Int {
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid] + 1) / 2
    }
}

// ⏣ HW ⏣ · code · tools · homelab · ⏣ HW ⏣
