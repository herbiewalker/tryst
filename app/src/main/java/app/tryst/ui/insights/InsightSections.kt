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
package app.tryst.ui.insights

import app.tryst.core.prefs.ChartStyle
import app.tryst.data.stats.Insights

/**
 * A reorderable / hideable Insights section card. [hasData] decides whether it's worth showing in
 * view mode (the customizer always lists every section). Ids are stable and persisted — don't rename.
 * The actual chart content is rendered in InsightsScreen keyed by [id].
 */
data class InsightSection(
    val id: String,
    val title: String,
    /** False for non-chart cards (Overview tiles, Achievements) — they have no per-card style picker. */
    val hasChart: Boolean = true,
    /** Caption shown in the customizer for a non-chart card (in place of the style chips). */
    val editorNote: String? = null,
    /**
     * Chart styles this section can actually draw, in chip order. A time series can't be a donut and
     * a category breakdown can't be a line, so offering those would only show a silent fallback.
     */
    val styles: List<ChartStyle> = ChartStyle.entries,
    val hasData: (Insights) -> Boolean,
) {
    /** The saved [style] if this section can draw it, otherwise the first style it offers. */
    fun resolve(style: ChartStyle?): ChartStyle = if (style != null && style in styles) style else styles.firstOrNull() ?: ChartStyle.BARS
}

object InsightSections {

    const val OVERVIEW = "overview"
    const val ACHIEVEMENTS = "achievements"
    const val ACTIVITY = "activity"
    const val SATISFACTION = "satisfaction"
    const val PEOPLE = "people"
    const val ACTS_POSITIONS = "acts_positions"
    const val VIBE = "vibe"
    const val INITIATOR = "initiator"
    const val ORGASMS = "orgasms"
    const val DETAILS = "details"

    /** CYC-1 (0.7.6). Its data lives outside [Insights], so the screen decides when it shows (see `CycleCard`). */
    const val CYCLE = "cycle"

    /** Ordered time/ordinal series (months, weekdays, ratings). */
    private val TREND_STYLES = listOf(ChartStyle.BARS, ChartStyle.LINE)

    /** Categorical breakdowns (partners, acts, moods, ...). */
    private val BREAKDOWN_STYLES = listOf(ChartStyle.BARS, ChartStyle.DONUT)

    /** Catalog order = the default layout for a fresh install. */
    val catalog: List<InsightSection> = listOf(
        InsightSection(OVERVIEW, "Overview", hasChart = false, editorNote = "Drag the stat tiles below to arrange them.") { it.totalCount > 0 },
        InsightSection(ACHIEVEMENTS, "Achievements", hasChart = false, editorNote = "Opens the Achievements screen.") { it.totalCount > 0 },
        InsightSection(ACTIVITY, "Activity", styles = TREND_STYLES) { it.totalCount > 0 },
        InsightSection(SATISFACTION, "Satisfaction", styles = TREND_STYLES) { i -> i.ratingHistogram.any { it > 0 } },
        InsightSection(PEOPLE, "People", styles = BREAKDOWN_STYLES) { it.topPartners.isNotEmpty() },
        InsightSection(ACTS_POSITIONS, "What you did", styles = BREAKDOWN_STYLES) {
            it.topActs.isNotEmpty() || it.topPositions.isNotEmpty()
        },
        InsightSection(VIBE, "Vibe & context", styles = BREAKDOWN_STYLES) {
            it.topMoods.isNotEmpty() ||
                it.topKinks.isNotEmpty() ||
                it.topSettings.isNotEmpty() ||
                it.topOccasions.isNotEmpty()
        },
        InsightSection(INITIATOR, "Initiator", styles = BREAKDOWN_STYLES) { it.topInitiators.isNotEmpty() },
        // Mixed: breakdowns plus an over-time series, so every style draws something.
        InsightSection(ORGASMS, "Orgasms") {
            it.totalSelfOrgasms > 0 ||
                it.totalPartnerOrgasms > 0 ||
                it.topEjaculation.isNotEmpty() ||
                it.yourOrgasmTypes.isNotEmpty() ||
                it.partnerOrgasmTypes.isNotEmpty() ||
                it.partnerFinishes.isNotEmpty()
        },
        InsightSection(DETAILS, "Details", styles = BREAKDOWN_STYLES) {
            it.topToys.isNotEmpty() || it.topProtection.isNotEmpty()
        },
        // Mixed like Orgasms: length trends plus breakdowns. Shown only while cycle tracking is on.
        InsightSection(CYCLE, "Cycle") { false },
    )

    private val byId = catalog.associateBy { it.id }

    /** Keeps known saved ids in order, then appends any catalog sections the save didn't mention. */
    fun ordered(order: List<String>): List<InsightSection> {
        val known = order.mapNotNull { byId[it] }
        val seen = known.mapTo(mutableSetOf()) { it.id }
        return known + catalog.filter { it.id !in seen }
    }
}

// ⏣ HW ⏣ · code · tools · homelab · ⏣ HW ⏣
