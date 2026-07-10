package app.tryst.data.search

import app.tryst.data.db.relation.EncounterWithDetails
import java.text.Normalizer

/**
 * The user's custom-catalog `id -> label` maps, one per id-based category. Categories have shipped
 * empty since v12, so essentially every act/position/kink/toy/occasion an encounter references is a
 * `custom:<uuid>` row and needs these maps to become searchable (and displayable) text.
 */
data class CatalogLabels(
    val acts: Map<String, String> = emptyMap(),
    val positions: Map<String, String> = emptyMap(),
    val kinks: Map<String, String> = emptyMap(),
    val toys: Map<String, String> = emptyMap(),
    val occasions: Map<String, String> = emptyMap(),
) {
    companion object {
        val EMPTY = CatalogLabels()
    }
}

/** The parts of an encounter that search looks at — and that the result card reports a match against. */
enum class SearchField(val label: String) {
    NOTE("Note"),
    PARTNER("Partner"),
    ACT("Acts"),
    POSITION("Positions"),
    PLACE("Place"),
    OCCASION("Occasion"),
    KINK("Kinks"),
    TOY("Toys"),
    MOOD("Mood"),
}

/**
 * One encounter's searchable content, extracted once per (log, catalog) change rather than per
 * keystroke. [values] are the human labels — the result card's expanded panel renders them directly —
 * and [folded] is the same text case- and accent-normalized for matching.
 */
data class SearchableEncounter(
    val encounter: EncounterWithDetails,
    val values: Map<SearchField, List<String>>,
    val folded: Map<SearchField, String>,
)

/** A matching encounter plus which fields the query actually hit, so the UI can explain the match. */
data class SearchHit(
    val searchable: SearchableEncounter,
    val matchedFields: Set<SearchField>,
) {
    val encounter: EncounterWithDetails get() = searchable.encounter
}

/** A half-open `[start, end)` span of a string to highlight. */
data class Highlight(val start: Int, val end: Int)

/**
 * Free-text search over the encounter log (SRCH-1) — the label-aware half of search, layered on top of
 * the label-agnostic [app.tryst.data.filter.EncounterFilter]. Structured constraints (dates, partners,
 * rating, photos) belong to the filter; this only answers *"does this encounter's visible text contain
 * what the user typed, and where?"*
 *
 * Matching is **AND across tokens** — each whitespace-separated token must appear in *some* field, so
 * `hotel kissing` finds encounters that are both, in any order and any field. Tokens never contain
 * whitespace, so a token can never bridge two adjacent values.
 *
 * Text is compared **folded**: lowercased and stripped of diacritics, so `cafe` matches `Café`. Folding
 * is length-preserving (one char in, one char out), which is what lets [highlightRanges] map folded
 * offsets straight back onto the original string.
 *
 * Pure data — no Android types — so it's unit-tested on the JVM like `InsightsEngine`.
 */
object EncounterSearch {

    private const val CUSTOM_PREFIX = "custom:"
    private val whitespace = Regex("\\s+")

    /**
     * Lowercases [text] and strips combining accents, **preserving length and offsets**: each character
     * is decomposed on its own and only its base character is kept, so `é` → `e` (1 char → 1 char).
     */
    fun fold(text: String): String = buildString(text.length) {
        for (ch in text) {
            val base = Normalizer.normalize(ch.toString(), Normalizer.Form.NFD).first()
            append(base.lowercaseChar())
        }
    }

    /** Splits a raw query into folded tokens. An all-blank query yields no tokens (matches everything). */
    fun tokenize(query: String): List<String> = fold(query.trim())
        .split(whitespace)
        .filter { it.isNotEmpty() }

    /** Extracts the searchable content of every encounter. Do this when the log or catalogs change — not per keystroke. */
    fun index(encounters: List<EncounterWithDetails>, labels: CatalogLabels = CatalogLabels.EMPTY): List<SearchableEncounter> = encounters.map { e ->
        val values = valuesOf(e, labels)
        SearchableEncounter(
            encounter = e,
            values = values,
            folded = values.mapValues { (_, list) -> fold(list.joinToString(" ")) },
        )
    }

