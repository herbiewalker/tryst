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
package app.tryst.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One period for one person (CYC-1, schema v20). The owner uses the same [ownerKind] + [ownerId] pair
 * as [PersonPhotoEntity]: "partner" + the partner's id, or "profile" + [ProfileEntity.SELF_ID].
 *
 * Dates are local calendar days stored as `LocalDate.toEpochDay()`, so a period never shifts with the
 * time zone. [endEpochDay] is the last day of the period, inclusive.
 *
 * "Period started" fills in the most likely length and flow (see `CycleDefaults`). [endEstimated] is
 * true while the end is still that guess; setting the real end clears it.
 */
@Entity(tableName = "cycle_period", indices = [Index(value = ["ownerKind", "ownerId", "startEpochDay"])])
data class CyclePeriodEntity(
    @PrimaryKey val id: String,
    val ownerKind: String,
    val ownerId: String,
    val startEpochDay: Long,
    val endEpochDay: Long,
    @ColumnInfo(defaultValue = "0") val endEstimated: Boolean = false,
    val note: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
) {
    val lengthDays: Int get() = (endEpochDay - startEpochDay + 1).toInt()

    companion object {
        const val OWNER_PARTNER = "partner"
        const val OWNER_PROFILE = "profile"
    }
}

/**
 * Flow for one day of a [CyclePeriodEntity]. [flow] is a [CycleFlow] name kept as text, so an unknown
 * value from a future version reads as "not set" instead of failing. [estimated] marks a day that was
 * filled from the defaults rather than entered.
 */
@Entity(
    tableName = "cycle_day",
    primaryKeys = ["periodId", "epochDay"],
    foreignKeys = [
        ForeignKey(
            entity = CyclePeriodEntity::class,
            parentColumns = ["id"],
            childColumns = ["periodId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class CycleDayEntity(
    val periodId: String,
    val epochDay: Long,
    val flow: String,
    @ColumnInfo(defaultValue = "0") val estimated: Boolean = false,
) {
    val flowLevel: CycleFlow? get() = CycleFlow.fromName(flow)
}

/** How heavy a period day is, lightest first. */
enum class CycleFlow {
    SPOTTING,
    LIGHT,
    MEDIUM,
    HEAVY,
    ;

    companion object {
        fun fromName(name: String?): CycleFlow? = entries.firstOrNull { it.name == name }
    }
}

/**
 * One symptom on the shared symptom list (CYC-1 symptoms, schema v22, D-73), offered on every person's
 * period days. Nothing is preset: every row is something the user typed, so the APK ships no symptom
 * names. Deleting a symptom removes every day it was logged on.
 */
@Entity(tableName = "cycle_symptom", indices = [Index(value = ["label"], unique = true)])
data class CycleSymptomEntity(
    @PrimaryKey val id: String,
    val label: String,
)

/**
 * A symptom logged on one day of a period, with how strong it was ([strength] is a [SymptomStrength]
 * level). Symptoms are only logged inside a period, so the row hangs off the period and goes with it.
 */
@Entity(
    tableName = "cycle_symptom_log",
    primaryKeys = ["periodId", "epochDay", "symptomId"],
    foreignKeys = [
        ForeignKey(
            entity = CyclePeriodEntity::class,
            parentColumns = ["id"],
            childColumns = ["periodId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = CycleSymptomEntity::class,
            parentColumns = ["id"],
            childColumns = ["symptomId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["symptomId"])],
)
data class CycleSymptomLogEntity(
    val periodId: String,
    val epochDay: Long,
    val symptomId: String,
    val strength: Int,
) {
    val strengthLevel: SymptomStrength get() = SymptomStrength.fromLevel(strength)
}

/** How strong a symptom was on a day, weakest first. Stored as [level]; an unknown level reads as mild. */
enum class SymptomStrength(val level: Int) {
    MILD(1),
    MEDIUM(2),
    STRONG(3),
    ;

    companion object {
        fun fromLevel(level: Int): SymptomStrength = entries.firstOrNull { it.level == level } ?: MILD

        /** One tap on a symptom chip: off, then mild, medium, strong, and back to off. */
        fun next(current: SymptomStrength?): SymptomStrength? = when (current) {
            null -> MILD
            MILD -> MEDIUM
            MEDIUM -> STRONG
            STRONG -> null
        }
    }
}

// ⏣ HW ⏣ · code · tools · homelab · ⏣ HW ⏣
