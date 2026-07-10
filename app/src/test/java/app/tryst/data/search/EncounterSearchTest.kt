package app.tryst.data.search

import app.tryst.data.db.entity.EncounterEntity
import app.tryst.data.db.entity.Mood
import app.tryst.data.db.entity.PartnerEntity
import app.tryst.data.db.entity.Place
import app.tryst.data.db.relation.EncounterWithDetails
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EncounterSearchTest {

    private val labels = CatalogLabels(
        acts = mapOf("a1" to "Kissing", "a2" to "Cuddling"),
        positions = mapOf("p1" to "Spooning"),
        kinks = mapOf("k1" to "Roleplay"),
        toys = mapOf("t1" to "Vibrator"),
        occasions = mapOf("o1" to "Date night"),
    )

    private fun partner(id: String, name: String?) = PartnerEntity(id, name, isAnonymous = name == null, color = null, note = null, archivedAt = null, createdAt = 0, updatedAt = 0)

    @Suppress("LongParameterList")
    private fun encounter(
        id: String = "e",
        note: String? = null,
        acts: Set<String> = emptySet(),
        received: Set<String> = emptySet(),
        positions: Set<String> = emptySet(),
        kinks: Set<String> = emptySet(),
        toys: Set<String> = emptySet(),
        occasions: Set<String> = emptySet(),
        places: Set<Place> = emptySet(),
        mood: Mood? = null,
        partners: List<PartnerEntity> = emptyList(),
    ) = EncounterWithDetails(
        encounter = EncounterEntity(
            id = id,
            startAt = 0,
            note = note,
            mood = mood,
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
        media = emptyList(),
        location = null,
    )

    /** Runs a raw query against a one-encounter index. */
    private fun hit(e: EncounterWithDetails, query: String): SearchHit? = EncounterSearch.search(EncounterSearch.index(listOf(e), labels), EncounterSearch.tokenize(query)).firstOrNull()

    private fun matches(e: EncounterWithDetails, query: String) = hit(e, query) != null

    // --- folding ---------------------------------------------------------

    @Test
    fun foldLowercasesStripsAccentsAndPreservesLength() {
        assertEquals("cafe", EncounterSearch.fold("Café"))
        assertEquals("aeiou", EncounterSearch.fold("áéíóú"))
        assertEquals("Café".length, EncounterSearch.fold("Café").length)
    }

    @Test
    fun accentInsensitiveMatching() {
        val e = encounter(note = "At the Café")
        assertTrue(matches(e, "cafe"))
        assertTrue(matches(e, "CAFÉ"))
    }

    // --- tokenizing ------------------------------------------------------

    @Test
    fun blankQueryMatchesEverything() {
        assertEquals(emptyList<String>(), EncounterSearch.tokenize("   "))
        assertTrue(matches(encounter(), ""))
        assertTrue(matches(encounter(), "   "))
    }

    @Test
    fun tokenizeFoldsAndSplitsOnWhitespace() {
        assertEquals(listOf("hotel", "kissing"), EncounterSearch.tokenize("  Hotel   KISSING "))
    }

    // --- matching --------------------------------------------------------

    @Test
    fun matchesNoteCaseInsensitively() {
        val e = encounter(note = "A Memorable evening")
        assertTrue(matches(e, "memorable"))
        assertTrue(matches(e, "MEMORABLE"))
        assertFalse(matches(e, "forgettable"))
    }

    @Test
    fun matchesPartnerNameAndSoloAndAnonymous() {
        assertTrue(matches(encounter(partners = listOf(partner("p1", "Sam"))), "sam"))
        assertFalse(matches(encounter(partners = listOf(partner("p1", "Sam"))), "alex"))
        assertTrue(matches(encounter(partners = emptyList()), "solo"))
        assertTrue(matches(encounter(partners = listOf(partner("p1", null))), "anonymous"))
        assertFalse(matches(encounter(partners = listOf(partner("p1", "Sam"))), "solo"))
    }

    @Test
    fun matchesResolvedCategoryLabels() {
        val e = encounter(
            acts = setOf("custom:a1"),
            positions = setOf("custom:p1"),
            kinks = setOf("custom:k1"),
            toys = setOf("custom:t1"),
            occasions = setOf("custom:o1"),
            places = setOf(Place.HOTEL),
            mood = Mood.PLAYFUL,
        )
        listOf("kissing", "spooning", "roleplay", "vibrator", "date", "hotel", "playful")
            .forEach { assertTrue("expected '$it' to match", matches(e, it)) }
    }

    @Test
    fun matchesReceivedActsNotJustGiven() {
        assertTrue(matches(encounter(received = setOf("custom:a2")), "cuddling"))
    }

    @Test
    fun multiTokenQueryIsAndAcrossFields() {
        val e = encounter(
            note = "great night",
            places = setOf(Place.HOTEL),
            acts = setOf("custom:a1"),
            partners = listOf(partner("p1", "Sam")),
        )
        assertTrue(matches(e, "hotel kissing"))
        assertTrue(matches(e, "kissing hotel"))
        assertTrue(matches(e, "sam great"))
        assertFalse(matches(e, "hotel beach"))
    }

    @Test
    fun tokensCannotBridgeTwoAdjacentValues() {
        val e = encounter(note = "great night", partners = listOf(partner("p1", "Sam")))
        assertFalse(matches(e, "nightsam"))
    }

    @Test
    fun unresolvedIdFallsBackToRawIdNotEveryEncounter() {
        val e = encounter(acts = setOf("custom:gone"))
        assertTrue(matches(e, "gone"))
        assertFalse(matches(e, "kissing"))
    }

    // --- matched fields --------------------------------------------------

    @Test
    fun reportsWhichFieldsMatched() {
        val e = encounter(
            note = "great night",
            acts = setOf("custom:a1"),
            places = setOf(Place.HOTEL),
            partners = listOf(partner("p1", "Sam")),
        )
        assertEquals(setOf(SearchField.ACT), hit(e, "kissing")!!.matchedFields)
        assertEquals(setOf(SearchField.PLACE), hit(e, "hotel")!!.matchedFields)
        // A multi-token query reports every field any token hit.
        assertEquals(setOf(SearchField.PLACE, SearchField.ACT), hit(e, "hotel kissing")!!.matchedFields)
    }

    @Test
    fun emptyQueryReportsNoMatchedFields() {
        val h = hit(encounter(note = "x"), "")!!
        assertTrue(h.matchedFields.isEmpty())
    }

    // --- index -----------------------------------------------------------

    @Test
    fun indexExposesDisplayValuesAndOmitsEmptyFields() {
        val e = encounter(note = "hi", acts = setOf("custom:a1", "custom:a2"), partners = emptyList())
        val item = EncounterSearch.index(listOf(e), labels).single()
        assertEquals(listOf("Cuddling", "Kissing"), item.values[SearchField.ACT]) // sorted for stable display
        assertEquals(listOf("Solo"), item.values[SearchField.PARTNER])
        assertEquals(null, item.values[SearchField.TOY]) // no toys -> field absent
    }

    // --- highlighting ----------------------------------------------------

    @Test
    fun highlightRangesFindAllOccurrencesOnOriginalOffsets() {
        val spans = EncounterSearch.highlightRanges("Bed and bedding", listOf("bed"))
        assertEquals(listOf(Highlight(0, 3), Highlight(8, 11)), spans)
    }

    @Test
    fun highlightRangesMapThroughAccents() {
        // "Café" -> folded "cafe"; the span must point at the original chars.
        assertEquals(listOf(Highlight(0, 4)), EncounterSearch.highlightRanges("Café", listOf("cafe")))
    }

    @Test
    fun highlightRangesMergeOverlaps() {
        // "kiss" and "issing" overlap inside "kissing" -> one merged span.
        assertEquals(listOf(Highlight(0, 7)), EncounterSearch.highlightRanges("kissing", listOf("kiss", "issing")))
    }

    @Test
    fun highlightRangesEmptyWhenNoTokensOrNoText() {
        assertTrue(EncounterSearch.highlightRanges("text", emptyList()).isEmpty())
        assertTrue(EncounterSearch.highlightRanges("", listOf("a")).isEmpty())
    }
}
