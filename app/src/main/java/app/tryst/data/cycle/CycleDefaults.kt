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
import app.tryst.data.db.entity.CyclePeriodEntity

/**
 * The "most likely" period for a person, used when someone logs a start and nothing else (CYC-1).
 * Pure and stateless so it's JVM-tested. Only data someone actually entered counts as history:
 * filled-in defaults never feed back into the next guess.
 *
 * With no history it falls back to a standard pattern: 5 days, medium, heavy, medium, light, light.
 */
object CycleDefaults {

    const val STANDARD_LENGTH = 5
    const val MIN_LENGTH = 1
    const val MAX_LENGTH = 14

    /** Completed periods needed before the person's own history replaces the standard length. */
    const val MIN_HISTORY = 2

    /** How many recent periods the guess looks at. */
    const val LOOKBACK = 6

    val STANDARD_PATTERN = listOf(CycleFlow.MEDIUM, CycleFlow.HEAVY, CycleFlow.MEDIUM, CycleFlow.LIGHT, CycleFlow.LIGHT)

    /** Median length of the person's recent periods with a real (not estimated) end, else [STANDARD_LENGTH]. */
    fun usualLength(periods: List<CyclePeriodEntity>): Int {
        val lengths = periods
            .filter { !it.endEstimated }
            .sortedByDescending { it.startEpochDay }
            .take(LOOKBACK)
            .map { it.lengthDays }
        if (lengths.size < MIN_HISTORY) return STANDARD_LENGTH
        return median(lengths).coerceIn(MIN_LENGTH, MAX_LENGTH)
    }

    /**
     * The usual flow for each day of a period [length] days long. Each day takes the flow entered most
     * often on that day of the person's recent periods (ties go to the heavier flow); a day with no
     * entered data takes the standard pattern, stretched or squeezed to fit [length].
     */
    fun usualPattern(length: Int, periods: List<CyclePeriodEntity>, days: List<CycleDayEntity>): List<CycleFlow> {
        val recent = periods.sortedByDescending { it.startEpochDay }.take(LOOKBACK).associateBy { it.id }
        val byIndex: Map<Int, List<CycleFlow>> = days
            .filter { !it.estimated }
            .mapNotNull { day ->
                val period = recent[day.periodId]
                val flow = day.flowLevel
                if (period == null || flow == null) null else (day.epochDay - period.startEpochDay).toInt() to flow
            }
            .groupBy({ it.first }, { it.second })
        return (0 until length).map { i ->
            byIndex[i]?.let { mostCommon(it) } ?: standardFlowAt(i, length)
        }
    }

    /** The standard pattern mapped onto a period of [length] days. */
    fun standardFlowAt(index: Int, length: Int): CycleFlow {
        if (length <= 0) return STANDARD_PATTERN.first()
        val scaled = index * STANDARD_PATTERN.size / length
        return STANDARD_PATTERN[scaled.coerceIn(0, STANDARD_PATTERN.lastIndex)]
    }

    /**
     * A new period starting on [startEpochDay], with the usual length and flow, all marked estimated.
     * Returns the period's end day and its filled days.
     */
    fun fill(
        periodId: String,
        startEpochDay: Long,
        history: List<CyclePeriodEntity>,
        historyDays: List<CycleDayEntity>,
    ): Pair<Long, List<CycleDayEntity>> {
        val length = usualLength(history)
        val pattern = usualPattern(length, history, historyDays)
        val days = pattern.mapIndexed { i, flow ->
            CycleDayEntity(periodId = periodId, epochDay = startEpochDay + i, flow = flow.name, estimated = true)
        }
        return (startEpochDay + length - 1) to days
    }

    /**
     * Days for a period that now runs [startEpochDay]..[endEpochDay]: keeps every [existing] day still
     * inside it and fills any new day with the usual flow for its position, marked estimated.
     */
    fun refit(period: CyclePeriodEntity, existing: List<CycleDayEntity>, history: CycleHistory): List<CycleDayEntity> {
        val start = period.startEpochDay
        val length = (period.endEpochDay - start + 1).toInt().coerceAtLeast(1)
        val kept = existing.filter { it.epochDay in start..period.endEpochDay }.associateBy { it.epochDay }
        val pattern by lazy { usualPattern(length, history.periods, history.days) }
        return (0 until length).map { i ->
            val day = start + i
            kept[day] ?: CycleDayEntity(periodId = period.id, epochDay = day, flow = pattern[i].name, estimated = true)
        }
    }

    private fun median(values: List<Int>): Int {
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid] + 1) / 2
    }

    private fun mostCommon(flows: List<CycleFlow>): CycleFlow = flows.groupingBy { it }.eachCount()
        .entries
        .maxWith(compareBy<Map.Entry<CycleFlow, Int>> { it.value }.thenBy { it.key.ordinal })
        .key
}

/** One person's earlier periods and their days, the input for the "most likely" guess. */
data class CycleHistory(val periods: List<CyclePeriodEntity>, val days: List<CycleDayEntity>)

/** Where a person is right now, for the Cycle tab's card. */
sealed interface CycleStatus {
    /** Nothing logged yet. */
    data object None : CycleStatus

    /** Today is inside [period]; [dayNumber] is 1-based. */
    data class OnPeriod(val period: CyclePeriodEntity, val dayNumber: Int) : CycleStatus

    /** The latest period is over; [cycleDay] counts from its first day (1-based). */
    data class Between(val last: CyclePeriodEntity, val cycleDay: Int) : CycleStatus

    companion object {
        fun of(periods: List<CyclePeriodEntity>, todayEpochDay: Long): CycleStatus {
            val started = periods.filter { it.startEpochDay <= todayEpochDay }
            val latest = started.maxByOrNull { it.startEpochDay } ?: return None
            val dayNumber = (todayEpochDay - latest.startEpochDay + 1).toInt()
            return if (todayEpochDay <= latest.endEpochDay) OnPeriod(latest, dayNumber) else Between(latest, dayNumber)
        }
    }
}

// ⏣ HW ⏣ · code · tools · homelab · ⏣ HW ⏣
