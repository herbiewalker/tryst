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

/**
 * Who the cycle screens list (D-71). Someone whose sex is set to Male can't have a period, so they're left off the
 * Cycle tab and the calendar's "whose periods" choice. Sex, not gender, decides it: a trans man can have periods.
 * No sex set or Intersex still shows, since Tryst can't tell. Anyone with periods already logged always shows, so
 * a later edit to someone's sex never hides data. Pure, so it's JVM-tested.
 */
object CycleEligibility {
    fun shows(sex: Sex?, hasLogs: Boolean): Boolean = sex != Sex.MALE || hasLogs
}

// ⏣ HW ⏣ · code · tools · homelab · ⏣ HW ⏣
