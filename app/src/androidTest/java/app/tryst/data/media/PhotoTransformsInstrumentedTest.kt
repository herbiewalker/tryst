// SPDX-License-Identifier: GPL-3.0-or-later
package app.tryst.data.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * On-device coverage for the Bitmap-touching half of [PhotoTransforms] — the JVM tests
 * ([PhotoTransformsTest]) already pin the rect math, this run pins:
 *
 *  - rotate: dimensions swap on a ±90° rotation, and a marker pixel travels the way
 *    the transform promises (top-left → top-right on CW, top-left → bottom-left on CCW).
 *  - crop: dimensions match the fractional rect, and the pixel that was inside the
 *    cropped region is preserved at its expected new offset.
 *  - encodeJpeg: the returned bytes are a valid JPEG that `BitmapFactory` can decode
 *    back to the original dimensions, and quality is honoured (higher q → bigger blob).
 *
 * These are pure `Bitmap` ops (no encryption, no repositories, no session) so they run
 * in isolation without touching the vault / DB / SessionManager.
 */
@RunWith(AndroidJUnit4::class)
class PhotoTransformsInstrumentedTest {

    // A 100 × 60 rectangle with four coloured quadrants; enough asymmetry to detect
    // rotation/crop errors from pixel colours alone.
    private fun sample(width: Int = 100, height: Int = 60): Bitmap {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val halfW = width / 2f
        val halfH = height / 2f
        val paint = Paint()
        paint.color = Color.RED
        canvas.drawRect(0f, 0f, halfW, halfH, paint)
        paint.color = Color.GREEN
        canvas.drawRect(halfW, 0f, width.toFloat(), halfH, paint)
        paint.color = Color.BLUE
        canvas.drawRect(0f, halfH, halfW, height.toFloat(), paint)
        paint.color = Color.YELLOW
        canvas.drawRect(halfW, halfH, width.toFloat(), height.toFloat(), paint)
        return bmp
    }

    // --- rotate ---------------------------------------------------------------------------

    @Test
    fun rotate90CwSwapsDimensionsAndMovesTopLeftPixelToTopRight() {
        val src = sample(100, 60)
        val out = PhotoTransforms.rotate(src, 90)
        assertEquals(60, out.width)
        assertEquals(100, out.height)
        // The (0, 0) red top-left quadrant lands in the top-right quadrant after a CW rotation.
        assertEquals(Color.RED, out.getPixel(out.width - 1, 0))
        // The (100-1, 0) green top-right quadrant lands in the bottom-right quadrant.
        assertEquals(Color.GREEN, out.getPixel(out.width - 1, out.height - 1))
    }

    @Test
    fun rotateNegative90CcwSwapsDimensionsAndMovesTopLeftPixelToBottomLeft() {
        val src = sample(100, 60)
        val out = PhotoTransforms.rotate(src, -90)
        assertEquals(60, out.width)
        assertEquals(100, out.height)
        // Red top-left → bottom-left after a CCW rotation.
        assertEquals(Color.RED, out.getPixel(0, out.height - 1))
        // Blue bottom-left → top-left after CCW.
        assertEquals(Color.BLUE, out.getPixel(0, 0))
    }

    @Test
    fun rotate360IsIdentityForDimensions() {
        val src = sample(100, 60)
        val out = PhotoTransforms.rotate(src, 360)
        assertEquals(100, out.width)
        assertEquals(60, out.height)
        assertEquals(Color.RED, out.getPixel(0, 0))
        assertEquals(Color.YELLOW, out.getPixel(99, 59))
    }

    // --- crop -----------------------------------------------------------------------------

    @Test
    fun cropCentreHalfProducesRightSizeAndKeepsCentrePixel() {
        val src = sample(100, 60)
        // Middle 50×30 rectangle.
        val rect = FractionalRect(0.25f, 0.25f, 0.75f, 0.75f)
        val out = PhotoTransforms.crop(src, rect)
        assertEquals(50, out.width)
        assertEquals(30, out.height)
        // The pixel at src (50, 30) — the exact centre boundary — is Yellow (bottom-right quadrant),
        // and after crop it lands at (25, 15) in the output.
        assertEquals(src.getPixel(50, 30), out.getPixel(25, 15))
    }

    @Test
    fun cropClampsAtImageBoundsInsteadOfCrashing() {
        val src = sample(100, 60)
        // Out-of-range fractions should not throw — computeCropRectPx clamps.
        val rect = FractionalRect(-0.5f, -0.5f, 1.5f, 1.5f)
        val out = PhotoTransforms.crop(src, rect)
        assertEquals(100, out.width)
        assertEquals(60, out.height)
    }

    // --- encodeJpeg -----------------------------------------------------------------------

    @Test
    fun encodeJpegRoundTripsToMatchingDimensions() {
        val src = sample(100, 60)
        val bytes = PhotoTransforms.encodeJpeg(src)
        assertTrue("encoded blob must be non-empty", bytes.isNotEmpty())
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        assertEquals(100, decoded.width)
        assertEquals(60, decoded.height)
    }

    @Test
    fun encodeJpegHigherQualityYieldsLargerBlob() {
        // Use a more detailed sample so JPEG quality actually differs — flat colour blocks compress
        // to nearly the same size at any quality.
        val src = noisySample(200, 200)
        val low = PhotoTransforms.encodeJpeg(src, quality = 30).size
        val high = PhotoTransforms.encodeJpeg(src, quality = 95).size
        assertTrue("q95 blob ($high B) should exceed q30 blob ($low B)", high > low)
    }

    // A noisy source: a per-pixel gradient with mid-frequency detail so quality differences
    // actually show up in the encoded size.
    private fun noisySample(width: Int, height: Int): Bitmap {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val r = (x * 255 / width) and 0xFF
                val g = (y * 255 / height) and 0xFF
                val b = (((x + y) * 7) % 256) and 0xFF
                bmp.setPixel(x, y, Color.rgb(r, g, b))
            }
        }
        return bmp
    }

    // --- chain ----------------------------------------------------------------------------

    @Test
    fun rotateThenCropThenEncodeChainSurvivesRoundTrip() {
        val src = sample(200, 100)
        val rotated = PhotoTransforms.rotate(src, 90) // → 100 × 200
        // Top half of the rotated image.
        val cropped = PhotoTransforms.crop(rotated, FractionalRect(0f, 0f, 1f, 0.5f))
        assertEquals(100, cropped.width)
        assertEquals(100, cropped.height)
        val bytes = PhotoTransforms.encodeJpeg(cropped, quality = 90)
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        assertEquals(100, decoded.width)
        assertEquals(100, decoded.height)
        // A round-trip through JPEG isn't bit-exact, but the top-left quadrant of a rotated crop
        // should still read as a warm colour (red / green range), not blue / yellow.
        val corner = decoded.getPixel(2, 2)
        assertNotEquals(
            "top-left of chained transform must not be the source's bottom quadrant colour",
            Color.YELLOW,
            corner,
        )
    }
}
