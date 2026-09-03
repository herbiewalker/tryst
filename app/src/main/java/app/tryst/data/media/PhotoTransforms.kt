// SPDX-License-Identifier: GPL-3.0-or-later
package app.tryst.data.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlin.math.max
import kotlin.math.min

/**
 * Pixel-space transforms for the in-app photo editor (EDIT-1) — rotate and crop, encoded back to
 * JPEG bytes ready for encryption.
 *
 * Callers do the IO (open the decrypted stream, hand us bytes, take our bytes to re-encrypt in place).
 * The Bitmap operations run on-device only; the fractional-rect math ([FractionalRect] + [snapToAspect])
 * is pure Kotlin and JVM-tested so the crop UI's snap-to-aspect logic is verifiable off the emulator.
 */
object PhotoTransforms {

    /** JPEG quality for the re-encoded output. High enough to hide artefacts even on a re-crop chain. */
    const val DEFAULT_JPEG_QUALITY = 92

    /** Fully decodes a decrypted image stream into a Bitmap. Caller closes the stream. */
    fun decode(stream: InputStream): Bitmap? = BitmapFactory.decodeStream(stream)

    /** Applies a 90/180/270-degree rotation. Non-cardinal degrees still work but aren't exposed in the UI. */
    fun rotate(bmp: Bitmap, degrees: Int): Bitmap {
        if (degrees % 360 == 0) return bmp
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
    }

    /**
     * Crops [bmp] to the pixel rectangle described by the fractional [rect]. The rect is clamped to
     * the image's own bounds first, so a UI that lets the user drag the handles a touch past the edge
     * still produces a valid crop.
     */
    fun crop(bmp: Bitmap, rect: FractionalRect): Bitmap {
        val px = computeCropRectPx(bmp.width, bmp.height, rect)
        return Bitmap.createBitmap(bmp, px[0], px[1], px[2] - px[0], px[3] - px[1])
    }

    /** Re-encodes [bmp] as JPEG bytes at [quality] (0..100). Recycles nothing; caller owns [bmp]. */
    fun encodeJpeg(bmp: Bitmap, quality: Int = DEFAULT_JPEG_QUALITY): ByteArray {
        val out = ByteArrayOutputStream(bmp.byteCount / 4)
        bmp.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(1, 100), out)
        return out.toByteArray()
    }

    /**
     * Pure integer pixel rect for a fractional [rect] over an image of [width] × [height]. Fractions are
     * clamped to [0, 1] first, then the pixel bounds are clamped so a 1-px wide crop is still valid
     * (never zero-width or -height, never past the image). Returns `[left, top, right, bottom]`.
     */
    fun computeCropRectPx(width: Int, height: Int, rect: FractionalRect): IntArray {
        require(width > 0 && height > 0) { "Image must have positive dimensions" }
        val l = (rect.left.coerceIn(0f, 1f) * width).toInt().coerceIn(0, width - 1)
        val t = (rect.top.coerceIn(0f, 1f) * height).toInt().coerceIn(0, height - 1)
        val r = (rect.right.coerceIn(0f, 1f) * width).toInt().coerceIn(l + 1, width)
        val b = (rect.bottom.coerceIn(0f, 1f) * height).toInt().coerceIn(t + 1, height)
        return intArrayOf(l, t, r, b)
    }

    /**
     * Re-frames [rect] to a target [aspectWidthOverHeight] while keeping its centre and staying inside
     * the image. If [aspectWidthOverHeight] is null the rect is returned unchanged (free-aspect chip).
     *
     * [imageAspect] is `imageWidth / imageHeight` — the crop rect lives in fractional (0..1 × 0..1)
     * coordinates, which are square, so we need the image's real aspect to keep the *pixel* aspect
     * ratio right when we transform the rect back to pixels.
     */
    fun snapToAspect(
        rect: FractionalRect,
        aspectWidthOverHeight: Float?,
        imageAspect: Float,
    ): FractionalRect {
        if (aspectWidthOverHeight == null) return rect
        require(aspectWidthOverHeight > 0f && imageAspect > 0f) { "Aspects must be positive" }
        // Convert the target photo aspect into fractional-space (which is 1×1 square).
        val fractionalTarget = aspectWidthOverHeight / imageAspect
        val cx = (rect.left + rect.right) / 2f
        val cy = (rect.top + rect.bottom) / 2f
        val currentW = (rect.right - rect.left).coerceAtLeast(MIN_FRACTIONAL_SIZE)
        val currentH = (rect.bottom - rect.top).coerceAtLeast(MIN_FRACTIONAL_SIZE)
        // Pick whichever axis limits us less, so we keep as much of the current selection as possible.
        val fromWidth = currentW / fractionalTarget
        val fromHeight = currentH * fractionalTarget
        val (halfW, halfH) = if (fromWidth <= currentH) {
            currentW / 2f to fromWidth / 2f
        } else {
            fromHeight / 2f to currentH / 2f
        }
        // Slide the centred rect into the image if the target aspect pushed it past an edge.
        val shiftedCx = cx.coerceIn(halfW, 1f - halfW)
        val shiftedCy = cy.coerceIn(halfH, 1f - halfH)
        return FractionalRect(
            left = (shiftedCx - halfW).coerceIn(0f, 1f),
            top = (shiftedCy - halfH).coerceIn(0f, 1f),
            right = (shiftedCx + halfW).coerceIn(0f, 1f),
            bottom = (shiftedCy + halfH).coerceIn(0f, 1f),
        )
    }

    /**
     * Clamp a candidate rect from a drag so it stays inside the image, never inverts, and never
     * collapses below [MIN_FRACTIONAL_SIZE] on either axis. Pure integer/float math; no Android deps.
     */
    fun clampRect(rect: FractionalRect): FractionalRect {
        val l = rect.left.coerceIn(0f, 1f - MIN_FRACTIONAL_SIZE)
        val t = rect.top.coerceIn(0f, 1f - MIN_FRACTIONAL_SIZE)
        val r = rect.right.coerceIn(l + MIN_FRACTIONAL_SIZE, 1f)
        val b = rect.bottom.coerceIn(t + MIN_FRACTIONAL_SIZE, 1f)
        return FractionalRect(l, t, r, b)
    }

    /** The smallest crop we allow, as a fraction of the image on each axis. Prevents 1-px slivers. */
    const val MIN_FRACTIONAL_SIZE = 0.05f
}

