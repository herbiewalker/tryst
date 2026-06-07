package app.tryst.ui.insights

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InsightSectionsTest {

    @Test
    fun emptyOrderReturnsFullCatalog() {
        assertEquals(InsightSections.catalog.map { it.id }, InsightSections.ordered(emptyList()).map { it.id })
    }

    @Test
    fun keepsSavedOrderThenAppendsRest() {
        val saved = listOf(InsightSections.ORGASMS, InsightSections.PEOPLE)
        val result = InsightSections.ordered(saved).map { it.id }
        assertEquals(InsightSections.ORGASMS, result[0])
        assertEquals(InsightSections.PEOPLE, result[1])
        assertEquals(InsightSections.catalog.size, result.size)
        assertEquals(InsightSections.catalog.map { it.id }.toSet(), result.toSet())
    }

    @Test
    fun ignoresUnknownIds() {
        val result = InsightSections.ordered(listOf("nope", InsightSections.ACTIVITY)).map { it.id }
        assertEquals(InsightSections.ACTIVITY, result.first())
        assertEquals(InsightSections.catalog.size, result.size)
    }

    @Test
    fun idsAreUnique() {
        val ids = InsightSections.catalog.map { it.id }
        assertTrue(ids.size == ids.toSet().size)
    }
}
