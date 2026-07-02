package app.tryst.data.stats

import app.tryst.data.db.entity.Act
import app.tryst.data.db.entity.Kink
import app.tryst.data.db.entity.Position
import app.tryst.data.db.relation.EncounterWithDetails
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/** A labelled count, used for ranked breakdowns (top partners, acts, moods, …). */
data class Tally(val label: String, val count: Int)

/** A single bucket in a time/category bar chart. */
data class Bucket(val label: String, val count: Int)

/**
 * Everything the Insights screen renders, precomputed from the encounter log. Pure data —
 * no Android/Compose types — so [InsightsEngine.compute] is unit-testable on the JVM.
 */
data class Insights(
    val totalCount: Int,
    val thisMonthCount: Int,
    val thisYearCount: Int,
    /** Whole days between the most recent encounter and [today]; null when there are none. */
    val daysSinceLast: Long?,
    /** Encounters per month averaged over the active span (first encounter → today). */
    val avgPerMonth: Double,
    /** Consecutive weeks up to and including the current week that have ≥1 encounter. */
    val currentStreakWeeks: Int,
    val longestStreakWeeks: Int,
    val avgRating: Double?,
    /** Counts for ratings 1..5 (index 0 = ★1). */
    val ratingHistogram: List<Int>,
    val totalDurationMin: Int,
    val avgDurationMin: Double?,
    val totalSelfOrgasms: Int,
    val totalPartnerOrgasms: Int,
    /** Average self-orgasms over encounters where a count was recorded. */
    val avgSelfOrgasms: Double?,
    /** The trailing 12 months ending with the current month, oldest first. */
    val monthly: List<Bucket>,
    /** Mon..Sun encounter counts. */
    val byWeekday: List<Bucket>,
    val topPartners: List<Tally>,
    val topActs: List<Tally>,
    val topPositions: List<Tally>,
    val topMoods: List<Tally>,
    val topKinks: List<Tally>,
    val topSettings: List<Tally>,
    val topToys: List<Tally>,
    val topOccasions: List<Tally>,
    val topProtection: List<Tally>,
    val topEjaculation: List<Tally>,
    val topInitiators: List<Tally>,
    /** Partners' orgasm counts summed per partner, named, ranked. */
    val orgasmsPerPartner: List<Tally>,
    /** Total orgasms (yours + partners') per month, trailing 12 months. */
    val orgasmsMonthly: List<Bucket>,
) {
    val isEmpty: Boolean get() = totalCount == 0

    companion object {
        val EMPTY = Insights(
            totalCount = 0,
            thisMonthCount = 0,
            thisYearCount = 0,
            daysSinceLast = null,
            avgPerMonth = 0.0,
            currentStreakWeeks = 0,
            longestStreakWeeks = 0,
            avgRating = null,
            ratingHistogram = List(5) { 0 },
            totalDurationMin = 0,
            avgDurationMin = null,
            totalSelfOrgasms = 0,
            totalPartnerOrgasms = 0,
            avgSelfOrgasms = null,
            monthly = emptyList(),
            byWeekday = emptyList(),
            topPartners = emptyList(),
            topActs = emptyList(),
            topPositions = emptyList(),
            topMoods = emptyList(),
            topKinks = emptyList(),
            topSettings = emptyList(),
            topToys = emptyList(),
            topOccasions = emptyList(),
            topProtection = emptyList(),
            topEjaculation = emptyList(),
            topInitiators = emptyList(),
            orgasmsPerPartner = emptyList(),
            orgasmsMonthly = emptyList(),
        )
    }
}

/**
 * Computes [Insights] from the full encounter log. Acts and positions are stored as string ids
 * (a built-in enum name, or `custom:<uuid>`); pass the custom rows' `uuid -> label` maps so
 * those resolve to human labels.
 */
object InsightsEngine {

    private const val CUSTOM_PREFIX = "custom:"

