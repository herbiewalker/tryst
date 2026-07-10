package app.tryst.data.stats

import app.tryst.data.db.entity.EncounterEntity
import app.tryst.data.db.entity.Initiator
import app.tryst.data.db.entity.Mood
import app.tryst.data.db.entity.PartnerEntity
import app.tryst.data.db.entity.Protection
import app.tryst.data.db.relation.EncounterWithDetails
import app.tryst.data.filter.DateRange
import app.tryst.data.filter.DateScope
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InsightsEngineTest {

    private val zone = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 6, 7) // a Sunday

    private fun epoch(date: LocalDate): Long = date.atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun encounter(
        id: String,
        date: LocalDate,
        rating: Int? = null,
        durationMin: Int? = null,
        mood: Mood? = null,
        orgasmCountSelf: Int? = null,
        partnerOrgasms: Map<String, Int>? = null,
        acts: Set<String>? = null,
        kinks: Set<String>? = null,
        protection: Set<Protection> = emptySet(),
        ejaculation: Map<Int, Set<String>>? = null,
        initiator: Initiator? = null,
        partners: List<PartnerEntity> = emptyList(),
    ) = EncounterWithDetails(
        encounter = EncounterEntity(
            id = id,
            startAt = epoch(date),
            durationMin = durationMin,
            satisfactionRating = rating,
            mood = mood,
            initiator = initiator,
            protectionUsed = protection,
            orgasmCountSelf = orgasmCountSelf,
            partnerOrgasms = partnerOrgasms,
            ejaculationLocations = ejaculation,
            practicesPerformed = acts,
            kinks = kinks,
            createdAt = 0,
            updatedAt = 0,
        ),
        partners = partners,
        positions = emptyList(),
        tags = emptyList(),
        media = emptyList(),
        location = null,
    )

    private fun partner(id: String, name: String?) = PartnerEntity(id, name, isAnonymous = name == null, color = null, note = null, archivedAt = null, createdAt = 0, updatedAt = 0)

    @Test
    fun emptyLogReturnsEmpty() {
        val result = InsightsEngine.compute(emptyList(), zone = zone, today = today)
        assertTrue(result.isEmpty)
        assertEquals(Insights.EMPTY, result)
    }

    @Test
    fun countsTotalsThisMonthAndYear() {
        val log = listOf(
            encounter("a", today), // this month + year
            encounter("b", LocalDate.of(2026, 5, 30)), // this year, prev month
            encounter("c", LocalDate.of(2025, 6, 7)), // last year
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
    fun initiatorTally() {
        val log = listOf(
            encounter("a", today, initiator = Initiator.ME),
            encounter("b", today.minusDays(1), initiator = Initiator.ME),
            encounter("c", today.minusDays(2), initiator = Initiator.PARTNER),
        )
        val r = InsightsEngine.compute(log, zone = zone, today = today)
        assertEquals(Tally("Me", 2), r.topInitiators.first())
        assertTrue(r.topInitiators.any { it.label == "Partner" && it.count == 1 })
    }

    @Test
    fun orgasmsPerPartnerResolvesNamesAndSums() {
        val sam = partner("p1", "Sam")
        val anon = partner("p2", null)
        val log = listOf(
            encounter("a", today, partnerOrgasms = mapOf("p1" to 2), partners = listOf(sam)),
            encounter("b", today.minusDays(1), partnerOrgasms = mapOf("p1" to 1, "p2" to 3), partners = listOf(sam, anon)),
        )
        val r = InsightsEngine.compute(log, zone = zone, today = today)
        // Anonymous (3) outranks Sam (2+1=3 tie) -> tie broken by label; both present with right counts.
        assertEquals(3, r.orgasmsPerPartner.first { it.label == "Sam" }.count)
        assertEquals(3, r.orgasmsPerPartner.first { it.label == "Anonymous" }.count)
    }

    @Test
    fun orgasmsMonthlyHasTwelveBucketsSummingSelfAndPartner() {
        val log = listOf(
            encounter("a", today, orgasmCountSelf = 2, partnerOrgasms = mapOf("p1" to 1)),
        )
        val r = InsightsEngine.compute(log, zone = zone, today = today)
        assertEquals(12, r.orgasmsMonthly.size)
        assertEquals(3, r.orgasmsMonthly.last().count)
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
        // Acts are all user-owned rows now (FDP-5); resolve by the custom label map.
        val log = listOf(
            encounter("a", today, acts = setOf("custom:a1", "custom:xyz")),
            encounter("b", today.minusDays(1), acts = setOf("custom:a1")),
        )
        val r = InsightsEngine.compute(
            log,
            customActLabels = mapOf("a1" to "Kissing", "xyz" to "My Custom Act"),
            zone = zone,
            today = today,
        )
        assertEquals(Tally("Kissing", 2), r.topActs.first())
        assertTrue(r.topActs.any { it.label == "My Custom Act" && it.count == 1 })
    }

    @Test
    fun kinksResolveCustomLabels() {
        // Kinks ship with no built-ins (FDP-5); every kink is a custom entry resolved by its label map.
        val log = listOf(
            encounter("a", today, kinks = setOf("custom:k1", "custom:k2")),
            encounter("b", today.minusDays(1), kinks = setOf("custom:k1")),
        )
        val r = InsightsEngine.compute(
            log,
            customKinkLabels = mapOf("k1" to "My Custom Kink", "k2" to "Another Kink"),
            zone = zone,
            today = today,
        )
        assertEquals(Tally("My Custom Kink", 2), r.topKinks.first())
        assertTrue(r.topKinks.any { it.label == "Another Kink" && it.count == 1 })
    }

    @Test
    fun monthlyHasTwelveBucketsEndingThisMonth() {
        val r = InsightsEngine.compute(listOf(encounter("a", today)), zone = zone, today = today)
        assertEquals(12, r.monthly.size)
        assertEquals(1, r.monthly.last().count) // current month holds the one encounter
        assertEquals(7, r.byWeekday.size)
    }

    // --- INS-2: time scope ------------------------------------------------

    @Test
    fun scopeRestrictsEveryFigureToTheWindow() {
        val log = listOf(
            encounter("in1", LocalDate.of(2025, 3, 5), rating = 5),
            encounter("in2", LocalDate.of(2025, 8, 20), rating = 3),
            encounter("out", LocalDate.of(2026, 1, 10), rating = 1),
        )
        val r = InsightsEngine.compute(log, zone = zone, today = today, scope = year(2025))
        assertEquals(2, r.totalCount)
        assertTrue(r.isScoped)
        assertEquals(4.0, r.avgRating!!, 0.001) // the 2026 entry's ★1 is excluded
    }

    @Test
    fun scopeBucketsMonthsAcrossTheWindowNotTrailingTwelve() {
        // The bug this guards: month buckets used to be the 12 months ending *today*, so a past-year
        // scope produced twelve buckets for the wrong year, all zero.
        val log = listOf(encounter("a", LocalDate.of(2024, 2, 14)))
        val r = InsightsEngine.compute(log, zone = zone, today = today, scope = year(2024))
        assertEquals(12, r.monthly.size)
        assertEquals("Jan", r.monthly.first().label)
        assertEquals("Dec", r.monthly.last().label)
        assertEquals(1, r.monthly[1].count) // February holds the single encounter
        assertEquals(0, r.monthly[0].count)
    }

    @Test
    fun unscopedStillUsesTrailingTwelveMonths() {
        val r = InsightsEngine.compute(listOf(encounter("a", today)), zone = zone, today = today)
        assertEquals(12, r.monthly.size)
        assertEquals(1, r.monthly.last().count) // current month is the last bucket
        assertFalse(r.isScoped)
    }

    @Test
    fun multiYearScopeQualifiesMonthLabelsWithTheYear() {
        val scope = DateRange(LocalDate.of(2025, 11, 1), LocalDate.of(2026, 2, 28))
        val r = InsightsEngine.compute(listOf(encounter("a", LocalDate.of(2025, 12, 1))), zone = zone, today = today, scope = scope)
        assertEquals(4, r.monthly.size)
        assertEquals("Nov 25", r.monthly.first().label)
        assertEquals("Feb 26", r.monthly.last().label)
    }

    @Test
    fun scopeWithholdsTodayRelativeFigures() {
        val r = InsightsEngine.compute(listOf(encounter("a", LocalDate.of(2024, 6, 1))), zone = zone, today = today, scope = year(2024))
        // Both describe "as of today" and would be lies for a past window.
        assertEquals(null, r.daysSinceLast)
        assertEquals(0, r.currentStreakWeeks)
        // Longest streak is a property of the window itself, so it survives.
        assertEquals(1, r.longestStreakWeeks)
    }

    @Test
    fun emptyScopedWindowIsEmptyButStillMarkedScoped() {
        val log = listOf(encounter("a", LocalDate.of(2026, 6, 1)))
        val r = InsightsEngine.compute(log, zone = zone, today = today, scope = year(2021))
        assertTrue(r.isEmpty)
        assertTrue(r.isScoped) // "no trysts in 2021", not "no trysts yet"
    }

    @Test
    fun avgPerMonthDividesByTheWindowsMonths() {
        // 12 encounters across 2025 -> 1.0/month, regardless of how long ago 2025 was.
        val log = (1..12).map { m -> encounter("e$m", LocalDate.of(2025, m, 15)) }
        val r = InsightsEngine.compute(log, zone = zone, today = today, scope = year(2025))
        assertEquals(1.0, r.avgPerMonth, 0.001)
    }

    @Test
    fun scopeIsInclusiveOnBothEdges() {
        val log = listOf(
            encounter("start", LocalDate.of(2025, 1, 1)),
            encounter("end", LocalDate.of(2025, 12, 31)),
        )
        assertEquals(2, InsightsEngine.compute(log, zone = zone, today = today, scope = year(2025)).totalCount)
    }

    private fun year(y: Int) = DateScope.Year(y).range()

    @Test
    fun ejaculationTallies() {
        val log = listOf(
            encounter(
                "a",
                today,
                ejaculation = mapOf(
                    // Custom finish-location ids resolve to their labels via customEjaculationLabels.
                    0 to setOf("custom:v", "custom:s"),
                    1 to setOf("custom:f"),
                ),
            ),
        )
        val r = InsightsEngine.compute(
            log,
            customEjaculationLabels = mapOf("v" to "Inside vagina", "s" to "On stomach", "f" to "On face"),
            zone = zone,
            today = today,
        )
        assertTrue(r.topEjaculation.any { it.label == "Inside vagina" })
        assertTrue(r.topEjaculation.any { it.label == "On face" })
        // multi-select: both locations of a single orgasm are counted
        assertTrue(r.topEjaculation.any { it.label == "On stomach" })
    }
}
