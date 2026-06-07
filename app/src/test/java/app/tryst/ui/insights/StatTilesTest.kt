package app.tryst.ui.insights

import app.tryst.data.stats.Insights
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StatTilesTest {

    @Test
    fun emptyOrderReturnsFullCatalogInOrder() {
        val result = StatTiles.ordered(emptyList())
        assertEquals(StatTiles.catalog.map { it.id }, result.map { it.id })
    }

    @Test
    fun keepsSavedOrderThenAppendsUnknownTiles() {
        val saved = listOf("avg_rating", "total") // a custom subset/order
        val result = StatTiles.ordered(saved).map { it.id }
        // Saved ids come first, in saved order...
        assertEquals("avg_rating", result[0])
        assertEquals("total", result[1])
        // ...then every other catalog tile appears exactly once.
        assertEquals(StatTiles.catalog.size, result.size)
        assertEquals(StatTiles.catalog.map { it.id }.toSet(), result.toSet())
    }

    @Test
    fun ignoresUnknownSavedIds() {
        val result = StatTiles.ordered(listOf("does_not_exist", "month")).map { it.id }
        assertEquals("month", result.first())
        assertEquals(StatTiles.catalog.size, result.size)
    }

    @Test
    fun valueExtractionFormatsAndOmitsWhenNotApplicable() {
        val tiles = StatTiles.catalog.associateBy { it.id }
        val empty = Insights.EMPTY

        // Always-present counters render even at zero.
        assertEquals("0", tiles.getValue("total").value(empty))
        // Optional figures are null when there's no data, so they're skipped in the grid.
        assertNull(tiles.getValue("avg_rating").value(empty))
        assertNull(tiles.getValue("avg_duration").value(empty))
        assertNull(tiles.getValue("orgasms_self").value(empty))

        val populated = empty.copy(
            totalCount = 10,
            avgRating = 4.5,
            avgDurationMin = 27.6,
            totalSelfOrgasms = 12,
            daysSinceLast = 0L,
            currentStreakWeeks = 1,
        )
        assertEquals("★ 4.5", tiles.getValue("avg_rating").value(populated))
        assertEquals("27 min", tiles.getValue("avg_duration").value(populated))
        assertEquals("12", tiles.getValue("orgasms_self").value(populated))
        assertEquals("Today", tiles.getValue("days_since").value(populated))
        assertEquals("1 wk", tiles.getValue("streak_current").value(populated))
    }

    @Test
    fun catalogIdsAreUnique() {
        val ids = StatTiles.catalog.map { it.id }
        assertTrue(ids.size == ids.toSet().size)
    }
}