    // One cohesive, sequential aggregation that builds the whole Insights snapshot; splitting it
    // would scatter tightly-related accumulation with no real readability gain (it is JVM-tested).
    @Suppress("LongMethod", "CyclomaticComplexMethod", "LongParameterList")
    fun compute(
        encounters: List<EncounterWithDetails>,
        customActLabels: Map<String, String> = emptyMap(),
        customPositionLabels: Map<String, String> = emptyMap(),
        customKinkLabels: Map<String, String> = emptyMap(),
        zone: ZoneId = ZoneId.systemDefault(),
        today: LocalDate = LocalDate.now(zone),
    ): Insights {
        if (encounters.isEmpty()) return Insights.EMPTY

        fun dateOf(e: EncounterWithDetails): LocalDate = Instant.ofEpochMilli(e.encounter.startAt).atZone(zone).toLocalDate()

        val dates = encounters.map(::dateOf)
        val thisMonth = YearMonth.from(today)
        val lastDate = dates.max()

        // --- streaks (ISO weeks, anchored to each week's Monday) ---
        fun weekStart(d: LocalDate): LocalDate = d.minusDays((d.dayOfWeek.value - 1).toLong())
        val activeWeeks = dates.map(::weekStart).toSortedSet()
        val thisWeek = weekStart(today)
        var currentStreak = 0
        var cursor = thisWeek
        // The streak is "alive" if there's activity this week or last week (mid-week grace).
        if (thisWeek !in activeWeeks && thisWeek.minusWeeks(1) in activeWeeks) cursor = thisWeek.minusWeeks(1)
        while (cursor in activeWeeks) {
            currentStreak++
            cursor = cursor.minusWeeks(1)
        }
        var longestStreak = 0
        var run = 0
        var prev: LocalDate? = null
        for (w in activeWeeks) {
            run = if (prev != null && prev.plusWeeks(1) == w) run + 1 else 1
            if (run > longestStreak) longestStreak = run
            prev = w
        }

        // --- averages ---
        val monthsActive = (ChronoUnit.MONTHS.between(YearMonth.from(dates.min()), thisMonth) + 1)
            .coerceAtLeast(1)
        val ratings = encounters.mapNotNull { it.encounter.satisfactionRating }
        val durations = encounters.mapNotNull { it.encounter.durationMin }
        val selfOrgasmCounts = encounters.mapNotNull { it.encounter.orgasmCountSelf }

        val ratingHistogram = MutableList(5) { 0 }
        ratings.forEach { r -> if (r in 1..5) ratingHistogram[r - 1]++ }

        val totalPartnerOrgasms = encounters.sumOf { e ->
            (e.encounter.partnerOrgasms?.values?.sum() ?: 0) + (e.encounter.orgasmCountPartner ?: 0)
        }

        // --- trailing-12-month buckets ---
        val monthly = (11 downTo 0).map { back ->
            val ym = thisMonth.minusMonths(back.toLong())
            val count = dates.count { YearMonth.from(it) == ym }
            Bucket(ym.month.getDisplayName(TextStyle.SHORT, Locale.getDefault()), count)
        }

        // --- weekday buckets (Mon..Sun) ---
        val byWeekday = (1..7).map { dow ->
            val count = dates.count { it.dayOfWeek.value == dow }
            Bucket(
                java.time.DayOfWeek.of(dow).getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                count,
            )
        }

        // --- breakdowns ---
        val topPartners = tally(
            encounters.flatMap { e -> e.partners.map { it.id to partnerName(it.displayName) } },
        )

        val topActs = tallyLabels(
            encounters.flatMap { e ->
                (
                    (e.encounter.practicesPerformed ?: emptySet()) +
                        (e.encounter.practicesReceived ?: emptySet())
                    )
                    .map { resolveAct(it, customActLabels) }
            },
        )
        val topPositions = tallyLabels(
            encounters.flatMap { e ->
                (e.encounter.positions ?: emptySet()).map { resolvePosition(it, customPositionLabels) }
            },
        )
        val topMoods = tallyLabels(encounters.mapNotNull { it.encounter.mood?.label })
        val topKinks = tallyLabels(
            encounters.flatMap { e -> (e.encounter.kinks ?: emptySet()).map { resolveKink(it, customKinkLabels) } },
        )
        val topSettings = tallyLabels(encounters.flatMap { e -> (e.encounter.contexts ?: emptySet()).map { it.label } })
        val topToys = tallyLabels(encounters.flatMap { e -> (e.encounter.toys ?: emptySet()).map { it.label } })
        val topOccasions = tallyLabels(encounters.flatMap { e -> (e.encounter.occasions ?: emptySet()).map { it.label } })
        val topProtection = tallyLabels(encounters.flatMap { e -> e.encounter.protectionUsed.map { it.label } })
        val topEjaculation = tallyLabels(
            encounters.flatMap { e -> (e.encounter.ejaculationLocations?.values?.flatten() ?: emptyList()).map { it.label } },
        )
        val topInitiators = tallyLabels(encounters.mapNotNull { it.encounter.initiator?.label })

        // --- per-partner orgasms (resolve partner ids -> names from the embedded partner rows) ---
        val partnerNames = encounters.flatMap { it.partners }.associate { it.id to partnerName(it.displayName) }
        val orgasmsPerPartner = encounters
            .flatMap { e -> (e.encounter.partnerOrgasms ?: emptyMap()).entries }
            .groupBy({ it.key }, { it.value })
            .map { (partnerId, counts) -> Tally(partnerNames[partnerId] ?: "Unknown", counts.sum()) }
            .filter { it.count > 0 }
            .sortedWith(compareByDescending<Tally> { it.count }.thenBy { it.label })

        // --- total orgasms (yours + partners') per month, trailing 12 ---
        val orgasmsMonthly = (11 downTo 0).map { back ->
            val ym = thisMonth.minusMonths(back.toLong())
            val count = encounters.filter { YearMonth.from(dateOf(it)) == ym }.sumOf { e ->
                (e.encounter.orgasmCountSelf ?: 0) +
                    (e.encounter.partnerOrgasms?.values?.sum() ?: 0) +
                    (e.encounter.orgasmCountPartner ?: 0)
            }
            Bucket(ym.month.getDisplayName(TextStyle.SHORT, Locale.getDefault()), count)
        }

        return Insights(
            totalCount = encounters.size,
            thisMonthCount = dates.count { YearMonth.from(it) == thisMonth },
            thisYearCount = dates.count { it.year == today.year },
            daysSinceLast = ChronoUnit.DAYS.between(lastDate, today).coerceAtLeast(0),
            avgPerMonth = encounters.size.toDouble() / monthsActive,
            currentStreakWeeks = currentStreak,
            longestStreakWeeks = longestStreak,
            avgRating = ratings.takeIf { it.isNotEmpty() }?.average(),
            ratingHistogram = ratingHistogram,
            totalDurationMin = durations.sum(),
            avgDurationMin = durations.takeIf { it.isNotEmpty() }?.average(),
            totalSelfOrgasms = selfOrgasmCounts.sum(),
            totalPartnerOrgasms = totalPartnerOrgasms,
            avgSelfOrgasms = selfOrgasmCounts.takeIf { it.isNotEmpty() }?.average(),
            monthly = monthly,
            byWeekday = byWeekday,
            topPartners = topPartners,
            topActs = topActs,
            topPositions = topPositions,
            topMoods = topMoods,
            topKinks = topKinks,
            topSettings = topSettings,
            topToys = topToys,
            topOccasions = topOccasions,
            topProtection = topProtection,
            topEjaculation = topEjaculation,
            topInitiators = topInitiators,
            orgasmsPerPartner = orgasmsPerPartner,
            orgasmsMonthly = orgasmsMonthly,
        )
    }

