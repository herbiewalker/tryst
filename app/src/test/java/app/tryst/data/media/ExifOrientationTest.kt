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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExifOrientationTest {

    @Test
    fun onlyTheQuarterTurnOrientationsSwapWidthAndHeight() {
        for (o in 5..8) assertTrue("orientation $o", ExifOrientation.swapsDimensions(o))
        for (o in listOf(0, 1, 2, 3, 4, 9)) assertFalse("orientation $o", ExifOrientation.swapsDimensions(o))
    }

    @Test
    fun normalAndUnknownValuesNeedNoTurning() {
        for (o in listOf(0, 1, 9, 42, -1)) assertTrue("orientation $o", ExifOrientation.isUpright(o))
        for (o in 2..8) assertFalse("orientation $o", ExifOrientation.isUpright(o))
    }

    @Test
    fun photoRevisionsGiveAnEditedPhotoANewCacheKey() {
        val revisions = PhotoRevisions()
        val before = revisions.keyFor("blob-1")
        revisions.bump("blob-1")
        revisions.bump("blob-1")
        assertEquals("blob-1#0", before)
        assertEquals("blob-1#2", revisions.keyFor("blob-1"))
        assertEquals("another photo is untouched", "blob-2#0", revisions.keyFor("blob-2"))
        assertEquals(mapOf("blob-1" to 2), revisions.revisions.value)
    }
}

// ⏣ HW ⏣ · code · tools · homelab · ⏣ HW ⏣