    /** Every encounter in [index] whose text contains all [tokens], each tagged with the fields it hit. */
    fun search(index: List<SearchableEncounter>, tokens: List<String>): List<SearchHit> {
        if (tokens.isEmpty()) return index.map { SearchHit(it, emptySet()) }
        return index.mapNotNull { item ->
            // AND across tokens: every token must land somewhere.
            if (!tokens.all { token -> item.folded.values.any { it.contains(token) } }) return@mapNotNull null
            val matched = item.folded
                .filterValues { text -> tokens.any { text.contains(it) } }
                .keys
            SearchHit(item, matched)
        }
    }

    /**
     * Where each of [tokens] occurs in [text], merged and sorted — for bolding the matched words.
     * Offsets are into the **original** [text] (see [fold]).
     */
    fun highlightRanges(text: String, tokens: List<String>): List<Highlight> {
        if (tokens.isEmpty() || text.isEmpty()) return emptyList()
        val folded = fold(text)
        val spans = mutableListOf<Highlight>()
        for (token in tokens) {
            var i = folded.indexOf(token)
            while (i >= 0) {
                spans += Highlight(i, i + token.length)
                i = folded.indexOf(token, i + token.length)
            }
        }
        if (spans.isEmpty()) return emptyList()

        // Merge overlapping/adjacent spans so nested tokens don't double-style.
        spans.sortBy { it.start }
        val merged = mutableListOf(spans.first())
        for (span in spans.drop(1)) {
            val last = merged.last()
            if (span.start <= last.end) {
                merged[merged.lastIndex] = Highlight(last.start, maxOf(last.end, span.end))
            } else {
                merged += span
            }
        }
        return merged
    }

    /** The display labels of every searchable field. Empty fields are omitted entirely. */
    private fun valuesOf(e: EncounterWithDetails, labels: CatalogLabels): Map<SearchField, List<String>> {
        val enc = e.encounter
        val acts = (enc.practicesPerformed ?: emptySet()) + (enc.practicesReceived ?: emptySet())
        val partners = if (e.partners.isEmpty()) {
            listOf("Solo")
        } else {
            e.partners.map { it.displayName?.takeIf { n -> n.isNotBlank() } ?: "Anonymous" }
        }
        return buildMap {
            putIfNotEmpty(SearchField.NOTE, listOfNotNull(enc.note?.takeIf { it.isNotBlank() }))
            putIfNotEmpty(SearchField.PARTNER, partners)
            putIfNotEmpty(SearchField.ACT, resolveAll(acts, labels.acts))
            putIfNotEmpty(SearchField.POSITION, resolveAll(enc.positions, labels.positions))
            putIfNotEmpty(SearchField.PLACE, (enc.contexts ?: emptySet()).map { it.label }.sorted())
            putIfNotEmpty(SearchField.OCCASION, resolveAll(enc.occasions, labels.occasions))
            putIfNotEmpty(SearchField.KINK, resolveAll(enc.kinks, labels.kinks))
            putIfNotEmpty(SearchField.TOY, resolveAll(enc.toys, labels.toys))
            putIfNotEmpty(SearchField.MOOD, listOfNotNull(enc.mood?.label))
        }
    }

    private fun MutableMap<SearchField, List<String>>.putIfNotEmpty(field: SearchField, values: List<String>) {
        if (values.isNotEmpty()) put(field, values)
    }

    private fun resolveAll(ids: Set<String>?, custom: Map<String, String>): List<String> = (ids ?: emptySet()).map { resolve(it, custom) }.sorted()

    /**
     * A stored id → its human label. Custom ids resolve via [custom]; an id with no row (a deleted
     * catalog entry) falls back to the raw id so it never silently matches everything.
     */
    private fun resolve(id: String, custom: Map<String, String>): String = if (id.startsWith(CUSTOM_PREFIX)) {
        custom[id.removePrefix(CUSTOM_PREFIX)] ?: id
    } else {
        id
    }
}
