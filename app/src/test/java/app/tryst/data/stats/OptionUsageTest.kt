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
import app.tryst.data.db.entity.Protection
import org.junit.Assert.assertEquals
import org.junit.Test

class OptionUsageTest {

    private fun encounter(
        id: String,
        performed: Set<String>? = null,
        received: Set<String>? = null,
        protection: Set<Protection> = emptySet(),
        ejaculation: Map<Int, Set<String>>? = null,
    ) = EncounterEntity(
        id = id,
        startAt = 0,
        protectionUsed = protection,
        practicesPerformed = performed,
        practicesReceived = received,
        ejaculationLocations = ejaculation,
        createdAt = 0,
        updatedAt = 0,
    )

    @Test
    fun emptyLogYieldsEmptyUsage() {
        assertEquals(OptionUsage.EMPTY, OptionUsage.from(emptyList()))
    }

    @Test
    fun actGivenAndReceivedInOneEncounterCountsOnce() {
        val usage = OptionUsage.from(
            listOf(encounter("a", performed = setOf("ORAL"), received = setOf("ORAL", "KISSING"))),
        )
        assertEquals(1, usage.acts["ORAL"])
        assertEquals(1, usage.acts["KISSING"])
    }

    @Test
    fun actsTallyAcrossEncounters() {
        val usage = OptionUsage.from(
            listOf(
                encounter("a", performed = setOf("VAGINAL")),
                encounter("b", performed = setOf("VAGINAL"), received = setOf("ORAL")),
                encounter("c", received = setOf("VAGINAL")),
            ),
        )
        assertEquals(3, usage.acts["VAGINAL"])
        assertEquals(1, usage.acts["ORAL"])
    }

    @Test
    fun ejaculationDedupesWithinEncounterAcrossOrgasms() {
        // Same location across two orgasm rows in one encounter counts once for that encounter.
        val usage = OptionUsage.from(
            listOf(
                encounter(
                    "a",
                    ejaculation = mapOf(
                        0 to setOf("ON_CHEST"),
                        1 to setOf("ON_CHEST", "SWALLOWED"),
                    ),
                ),
            ),
        )
        assertEquals(1, usage.ejaculation["ON_CHEST"])
        assertEquals(1, usage.ejaculation["SWALLOWED"])
    }

    @Test
    fun protectionTallies() {
        val usage = OptionUsage.from(
            listOf(
                encounter("a", protection = setOf(Protection.CONDOM, Protection.VASECTOMY)),
                encounter("b", protection = setOf(Protection.VASECTOMY)),
            ),
        )
        assertEquals(2, usage.protection[Protection.VASECTOMY])
        assertEquals(1, usage.protection[Protection.CONDOM])
    }

    // --- mostUsedCommon ---

    private val curated = listOf("a", "b", "c", "d")
    private val all = listOf("a", "b", "c", "d", "e", "f", "g", "h", "i", "j")

    @Test
    fun noUsageFallsBackToCuratedUpToTarget() {
        val result = mostUsedCommon(curated, all, target = 3) { 0 }
        assertEquals(listOf("a", "b", "c"), result)
    }

    @Test
    fun frequentNonCuratedOptionSurfacesAndCuratedBackfills() {
        // "j" isn't curated but is used a lot → it leads; curated backfills the rest up to target.
        val usage = mapOf("j" to 5)
        val result = mostUsedCommon(curated, all, target = 4) { usage[it] ?: 0 }
        assertEquals("j", result.first())
        assertEquals(4, result.size)
        assertEquals(setOf("j", "a", "b", "c"), result.toSet())
    }

    @Test
    fun belowMinCountDoesNotSurface() {
        val usage = mapOf("j" to 1)
        val result = mostUsedCommon(curated, all, target = 4, minCount = 2) { usage[it] ?: 0 }
        // Single use stays hidden; pure curated set returned.
        assertEquals(curated, result)
    }

    @Test
    fun capsAtTargetAndOrdersByCountDescending() {
        val usage = mapOf("e" to 9, "f" to 8, "g" to 7, "h" to 6, "i" to 5)
        val result = mostUsedCommon(curated, all, target = 3, minCount = 2) { usage[it] ?: 0 }
        // Only the three most-used, no curated backfill needed.
        assertEquals(listOf("e", "f", "g"), result)
    }

    @Test
    fun equalCountsBreakTieByNaturalOrder() {
        val usage = mapOf("h" to 3, "e" to 3, "g" to 3)
        val result = mostUsedCommon(curated, all, target = 2, minCount = 2) { usage[it] ?: 0 }
        // All tied at 3 → `all` order (e before g before h) decides the cut deterministically.
        assertEquals(listOf("e", "g"), result)
    }

    private fun orgasms(
        id: String,
        types: Map<String, Map<Int, Set<String>>>? = null,
        selfFinish: Map<Int, Set<String>>? = null,
        partnerFinish: Map<String, Map<Int, Set<String>>>? = null,
    ) = encounter(id, ejaculation = selfFinish).copy(orgasmTypes = types, partnerFinishes = partnerFinish)

    @Test
    fun orgasmSuggestionsLearnPerPersonOnceTheyHaveEnoughPicks() {
        val self = EncounterEntity.SELF_KEY
        val usage = OptionUsage.from(
            listOf(
                orgasms("a", types = mapOf(self to mapOf(0 to setOf("P")), "j" to mapOf(0 to setOf("C"), 1 to setOf("C", "G")))),
                orgasms("b", types = mapOf(self to mapOf(0 to setOf("P")), "j" to mapOf(0 to setOf("C")))),
                orgasms("c", types = mapOf(self to mapOf(0 to setOf("P")), "j" to mapOf(0 to setOf("G")))),
                orgasms("d", types = mapOf("s" to mapOf(0 to setOf("P")))),
            ),
        )
        // Same type twice in one tryst counts once for that person.
        assertEquals(mapOf("P" to 3), usage.orgasmTypesFor(self))
        assertEquals(mapOf("C" to 2, "G" to 2), usage.orgasmTypesFor("j"))
        // One pick is not enough to learn from, so Sam falls back to everyone's counts.
        assertEquals(usage.orgasmTypes, usage.orgasmTypesFor("s"))
        assertEquals(usage.orgasmTypes, usage.orgasmTypesFor("nobody"))
    }

    @Test
    fun finishSuggestionsSplitYoursFromPartners() {
        val usage = OptionUsage.from(
            listOf(
                orgasms("a", selfFinish = mapOf(0 to setOf("CHEST")), partnerFinish = mapOf("s" to mapOf(0 to setOf("CONDOM")))),
                orgasms("b", selfFinish = mapOf(0 to setOf("CHEST"), 1 to setOf("MOUTH")), partnerFinish = mapOf("s" to mapOf(0 to setOf("CONDOM")))),
                orgasms("c", partnerFinish = mapOf("s" to mapOf(0 to setOf("HAND")))),
            ),
        )
        assertEquals(mapOf("CHEST" to 2, "MOUTH" to 1), usage.finishesFor(EncounterEntity.SELF_KEY))
        assertEquals(mapOf("CONDOM" to 2, "HAND" to 1), usage.finishesFor("s"))
    }
}
