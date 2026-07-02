package app.tryst.data.achievements

import app.tryst.data.db.entity.Act
import app.tryst.data.db.entity.EncounterEntity
import app.tryst.data.db.entity.MediaEntity
import app.tryst.data.db.entity.Occasion
import app.tryst.data.db.entity.PartnerEntity
import app.tryst.data.db.relation.EncounterWithDetails
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AchievementEngineTest {

    private val zone = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 6, 8)

    private fun epoch(date: LocalDate): Long = date.atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun enc(
        id: String,
        date: LocalDate,
        rating: Int? = null,
        durationMin: Int? = null,
        orgasmSelf: Int? = null,
        partnerOrgasms: Map<String, Int>? = null,
        acts: Set<String>? = null,
        positions: Set<String>? = null,
        occasions: Set<Occasion>? = null,
        media: List<MediaEntity> = emptyList(),
        partners: List<PartnerEntity> = emptyList(),
    ) = EncounterWithDetails(
        encounter = EncounterEntity(
            id = id,
            startAt = epoch(date),
            durationMin = durationMin,
            satisfactionRating = rating,
            orgasmCountSelf = orgasmSelf,
            partnerOrgasms = partnerOrgasms,
            practicesPerformed = acts,
            positions = positions,
            occasions = occasions,
            createdAt = 0,
            updatedAt = 0,
        ),
        partners = partners,
        positions = emptyList(),
        tags = emptyList(),
        media = media,
        location = null,
    )

    private fun partner(id: String) = PartnerEntity(id, "P$id", isAnonymous = false, color = null, note = null, archivedAt = null, createdAt = 0, updatedAt = 0)

    private fun media(id: String) = MediaEntity(id, encounterId = "e", encFilePath = "/x", mimeType = "image/jpeg", createdAt = 0)

    private fun List<AchievementStatus>.byId(id: String) = first { it.def.id == id }

    @Test
    fun catalogIdsAreUnique() {
        val ids = Achievements.catalog.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun emptyLogUnlocksNothing() {
        val r = AchievementEngine.evaluate(emptyList(), zone, today)
        assertEquals(Achievements.catalog.size, r.size)
        assertTrue(r.none { it.unlocked })
        assertTrue(r.all { it.current == 0 && it.unlockedAt == null })
    }

    @Test
    fun firstTrystUnlocksWithItsDate() {
        val day = LocalDate.of(2026, 5, 1)
        val r = AchievementEngine.evaluate(listOf(enc("a", day)), zone, today)
        val first = r.byId("m_first")
        assertTrue(first.unlocked)
        assertEquals(day, first.unlockedAt)
    }

    @Test
    fun countMilestoneReportsProgressAndUnlockDateOfTheTenth() {
        val days = (1..10).map { LocalDate.of(2026, 1, it) }
        val r = AchievementEngine.evaluate(days.mapIndexed { i, d -> enc("e$i", d) }, zone, today)
        val ten = r.byId("m_10")
        assertTrue(ten.unlocked)
        assertEquals(LocalDate.of(2026, 1, 10), ten.unlockedAt) // the 10th encounter's date
        // 25-milestone is locked, with current = 10 / 25 and no date.
        val twentyFive = r.byId("m_25")
        assertFalse(twentyFive.unlocked)
        assertEquals(10, twentyFive.current)
        assertNull(twentyFive.unlockedAt)
    }

    @Test
    fun sumRuleAccumulatesOrgasms() {
        val r = AchievementEngine.evaluate(
            listOf(
                enc("a", LocalDate.of(2026, 1, 1), orgasmSelf = 4),
                enc("b", LocalDate.of(2026, 1, 2), orgasmSelf = 6),
            ),
            zone,
            today,
        )
        val good = r.byId("p_self10")
        assertTrue(good.unlocked)
        assertEquals(LocalDate.of(2026, 1, 2), good.unlockedAt) // crossed 10 on the second
        assertEquals(10, good.current)
    }

    @Test
    fun distinctRuleCountsUniqueActsAndDatesTheThreshold() {
        // 5 distinct acts: first 4 on day1, the 5th on day2 → unlock date = day2.
        val r = AchievementEngine.evaluate(
            listOf(
                enc("a", LocalDate.of(2026, 2, 1), acts = setOf(Act.KISSING.name, Act.ORAL.name, Act.VAGINAL.name, Act.MANUAL.name)),
                enc("b", LocalDate.of(2026, 2, 2), acts = setOf(Act.KISSING.name, Act.ANAL.name)),
            ),
            zone,
            today,
        )
        val explorer = r.byId("v_acts5")
        assertTrue(explorer.unlocked)
        assertEquals(5, explorer.current)
        assertEquals(LocalDate.of(2026, 2, 2), explorer.unlockedAt)
    }

    @Test
    fun distinctPartnersCountById() {
        val r = AchievementEngine.evaluate(
            listOf(
                enc("a", LocalDate.of(2026, 1, 1), partners = listOf(partner("1"), partner("2"))),
                enc("b", LocalDate.of(2026, 1, 2), partners = listOf(partner("3"))),
            ),
            zone,
            today,
        )
        assertTrue(r.byId("v_partners3").unlocked)
        assertEquals(3, r.byId("v_partners3").current)
    }

    @Test
    fun streakUnlocksOnConsecutiveWeeks() {
        // Three consecutive Mondays → a 3-week streak (clears the 2-week achievement).
        val r = AchievementEngine.evaluate(
            listOf(
                enc("w0", LocalDate.of(2026, 1, 5)),
                enc("w1", LocalDate.of(2026, 1, 12)),
                enc("w2", LocalDate.of(2026, 1, 19)),
            ),
            zone,
            today,
        )
        assertTrue(r.byId("s_2").unlocked)
        assertEquals(LocalDate.of(2026, 1, 12), r.byId("s_2").unlockedAt) // 2-in-a-row reached here
        assertFalse(r.byId("s_4").unlocked)
        assertEquals(3, r.byId("s_4").current) // longest streak so far
    }

    @Test
    fun countTargetOneOccasionAndPhotoAndMarathon() {
        val r = AchievementEngine.evaluate(
            listOf(
                enc("a", LocalDate.of(2026, 3, 1), occasions = setOf(Occasion.MORNING_SEX), media = listOf(media("m1")), durationMin = 75),
            ),
            zone,
            today,
        )
        assertTrue(r.byId("o_morning").unlocked)
        assertTrue(r.byId("x_photo").unlocked)
        assertTrue(r.byId("x_marathon").unlocked)
        // A short, photo-less, plain encounter would not have unlocked these.
        val none = AchievementEngine.evaluate(listOf(enc("b", LocalDate.of(2026, 3, 2), durationMin = 20)), zone, today)
        assertFalse(none.byId("o_morning").unlocked)
        assertFalse(none.byId("x_photo").unlocked)
        assertFalse(none.byId("x_marathon").unlocked)
    }

    @Test
    fun summaryCountsAndSurfacesRecentAndNearest() {
        val r = AchievementEngine.evaluate(
            listOf(
                enc("a", today.minusDays(2)), // recent first tryst
                enc("b", today.minusDays(1)),
                enc("c", today),
            ),
            zone,
            today,
        )
        val s = AchievementEngine.summarize(r, today)
        assertEquals(r.count { it.unlocked }, s.unlockedCount)
        assertEquals(Achievements.catalog.size, s.total)
        assertTrue(s.recent.any { it.def.id == "m_first" }) // unlocked within the window
        assertTrue(s.nearest.all { !it.unlocked })
    }
}
