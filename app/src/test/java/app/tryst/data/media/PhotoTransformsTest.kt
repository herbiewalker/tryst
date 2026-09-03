// SPDX-License-Identifier: GPL-3.0-or-later
package app.tryst.data.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM coverage for the rect math driving the EDIT-1 crop UI. The Bitmap ops (rotate / crop /
 * encodeJpeg) all sit on Android and are exercised on-device; here we pin the pure helpers
 * (`computeCropRectPx`, `snapToAspect`, `clampRect`, `FractionalRect` transforms) so the crop UI
 * can't silently drift into producing bad pixel rects.
 */
class PhotoTransformsTest {

    // --- computeCropRectPx --------------------------------------------------

    @Test
    fun fullFractionalRectMapsToFullPixelBounds() {
        val r = PhotoTransforms.computeCropRectPx(800, 600, FractionalRect.FULL)
        // width/height are clamped to (w-1)/(h-1) at floor and then the max side is 800/600
        // (right is coerced to at least left+1 and at most `width`).
        assertEquals(0, r[0])
        assertEquals(0, r[1])
        assertEquals(800, r[2])
        assertEquals(600, r[3])
    }

    @Test
    fun centeredSquareOnLandscapeStaysInside() {
        val rect = FractionalRect(0.25f, 0.1f, 0.75f, 0.9f)
        val r = PhotoTransforms.computeCropRectPx(1000, 500, rect)
        assertEquals(250, r[0])
        assertEquals(50, r[1])
        assertEquals(750, r[2])
        assertEquals(450, r[3])
    }

    @Test
    fun computeCropClampsOutOfBoundsAndMinsToOnePixel() {
        val rect = FractionalRect(-0.5f, -0.5f, 2f, 2f)
        val r = PhotoTransforms.computeCropRectPx(100, 100, rect)
        assertEquals(0, r[0])
        assertEquals(0, r[1])
        assertEquals(100, r[2])
        assertEquals(100, r[3])
    }

    @Test
    fun computeCropDegenerateRectYieldsMinSizedCrop() {
        // Both fractions land on the same pixel — the pixel-space result must still have
        // right > left and bottom > top (right >= left + 1, same for bottom).
        val rect = FractionalRect(0.5f, 0.5f, 0.5f, 0.5f)
        val r = PhotoTransforms.computeCropRectPx(200, 200, rect)
        assertTrue("right must be strictly greater than left", r[2] > r[0])
        assertTrue("bottom must be strictly greater than top", r[3] > r[1])
    }

    // --- snapToAspect -------------------------------------------------------

    @Test
    fun snapToAspectNullReturnsSameRect() {
        val rect = FractionalRect(0.1f, 0.2f, 0.6f, 0.8f)
        assertEquals(rect, PhotoTransforms.snapToAspect(rect, null, 1f))
    }

    @Test
    fun snapToAspectSquareOnSquareImageProducesSquareFractional() {
        val rect = FractionalRect(0.1f, 0.1f, 0.9f, 0.7f) // wider than tall in fractional
        val snapped = PhotoTransforms.snapToAspect(rect, 1f, imageAspect = 1f)
        // On a square image, fractional-target for 1:1 == 1; snapped w == snapped h.
        assertEquals(snapped.width, snapped.height, 1e-4f)
    }

    @Test
    fun snapToAspectKeepsCentreAndStaysInsideImage() {
        // Centred rect on a wide 2:1 image; snap to 1:1.
        val rect = FractionalRect(0.2f, 0.2f, 0.8f, 0.8f)
        val snapped = PhotoTransforms.snapToAspect(rect, 1f, imageAspect = 2f)
        // Centre stays put within numeric tolerance.
        val cx = (snapped.left + snapped.right) / 2f
        val cy = (snapped.top + snapped.bottom) / 2f
        assertEquals(0.5f, cx, 1e-3f)
        assertEquals(0.5f, cy, 1e-3f)
        // Stays inside [0, 1] on both axes.
        assertTrue(snapped.left in 0f..1f)
        assertTrue(snapped.right in 0f..1f)
        assertTrue(snapped.top in 0f..1f)
        assertTrue(snapped.bottom in 0f..1f)
    }

    // --- clampRect ----------------------------------------------------------

    @Test
    fun clampRectEnforcesMinSizeAndImageBounds() {
        val out = PhotoTransforms.clampRect(FractionalRect(-1f, -1f, 1.5f, 0.02f))
        assertTrue(out.left >= 0f)
        assertTrue(out.top >= 0f)
        assertTrue(out.right <= 1f)
        assertTrue(out.bottom <= 1f)
        assertTrue("width >= MIN", out.width >= PhotoTransforms.MIN_FRACTIONAL_SIZE - 1e-6f)
        assertTrue("height >= MIN", out.height >= PhotoTransforms.MIN_FRACTIONAL_SIZE - 1e-6f)
    }

    // --- FractionalRect helpers ---------------------------------------------

    @Test
    fun translateKeepsRectInsideBoundsAndPreservesSize() {
        val rect = FractionalRect(0.4f, 0.4f, 0.7f, 0.7f)
        val nudged = rect.translate(0.5f, -0.5f)
        assertEquals(rect.width, nudged.width, 1e-6f)
        assertEquals(rect.height, nudged.height, 1e-6f)
        assertTrue(nudged.right <= 1f)
        assertTrue(nudged.top >= 0f)
    }

    @Test
    fun centeredWithAspectPicksMaxFittingSize() {
        // Portrait image (aspect 0.5); target 1:1 → limited by width (0.9 across).
        val rect = FractionalRect.centeredWithAspect(1f, imageAspect = 0.5f)
        // Should be centred and non-degenerate.
        assertTrue(rect.width > 0f)
        assertTrue(rect.height > 0f)
        val cx = (rect.left + rect.right) / 2f
        val cy = (rect.top + rect.bottom) / 2f
        assertEquals(0.5f, cx, 1e-4f)
        assertEquals(0.5f, cy, 1e-4f)
    }
}
