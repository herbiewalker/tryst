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
package app.tryst.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import app.tryst.data.db.entity.CycleDayEntity
import app.tryst.data.db.entity.CyclePeriodEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class CycleDao {

    @Query("SELECT * FROM cycle_period ORDER BY startEpochDay DESC")
    abstract fun observeAllPeriods(): Flow<List<CyclePeriodEntity>>

    @Query("SELECT * FROM cycle_period WHERE ownerKind = :ownerKind AND ownerId = :ownerId ORDER BY startEpochDay DESC")
    abstract fun observePeriods(ownerKind: String, ownerId: String): Flow<List<CyclePeriodEntity>>

    @Query("SELECT * FROM cycle_period WHERE ownerKind = :ownerKind AND ownerId = :ownerId ORDER BY startEpochDay DESC")
    abstract suspend fun getPeriods(ownerKind: String, ownerId: String): List<CyclePeriodEntity>

    @Query("SELECT * FROM cycle_period WHERE id = :id")
    abstract suspend fun getPeriod(id: String): CyclePeriodEntity?

    @Query("SELECT * FROM cycle_period WHERE id = :id")
    abstract fun observePeriod(id: String): Flow<CyclePeriodEntity?>

    @Query("SELECT * FROM cycle_day WHERE periodId = :periodId ORDER BY epochDay")
    abstract fun observeDays(periodId: String): Flow<List<CycleDayEntity>>

    @Query("SELECT * FROM cycle_day WHERE periodId = :periodId ORDER BY epochDay")
    abstract suspend fun getDays(periodId: String): List<CycleDayEntity>

    @Query(
        "SELECT d.* FROM cycle_day d JOIN cycle_period p ON p.id = d.periodId " +
            "WHERE p.ownerKind = :ownerKind AND p.ownerId = :ownerId ORDER BY d.epochDay",
    )
    abstract suspend fun getDaysForOwner(ownerKind: String, ownerId: String): List<CycleDayEntity>

    @Upsert
    abstract suspend fun upsertPeriod(period: CyclePeriodEntity)

    @Upsert
    abstract suspend fun upsertDays(days: List<CycleDayEntity>)

    @Query("DELETE FROM cycle_day WHERE periodId = :periodId AND (epochDay < :fromDay OR epochDay > :toDay)")
    abstract suspend fun deleteDaysOutside(periodId: String, fromDay: Long, toDay: Long)

    @Query("DELETE FROM cycle_period WHERE id = :id")
    abstract suspend fun deletePeriod(id: String)

    @Query("DELETE FROM cycle_period")
    abstract suspend fun deleteAllPeriods()

    @Query("SELECT COUNT(*) FROM cycle_period")
    abstract fun observePeriodCount(): Flow<Int>

    /** Saves a period and its days together, dropping any days that now fall outside it. */
    @Transaction
    open suspend fun savePeriodWithDays(period: CyclePeriodEntity, days: List<CycleDayEntity>) {
        upsertPeriod(period)
        deleteDaysOutside(period.id, period.startEpochDay, period.endEpochDay)
        upsertDays(days)
    }
}

// ⏣ HW ⏣ · code · tools · homelab · ⏣ HW ⏣
