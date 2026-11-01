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
package app.tryst.ui.common

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID

/**
 * Returns a "pick an image" action. Uses the system document picker rather than the Android Photo Picker:
 * the Photo Picker strips location (and other EXIF) from every photo it returns, while the document
 * picker hands over the original file, so a photo's GPS and camera details survive for its details sheet
 * and, later, a map. No permission is needed.
 */
@Composable
fun rememberImagePicker(onLaunch: () -> Unit = {}, onPicked: (Uri) -> Unit): () -> Unit {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(onPicked)
    }
    return remember(picker, onLaunch) {
        {
            onLaunch() // e.g. suppress the auto-lock that the picker handoff would otherwise trigger
            runCatching { picker.launch(arrayOf("image/*")) }
            Unit
        }
    }
}

/**
 * Multi-pick variant of [rememberImagePicker]: returns URIs for every selected image in one call.
 */
@Composable
fun rememberMultiImagePicker(onLaunch: () -> Unit = {}, onPicked: (List<Uri>) -> Unit): () -> Unit {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) onPicked(uris)
    }
    return remember(picker, onLaunch) {
        {
            onLaunch()
            runCatching { picker.launch(arrayOf("image/*")) }
            Unit
        }
    }
}

/**
 * Returns a "take a photo" action that captures directly into the app's private cache via the
 * system camera (no CAMERA permission needed for ACTION_IMAGE_CAPTURE), so the shot never lands in
 * the device gallery or cloud backup. On success, [onCaptured] gets the content URI and the temp
 * [File] — the caller must encrypt it and then delete the temp. On cancel/failure the temp is removed.
 */
@Composable
fun rememberCameraCapture(onLaunch: () -> Unit = {}, onCaptured: (Uri, File) -> Unit): () -> Unit {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<Pair<Uri, File>?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val captured = pending
        pending = null
        if (success && captured != null) onCaptured(captured.first, captured.second) else captured?.second?.delete()
    }
    return remember(context, launcher, onLaunch) {
        {
            val dir = File(context.cacheDir, "captures").apply { mkdirs() }
            val file = File(dir, "cap_${UUID.randomUUID()}.jpg")
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            pending = uri to file
            onLaunch() // suppress the auto-lock that the camera handoff would otherwise trigger
            runCatching { launcher.launch(uri) }.onFailure {
                pending = null
                file.delete()
            }
        }
    }
}

// ⏣ HW ⏣ · code · tools · homelab · ⏣ HW ⏣
