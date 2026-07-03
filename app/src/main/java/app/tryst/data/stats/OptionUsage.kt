package app.tryst.data.stats

import app.tryst.data.db.entity.EjaculationLocation
import app.tryst.data.db.entity.EncounterEntity
import app.tryst.data.db.entity.Mood
import app.tryst.data.db.entity.Occasion
import app.tryst.data.db.entity.Place
import app.tryst.data.db.entity.Protection

/**
 * How often each option has been picked across the whole log, per editor category (ENC-1). Pure
 * data — no Android types — so it's derived and unit-tested on the JVM, the same way [InsightsEngine]
 * is. Acts, positions, kinks, and toys are keyed by their stored id (a built-in enum name, or
 * `custom:<uuid>`); the remaining enum categories are keyed by the enum value itself.
 *
 * A count is "encounters that include the option", so an act logged as both given *and* received in
 * one encounter counts once (matching how [InsightsEngine] tallies acts).
 */
data class OptionUsage(
    val acts: Map<String, Int> = emptyMap(),
    val positions: Map<String, Int> = emptyMap(),
    val protection: Map<Protection, Int> = emptyMap(),
    val moods: Map<Mood, Int> = emptyMap(),
    val ejaculation: Map<EjaculationLocation, Int> = emptyMap(),
    val kinks: Map<String, Int> = emptyMap(),
    val places: Map<Place, Int> = emptyMap(),
    val occasions: Map<Occasion, Int> = emptyMap(),
    val toys: Map<String, Int> = emptyMap(),
) {
    companion object {
        val EMPTY = OptionUsage()

        fun from(encounters: List<EncounterEntity>): OptionUsage {
            fun <T> tally(select: (EncounterEntity) -> Iterable<T>): Map<T, Int> = encounters.flatMap(select).groupingBy { it }.eachCount()

            return OptionUsage(
                // Union per encounter (Set + Set), so an act given *and* received counts once.
                acts = tally { (it.practicesPerformed ?: emptySet()) + (it.practicesReceived ?: emptySet()) },
                positions = tally { it.positions ?: emptySet() },
                protection = tally { it.protectionUsed },
                moods = tally { listOfNotNull(it.mood) },
                ejaculation = tally { it.ejaculationLocations?.values?.flatten()?.toSet() ?: emptySet() },
                kinks = tally { it.kinks ?: emptySet() },
                places = tally { it.contexts ?: emptySet() },
                occasions = tally { it.occasions ?: emptySet() },
                toys = tally { it.toys ?: emptySet() },
            )
        }
    }
}

/** Default size of the inline (always-visible) set; the rest of the options stay in "More…". */
const val INLINE_TARGET = 8

/** An option must have been picked at least this many times before it auto-surfaces inline. */
const val INLINE_MIN_COUNT = 2

/**
 * The inline option set for one editor category (ENC-1): the user's most-frequently-picked options
 * first, then the hand-[curated] anchors backfilled in when they have fewer than [target] frequent
 * picks. So a brand-new log shows exactly today's curated set, while an established log surfaces the
 * user's own go-to choices (e.g. *Vasectomy* in Protection) without a "More…" tap.
 *
 * Order here is irrelevant to what the user sees — `SelectionField` re-sorts the inline set
 * alphabetically — so this only decides *membership*, which changes slowly. Everything stays
 * reachable via "More…".
 *
 * @param all every selectable option in the category (built-ins + customs).
 * @param usageOf how many encounters used an option (0 when never picked).
 */
fun <T> mostUsedCommon(
    curated: List<T>,
    all: List<T>,
    target: Int = INLINE_TARGET,
    minCount: Int = INLINE_MIN_COUNT,
    usageOf: (T) -> Int,
): List<T> {
    // Stable sort preserves `all`'s natural order among equal counts, so the cut is deterministic.
    val mostUsed = all
        .filter { usageOf(it) >= minCount }
        .sortedByDescending(usageOf)
        .take(target)
    if (mostUsed.size >= target) return mostUsed

    val present = mostUsed.toHashSet()
    val backfill = curated.filter { it !in present }.take(target - mostUsed.size)
    return mostUsed + backfill
}
