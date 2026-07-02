package app.tryst.data.db

import org.junit.Assert.assertEquals
import org.junit.Test

/** [CatalogAdoption.prettify] is the only label source for adopted ids — pin its shape. */
class CatalogAdoptionTest {

    @Test
    fun prettify_sentenceCasesEnumNames() {
        assertEquals("Foot play", CatalogAdoption.prettify("FOOT_PLAY"))
        assertEquals("Oral", CatalogAdoption.prettify("ORAL"))
        assertEquals("Sixty nine", CatalogAdoption.prettify("SIXTY_NINE"))
        assertEquals("Hand on neck", CatalogAdoption.prettify("HAND_ON_NECK"))
        assertEquals("A", CatalogAdoption.prettify("A"))
        assertEquals("Plan 9", CatalogAdoption.prettify("PLAN_9"))
    }
}
