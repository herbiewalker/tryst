package app.tryst.ui.common

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
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
 * Returns a "pick an image" action. Prefers the modern Android Photo Picker (no permission), but
 * some devices/emulators advertise it without actually providing the activity — so we fall back to
 * ACTION_GET_CONTENT (handled by the system Files/Documents UI) instead of crashing.
 */
@Composable
fun rememberImagePicker(onLaunch: () -> Unit = {}, onPicked: (Uri) -> Unit): () -> Unit {
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(onPicked)
    }
    val getContent = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let(onPicked)
    }
    return remember(photoPicker, getContent, onLaunch) {
        {
            onLaunch() // e.g. suppress the auto-lock that the picker handoff would otherwise trigger
            val launched = runCatching {
                photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }.isSuccess
            if (!launched) runCatching { getContent.launch("image/*") }
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
