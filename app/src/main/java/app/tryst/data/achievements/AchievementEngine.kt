package app.tryst.data.achievements

import app.tryst.data.db.relation.EncounterWithDetails
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Grouping for the achievements screen. */
enum class AchievementCategory(val label: String) {
    MILESTONES("Milestones"),
    STREAKS("Streaks"),
    VARIETY("Variety"),
    PLEASURE("Pleasure"),
    OCCASIONS("Occasions"),
    MISC("Odds & ends"),
}

/**
 * How an achievement's progress is measured against its target, by replaying the encounter log:
 * - [Count]: number of encounters matching a predicate.
 * - [Sum]: sum of a per-encounter integer.
 * - [Distinct]: size of a growing set of keys (e.g. distinct acts/partners/places). The lambda also
 *   receives the encounter's [LocalDate] for time-based keys (weekday, month).
 * - [Streak]: longest run of consecutive ISO weeks with activity.
 */
sealed interface Rule
data class Count(val predicate: (EncounterWithDetails) -> Boolean) : Rule
data class Sum(val amount: (EncounterWithDetails) -> Int) : Rule
data class Distinct(val keys: (EncounterWithDetails, LocalDate) -> Set<String>) : Rule
data object Streak : Rule

/** A static, code-defined achievement. Ids are stable; nothing is persisted. */
data class AchievementDef(
    val id: String,
    val title: String,
    val description: String,
    val category: AchievementCategory,
    val target: Int,
    val emoji: String,
    val rule: Rule,
)

/** A definition evaluated against the current log. [current] may exceed [target]; the UI caps it. */
data class AchievementStatus(
    val def: AchievementDef,
    val current: Int,
    val unlocked: Boolean,
    val unlockedAt: LocalDate?,
) {
    /** 0f..1f progress toward the target. */
    val progress: Float get() = (current.toFloat() / def.target).coerceIn(0f, 1f)

    /** Unlocked within the recent window relative to [today] → worth a "New" flag. */
    fun isNew(today: LocalDate, windowDays: Long = RECENT_WINDOW_DAYS): Boolean = unlockedAt != null && ChronoUnit.DAYS.between(unlockedAt, today) in 0..windowDays
}

/** Compact rollup for the Insights teaser card. */
data class AchievementSummary(
    val unlockedCount: Int,
    val total: Int,
    /** Recently-unlocked, newest first. */
    val recent: List<AchievementStatus>,
    /** Locked-but-closest, by progress descending. */
    val nearest: List<AchievementStatus>,
)

private const val RECENT_WINDOW_DAYS = 14L

/**
 * Evaluates every [Achievements.catalog] definition against the encounter log. Pure Kotlin (no
 * Android/Room types beyond the entity relation) so it's unit-testable on the JVM, mirroring
 * [app.tryst.data.stats.InsightsEngine]. Nothing is persisted — progress and unlock dates are derived
 * each time by replaying the log in chronological order.
 */
object AchievementEngine {

    // `today` is unused by the (purely historical) evaluation but kept for API symmetry with
    // summarize() and so tests can pin a deterministic reference date.
    @Suppress("UnusedParameter")
    fun evaluate(
        encounters: List<EncounterWithDetails>,
        zone: ZoneId = ZoneId.systemDefault(),
        today: LocalDate = LocalDate.now(zone),
    ): List<AchievementStatus> {
        val sorted = encounters.sortedBy { it.encounter.startAt }
        fun dateOf(e: EncounterWithDetails): LocalDate = Instant.ofEpochMilli(e.encounter.startAt).atZone(zone).toLocalDate()

        return Achievements.catalog.map { def ->
            val (current, unlockedAt) = when (val rule = def.rule) {
                is Count -> replayTotal(sorted, def.target, ::dateOf) { if (rule.predicate(it)) 1 else 0 }
                is Sum -> replayTotal(sorted, def.target, ::dateOf) { rule.amount(it) }
                is Distinct -> replayDistinct(sorted, def.target, ::dateOf, rule.keys)
                Streak -> replayStreak(sorted, def.target, ::dateOf)
            }
            AchievementStatus(def, current, unlocked = current >= def.target, unlockedAt = unlockedAt)
        }
    }

    fun summarize(
        statuses: List<AchievementStatus>,
        today: LocalDate = LocalDate.now(),
        nearestCount: Int = 3,
    ): AchievementSummary = AchievementSummary(
        unlockedCount = statuses.count { it.unlocked },
        total = statuses.size,
        recent = statuses.filter { it.isNew(today) }
            .sortedByDescending { it.unlockedAt },
        nearest = statuses.filter { !it.unlocked && it.current > 0 }
            .sortedByDescending { it.progress }
            .take(nearestCount),
    )

    private fun replayTotal(
        sorted: List<EncounterWithDetails>,
        target: Int,
        dateOf: (EncounterWithDetails) -> LocalDate,
        amount: (EncounterWithDetails) -> Int,
    ): Pair<Int, LocalDate?> {
        var running = 0
        var unlockedAt: LocalDate? = null
        for (e in sorted) {
            running += amount(e)
            if (unlockedAt == null && running >= target) unlockedAt = dateOf(e)
        }
        return running to unlockedAt
    }

    private fun replayDistinct(
        sorted: List<EncounterWithDetails>,
        target: Int,
        dateOf: (EncounterWithDetails) -> LocalDate,
        keys: (EncounterWithDetails, LocalDate) -> Set<String>,
    ): Pair<Int, LocalDate?> {
        val seen = HashSet<String>()
        var unlockedAt: LocalDate? = null
        for (e in sorted) {
            seen.addAll(keys(e, dateOf(e)))
            if (unlockedAt == null && seen.size >= target) unlockedAt = dateOf(e)
        }
        return seen.size to unlockedAt
    }

    /** Longest run of consecutive Monday-anchored weeks; unlock date = when it first reaches target. */
    private fun replayStreak(
        sorted: List<EncounterWithDetails>,
        target: Int,
        dateOf: (EncounterWithDetails) -> LocalDate,
    ): Pair<Int, LocalDate?> {
        fun weekStart(d: LocalDate): LocalDate = d.minusDays((d.dayOfWeek.value - 1).toLong())
        val weeks = HashSet<LocalDate>()
        var maxStreak = 0
        var unlockedAt: LocalDate? = null
        for (e in sorted) {
            val ws = weekStart(dateOf(e))
            weeks.add(ws)
            // Length of the consecutive run ending at this week.
            var run = 0
            var cur = ws
            while (cur in weeks) {
                run++
                cur = cur.minusWeeks(1)
            }
            if (run > maxStreak) maxStreak = run
            if (unlockedAt == null && maxStreak >= target) unlockedAt = dateOf(e)
        }
        return maxStreak to unlockedAt
    }
}
