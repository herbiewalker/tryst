package app.tryst.data.stats

import app.tryst.data.db.entity.EjaculationLocation
import app.tryst.data.db.entity.EncounterEntity
import app.tryst.data.db.entity.Mood
import app.tryst.data.db.entity.PartnerEntity
import app.tryst.data.db.entity.Practice
import app.tryst.data.db.entity.Protection
import app.tryst.data.db.relation.EncounterWithDetails
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class InsightsEngineTest {

    private val zone = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 6, 7) // a Sunday

    private fun epoch(date: LocalDate): Long =
        date.atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun encounter(
        id: String,
        date: LocalDate,
        rating: Int? = null,
        durationMin: Int? = null,
        mood: Mood? = null,
        orgasmCountSelf: Int? = null,
        partnerOrgasms: Map<String, Int>? = null,
        acts: Set<String>? = null,
        protection: Set<Protection> = emptySet(),
        ejaculation: Map<Int, EjaculationLocation>? = null,
        partners: List<PartnerEntity> = emptyList(),
    ) = EncounterWithDetails(
        encounter = EncounterEntity(
            id = id,
            startAt = epoch(date),
            durationMin = durationMin,
            satisfactionRating = rating,
            mood = mood,
            protectionUsed = protection,
            orgasmCountSelf = orgasmCountSelf,
            partnerOrgasms = partnerOrgasms,
            ejaculationLocations = ejaculation,
            practicesPerformed = acts,
            createdAt = 0,
            updatedAt = 0,
        ),
        partners = partners,
        positions = emptyList(),
        tags = emptyList(),
        media = emptyList(),
        location = null,
    )

    private fun partner(id: String, name: String?) =
        PartnerEntity(id, name, isAnonymous = name == null, color = null, note = null, archivedAt = null, createdAt = 0, updatedAt = 0)

    @Test
    fun emptyLogReturnsEmpty() {
        val result = InsightsEngine.compute(emptyList(), zone = zone, today = today)
        assertTrue(result.isEmpty)
        assertEquals(Insights.EMPTY, result)
    }

    @Test
    fun countsTotalsThisMonthAndYear() {
        val log = listOf(
            encounter("a", today),                       // this month + year
            encounter("b", LocalDate.of(2026, 5, 30)),   // this year, prev month
            encounter("c", LocalDate.of(2025, 6, 7)),    // last year
        )
        val r = InsightsEngine.compute(log, zone = zone, today = today)
        assertEquals(3, r.totalCount)
        assertEquals(1, r.thisMonthCount)
        assertEquals(2, r.thisYearCount)
        assertEquals(0L, r.daysSinceLast) // most recent is today
    }

    @Test
    fun averagesRatingDurationAndOrgasms() {
        val log = listOf(
            encounter("a", today, rating = 5, durationMin = 30, orgasmCountSelf = 2),
            encounter("b", today.minusDays(1), rating = 3, durationMin = 10, orgasmCountSelf = 0),
            encounter("c", today.minusDays(2)), // no rating/duration/orgasm recorded
        )
        val r = InsightsEngine.compute(log, zone = zone, today = today)
        assertEquals(4.0, r.avgRating!!, 0.001)
        assertEquals(40, r.totalDurationMin)
        assertEquals(20.0, r.avgDurationMin!!, 0.001)
        assertEquals(2, r.totalSelfOrgasms)
        assertEquals(1.0, r.avgSelfOrgasms!!, 0.001) // averaged over the 2 with a recorded count
        // ratings 3 and 5 each appear once
        assertEquals(1, r.ratingHistogram[2])
        assertEquals(1, r.ratingHistogram[4])
    }

    @Test
    fun currentAndLongestStreakInWeeks() {
        // Three consecutive weeks including this one, then a gap, then two consecutive weeks.
        val log = listOf(
            encounter("w0", today),
            encounter("w1", today.minusWeeks(1)),
            encounter("w2", today.minusWeeks(2)),
            encounter("g1", today.minusWeeks(6)),
            encounter("g2", today.minusWeeks(7)),
        )
        val r = InsightsEngine.compute(log, zone = zone, today = today)
        assertEquals(3, r.currentStreakWeeks)
        assertEquals(3, r.longestStreakWeeks)
    }

    @Test
    fun partnerOrgasmsCombineMapAndLegacy() {
        val log = listOf(
            encounter("a", today, partnerOrgasms = mapOf("p1" to 2, "p2" to 1)),
        )
        val r = InsightsEngine.compute(log, zone = zone, today = today)
        assertEquals(3, r.totalPartnerOrgasms)
    }

    @Test
    fun topPartnersAndAttributesRankByCount() {
        val sam = partner("p1", "Sam")
        val anon = partner("p2", null)
        val log = listOf(
            encounter("a", today, mood = Mood.PASSIONATE, partners = listOf(sam)),
            encounter("b", today.minusDays(1), mood = Mood.PASSIONATE, partners = listOf(sam, anon)),
            encounter("c", today.minusDays(2), mood = Mood.TENDER, partners = listOf(sam, anon)),
        )
        val r = InsightsEngine.compute(log, zone = zone, today = today)
        assertEquals(Tally("Sam", 3), r.topPartners.first())
        assertEquals(Tally("Anonymous", 2), r.topPartners[1])
        assertEquals(Tally("Passionate", 2), r.topMoods.first())
    }

    @Test
    fun actsCountOncePerEncounterAndResolveLabels() {
        val log = listOf(
            encounter("a", today, acts = setOf(Practice.VAGINAL.name, "custom:xyz")),
            encounter("b", today.minusDays(1), acts = setOf(Practice.VAGINAL.name)),
        )
        val r = InsightsEngine.compute(
            log,
            customActLabels = mapOf("xyz" to "My Custom Act"),
            zone = zone,
            today = today,
        )
        assertEquals(Tally("Vaginal", 2), r.topActs.first())
        assertTrue(r.topActs.any { it.label == "My Custom Act" && it.count == 1 })
    }

    @Test
    fun monthlyHasTwelveBucketsEndingThisMonth() {
        val r = InsightsEngine.compute(listOf(encounter("a", today)), zone = zone, today = today)
        assertEquals(12, r.monthly.size)
        assertEquals(1, r.monthly.last().count) // current month holds the one encounter
        assertEquals(7, r.byWeekday.size)
    }

    @Test
    fun ejaculationTallies() {
        val log = listOf(
            encounter("a", today, ejaculation = mapOf(0 to EjaculationLocation.VAGINAL, 1 to EjaculationLocation.ON_FACE)),
        )
        val r = InsightsEngine.compute(log, zone = zone, today = today)
        assertTrue(r.topEjaculation.any { it.label == EjaculationLocation.VAGINAL.label })
        assertTrue(r.topEjaculation.any { it.label == EjaculationLocation.ON_FACE.label })
    }
}
