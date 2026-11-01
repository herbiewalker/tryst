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
package app.tryst.data.media

import java.time.LocalDateTime
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhotoMetadataTest {

    private val utc = ZoneOffset.UTC

    @Test
    fun parsesExifDateTimeToEpochMillis() {
        val expected = LocalDateTime.of(2026, 5, 1, 9, 30, 15).toInstant(ZoneOffset.UTC).toEpochMilli()
        assertEquals(expected, PhotoMetadata.parseExifDateTime("2026:05:01 09:30:15", utc))
    }

    @Test
    fun parsesExifUtcOffsets() {
        assertEquals(-240, PhotoMetadata.parseOffsetMinutes("-04:00"))
        assertEquals(330, PhotoMetadata.parseOffsetMinutes("+05:30"))
        assertEquals(0, PhotoMetadata.parseOffsetMinutes("+00:00"))
        assertNull(PhotoMetadata.parseOffsetMinutes(null))
        assertNull(PhotoMetadata.parseOffsetMinutes("EST"))
    }

    @Test
    fun storedDetailsWinButDimensionsStayLive() {
        val live = PhotoMeta(width = 100, height = 200)
        val stored = PhotoMeta(capturedAt = 5L, camera = "Pixel", latLon = 37.7 to -85.8, fileName = "a.jpg", width = 1, height = 1)
        val merged = live.preferring(stored)
        assertEquals(37.7 to -85.8, merged.latLon)
        assertEquals("a.jpg", merged.fileName)
        assertEquals(100, merged.width)
        assertEquals(200, merged.height)
        assertEquals(live, live.preferring(null))
    }

    @Test
    fun badOrMissingDateTimeIsNull() {
        assertNull(PhotoMetadata.parseExifDateTime(null, utc))
        assertNull(PhotoMetadata.parseExifDateTime("", utc))
        assertNull(PhotoMetadata.parseExifDateTime("not a date", utc))
        assertNull(PhotoMetadata.parseExifDateTime("2026-05-01T09:30:15", utc)) // ISO, not EXIF format
    }

    @Test
    fun cameraJoinsMakeAndModelAndDropsRedundantMake() {
        assertEquals("Google Pixel 9", PhotoMetadata.camera("Google", "Pixel 9"))
        assertEquals("Pixel 9 Pro", PhotoMetadata.camera("Pixel", "Pixel 9 Pro")) // model already carries make
        assertEquals("Canon", PhotoMetadata.camera("Canon", null))
        assertEquals("SM-G998B", PhotoMetadata.camera(null, "SM-G998B"))
        assertNull(PhotoMetadata.camera(null, null))
        assertNull(PhotoMetadata.camera("  ", ""))
    }
}

// ⏣ HW ⏣ · code · tools · homelab · ⏣ HW ⏣
