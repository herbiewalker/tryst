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

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.InputStream

/**
 * Many photos, especially older phone and scanner files, store their pixels sideways and carry an EXIF orientation tag
 * that says how to turn them upright. Android's bitmap decoder ignores that tag, so those photos drew sideways. This is
 * the one place that reads the tag and turns the pixels, so tiles, the viewer, crop and rotate all see the same upright
 * picture, and the edited copy (which is re-encoded with no tag) matches what was on screen.
 */
object ExifOrientation {

    const val NORMAL = ExifInterface.ORIENTATION_NORMAL

    /** Reads the orientation tag from the start of an image stream; [NORMAL] when absent or unreadable. */
    fun read(stream: InputStream): Int = runCatching {
        ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, NORMAL)
    }.getOrDefault(NORMAL)

    /** True for the four orientations that swap width and height (the 90 and 270 degree ones, EXIF 5 to 8). */
    fun swapsDimensions(orientation: Int): Boolean = orientation in ExifInterface.ORIENTATION_TRANSPOSE..ExifInterface.ORIENTATION_ROTATE_270

    /** True when the pixels already face the right way, so nothing needs doing. */
    fun isUpright(orientation: Int): Boolean = orientation == NORMAL || orientation !in ExifInterface.ORIENTATION_FLIP_HORIZONTAL..ExifInterface.ORIENTATION_ROTATE_270

    /** Returns [bitmap] turned upright for [orientation]; the same instance when it already is. */
    fun upright(bitmap: Bitmap, orientation: Int): Bitmap {
        if (isUpright(orientation)) return bitmap
        val m = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
                m.setRotate(180f)
                m.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                m.setRotate(90f)
                m.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> m.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                m.setRotate(-90f)
                m.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> m.setRotate(-90f)
        }
        return runCatching { Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true) }.getOrDefault(bitmap)
    }
}

// ⏣ HW ⏣ · code · tools · homelab · ⏣ HW ⏣
