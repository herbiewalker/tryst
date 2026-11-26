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
package app.tryst.data.cycle

import app.tryst.data.db.entity.Sex
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CycleEligibilityTest {

    @Test
    fun maleIsLeftOff() = assertFalse(CycleEligibility.shows(Sex.MALE, hasLogs = false))

    @Test
    fun femaleIntersexAndUnsetShow() {
        assertTrue(CycleEligibility.shows(Sex.FEMALE, hasLogs = false))
        assertTrue(CycleEligibility.shows(Sex.INTERSEX, hasLogs = false))
        assertTrue(CycleEligibility.shows(null, hasLogs = false))
    }

    @Test
    fun loggedDataNeverHides() = assertTrue(CycleEligibility.shows(Sex.MALE, hasLogs = true))
}

// ⏣ HW ⏣ · code · tools · homelab · ⏣ HW ⏣
