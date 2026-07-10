package app.tryst.data.filter

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DateScopeTest {

    @Test
    fun allTimeHasNoRange() {
        assertNull(DateScope.AllTime.range())
    }

    @Test
    fun yearSpansJanuaryFirstToDecemberThirtyFirst() {
        val range = DateScope.Year(2025).range()!!
        assertEquals(LocalDate.of(2025, 1, 1), range.start)
        assertEquals(LocalDate.of(2025, 12, 31), range.end)
    }

    @Test
    fun customReturnsItsOwnRange() {
        val range = DateRange(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31))
        assertEquals(range, DateScope.Custom(range).range())
    }

    @Test
    fun quartersCoverTheYearWithoutGapsOrOverlap() {
        val expected = listOf(
            DateRange(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 3, 31)),
            DateRange(LocalDate.of(2025, 4, 1), LocalDate.of(2025, 6, 30)),
            DateRange(LocalDate.of(2025, 7, 1), LocalDate.of(2025, 9, 30)),
            DateRange(LocalDate.of(2025, 10, 1), LocalDate.of(2025, 12, 31)),
        )
        for (q in 1..4) assertEquals(expected[q - 1], DateScope.Quarter(2025, q).range())
    }

    @Test
    fun q1OfALeapYearEndsOnMarch31() {
        // Guards the plusMonths(3).minusDays(1) arithmetic against February's length.
        assertEquals(LocalDate.of(2024, 3, 31), DateScope.Quarter(2024, 1).range()!!.end)
    }

    @Test(expected = IllegalArgumentException::class)
    fun quarterOutsideOneToFourIsRejected() {
        DateScope.Quarter(2025, 5)
    }

    @Test
    fun anchorYearIsSetOnlyForYearAndQuarter() {
        assertEquals(2025, DateScope.Year(2025).anchorYear)
        assertEquals(2025, DateScope.Quarter(2025, 3).anchorYear)
        assertNull(DateScope.AllTime.anchorYear)
        assertNull(DateScope.Custom(DateRange(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 2, 1))).anchorYear)
    }

    @Test
    fun encodeDecodeRoundTrips() {
        val scopes = listOf(
            DateScope.AllTime,
            DateScope.Year(2024),
            DateScope.Quarter(2025, 3),
            DateScope.Custom(DateRange(LocalDate.of(2025, 1, 2), LocalDate.of(2025, 6, 7))),
        )
        scopes.forEach { assertEquals(it, DateScope.decode(DateScope.encode(it))) }
    }

    @Test
    fun decodeFallsBackToAllTimeOnAnythingUnrecognized() {
        // A missing pref, a corrupt value, or a format written by a future version must never crash.
        // "quarter:2025:9" also lands here: Quarter's init rejects it, and decode swallows that.
        listOf(null, "", "nonsense", "year:notanumber", "custom:2025-01-01", "custom:bad:worse", "quarter:2025:9", "quarter:2025")
            .forEach { assertEquals(DateScope.AllTime, DateScope.decode(it)) }
    }
}
