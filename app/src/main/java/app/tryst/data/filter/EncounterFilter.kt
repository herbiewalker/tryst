package app.tryst.data.filter

import app.tryst.data.db.entity.DisplayLabel
import app.tryst.data.db.entity.Initiator
import app.tryst.data.db.entity.Mood
import app.tryst.data.db.entity.Place
import app.tryst.data.db.entity.Protection
import app.tryst.data.db.relation.EncounterWithDetails
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * An inclusive local-date window. Kept separate from the encounter's epoch-millis storage so the
 * whole filter is pure and JVM-testable — the query resolves an encounter's local date via a [ZoneId].
 */
data class DateRange(val start: LocalDate, val end: LocalDate) {
    operator fun contains(date: LocalDate): Boolean = date >= start && date <= end
}

/**
 * Coarse time-of-day bucket derived from the encounter's local start hour. Boundaries follow the
 * common morning/afternoon/evening/night split; [NIGHT] deliberately wraps past midnight (21:00–04:59).
 */
enum class TimeOfDay(override val label: String) : DisplayLabel {
    MORNING("Morning"), // 05:00–11:59
    AFTERNOON("Afternoon"), // 12:00–16:59
    EVENING("Evening"), // 17:00–20:59
    NIGHT("Night"), // 21:00–04:59
    ;

    companion object {
        fun of(hour: Int): TimeOfDay = when (hour) {
            in 5..11 -> MORNING
            in 12..16 -> AFTERNOON
            in 17..20 -> EVENING
            else -> NIGHT
        }
    }
}

/**
 * A declarative selection of a subset of the encounter log — the shared foundation (FILT-1) under
 * Search, the Insights scope/Explorer, the gallery, and selective-erase, so filtering is written once.
 *
 * **Semantics:** AND *across* categories, OR (any-of) *within* a multi-select category. An empty
 * value in a category means "no constraint from this category" (it matches everything), so the
 * default [EncounterFilter] (all-empty) matches the whole log — see [isActive].
 *
 * Pure data — no Android/Compose types, no DB access — so it and [matches] are unit-tested on the JVM
 * exactly like [app.tryst.data.stats.InsightsEngine]. It queries the existing log; **no schema change**.
 *
 * Ids (acts/positions/occasions/kinks/toys) are the stored `custom:<uuid>` refs, matched as-is — the
 * filter never needs to resolve labels, so it stays label-agnostic (label-based free text is the
 * consuming Search feature's job, layered on top of this via [noteContains] + resolved sets).
 */
data class EncounterFilter(
    /** Match if the encounter's local date falls in *any* range. Empty = any date. */
    val dateRanges: List<DateRange> = emptyList(),
    /** Partner row ids; an encounter matches if it involves any of them. */
    val partnerIds: Set<String> = emptySet(),
    /** Also match solo encounters (no partners). Combines with [partnerIds] as OR within the partner category. */
    val includeSolo: Boolean = false,
    /** Act ids (gave ∪ received). */
    val actIds: Set<String> = emptySet(),
    val positionIds: Set<String> = emptySet(),
    val places: Set<Place> = emptySet(),
    val occasionIds: Set<String> = emptySet(),
    val kinkIds: Set<String> = emptySet(),
    val toyIds: Set<String> = emptySet(),
    val protection: Set<Protection> = emptySet(),
    val moods: Set<Mood> = emptySet(),
    val initiators: Set<Initiator> = emptySet(),
    val weekdays: Set<DayOfWeek> = emptySet(),
    val timesOfDay: Set<TimeOfDay> = emptySet(),
    /** Inclusive rating band (e.g. `4..5`); unrated encounters never match a set band. */
    val ratingRange: IntRange? = null,
    /** Inclusive duration band in minutes; encounters with no recorded duration never match a set band. */
    val durationRange: IntRange? = null,
    /** null = don't care; true = must have ≥1 photo/media; false = must have none. */
    val hasPhoto: Boolean? = null,
    /** null = don't care; true = must have a non-blank note; false = must have none. */
    val hasNote: Boolean? = null,
    /** Case-insensitive substring on the note; blank/null = no constraint. */
    val noteContains: String? = null,
) {
    /** True when at least one constraint is set (i.e. this filter would narrow the log). */
    val isActive: Boolean
        get() = this != EMPTY

    /**
     * Whether [e] satisfies every set category. [zone] resolves the stored epoch-millis start into a
     * local date/hour for the date, weekday, and time-of-day checks (default: the system zone).
     */
    @Suppress("CyclomaticComplexMethod", "ReturnCount")
    fun matches(e: EncounterWithDetails, zone: ZoneId = ZoneId.systemDefault()): Boolean {
        val enc = e.encounter
        val zoned = Instant.ofEpochMilli(enc.startAt).atZone(zone)
        val date = zoned.toLocalDate()

        // Date — OR across the configured ranges.
        if (dateRanges.isNotEmpty() && dateRanges.none { date in it }) return false

        // Partners — any selected partner, or (opt-in) a solo encounter.
        if (partnerIds.isNotEmpty() || includeSolo) {
            val ids = e.partners.mapTo(HashSet()) { it.id }
            val byPartner = partnerIds.isNotEmpty() && partnerIds.any { it in ids }
            val bySolo = includeSolo && ids.isEmpty()
            if (!byPartner && !bySolo) return false
        }

        // Multi-select id/enum categories — each is any-of.
        val acts = (enc.practicesPerformed ?: emptySet()) + (enc.practicesReceived ?: emptySet())
        if (!anyOf(actIds, acts)) return false
        if (!anyOf(positionIds, enc.positions ?: emptySet())) return false
        if (!anyOf(places, enc.contexts ?: emptySet())) return false
        if (!anyOf(occasionIds, enc.occasions ?: emptySet())) return false
        if (!anyOf(kinkIds, enc.kinks ?: emptySet())) return false
        if (!anyOf(toyIds, enc.toys ?: emptySet())) return false
        if (!anyOf(protection, enc.protectionUsed)) return false

        // Single-value categories — membership in the selected set.
        val mood = enc.mood
        if (moods.isNotEmpty() && (mood == null || mood !in moods)) return false
        val initiator = enc.initiator
        if (initiators.isNotEmpty() && (initiator == null || initiator !in initiators)) return false
        if (weekdays.isNotEmpty() && date.dayOfWeek !in weekdays) return false
        if (timesOfDay.isNotEmpty() && TimeOfDay.of(zoned.hour) !in timesOfDay) return false

        // Numeric bands — a set band excludes encounters with no recorded value.
        val rating = enc.satisfactionRating
        if (ratingRange != null && (rating == null || rating !in ratingRange)) return false
        val duration = enc.durationMin
        if (durationRange != null && (duration == null || duration !in durationRange)) return false

        // Presence flags.
        if (hasPhoto != null && e.media.isNotEmpty() != hasPhoto) return false
        if (hasNote != null && !enc.note.isNullOrBlank() != hasNote) return false
        if (!noteContains.isNullOrBlank() && enc.note?.contains(noteContains, ignoreCase = true) != true) return false

        return true
    }

    private fun <T> anyOf(selected: Set<T>, values: Set<T>): Boolean = selected.isEmpty() || selected.any { it in values }

    companion object {
        val EMPTY = EncounterFilter()
    }
}

/** Applies an [EncounterFilter] to the log — the stateless query, mirroring `InsightsEngine.compute`. */
object EncounterQuery {
    fun filter(
        encounters: List<EncounterWithDetails>,
        filter: EncounterFilter,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<EncounterWithDetails> = if (!filter.isActive) encounters else encounters.filter { filter.matches(it, zone) }
}
