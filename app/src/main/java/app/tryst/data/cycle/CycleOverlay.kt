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

/** One person's mark on one calendar day (CYC-1, 0.7.2). [predicted] means "next period likely starts around here". */
data class CycleMark(val owner: CycleOwner, val predicted: Boolean)

/**
 * The period overlay for the Trysts calendar. Pure so it's JVM-tested.
 *
 * Logged periods mark every day from start to end (an estimated end included, since that's the best guess
 * there is). When [predict] is on, each person's next-period range from [CyclePredictor] is marked as
 * predicted, from today on only: a range that has already passed says nothing useful on a calendar (the
 * Cycle tab says how late it is). A day logged for someone is never also a predicted day for them.
 */
object CycleOverlay {

    /** Epoch day → marks on that day, ordered the same way every time. [owners] null means everyone. */
    fun build(
        periods: List<CyclePeriodEntity>,
        todayEpochDay: Long,
        predict: Boolean,
        owners: Set<CycleOwner>?,
    ): Map<Long, List<CycleMark>> {
        val byOwner = periods
            .groupBy { CycleOwner(it.ownerKind, it.ownerId) }
            .filterKeys { owners == null || it in owners }
        val marks = mutableMapOf<Long, MutableList<CycleMark>>()
        for ((owner, list) in byOwner.entries.sortedBy { "${it.key.kind}:${it.key.id}" }) {
            val logged = list.flatMap { it.startEpochDay..it.endEpochDay }.toSet()
            logged.forEach { marks.getOrPut(it) { mutableListOf() } += CycleMark(owner, predicted = false) }
            if (predict) {
                predictedDays(list, todayEpochDay).filterNot { it in logged }
                    .forEach { marks.getOrPut(it) { mutableListOf() } += CycleMark(owner, predicted = true) }
            }
        }
        return marks
    }

    /** The next-period range from today on, or nothing when there's no prediction to show. */
    private fun predictedDays(periods: List<CyclePeriodEntity>, todayEpochDay: Long): LongRange {
        val next = CyclePredictor.predict(periods, todayEpochDay) as? CyclePrediction.Next ?: return LongRange.EMPTY
        return maxOf(next.earliest, todayEpochDay)..next.latest
    }

    /** "kind:id", the form the calendar's person filter is stored in. */
    fun key(owner: CycleOwner): String = "${owner.kind}:${owner.id}"

    fun ownerFromKey(key: String): CycleOwner? {
        val i = key.indexOf(':')
        return if (i <= 0 || i == key.lastIndex) null else CycleOwner(key.substring(0, i), key.substring(i + 1))
    }
}

// ⏣ HW ⏣ · code · tools · homelab · ⏣ HW ⏣
