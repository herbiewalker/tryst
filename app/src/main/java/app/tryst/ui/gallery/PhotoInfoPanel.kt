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

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.tryst.R
import app.tryst.data.gallery.GalleryPhoto
import app.tryst.data.media.PhotoMeta
import app.tryst.data.media.PhotoMetadata
import app.tryst.data.media.preferring
import app.tryst.data.media.storedMeta
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

/**
 * The viewer's details sheet: when it was taken, the file, the camera and lens, and where it was taken
 * (coordinates, with copy and "open in a maps app"). Values come from the details stored on the photo's row
 * at import (so they survive a rotate or crop) and fall back to reading the encrypted file. Absent fields
 * are simply omitted. Nothing here touches the network: there is no map tile or place-name lookup, and the
 * maps hand-off only happens when the user confirms it.
 */
@Composable
internal fun PhotoInfoPanel(
    photo: GalleryPhoto,
    onLoadMeta: suspend (blobId: String) -> PhotoMeta,
    showCaptionRow: Boolean,
    onEditCaption: () -> Unit,
    onLeaveApp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val meta by produceState(PhotoMeta(), photo.id, photo.media) {
        val live = onLoadMeta(photo.blobId)
        value = live.preferring(photo.media?.storedMeta())
    }
    val locale = LocalConfiguration.current.locales[0]
    Column(
        modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.45f))
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // Caption row (CAP-1) — only for encounter photos (person portraits have no media row).
        if (showCaptionRow && photo.media != null) {
            val existing = photo.media?.caption?.takeIf { it.isNotBlank() }
            Row(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onEditCaption),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.gallery_caption_label),
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.width(96.dp),
                )
                Text(
                    text = existing ?: stringResource(R.string.gallery_caption_add),
                    color = if (existing != null) Color.White else Color.White.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        meta.capturedAt?.let { InfoRow(stringResource(R.string.gallery_meta_taken), formatTaken(it, meta.offsetMinutes, locale)) }
        meta.fileName?.let { InfoRow(stringResource(R.string.gallery_meta_file), it) }
        val width = meta.width
        val height = meta.height
        if (width != null && height != null) {
            val megapixels = Math.round(width.toLong() * height / 1_000_000.0).toInt()
            val mp = if (megapixels >= 1) "$megapixels MP · " else ""
            InfoRow(stringResource(R.string.gallery_meta_dimensions), "$mp$width × $height")
        }
        meta.camera?.let { InfoRow(stringResource(R.string.gallery_meta_camera), it) }
        meta.lens?.let { InfoRow(stringResource(R.string.gallery_meta_lens), it) }
        // "50 mm · f/1.8 · 1/125 · ISO 200" — condensed shot-settings row when we have any of them.
        val shot = buildList {
            meta.focalLengthMm?.let { add("${it.toInt()} mm") }
            meta.aperture?.let { add("f/%.1f".format(it)) }
            meta.shutterSeconds?.let { add(PhotoMetadata.formatShutter(it)) }
            meta.iso?.let { add("ISO $it") }
        }.joinToString(" · ")
        if (shot.isNotBlank()) InfoRow(stringResource(R.string.gallery_meta_shot), shot)
        meta.orientation?.let { InfoRow(stringResource(R.string.gallery_meta_orientation), PhotoMetadata.orientationLabel(it)) }
        meta.latLon?.let { (lat, lon) -> LocationRows(lat, lon, meta.altitudeM, onLeaveApp) }
        if (!meta.hasAny) {
            Text(
                text = stringResource(R.string.gallery_meta_none),
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun LocationRows(lat: Double, lon: Double, altitudeM: Double?, onLeaveApp: () -> Unit) {
    val context = LocalContext.current
    var confirmOpen by remember { mutableStateOf(false) }
    val coords = formatCoordinates(lat, lon)
    InfoRow(stringResource(R.string.gallery_meta_location), coords)
    altitudeM?.let { InfoRow(stringResource(R.string.gallery_meta_altitude), "${Math.round(it)} m") }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("", Modifier.width(92.dp))
        TextButton(onClick = {
            copyText(context, "%.6f, %.6f".format(Locale.US, lat, lon))
            Toast.makeText(context, R.string.gallery_location_copied, Toast.LENGTH_SHORT).show()
        }) { Text(stringResource(R.string.gallery_location_copy)) }
        TextButton(onClick = { confirmOpen = true }) { Text(stringResource(R.string.gallery_location_open)) }
    }
    if (confirmOpen) {
        AlertDialog(
            onDismissRequest = { confirmOpen = false },
            title = { Text(stringResource(R.string.gallery_location_open_title)) },
            text = { Text(stringResource(R.string.gallery_location_open_body, coords)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmOpen = false
                    onLeaveApp() // the hand-off would otherwise trip the immediate auto-lock
                    openInMaps(context, lat, lon)
                }) { Text(stringResource(R.string.gallery_location_open_confirm)) }
            },
            dismissButton = { TextButton(onClick = { confirmOpen = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.7f),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.width(96.dp),
        )
        Text(text = value, color = Color.White, style = MaterialTheme.typography.bodyMedium)
    }
}

/** "Sun, Jun 21, 2026 · 10:16 PM GMT-04:00"; the zone is the camera's own offset when it recorded one. */
internal fun formatTaken(epochMillis: Long, offsetMinutes: Int?, locale: Locale): String {
    val zone: ZoneId = offsetMinutes?.let { ZoneOffset.ofTotalSeconds(it * 60) } ?: ZoneId.systemDefault()
    val at = Instant.ofEpochMilli(epochMillis).atZone(zone)
    val pattern = if (offsetMinutes != null) "EEE, MMM d, yyyy · h:mm a 'GMT'xxx" else "EEE, MMM d, yyyy · h:mm a"
    return at.format(DateTimeFormatter.ofPattern(pattern, locale))
}

/** "37.69167° N, 85.85417° W". */
internal fun formatCoordinates(lat: Double, lon: Double): String = "%.5f° %s, %.5f° %s".format(Locale.US, abs(lat), if (lat >= 0) "N" else "S", abs(lon), if (lon >= 0) "E" else "W")

private fun copyText(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("coordinates", text))
}

private fun openInMaps(context: Context, lat: Double, lon: Double) {
    val point = "%.6f,%.6f".format(Locale.US, lat, lon)
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:$point?q=$point")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, R.string.gallery_location_no_maps, Toast.LENGTH_SHORT).show()
    }
}

// ⏣ HW ⏣ · code · tools · homelab · ⏣ HW ⏣
