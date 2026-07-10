package app.tryst.data.filter

import app.tryst.data.db.entity.EncounterEntity
import app.tryst.data.db.entity.Initiator
import app.tryst.data.db.entity.MediaEntity
import app.tryst.data.db.entity.Mood
import app.tryst.data.db.entity.PartnerEntity
import app.tryst.data.db.entity.Place
import app.tryst.data.db.entity.Protection
import app.tryst.data.db.relation.EncounterWithDetails
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class EncounterFilterTest {

    private val zone = ZoneOffset.UTC

    // matches()/of() derive local hour/date from the system zone by default, so pin it to UTC (the
    // zone epoch() builds instants in) for deterministic time-of-day/weekday buckets on any machine.
    private val originalZone = TimeZone.getDefault()

    @Before fun forceUtc() = TimeZone.setDefault(TimeZone.getTimeZone("UTC"))

    @After fun restoreZone() = TimeZone.setDefault(originalZone)

    private fun epoch(dateTime: LocalDateTime): Long = dateTime.toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun partner(id: String, name: String? = id) = PartnerEntity(id, name, isAnonymous = name == null, color = null, note = null, archivedAt = null, createdAt = 0, updatedAt = 0)

    private fun media(id: String) = MediaEntity(id = id, encounterId = "e", encFilePath = id, mimeType = "image/jpeg", createdAt = 0)

    @Suppress("LongParameterList")
    private fun encounter(
        id: String = "e",
        at: LocalDateTime = LocalDateTime.of(2026, 6, 7, 12, 0),
        rating: Int? = null,
        durationMin: Int? = null,
        note: String? = null,
        mood: Mood? = null,
        initiator: Initiator? = null,
        protection: Set<Protection> = emptySet(),
        acts: Set<String> = emptySet(),
        received: Set<String> = emptySet(),
        positions: Set<String> = emptySet(),
        places: Set<Place> = emptySet(),
        occasions: Set<String> = emptySet(),
        kinks: Set<String> = emptySet(),
        toys: Set<String> = emptySet(),
        partners: List<PartnerEntity> = emptyList(),
        media: List<MediaEntity> = emptyList(),
    ) = EncounterWithDetails(
        encounter = EncounterEntity(
            id = id,
            startAt = epoch(at),
            durationMin = durationMin,
            note = note,
            satisfactionRating = rating,
            mood = mood,
            initiator = initiator,
            protectionUsed = protection,
            practicesPerformed = acts.ifEmpty { null },
            practicesReceived = received.ifEmpty { null },
            positions = positions.ifEmpty { null },
            contexts = places.ifEmpty { null },
            occasions = occasions.ifEmpty { null },
            kinks = kinks.ifEmpty { null },
            toys = toys.ifEmpty { null },
            createdAt = 0,
            updatedAt = 0,
        ),
        partners = partners,
        positions = emptyList(),
        tags = emptyList(),
        media = media,
        location = null,
    )

    @Test
    fun emptyFilterIsInactiveAndMatchesEverything() {
        val f = EncounterFilter()
        assertFalse(f.isActive)
        assertTrue(f.matches(encounter()))
        val log = listOf(encounter("a"), encounter("b"))
        // Passthrough: the query returns the same list untouched.
        assertEquals(log, EncounterQuery.filter(log, f, zone))
    }

    @Test
    fun anySetFieldMakesTheFilterActive() {
        assertTrue(EncounterFilter(hasPhoto = false).isActive)
        assertTrue(EncounterFilter(moods = setOf(Mood.PLAYFUL)).isActive)
    }

    @Test
    fun dateRangesMatchAnyRangeInclusive() {
        val e = encounter(at = LocalDateTime.of(2026, 6, 7, 12, 0))
        // Two disjoint ranges — the second contains the encounter's date.
        val f = EncounterFilter(
            dateRanges = listOf(
                DateRange(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)),
                DateRange(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30)),
            ),
        )
        assertTrue(f.matches(e))
        // Boundaries are inclusive.
        assertTrue(EncounterFilter(dateRanges = listOf(DateRange(LocalDate.of(2026, 6, 7), LocalDate.of(2026, 6, 7)))).matches(e))
        assertFalse(EncounterFilter(dateRanges = listOf(DateRange(LocalDate.of(2026, 6, 8), LocalDate.of(2026, 6, 30)))).matches(e))
    }

    @Test
    fun partnersMatchAnySelectedId() {
        val e = encounter(partners = listOf(partner("p1"), partner("p2")))
        assertTrue(EncounterFilter(partnerIds = setOf("p2")).matches(e))
        assertTrue(EncounterFilter(partnerIds = setOf("p3", "p1")).matches(e))
        assertFalse(EncounterFilter(partnerIds = setOf("p3")).matches(e))
    }

    @Test
    fun soloMatchesOnlyEncountersWithNoPartners() {
        val solo = encounter("s", partners = emptyList())
        val withPartner = encounter("w", partners = listOf(partner("p1")))
        val f = EncounterFilter(includeSolo = true)
        assertTrue(f.matches(solo))
        assertFalse(f.matches(withPartner))
        // Combined with a partner id, the category is OR: either the partner OR solo.
        val combined = EncounterFilter(partnerIds = setOf("p1"), includeSolo = true)
        assertTrue(combined.matches(solo))
        assertTrue(combined.matches(withPartner))
    }

    @Test
    fun actsMatchAcrossGivenAndReceived() {
        val e = encounter(acts = setOf("custom:a1"), received = setOf("custom:a2"))
        assertTrue(EncounterFilter(actIds = setOf("custom:a2")).matches(e)) // received-only
        assertTrue(EncounterFilter(actIds = setOf("custom:a1")).matches(e)) // given-only
        assertFalse(EncounterFilter(actIds = setOf("custom:zz")).matches(e))
    }

    @Test
    fun multiSelectCategoriesAreAnyOf() {
        val e = encounter(
            positions = setOf("custom:p1"),
            places = setOf(Place.HOTEL),
            occasions = setOf("custom:o1"),
            kinks = setOf("custom:k1"),
            toys = setOf("custom:t1"),
            protection = setOf(Protection.CONDOM),
        )
        assertTrue(EncounterFilter(positionIds = setOf("custom:p1", "custom:other")).matches(e))
        assertTrue(EncounterFilter(places = setOf(Place.HOTEL, Place.HOME)).matches(e))
        assertTrue(EncounterFilter(occasionIds = setOf("custom:o1")).matches(e))
        assertTrue(EncounterFilter(kinkIds = setOf("custom:k1")).matches(e))
        assertTrue(EncounterFilter(toyIds = setOf("custom:t1")).matches(e))
        assertTrue(EncounterFilter(protection = setOf(Protection.CONDOM)).matches(e))
        assertFalse(EncounterFilter(places = setOf(Place.HOME)).matches(e))
    }

    @Test
    fun moodAndInitiatorMembership() {
        val e = encounter(mood = Mood.PLAYFUL, initiator = Initiator.ME)
        assertTrue(EncounterFilter(moods = setOf(Mood.PLAYFUL, Mood.WILD)).matches(e))
        assertFalse(EncounterFilter(moods = setOf(Mood.WILD)).matches(e))
        assertTrue(EncounterFilter(initiators = setOf(Initiator.ME)).matches(e))
        // A null-valued field never satisfies a set membership filter.
        assertFalse(EncounterFilter(moods = setOf(Mood.PLAYFUL)).matches(encounter(mood = null)))
    }

    @Test
    fun weekdayAndTimeOfDayDeriveFromLocalStart() {
        // 2026-06-07 is a Sunday; 08:00 local -> MORNING.
        val e = encounter(at = LocalDateTime.of(2026, 6, 7, 8, 0))
        assertTrue(EncounterFilter(weekdays = setOf(DayOfWeek.SUNDAY)).matches(e))
        assertFalse(EncounterFilter(weekdays = setOf(DayOfWeek.MONDAY)).matches(e))
        assertTrue(EncounterFilter(timesOfDay = setOf(TimeOfDay.MORNING)).matches(e))
        assertFalse(EncounterFilter(timesOfDay = setOf(TimeOfDay.NIGHT)).matches(e))
    }

    @Test
    fun timeOfDayNightWrapsPastMidnight() {
        assertEquals(TimeOfDay.NIGHT, TimeOfDay.of(23))
        assertEquals(TimeOfDay.NIGHT, TimeOfDay.of(2))
        assertEquals(TimeOfDay.MORNING, TimeOfDay.of(5))
        assertEquals(TimeOfDay.EVENING, TimeOfDay.of(20))
        val lateNight = encounter(at = LocalDateTime.of(2026, 6, 7, 1, 30))
        assertTrue(EncounterFilter(timesOfDay = setOf(TimeOfDay.NIGHT)).matches(lateNight))
    }

    @Test
    fun ratingAndDurationBandsExcludeUnrecordedValues() {
        val rated = encounter(rating = 4, durationMin = 30)
        assertTrue(EncounterFilter(ratingRange = 4..5).matches(rated))
        assertFalse(EncounterFilter(ratingRange = 1..3).matches(rated))
        assertTrue(EncounterFilter(durationRange = 20..40).matches(rated))
        assertFalse(EncounterFilter(durationRange = 40..60).matches(rated))
        // No recorded value is excluded by any set band.
        val blank = encounter(rating = null, durationMin = null)
        assertFalse(EncounterFilter(ratingRange = 1..5).matches(blank))
        assertFalse(EncounterFilter(durationRange = 0..1000).matches(blank))
    }

    @Test
    fun photoPresenceFlag() {
        val withPhoto = encounter(media = listOf(media("m1")))
        val without = encounter(media = emptyList())
        assertTrue(EncounterFilter(hasPhoto = true).matches(withPhoto))
        assertFalse(EncounterFilter(hasPhoto = true).matches(without))
        assertTrue(EncounterFilter(hasPhoto = false).matches(without))
        assertFalse(EncounterFilter(hasPhoto = false).matches(withPhoto))
    }

    @Test
    fun notePresenceAndSubstring() {
        val noted = encounter(note = "A memorable Evening")
        val blank = encounter(note = "  ")
        assertTrue(EncounterFilter(hasNote = true).matches(noted))
        assertFalse(EncounterFilter(hasNote = true).matches(blank))
        assertTrue(EncounterFilter(hasNote = false).matches(blank))
        // Case-insensitive substring.
        assertTrue(EncounterFilter(noteContains = "memorable").matches(noted))
        assertTrue(EncounterFilter(noteContains = "EVENING").matches(noted))
        assertFalse(EncounterFilter(noteContains = "morning").matches(noted))
        // Blank query is not a constraint.
        assertTrue(EncounterFilter(noteContains = "   ").matches(noted))
    }

    @Test
    fun categoriesCombineWithAnd() {
        val match = encounter(
            at = LocalDateTime.of(2026, 6, 5, 19, 0), // Friday evening
            rating = 5,
            places = setOf(Place.HOTEL),
            partners = listOf(partner("p1")),
        )
        val f = EncounterFilter(
            partnerIds = setOf("p1"),
            places = setOf(Place.HOTEL),
            ratingRange = 4..5,
            timesOfDay = setOf(TimeOfDay.EVENING),
        )
        assertTrue(f.matches(match))
        // Break exactly one condition (wrong place) -> the AND fails.
        assertFalse(f.matches(encounter(rating = 5, places = setOf(Place.HOME), partners = listOf(partner("p1")), at = LocalDateTime.of(2026, 6, 5, 19, 0))))
    }

    @Test
    fun queryFiltersTheLog() {
        val log = listOf(
            encounter("a", rating = 5),
            encounter("b", rating = 2),
            encounter("c", rating = 4),
        )
        val result = EncounterQuery.filter(log, EncounterFilter(ratingRange = 4..5), zone)
        assertEquals(listOf("a", "c"), result.map { it.encounter.id })
    }
}
