package app.tryst.data.stats

import app.tryst.data.db.entity.EjaculationLocation
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
        ejaculation: Map<Int, Set<EjaculationLocation>>? = null,
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
                        0 to setOf(EjaculationLocation.ON_CHEST),
                        1 to setOf(EjaculationLocation.ON_CHEST, EjaculationLocation.SWALLOWED),
                    ),
                ),
            ),
        )
        assertEquals(1, usage.ejaculation[EjaculationLocation.ON_CHEST])
        assertEquals(1, usage.ejaculation[EjaculationLocation.SWALLOWED])
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
}
