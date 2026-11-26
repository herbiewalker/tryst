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
package app.tryst.data.color

import app.tryst.data.color.ColorAssignment.Person
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ColorAssignmentTest {

    private val p = PersonPalette.entries.map { it.hex }

    @Test
    fun peopleWithoutAColorGetDistinctOnesInOrder() {
        val got = ColorAssignment.fill(listOf(Person("self", null), Person("a", null), Person("b", null)))
        assertEquals(listOf(p[0], p[1], p[2]), listOf(got["self"], got["a"], got["b"]))
    }

    @Test
    fun aPickedColorIsKeptAndSkipped() {
        // "a" picked the first palette color, so the others start at the second.
        val got = ColorAssignment.fill(listOf(Person("self", null), Person("a", p[0].lowercase()), Person("b", null)))
        assertFalse("a keeps its own color", got.containsKey("a"))
        assertEquals(p[1], got["self"])
        assertEquals(p[2], got["b"])
    }

    @Test
    fun anOffPaletteColorDoesNotBlockAnything() {
        val got = ColorAssignment.fill(listOf(Person("a", "#123456"), Person("b", null)))
        assertEquals(p[0], got["b"])
    }

    @Test
    fun twentyFourPeopleAllDifferThenTheTwentyFifthShares() {
        val people = p.indices.map { Person("x$it", null) }
        val got = ColorAssignment.fill(people)
        assertEquals(p.size, got.values.toSet().size)
        val more = ColorAssignment.fill(people.map { it.copy(hex = got[it.id]) } + Person("late", null))
        assertEquals(p[0], more["late"])
    }

    @Test
    fun anArchivedPartnersColorIsReusedBeforeTwoCurrentPeopleShare() {
        // 23 current people hold every color except Blue, which only an archived partner holds.
        val current = (1 until p.size).map { Person("c$it", p[it]) }
        val archived = Person("gone", p[0], active = false)
        val got = ColorAssignment.fill(listOf(archived) + current + Person("new", null))
        assertEquals("Blue is only held by someone archived", p[0], got["new"])
    }

    @Test
    fun aFreeColorWinsOverAnArchivedOne() {
        val got = ColorAssignment.fill(listOf(Person("gone", p[0], active = false), Person("new", null)))
        assertEquals(p[1], got["new"])
    }

    @Test
    fun nextSuggestionAvoidsColorsInUse() {
        assertEquals(p[2], ColorAssignment.next(listOf(Person("a", p[0]), Person("b", p[1]), Person("c", null))))
        assertEquals(p[0], ColorAssignment.next(emptyList()))
    }
}

// ⏣ HW ⏣ · code · tools · homelab · ⏣ HW ⏣
