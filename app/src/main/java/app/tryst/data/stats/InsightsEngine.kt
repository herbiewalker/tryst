package app.tryst.data.stats

import app.tryst.data.db.entity.Act
import app.tryst.data.db.entity.EjaculationLocation
import app.tryst.data.db.entity.Kink
import app.tryst.data.db.entity.Occasion
import app.tryst.data.db.entity.Position
import app.tryst.data.db.entity.ToyType
import app.tryst.data.db.relation.EncounterWithDetails
import app.tryst.data.filter.DateRange
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
 *
 * When [isScoped] the figures cover only the selected window (INS-2). A few of them are inherently
 * "as of today" and cannot be honestly reported for a past window — [currentStreakWeeks] and
 * [daysSinceLast] are zeroed/nulled, and [thisMonthCount]/[thisYearCount] keep counting against the
 * real calendar; the Overview hides all four while a scope is active (see `StatTiles`).
 */
data class Insights(
    val totalCount: Int,
    val thisMonthCount: Int,
    val thisYearCount: Int,
    /** Whole days between the most recent encounter and today; null when there are none, or when scoped. */
    val daysSinceLast: Long?,
    /** Encounters per month, averaged over the scope's months (or first-encounter→today when unscoped). */
    val avgPerMonth: Double,
    /** Consecutive weeks up to and including the current week that have ≥1 encounter; always 0 when scoped. */
    val currentStreakWeeks: Int,
    /** Longest run of consecutive active weeks — a property of the window, so it survives a scope. */
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
    /** Oldest first: the scope's own months, or the trailing 12 ending this month when unscoped. */
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
    /** Total orgasms (yours + partners') over the same month buckets as [monthly]. */
    val orgasmsMonthly: List<Bucket>,
    /** True when a time scope narrowed the log (INS-2) — some tiles are meaningless then. */
    val isScoped: Boolean = false,
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
 *
 * Pass a [scope] to restrict every figure to a window (INS-2). Note this is **not** simply a matter of
 * pre-filtering the input: the month buckets, the per-month average, the streak and "days since last"
 * are all computed against *today*, so the window has to reach the engine itself.
 */
object InsightsEngine {

    private const val CUSTOM_PREFIX = "custom:"

    /** Widest month-bucket chart we'll draw before falling back to the most recent [MAX_MONTH_BUCKETS]. */
    private const val MAX_MONTH_BUCKETS = 24

    /** Trailing window used when no scope is set — the historical behaviour. */
    private const val DEFAULT_MONTH_BUCKETS = 12

    // One cohesive, sequential aggregation that builds the whole Insights snapshot; splitting it
    // would scatter tightly-related accumulation with no real readability gain (it is JVM-tested).
    @Suppress("LongMethod", "CyclomaticComplexMethod", "LongParameterList")
    fun compute(
        encounters: List<EncounterWithDetails>,
        customActLabels: Map<String, String> = emptyMap(),
        customPositionLabels: Map<String, String> = emptyMap(),
        customKinkLabels: Map<String, String> = emptyMap(),
        customToyLabels: Map<String, String> = emptyMap(),
        customOccasionLabels: Map<String, String> = emptyMap(),
        customEjaculationLabels: Map<String, String> = emptyMap(),
        zone: ZoneId = ZoneId.systemDefault(),
        today: LocalDate = LocalDate.now(zone),
        /** The INS-2 window; null = all time. */
        scope: DateRange? = null,
    ): Insights {
        fun dateOf(e: EncounterWithDetails): LocalDate = Instant.ofEpochMilli(e.encounter.startAt).atZone(zone).toLocalDate()

        val scoped = scope != null

        @Suppress("NAME_SHADOWING")
        val encounters = if (scope == null) encounters else encounters.filter { dateOf(it) in scope }
        // An empty window is a real answer ("no trysts in 2021"), not the same as an empty app.
        if (encounters.isEmpty()) return Insights.EMPTY.copy(isScoped = scoped)

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

        // --- month buckets: the scope's own months, else the trailing 12 ending this month ---
        val bucketMonths: List<YearMonth> = if (scope == null) {
            (DEFAULT_MONTH_BUCKETS - 1 downTo 0).map { thisMonth.minusMonths(it.toLong()) }
        } else {
            val last = YearMonth.from(scope.end)
            generateSequence(YearMonth.from(scope.start)) { if (it < last) it.plusMonths(1) else null }
                .toList()
                .takeLast(MAX_MONTH_BUCKETS)
        }
        // "Jan" is ambiguous once the window spans years, so qualify it then (and only then).
        val multiYear = bucketMonths.distinctBy { it.year }.size > 1
        fun monthLabel(ym: YearMonth): String {
            val month = ym.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())
            return if (multiYear) "$month ${"%02d".format(ym.year % 100)}" else month
        }

        // --- averages ---
        val monthsActive = if (scope == null) {
            ChronoUnit.MONTHS.between(YearMonth.from(dates.min()), thisMonth) + 1
        } else {
            ChronoUnit.MONTHS.between(YearMonth.from(scope.start), YearMonth.from(scope.end)) + 1
        }.coerceAtLeast(1)
        val ratings = encounters.mapNotNull { it.encounter.satisfactionRating }
        val durations = encounters.mapNotNull { it.encounter.durationMin }
        val selfOrgasmCounts = encounters.mapNotNull { it.encounter.orgasmCountSelf }

        val ratingHistogram = MutableList(5) { 0 }
        ratings.forEach { r -> if (r in 1..5) ratingHistogram[r - 1]++ }

        val totalPartnerOrgasms = encounters.sumOf { e ->
            (e.encounter.partnerOrgasms?.values?.sum() ?: 0) + (e.encounter.orgasmCountPartner ?: 0)
        }

        val monthly = bucketMonths.map { ym ->
            Bucket(monthLabel(ym), dates.count { YearMonth.from(it) == ym })
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
        val topToys = tallyLabels(encounters.flatMap { e -> (e.encounter.toys ?: emptySet()).map { resolveToy(it, customToyLabels) } })
        val topOccasions = tallyLabels(
            encounters.flatMap { e -> (e.encounter.occasions ?: emptySet()).map { resolveOccasion(it, customOccasionLabels) } },
        )
        val topProtection = tallyLabels(encounters.flatMap { e -> e.encounter.protectionUsed.map { it.label } })
        val topEjaculation = tallyLabels(
            encounters.flatMap { e ->
                (e.encounter.ejaculationLocations?.values?.flatten() ?: emptyList()).map { resolveEjaculation(it, customEjaculationLabels) }
            },
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

        // --- total orgasms (yours + partners') per month, over the same buckets ---
        val orgasmsMonthly = bucketMonths.map { ym ->
            val count = encounters.filter { YearMonth.from(dateOf(it)) == ym }.sumOf { e ->
                (e.encounter.orgasmCountSelf ?: 0) +
                    (e.encounter.partnerOrgasms?.values?.sum() ?: 0) +
                    (e.encounter.orgasmCountPartner ?: 0)
            }
            Bucket(monthLabel(ym), count)
        }

        return Insights(
            totalCount = encounters.size,
            thisMonthCount = dates.count { YearMonth.from(it) == thisMonth },
            thisYearCount = dates.count { it.year == today.year },
            // "Days since last" and "current streak" are both measured from today. Under a past window
            // they'd describe a moment that has already passed, so they're withheld rather than faked.
            daysSinceLast = if (scoped) null else ChronoUnit.DAYS.between(lastDate, today).coerceAtLeast(0),
            avgPerMonth = encounters.size.toDouble() / monthsActive,
            currentStreakWeeks = if (scoped) 0 else currentStreak,
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
            isScoped = scoped,
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

    private fun resolveToy(id: String, custom: Map<String, String>): String = if (id.startsWith(CUSTOM_PREFIX)) {
        custom[id.removePrefix(CUSTOM_PREFIX)] ?: "Custom toy"
    } else {
        runCatching { ToyType.valueOf(id).label }.getOrDefault(id)
    }

    private fun resolveOccasion(id: String, custom: Map<String, String>): String = if (id.startsWith(CUSTOM_PREFIX)) {
        custom[id.removePrefix(CUSTOM_PREFIX)] ?: "Custom occasion"
    } else {
        runCatching { Occasion.valueOf(id).label }.getOrDefault(id)
    }

    private fun resolveEjaculation(id: String, custom: Map<String, String>): String = if (id.startsWith(CUSTOM_PREFIX)) {
        custom[id.removePrefix(CUSTOM_PREFIX)] ?: "Custom finish"
    } else {
        runCatching { EjaculationLocation.valueOf(id).label }.getOrDefault(id)
    }

    /** Tallies `(stableKey, label)` pairs by key, labelling each with its first-seen label. */
    private fun tally(items: List<Pair<String, String>>): List<Tally> = items.groupBy({ it.first }, { it.second })
        .map { (_, labels) -> Tally(labels.first(), labels.size) }
        .sortedWith(compareByDescending<Tally> { it.count }.thenBy { it.label })

    private fun tallyLabels(labels: List<String>): List<Tally> = labels.groupingBy { it }.eachCount()
        .map { (label, count) -> Tally(label, count) }
        .sortedWith(compareByDescending<Tally> { it.count }.thenBy { it.label })
}
