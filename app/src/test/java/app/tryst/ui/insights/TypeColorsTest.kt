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
package app.tryst.ui.insights

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TypeColorsTest {

    @Test
    fun parsesRgbAndArgb() {
        assertEquals(Color(0xFFFF2D6F), TypeColors.parse("#FF2D6F"))
        assertEquals(Color(0xFFFF2D6F), TypeColors.parse("ff2d6f"))
        assertEquals(Color(0x80FF2D6F), TypeColors.parse("#80FF2D6F"))
    }

    @Test
    fun rejectsJunk() {
        assertNull(TypeColors.parse(null))
        assertNull(TypeColors.parse(""))
        assertNull(TypeColors.parse("#FFF"))
        assertNull(TypeColors.parse("#GGGGGG"))
    }

    @Test
    fun pickedColorWinsElseTheNameDecides() {
        assertEquals(Color(0xFF2D9CFF), TypeColors.personColor("Jordan", "#2D9CFF"))
        assertEquals(TypeColors.colorFor("Jordan"), TypeColors.personColor("Jordan", null))
        assertEquals(TypeColors.colorFor("Jordan"), TypeColors.personColor("Jordan", "not a color"))
    }

    @Test
    fun everyPaletteHexParsesToItsOwnColor() {
        TypeColors.palette.forEach { assertEquals(it.name, it.color, TypeColors.parse(it.hex)) }
    }
}

// ⏣ HW ⏣ · code · tools · homelab · ⏣ HW ⏣
