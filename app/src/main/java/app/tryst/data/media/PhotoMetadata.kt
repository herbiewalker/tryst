package app.tryst.data.media

import android.graphics.BitmapFactory
import androidx.exifinterface.media.ExifInterface
import java.io.InputStream
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** A photo's embedded metadata, read from the (decrypted) blob for display. Any field may be absent. */
data class PhotoMeta(
    val capturedAt: Long? = null,
    val width: Int? = null,
    val height: Int? = null,
    val camera: String? = null,
    val latLon: Pair<Double, Double>? = null,
) {
    val hasAny: Boolean get() = capturedAt != null || width != null || camera != null || latLon != null
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
        val capturedAt = parseExifDateTime(
            exif?.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL) ?: exif?.getAttribute(ExifInterface.TAG_DATETIME),
            ZoneId.systemDefault(),
        )
        val camera = camera(exif?.getAttribute(ExifInterface.TAG_MAKE), exif?.getAttribute(ExifInterface.TAG_MODEL))
        val latLon = exif?.latLong?.let { it[0] to it[1] }
        val (width, height) = readDimensions(openStream)
        return PhotoMeta(capturedAt = capturedAt, width = width, height = height, camera = camera, latLon = latLon)
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

    /** Parses EXIF's `yyyy:MM:dd HH:mm:ss` (local, zoneless) into epoch millis at [zone]; null if unparseable. */
    fun parseExifDateTime(raw: String?, zone: ZoneId): Long? {
        if (raw.isNullOrBlank()) return null
        return runCatching {
            LocalDateTime.parse(raw.trim(), EXIF_FORMATTER).atZone(zone).toInstant().toEpochMilli()
        }.getOrNull()
    }

    private val EXIF_FORMATTER = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss")
}
