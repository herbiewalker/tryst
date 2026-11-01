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

import android.graphics.BitmapFactory
import androidx.exifinterface.media.ExifInterface
import java.io.InputStream
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * A photo's embedded metadata, read from the (decrypted) blob for display. Any field may be absent —
 * a phone-shot JPEG carries most fields; a screenshot / imported PNG carries almost none.
 *
 * Fields we deliberately do NOT decode:
 * - Reverse-geocoded place names (needs a `Geocoder` call → network in some regions → violates the app's
 *   no-INTERNET invariant). Raw [latLon] coords are exposed instead so the user can hand them to a
 *   maps app manually if they want.
 * - EXIF thumbnail (redundant — we render the real photo already).
 */
data class PhotoMeta(
    val capturedAt: Long? = null,
    val width: Int? = null,
    val height: Int? = null,
    val camera: String? = null,
    /** Raw EXIF GPS coordinates. Absent when the photo has no location tag (e.g. screenshots). */
    val latLon: Pair<Double, Double>? = null,
    /** Aperture as an f-number (e.g. 1.8, 2.2). */
    val aperture: Double? = null,
    /** Shutter speed in seconds — inverted for display as "1/125". */
    val shutterSeconds: Double? = null,
    val iso: Int? = null,
    /** Focal length in mm. */
    val focalLengthMm: Double? = null,
    /** EXIF orientation constant (`TAG_ORIENTATION` value, 1..8). */
    val orientation: Int? = null,
    /** Lens model string (EXIF `LensModel`), e.g. "Pixel 9 Pro XL back camera 6.9mm f/1.7". */
    val lens: String? = null,
    /** Minutes east of UTC at capture (EXIF `OffsetTimeOriginal`); null when the camera did not record it. */
    val offsetMinutes: Int? = null,
    /** GPS altitude in metres above sea level, when recorded. */
    val altitudeM: Double? = null,
    /** The file name it was imported under (not an EXIF tag; filled from the stored row). */
    val fileName: String? = null,
) {
    val hasAny: Boolean get() =
        capturedAt != null ||
            width != null ||
            camera != null ||
            latLon != null ||
            aperture != null ||
            shutterSeconds != null ||
            iso != null ||
            focalLengthMm != null ||
            orientation != null ||
            lens != null ||
            altitudeM != null ||
            fileName != null
}

/**
 * Reads a photo's EXIF/dimension metadata for the viewer (META-1). Tryst **deliberately does not strip
 * EXIF on import** (it's tiny, stripping means a lossy re-encode, and the app has no network / no share /
 * encrypted-at-rest, so embedded location never leaves the device) — so the data is still in the stored
 * blob and we simply read it at display time. No schema change; nothing persisted.
 *
 * [openStream] must return a **fresh** decrypting stream each call (read twice: EXIF, then bounds).
 * Reverse-geocoding is deliberately avoided (it can hit the network); location is reported as raw coords.
 */
object PhotoMetadata {

