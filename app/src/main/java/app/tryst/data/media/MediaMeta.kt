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

import app.tryst.data.db.entity.MediaEntity

/** Folds freshly read [PhotoMeta] into the media row (v18 columns) and marks it scanned. */
fun MediaEntity.withMeta(meta: PhotoMeta, originalName: String? = null): MediaEntity = copy(
    capturedAt = meta.capturedAt,
    capturedOffsetMin = meta.offsetMinutes,
    latitude = meta.latLon?.first,
    longitude = meta.latLon?.second,
    altitudeM = meta.altitudeM,
    camera = meta.camera,
    lens = meta.lens,
    aperture = meta.aperture,
    shutterSeconds = meta.shutterSeconds,
    iso = meta.iso,
    focalLengthMm = meta.focalLengthMm,
    originalName = originalName ?: this.originalName,
    metaScanned = true,
)

/**
 * Merges what was read live from the blob with the details stored on the row. Stored values win (an edit
 * re-encodes the photo and drops its EXIF, so the live read can be empty); dimensions are always live,
 * since a rotate or crop changes them.
 */
fun PhotoMeta.preferring(stored: PhotoMeta?): PhotoMeta {
    if (stored == null) return this
    return copy(
        capturedAt = stored.capturedAt ?: capturedAt,
        camera = stored.camera ?: camera,
        latLon = stored.latLon ?: latLon,
        aperture = stored.aperture ?: aperture,
        shutterSeconds = stored.shutterSeconds ?: shutterSeconds,
        iso = stored.iso ?: iso,
        focalLengthMm = stored.focalLengthMm ?: focalLengthMm,
        lens = stored.lens ?: lens,
        offsetMinutes = stored.offsetMinutes ?: offsetMinutes,
        altitudeM = stored.altitudeM ?: altitudeM,
        fileName = stored.fileName ?: fileName,
    )
}

/**
 * The details stored on the row, or null if it has not been scanned yet. Dimensions and orientation are
 * deliberately absent: a rotate/crop changes both, so the viewer reads them live from the blob.
 */
fun MediaEntity.storedMeta(): PhotoMeta? {
    if (!metaScanned) return null
    val coords = if (latitude != null && longitude != null) latitude to longitude else null
    return PhotoMeta(
        capturedAt = capturedAt,
        camera = camera,
        latLon = coords,
        aperture = aperture,
        shutterSeconds = shutterSeconds,
        iso = iso,
        focalLengthMm = focalLengthMm,
        lens = lens,
        offsetMinutes = capturedOffsetMin,
        altitudeM = altitudeM,
        fileName = originalName,
    )
}

// ⏣ HW ⏣ · code · tools · homelab · ⏣ HW ⏣
