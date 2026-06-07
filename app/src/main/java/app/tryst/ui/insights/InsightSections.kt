package app.tryst.ui.insights

import app.tryst.data.stats.Insights

/**
 * A reorderable / hideable Insights section card. [hasData] decides whether it's worth showing in
 * view mode (the customizer always lists every section). Ids are stable and persisted — don't rename.
 * The actual chart content is rendered in InsightsScreen keyed by [id].
 */
data class InsightSection(
    val id: String,
    val title: String,
    val hasData: (Insights) -> Boolean,
)

object InsightSections {

    const val ACTIVITY = "activity"
    const val SATISFACTION = "satisfaction"
    const val PEOPLE = "people"
    const val ACTS_POSITIONS = "acts_positions"
    const val VIBE = "vibe"
    const val INITIATOR = "initiator"
    const val ORGASMS = "orgasms"
    const val DETAILS = "details"

    /** Catalog order = the default layout for a fresh install. */
    val catalog: List<InsightSection> = listOf(
        InsightSection(ACTIVITY, "Activity") { it.totalCount > 0 },
        InsightSection(SATISFACTION, "Satisfaction") { i -> i.ratingHistogram.any { it > 0 } },
        InsightSection(PEOPLE, "People") { it.topPartners.isNotEmpty() },
        InsightSection(ACTS_POSITIONS, "What you did") { it.topActs.isNotEmpty() || it.topPositions.isNotEmpty() },
        InsightSection(VIBE, "Vibe & context") {
            it.topMoods.isNotEmpty() || it.topKinks.isNotEmpty() ||
                it.topSettings.isNotEmpty() || it.topOccasions.isNotEmpty()
        },
        InsightSection(INITIATOR, "Initiator") { it.topInitiators.isNotEmpty() },
        InsightSection(ORGASMS, "Orgasms") {
            it.totalSelfOrgasms > 0 || it.totalPartnerOrgasms > 0 || it.topEjaculation.isNotEmpty()
        },
        InsightSection(DETAILS, "Details") { it.topToys.isNotEmpty() || it.topProtection.isNotEmpty() },
    )

    private val byId = catalog.associateBy { it.id }

    /** Keeps known saved ids in order, then appends any catalog sections the save didn't mention. */
    fun ordered(order: List<String>): List<InsightSection> {
        val known = order.mapNotNull { byId[it] }
        val seen = known.mapTo(mutableSetOf()) { it.id }
        return known + catalog.filter { it.id !in seen }
    }
}
