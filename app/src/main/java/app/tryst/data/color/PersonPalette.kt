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

/**
 * The colors people (and Insights categories) are drawn in (D-70). Twenty-four hues readable on the dark and light
 * themes: twelve bold ones spread around the wheel and ordered so the first people get the most different ones
 * (blue and orange, then green and pink, and so on), then twelve deeper or lighter shades for when there are more
 * people than that over the years. Stored on a person as "#RRGGBB".
 */
object PersonPalette {

    data class Entry(val hex: String, val name: String)

    val entries: List<Entry> = listOf(
        Entry("#4C8DF6", "Blue"),
        Entry("#F7853A", "Orange"),
        Entry("#3DBE6E", "Green"),
        Entry("#EC5FA6", "Pink"),
        Entry("#F2C230", "Yellow"),
        Entry("#9C6ADE", "Purple"),
        Entry("#1FB5A9", "Teal"),
        Entry("#E5484D", "Red"),
        Entry("#A6D15A", "Lime"),
        Entry("#B07D4F", "Brown"),
        Entry("#3FC6EF", "Cyan"),
        Entry("#5B5FD9", "Indigo"),
        // Second tier: deeper or lighter shades, still apart from the first twelve.
        Entry("#2E5AAC", "Navy"),
        Entry("#FFB98A", "Peach"),
        Entry("#1E7F4A", "Forest"),
        Entry("#F7A8CF", "Rose"),
        Entry("#B8901A", "Gold"),
        Entry("#C9AEF2", "Lavender"),
        Entry("#0E7C74", "Deep teal"),
        Entry("#9E2A3A", "Maroon"),
        Entry("#7A8F2E", "Olive"),
        Entry("#D9B48A", "Tan"),
        Entry("#A3DDF7", "Sky"),
        Entry("#C2399A", "Magenta"),
    )

    /** How many of [entries] are the bold first tier; Insights categories only use these. */
    const val FIRST_TIER = 12

    fun normalize(hex: String?): String? = hex?.trim()?.uppercase()?.takeIf { it.isNotEmpty() }
}

/**
 * Gives everyone without a color their own (D-70). People are taken in a stable order (the self profile, then
 * partners in the order they were added); each one missing a color gets the palette color the fewest *current*
 * people have (the profile and partners not archived), then the fewest people overall, then the earliest in the
 * palette. So nobody active shares while there are colors to go round, a partner archived years ago only holds
 * their color until it's needed, and a color someone already has, picked or assigned, is never changed here.
 * Pure, so it's JVM-tested.
 */
object ColorAssignment {

    /** One person: id, stored color or null, and whether they're current (not archived). */
    data class Person(val id: String, val hex: String?, val active: Boolean = true)

    /** [people] in order. Returns id → new color for each person that had none. */
    fun fill(people: List<Person>): Map<String, String> {
        val tally = Tally(people)
        val assigned = LinkedHashMap<String, String>()
        for (p in people) {
            if (PersonPalette.normalize(p.hex) != null) continue
            val next = tally.leastUsed()
            tally.add(next, p.active)
            assigned[p.id] = next
        }
        return assigned
    }

    /** The color a new (current) person would get next, given everyone's colors. */
    fun next(people: List<Person>): String = Tally(people).leastUsed()

    private class Tally(people: List<Person>) {
        private val active = PersonPalette.entries.associate { it.hex to 0 }.toMutableMap()
        private val all = active.toMutableMap()

        init {
            people.forEach { p -> PersonPalette.normalize(p.hex)?.let { add(it, p.active) } }
        }

        fun add(hex: String, isActive: Boolean) {
            all.computeIfPresent(hex) { _, n -> n + 1 }
            if (isActive) active.computeIfPresent(hex) { _, n -> n + 1 }
        }

        // minWith keeps the first of equals, so palette order breaks the last tie.
        fun leastUsed(): String = PersonPalette.entries
            .minWith(compareBy({ active.getValue(it.hex) }, { all.getValue(it.hex) }))
            .hex
    }
}

// ⏣ HW ⏣ · code · tools · homelab · ⏣ HW ⏣
