package app.tryst.data.filter

import java.time.LocalDate

/**
 * A named time window — the shared vocabulary behind the Insights time scope (INS-2) and Search's date
 * filter (SRCH-1). Both screens present the same year → quarter → custom-range narrowing, so both speak
 * this type and resolve it to a [DateRange] to hand to the query layer.
 *
 * Pure data, no Android types: [encode]/[decode] round-trip it through a prefs store, and it's
 * JVM-tested alongside the rest of `data/filter`.
 */
sealed interface DateScope {

    /** The whole log — no date constraint. */
    data object AllTime : DateScope

    /** A single calendar year. "This year" is just `Year(today.year)`. */
    data class Year(val year: Int) : DateScope

    /** One calendar quarter of [year]. [quarter] is 1..4. */
    data class Quarter(val year: Int, val quarter: Int) : DateScope {
        init {
            require(quarter in 1..QUARTERS) { "quarter must be 1..$QUARTERS, was $quarter" }
        }
    }

    /** An arbitrary user-picked window. */
    data class Custom(val range: DateRange) : DateScope

    /** The calendar year this scope sits in, or null when it isn't anchored to one. */
    val anchorYear: Int?
        get() = when (this) {
            is Year -> year
            is Quarter -> year
            AllTime, is Custom -> null
        }

    /** The window to compute over, or null for [AllTime] (which constrains nothing). */
    fun range(): DateRange? = when (this) {
        AllTime -> null
        is Year -> DateRange(LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31))
        is Quarter -> {
            val start = LocalDate.of(year, (quarter - 1) * MONTHS_PER_QUARTER + 1, 1)
            DateRange(start, start.plusMonths(MONTHS_PER_QUARTER.toLong()).minusDays(1))
        }
        is Custom -> range
    }

    companion object {
        const val QUARTERS = 4
        private const val MONTHS_PER_QUARTER = 3

        /** Stable string form for [app.tryst.core.prefs.InsightsPreferences]. Never change these prefixes. */
        fun encode(scope: DateScope): String = when (scope) {
            AllTime -> ALL
            is Year -> "$YEAR$SEP${scope.year}"
            is Quarter -> "$QUARTER$SEP${scope.year}$SEP${scope.quarter}"
            is Custom -> "$CUSTOM$SEP${scope.range.start}$SEP${scope.range.end}"
        }

        /** Parses [encode]'s output. Anything unrecognized (corrupt pref, older/newer format) falls back to [AllTime]. */
        fun decode(value: String?): DateScope {
            val parts = value?.split(SEP) ?: return AllTime
            return runCatching {
                when (parts.firstOrNull()) {
                    YEAR -> Year(parts[1].toInt())
                    QUARTER -> Quarter(parts[1].toInt(), parts[2].toInt())
                    CUSTOM -> Custom(DateRange(LocalDate.parse(parts[1]), LocalDate.parse(parts[2])))
                    else -> AllTime
                }
            }.getOrDefault(AllTime)
        }

        private const val ALL = "all"
        private const val YEAR = "year"
        private const val QUARTER = "quarter"
        private const val CUSTOM = "custom"
        private const val SEP = ":"
    }
}
