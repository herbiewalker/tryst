package app.tryst.ui.insights

import app.tryst.data.stats.Insights
import java.util.Locale

/**
 * One customizable Overview tile. [value] returns the formatted figure, or null when it doesn't
 * apply yet (e.g. no rating recorded) — null tiles are skipped in view mode. Ids are stable and
 * persisted (order + hidden set), so don't rename them.
 */
data class StatTile(
    val id: String,
    val label: String,
    val value: (Insights) -> String?,
)

object StatTiles {

    /** Catalog order = the default layout for a fresh install. */
    val catalog: List<StatTile> = listOf(
        StatTile("total", "Total trysts") { it.totalCount.toString() },
        StatTile("month", "This month") { it.thisMonthCount.toString() },
        StatTile("year", "This year") { it.thisYearCount.toString() },
        StatTile("days_since", "Days since last") { i ->
            i.daysSinceLast?.let { if (it == 0L) "Today" else it.toString() }
        },
        StatTile("avg_month", "Trysts / month") { decimal(it.avgPerMonth) },
        StatTile("streak_current", "Current streak") { weeks(it.currentStreakWeeks) },
        StatTile("streak_longest", "Longest streak") { weeks(it.longestStreakWeeks) },
        StatTile("avg_rating", "Avg rating") { i -> i.avgRating?.let { "★ ${decimal(it)}" } },
        StatTile("avg_duration", "Avg duration") { i -> i.avgDurationMin?.let { "${it.toInt()} min" } },
        StatTile("total_time", "Total time") { i -> i.totalDurationMin.takeIf { it > 0 }?.let(::duration) },
        StatTile("orgasms_self", "Your orgasms") { i -> i.totalSelfOrgasms.takeIf { it > 0 }?.toString() },
        StatTile("orgasms_partner", "Partner orgasms") { i -> i.totalPartnerOrgasms.takeIf { it > 0 }?.toString() },
    )

    private val byId = catalog.associateBy { it.id }

    /**
     * Resolves the saved [order] against the catalog: keeps known ids in saved order, then appends
     * any catalog tiles the saved order didn't mention (e.g. tiles added in a later version).
     */
    fun ordered(order: List<String>): List<StatTile> {
        val known = order.mapNotNull { byId[it] }
        val seen = known.mapTo(mutableSetOf()) { it.id }
        return known + catalog.filter { it.id !in seen }
    }

    private fun weeks(w: Int): String = if (w == 1) "1 wk" else "$w wks"

    private fun decimal(value: Double): String =
        if (value == value.toLong().toDouble()) value.toLong().toString()
        else String.format(Locale.getDefault(), "%.1f", value)

    private fun duration(minutes: Int): String {
        val h = minutes / 60
        val m = minutes % 60
        return when {
            h == 0 -> "${m}m"
            m == 0 -> "${h}h"
            else -> "${h}h ${m}m"
        }
    }
}
