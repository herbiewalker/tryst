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
package app.tryst.data.db

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The ORG-1 (v23) column format: person key → orgasm index → ids. */
class PersonOrgasmMapConverterTest {

    private val converters = Converters()

    @Test
    fun roundTripsSeveralPeopleAndOrgasms() {
        val value = mapOf(
            "self" to mapOf(0 to setOf("custom:a"), 2 to setOf("custom:a", "custom:b")),
            "5f1c-partner" to mapOf(1 to setOf("NONE")),
        )
        val text = converters.personOrgasmMapToString(value)
        assertEquals(value, converters.stringToPersonOrgasmMap(text))
    }

    @Test
    fun emptyDetailsAreStoredAsNull() {
        assertNull(converters.personOrgasmMapToString(null))
        assertNull(converters.personOrgasmMapToString(emptyMap()))
        assertNull(converters.personOrgasmMapToString(mapOf("self" to mapOf(0 to emptySet()))))
        assertNull(converters.stringToPersonOrgasmMap(""))
    }

    @Test
    fun skipsMalformedEntries() {
        val parsed = converters.stringToPersonOrgasmMap("self~0=custom:a,broken,~1=x,self~x=y,p1~3=")
        assertEquals(mapOf("self" to mapOf(0 to setOf("custom:a"))), parsed)
    }
}

// ⏣ HW ⏣ · code · tools · homelab · ⏣ HW ⏣
