package app.tryst.ui.common

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * Returns a "pick an image" action. Prefers the modern Android Photo Picker (no permission), but
 * some devices/emulators advertise it without actually providing the activity — so we fall back to
 * ACTION_GET_CONTENT (handled by the system Files/Documents UI) instead of crashing.
 */
@Composable
fun rememberImagePicker(onPicked: (Uri) -> Unit): () -> Unit {
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(onPicked)
    }
    val getContent = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let(onPicked)
    }
    return remember(photoPicker, getContent) {
        {
            val launched = runCatching {
                photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }.isSuccess
            if (!launched) runCatching { getContent.launch("image/*") }
        }
    }
}