    private fun partnerName(displayName: String?): String = displayName?.takeIf { it.isNotBlank() } ?: "Anonymous"

    private fun resolveAct(id: String, custom: Map<String, String>): String = if (id.startsWith(CUSTOM_PREFIX)) {
        custom[id.removePrefix(CUSTOM_PREFIX)] ?: "Custom act"
    } else {
        runCatching { Act.valueOf(id).label }.getOrDefault(id)
    }

    private fun resolvePosition(id: String, custom: Map<String, String>): String = if (id.startsWith(CUSTOM_PREFIX)) {
        custom[id.removePrefix(CUSTOM_PREFIX)] ?: "Custom position"
    } else {
        runCatching { Position.valueOf(id).label }.getOrDefault(id)
    }

    private fun resolveKink(id: String, custom: Map<String, String>): String = if (id.startsWith(CUSTOM_PREFIX)) {
        custom[id.removePrefix(CUSTOM_PREFIX)] ?: "Custom kink"
    } else {
        runCatching { Kink.valueOf(id).label }.getOrDefault(id)
    }

    /** Tallies `(stableKey, label)` pairs by key, labelling each with its first-seen label. */
    private fun tally(items: List<Pair<String, String>>): List<Tally> = items.groupBy({ it.first }, { it.second })
        .map { (_, labels) -> Tally(labels.first(), labels.size) }
        .sortedWith(compareByDescending<Tally> { it.count }.thenBy { it.label })

    private fun tallyLabels(labels: List<String>): List<Tally> = labels.groupingBy { it }.eachCount()
        .map { (label, count) -> Tally(label, count) }
        .sortedWith(compareByDescending<Tally> { it.count }.thenBy { it.label })
}