    fun read(openStream: () -> InputStream?): PhotoMeta {
        val exif = runCatching { openStream()?.use { ExifInterface(it) } }.getOrNull()
        val offsetMinutes = parseOffsetMinutes(exif?.getAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL) ?: exif?.getAttribute(ExifInterface.TAG_OFFSET_TIME))
        // Prefer the camera-recorded UTC offset so the instant is right even if the phone has since changed zone.
        val zone = offsetMinutes?.let { ZoneOffset.ofTotalSeconds(it * 60) } ?: ZoneId.systemDefault()
        val capturedAt = parseExifDateTime(
            exif?.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL) ?: exif?.getAttribute(ExifInterface.TAG_DATETIME),
            zone,
        )
        val camera = camera(exif?.getAttribute(ExifInterface.TAG_MAKE), exif?.getAttribute(ExifInterface.TAG_MODEL))
        val latLon = exif?.latLong?.let { it[0] to it[1] }
        val aperture = exif?.getAttributeDouble(ExifInterface.TAG_F_NUMBER, -1.0)?.takeIf { it > 0 }
        val shutterSeconds = exif?.getAttributeDouble(ExifInterface.TAG_EXPOSURE_TIME, -1.0)?.takeIf { it > 0 }
        val iso = exif?.getAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY)?.toIntOrNull()
            ?: exif?.getAttribute(ExifInterface.TAG_ISO_SPEED_RATINGS)?.toIntOrNull()
        val focalLength = exif?.getAttributeDouble(ExifInterface.TAG_FOCAL_LENGTH, -1.0)?.takeIf { it > 0 }
        val lens = exif?.getAttribute(ExifInterface.TAG_LENS_MODEL)?.trim()?.ifBlank { null }
        val altitude = exif?.getAltitude(Double.NaN)?.takeIf { !it.isNaN() }
        val orientation = exif?.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED)
            ?.takeIf { it != ExifInterface.ORIENTATION_UNDEFINED }
        val (width, height) = readDimensions(openStream)
        return PhotoMeta(
            capturedAt = capturedAt,
            width = width,
            height = height,
            camera = camera,
            latLon = latLon,
            aperture = aperture,
            shutterSeconds = shutterSeconds,
            iso = iso,
            focalLengthMm = focalLength,
            orientation = orientation,
            lens = lens,
            offsetMinutes = offsetMinutes,
            altitudeM = altitude,
        )
    }

    private fun readDimensions(openStream: () -> InputStream?): Pair<Int?, Int?> {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching { openStream()?.use { BitmapFactory.decodeStream(it, null, opts) } }
        return opts.outWidth.takeIf { it > 0 } to opts.outHeight.takeIf { it > 0 }
    }

    /** Joins make + model into a display string, dropping a make that the model already repeats. */
    fun camera(make: String?, model: String?): String? {
        val mk = make?.trim()?.ifBlank { null }
        val md = model?.trim()?.ifBlank { null }
        return when {
            mk != null && md != null -> if (md.startsWith(mk, ignoreCase = true)) md else "$mk $md"
            else -> md ?: mk
        }
    }

    /** Parses an EXIF offset such as `-04:00` or `+05:30` into minutes east of UTC; null if absent or malformed. */
    fun parseOffsetMinutes(raw: String?): Int? {
        val m = OFFSET_REGEX.matchEntire(raw?.trim().orEmpty()) ?: return null
        val sign = if (m.groupValues[1] == "-") -1 else 1
        return sign * (m.groupValues[2].toInt() * 60 + m.groupValues[3].toInt())
    }

    /** Parses EXIF's `yyyy:MM:dd HH:mm:ss` (local, zoneless) into epoch millis at [zone]; null if unparseable. */
    fun parseExifDateTime(raw: String?, zone: ZoneId): Long? {
        if (raw.isNullOrBlank()) return null
        return runCatching {
            LocalDateTime.parse(raw.trim(), EXIF_FORMATTER).atZone(zone).toInstant().toEpochMilli()
        }.getOrNull()
    }

    /** Formats shutter as "1/125" (or "1s" / "2s" for slow shutters). */
    fun formatShutter(seconds: Double): String {
        if (seconds <= 0.0) return ""
        return if (seconds >= 1.0) {
            "${seconds.toInt()}s"
        } else {
            "1/${kotlin.math.round(1.0 / seconds).toInt()}"
        }
    }

    /** Human name for the small EXIF orientation set (1..8). "Normal" is the identity. */
    fun orientationLabel(value: Int): String = when (value) {
        ExifInterface.ORIENTATION_NORMAL -> "Normal"
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> "Flipped horizontally"
        ExifInterface.ORIENTATION_ROTATE_180 -> "Rotated 180°"
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> "Flipped vertically"
        ExifInterface.ORIENTATION_TRANSPOSE -> "Transposed"
        ExifInterface.ORIENTATION_ROTATE_90 -> "Rotated 90° clockwise"
        ExifInterface.ORIENTATION_TRANSVERSE -> "Transverse"
        ExifInterface.ORIENTATION_ROTATE_270 -> "Rotated 270° clockwise"
        else -> "Unknown"
    }

    private val OFFSET_REGEX = Regex("""([+-])(\d{2}):(\d{2})""")

    private val EXIF_FORMATTER = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss")
}

// ⏣ HW ⏣ · code · tools · homelab · ⏣ HW ⏣
