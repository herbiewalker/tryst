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
package app.tryst.data.repository

import app.tryst.core.session.SessionManager
import app.tryst.data.cycle.CycleDefaults
import app.tryst.data.cycle.CycleHistory
import app.tryst.data.db.entity.CycleDayEntity
import app.tryst.data.db.entity.CyclePeriodEntity
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/** Who a period belongs to: the self profile or one partner (same pair as `person_photo`). */
data class CycleOwner(val kind: String, val id: String) {
    val isSelf: Boolean get() = kind == CyclePeriodEntity.OWNER_PROFILE
}

/** Optional cycle tracking (CYC-1). Everything stays in the encrypted DB; nothing here touches the network. */
@Singleton
class CycleRepository @Inject constructor(
    private val session: SessionManager,
) {
    private val dao get() = session.database().cycleDao()

    fun observeAllPeriods(): Flow<List<CyclePeriodEntity>> = dao.observeAllPeriods()

    fun observePeriods(owner: CycleOwner): Flow<List<CyclePeriodEntity>> = dao.observePeriods(owner.kind, owner.id)

    fun observePeriod(id: String): Flow<CyclePeriodEntity?> = dao.observePeriod(id)

    fun observeDays(periodId: String): Flow<List<CycleDayEntity>> = dao.observeDays(periodId)

    fun observePeriodCount(): Flow<Int> = dao.observePeriodCount()

    suspend fun getPeriod(id: String): CyclePeriodEntity? = dao.getPeriod(id)

    suspend fun getDays(periodId: String): List<CycleDayEntity> = dao.getDays(periodId)

    /**
     * Logs a period starting on [startEpochDay] with the most likely length and flow for this person,
     * all marked estimated. Returns the new period's id, or null when [startEpochDay] already falls
     * inside one of their periods.
     */
    suspend fun startPeriod(owner: CycleOwner, startEpochDay: Long, now: Long = System.currentTimeMillis()): String? {
        val history = dao.getPeriods(owner.kind, owner.id)
        if (history.any { startEpochDay in it.startEpochDay..it.endEpochDay }) return null
        val historyDays = dao.getDaysForOwner(owner.kind, owner.id)
        val id = UUID.randomUUID().toString()
        val (end, days) = CycleDefaults.fill(id, startEpochDay, history, historyDays)
        // Don't let the guessed end run into the person's next logged period.
        val nextStart = history.filter { it.startEpochDay > startEpochDay }.minOfOrNull { it.startEpochDay }
        val clampedEnd = if (nextStart != null) minOf(end, nextStart - 1) else end
        val period = CyclePeriodEntity(
            id = id,
            ownerKind = owner.kind,
            ownerId = owner.id,
            startEpochDay = startEpochDay,
            endEpochDay = clampedEnd,
            endEstimated = true,
            createdAt = now,
            updatedAt = now,
        )
        dao.savePeriodWithDays(period, days.filter { it.epochDay <= clampedEnd })
        return id
    }

    /**
     * Days for [period] after its start or end changed: keeps days still inside it and fills new ones
     * with this person's usual flow (estimated). History excludes the period itself.
     */
    suspend fun refitDays(period: CyclePeriodEntity, existing: List<CycleDayEntity>): List<CycleDayEntity> {
        val history = CycleHistory(
            periods = dao.getPeriods(period.ownerKind, period.ownerId).filter { it.id != period.id },
            days = dao.getDaysForOwner(period.ownerKind, period.ownerId).filter { it.periodId != period.id },
        )
        return CycleDefaults.refit(period, existing, history)
    }

    /** True when [startEpochDay]..[endEpochDay] overlaps another period of the same person. */
    suspend fun overlapsOther(period: CyclePeriodEntity): Boolean = dao.getPeriods(period.ownerKind, period.ownerId)
        .any { it.id != period.id && it.startEpochDay <= period.endEpochDay && period.startEpochDay <= it.endEpochDay }

    suspend fun save(period: CyclePeriodEntity, days: List<CycleDayEntity>) = dao.savePeriodWithDays(period, days)

    suspend fun deletePeriod(id: String) = dao.deletePeriod(id)

    /** Settings → danger zone "Delete cycle data": every period and day, for everyone. */
    suspend fun deleteAll() = dao.deleteAllPeriods()
}

// ⏣ HW ⏣ · code · tools · homelab · ⏣ HW ⏣