/**
 * A crop rectangle expressed as fractions of the image's own dimensions on each axis. Because both
 * axes are normalized to 0..1, the value is agnostic to the underlying pixel size — the UI edits
 * fractions, and [PhotoTransforms.computeCropRectPx] converts them back to pixel coordinates once
 * we know the image's real bounds.
 */
data class FractionalRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = (right - left).coerceAtLeast(0f)
    val height: Float get() = (bottom - top).coerceAtLeast(0f)

    fun translate(dx: Float, dy: Float): FractionalRect {
        val w = width
        val h = height
        val newLeft = (left + dx).coerceIn(0f, 1f - w)
        val newTop = (top + dy).coerceIn(0f, 1f - h)
        return FractionalRect(newLeft, newTop, newLeft + w, newTop + h)
    }

    companion object {
        val FULL = FractionalRect(0f, 0f, 1f, 1f)

        /** A centred square with a bit of margin — the initial crop the UI shows. */
        fun centeredSquare(margin: Float = 0.08f): FractionalRect {
            val half = max(0.1f, 0.5f - margin)
            return FractionalRect(0.5f - half, 0.5f - half, 0.5f + half, 0.5f + half)
        }

        /** Aspect-fixed rect centred in the image. Used by the aspect chips as an initial value. */
        fun centeredWithAspect(aspectWidthOverHeight: Float, imageAspect: Float): FractionalRect {
            val fractionalTarget = aspectWidthOverHeight / imageAspect
            // Fit whichever axis is limiting.
            val halfW: Float
            val halfH: Float
            if (fractionalTarget >= 1f) {
                // Wide relative to image: width fills, height derived.
                halfW = 0.45f
                halfH = min(0.45f, halfW / fractionalTarget)
            } else {
                halfH = 0.45f
                halfW = min(0.45f, halfH * fractionalTarget)
            }
            return FractionalRect(0.5f - halfW, 0.5f - halfH, 0.5f + halfW, 0.5f + halfH)
        }
    }
}
