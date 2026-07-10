package app.tryst.ui.insights

import app.tryst.data.stats.Insights
import java.util.Locale

/**
 * One customizable Overview tile. [value] returns the formatted figure, or null when it doesn't
 * apply yet (e.g. no rating recorded) — null tiles are skipped in view mode. Ids are stable and
 * persisted (order + hidden set), so don't rename them.
 *
 * A few tiles are "as of today" by nature and return null while a time scope is active (INS-2):
 * *This month* / *This year* would count against the real calendar rather than the window, and
 * *Current streak* / *Days since last* describe a moment that has already passed. Rather than
 * silently mean something other than their label, they drop out of the grid.
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
        StatTile("month", "This month") { i -> i.thisMonthCount.toString().takeUnless { i.isScoped } },
        StatTile("year", "This year") { i -> i.thisYearCount.toString().takeUnless { i.isScoped } },
        // The engine already nulls daysSinceLast under a scope; this is the same rule, stated once.
        StatTile("days_since", "Days since last") { i ->
            i.daysSinceLast?.let { if (it == 0L) "Today" else it.toString() }
        },
        StatTile("avg_month", "Trysts / month") { decimal(it.avgPerMonth) },
        StatTile("streak_current", "Current streak") { i -> weeks(i.currentStreakWeeks).takeUnless { i.isScoped } },
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

    private fun decimal(value: Double): String = if (value == value.toLong().toDouble()) {
        value.toLong().toString()
    } else {
        String.format(Locale.getDefault(), "%.1f", value)
    }

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
