// SPDX-License-Identifier: GPL-3.0-or-later
// ╭──────────────────────────────────────────────────────────────────╮
// │  ┌●───●───●───●───●───●───●───●───●───●───●───●───●───●───●───┐  │
// │  │                                                            │  │
// │  ●   ██╗  ██╗    ██╗    ██╗                                   ●  │
// │  │   ██║  ██║    ██║    ██║           herbiewalker            │  │
// │  │   ███████║    ██║ █╗ ██║──●──●──●──┐                       │  │
// │  │   ██╔══██║    ██║███╗██║           │                       │  │
// │  ●   ██║  ██║    ╚███╔███╔╝           ●───⏣  code · tools     ●  │
// │  │   ╚═╝  ╚═╝     ╚══╝╚══╝                    homelab         │  │
// │  │                                                            │  │
// │  └●───●───●───●───●───●───●───●───●───●───●───●───●───●───●───┘  │
// ╰──────────────────────────────────────────────────────────────────╯
package app.tryst.data.stats

import app.tryst.data.db.entity.EncounterEntity
import app.tryst.data.db.entity.Mood
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
    val ejaculation: Map<String, Int> = emptyMap(),
    val kinks: Map<String, Int> = emptyMap(),
    val places: Map<Place, Int> = emptyMap(),
    val occasions: Map<String, Int> = emptyMap(),
    val toys: Map<String, Int> = emptyMap(),
    /** Orgasm types (ORG-1), counted once per tryst whoever had them. */
    val orgasmTypes: Map<String, Int> = emptyMap(),
    /** Orgasm types per person ([EncounterEntity.SELF_KEY] or a partner id), once per tryst each. */
    val orgasmTypesByPerson: Map<String, Map<String, Int>> = emptyMap(),
    /** Finish locations per person: yours under [EncounterEntity.SELF_KEY], partners' by id. */
    val finishesByPerson: Map<String, Map<String, Int>> = emptyMap(),
) {
    /**
     * The orgasm-type counts to learn from for [person]: their own once they have [PERSON_MIN_PICKS]
     * picks, otherwise everyone's, so a new partner still gets sensible suggestions.
     */
    fun orgasmTypesFor(person: String): Map<String, Int> = ownOrShared(orgasmTypesByPerson[person], orgasmTypes)

    /** Same as [orgasmTypesFor], for finish locations. */
    fun finishesFor(person: String): Map<String, Int> = ownOrShared(finishesByPerson[person], ejaculation)

    private fun ownOrShared(own: Map<String, Int>?, shared: Map<String, Int>): Map<String, Int> = if (own != null && own.values.sum() >= PERSON_MIN_PICKS) own else shared

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
                // Your finishes and partners' (ORG-1) share one list, so both count toward its inline chips.
                ejaculation = tally {
                    it.ejaculationLocations?.values?.flatten().orEmpty().toSet() +
                        it.partnerFinishes?.values?.flatMap { m -> m.values.flatten() }.orEmpty()
                },
                kinks = tally { it.kinks ?: emptySet() },
                places = tally { it.contexts ?: emptySet() },
                occasions = tally { it.occasions ?: emptySet() },
                toys = tally { it.toys ?: emptySet() },
                orgasmTypes = tally { e -> e.orgasmTypes?.values?.flatMap { m -> m.values.flatten() }.orEmpty().toSet() },
                orgasmTypesByPerson = tallyByPerson(encounters) { it.orgasmTypes },
                finishesByPerson = tallyByPerson(encounters) { e ->
                    val self = e.ejaculationLocations?.let { mapOf(EncounterEntity.SELF_KEY to it) }.orEmpty()
                    self + e.partnerFinishes.orEmpty()
                },
            )
        }

        /** Per person, how many trysts each option was picked in for them (any of their orgasms). */
        private fun tallyByPerson(
            encounters: List<EncounterEntity>,
            select: (EncounterEntity) -> Map<String, Map<Int, Set<String>>>?,
        ): Map<String, Map<String, Int>> {
            val out = HashMap<String, HashMap<String, Int>>()
            encounters.forEach { e ->
                select(e)?.forEach { (person, perOrgasm) ->
                    val counts = out.getOrPut(person) { HashMap() }
                    perOrgasm.values.flatten().toSet().forEach { id -> counts.merge(id, 1, Int::plus) }
                }
            }
            return out
        }
    }
}

/** Default size of the inline (always-visible) set; the rest of the options stay in "More…". */
const val INLINE_TARGET = 8

/** An option must have been picked at least this many times before it auto-surfaces inline. */
const val INLINE_MIN_COUNT = 2

/** A person needs this many picks of their own before their orgasm chips learn from them alone (ORG-1). */
const val PERSON_MIN_PICKS = 3

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

// ⏣ HW ⏣ · code · tools · homelab · ⏣ HW ⏣
