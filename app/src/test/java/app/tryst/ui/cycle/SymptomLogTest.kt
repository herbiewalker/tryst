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
package app.tryst.ui.cycle

import app.tryst.data.db.entity.CyclePeriodEntity
import app.tryst.data.db.entity.CycleSymptomLogEntity
import app.tryst.data.db.entity.SymptomStrength
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SymptomLogTest {

    private val period = CyclePeriodEntity(
        id = "p",
        ownerKind = CyclePeriodEntity.OWNER_PROFILE,
        ownerId = "self",
        startEpochDay = 100,
        endEpochDay = 104,
        createdAt = 0,
        updatedAt = 0,
    )

    @Test
    fun tapsCycleOffMildMediumStrongOff() {
        assertEquals(SymptomStrength.MILD, SymptomStrength.next(null))
        assertEquals(SymptomStrength.MEDIUM, SymptomStrength.next(SymptomStrength.MILD))
        assertEquals(SymptomStrength.STRONG, SymptomStrength.next(SymptomStrength.MEDIUM))
        assertNull(SymptomStrength.next(SymptomStrength.STRONG))
    }

    @Test
    fun unknownLevelReadsAsMild() = assertEquals(SymptomStrength.MILD, SymptomStrength.fromLevel(9))

    @Test
    fun rowsRoundTripThroughTheDraft() {
        val rows = listOf(
            CycleSymptomLogEntity("p", 100, "a", 3),
            CycleSymptomLogEntity("p", 100, "b", 1),
            CycleSymptomLogEntity("p", 102, "a", 2),
        )
        val draft = PeriodDraft(period, emptyList(), PeriodDraft.symptomsOf(rows))
        assertEquals(SymptomStrength.STRONG, draft.symptoms[100]?.get("a"))
        assertEquals(rows.toSet(), draft.symptomLogs().toSet())
    }

    @Test
    fun daysOutsideAShortenedPeriodAreDropped() {
        val draft = PeriodDraft(
            period.copy(endEpochDay = 101),
            emptyList(),
            mapOf(100L to mapOf("a" to SymptomStrength.MILD), 103L to mapOf("a" to SymptomStrength.STRONG)),
        )
        assertEquals(listOf(CycleSymptomLogEntity("p", 100, "a", 1)), draft.symptomLogs())
    }
}

// ⏣ HW ⏣ · code · tools · homelab · ⏣ HW ⏣
