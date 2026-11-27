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

/**
 * Where one person was in their cycle on one day (CYC-1, 0.7.5, D-74). Day 1 is the first day of the
 * period, the way cycle days are usually counted. No medical phase names, on purpose.
 */
sealed interface CycleDayInfo {
    val day: Int

    /** On a logged period that day; [day] counts from the period's first day. */
    data class OnPeriod(override val day: Int) : CycleDayInfo

    /** Between periods; [day] counts from the start of the most recent period. */
    data class Between(override val day: Int) : CycleDayInfo
}

/**
 * Cycle days for trysts, read-side only. Pure so it's JVM-tested.
 *
 * A tryst always includes the user, plus its partners. Each of them who has periods logged can have a
 * cycle day. Before someone's first logged period, or more than [MAX_CYCLE_DAY] days after their latest
 * one, there's nothing honest to say, so they get none: a gap that long means periods weren't logged,
 * not a 200-day cycle.
 */
class CycleDays private constructor(
    private val periodsByOwner: Map<CycleOwner, List<CyclePeriodEntity>>,
) {

    /** [owner]'s cycle day on [epochDay], or null when there's none to show. */
    fun on(owner: CycleOwner, epochDay: Long): CycleDayInfo? {
        val latest = periodsByOwner[owner].orEmpty()
            .lastOrNull { it.startEpochDay <= epochDay } ?: return null
        val day = (epochDay - latest.startEpochDay + 1).toInt()
        return when {
            epochDay <= latest.endEpochDay -> CycleDayInfo.OnPeriod(day)
            day > MAX_CYCLE_DAY -> null
            else -> CycleDayInfo.Between(day)
        }
    }

    /** Everyone in a tryst with [partnerIds] who has a cycle day on [epochDay]; the user first. */
    fun forTryst(partnerIds: Collection<String>, epochDay: Long): List<Pair<CycleOwner, CycleDayInfo>> = owners(partnerIds).mapNotNull { owner -> on(owner, epochDay)?.let { owner to it } }

    /** Whether anyone in a tryst with [partnerIds] was on a logged period on [epochDay]. */
    fun anyOnPeriod(partnerIds: Collection<String>, epochDay: Long): Boolean = owners(partnerIds).any { on(it, epochDay) is CycleDayInfo.OnPeriod }

    private fun owners(partnerIds: Collection<String>): List<CycleOwner> = listOf(SELF) + partnerIds.map { CycleOwner(CyclePeriodEntity.OWNER_PARTNER, it) }

    companion object {
        /** Past this many days since a period started, a gap means unlogged periods, so no cycle day is shown. */
        const val MAX_CYCLE_DAY = 90

        private val SELF = CycleOwner(CyclePeriodEntity.OWNER_PROFILE, ProfileEntity.SELF_ID)

        /** No periods at all: nobody has a cycle day. Used when cycle tracking is off. */
        val NONE = CycleDays(emptyMap())

        fun from(periods: List<CyclePeriodEntity>): CycleDays = CycleDays(
            periods.groupBy { CycleOwner(it.ownerKind, it.ownerId) }
                .mapValues { (_, list) -> list.sortedBy { it.startEpochDay } },
        )
    }
}

// ⏣ HW ⏣ · code · tools · homelab · ⏣ HW ⏣
