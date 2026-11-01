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
package app.tryst.ui.gallery

import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

/** Formatting of the details sheet's date and coordinates. */
class PhotoDetailsFormatTest {

    @Test
    fun takenUsesTheCamerasOwnOffset() {
        // 22:16 at -04:00 is 02:16 UTC the next day; the label must read the camera's local time.
        val instant = LocalDateTime.of(2026, 6, 22, 2, 16).toInstant(ZoneOffset.UTC).toEpochMilli()
        val text = formatTaken(instant, -240, Locale.US)
        assertEquals("Sun, Jun 21, 2026 · 10:16 PM GMT-04:00", text)
    }

    @Test
    fun coordinatesCarryHemispheres() {
        assertEquals("37.69167° N, 85.85417° W", formatCoordinates(37.691667, -85.854167))
        assertEquals("33.86880° S, 151.20930° E", formatCoordinates(-33.8688, 151.2093))
    }
}

// ⏣ HW ⏣ · code · tools · homelab · ⏣ HW ⏣
