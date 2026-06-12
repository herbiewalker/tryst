package app.tryst.ui.common

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import java.io.InputStream
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Decodes images for display without any third-party loader (no network-capable lib, keeping the
 * zero-permission/no-network guarantee). [open] must return a *fresh* stream each call — it's read
 * twice: once for bounds, once downsampled to ~[reqPx]. For encrypted media, [open] returns the
 * in-memory decrypting stream; the decoded bitmap never touches disk.
 */
object MediaImages {

    suspend fun decodeSampled(reqPx: Int, open: () -> InputStream?): ImageBitmap? = withContext(Dispatchers.IO) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching { open()?.use { BitmapFactory.decodeStream(it, null, bounds) } }.getOrNull()
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, reqPx)
        }
        runCatching { open()?.use { BitmapFactory.decodeStream(it, null, opts) } }
            .getOrNull()?.asImageBitmap()
    }

    private fun sampleSize(width: Int, height: Int, reqPx: Int): Int {
        if (reqPx <= 0) return 1
        var sample = 1
        val maxDim = max(width, height)
        while (maxDim / (sample * 2) >= reqPx) sample *= 2
        return sample
    }
}

/**
 * Renders a decrypted image, loading it off the main thread keyed by [model]. Shows a neutral
 * placeholder while decoding (or if decode fails).
 */
@Composable
fun DecodedImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    load: suspend () -> ImageBitmap?,
) {
    var bitmap by remember(model) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(model) { bitmap = load() }
    val bmp = bitmap
    if (bmp != null) {
        Image(bitmap = bmp, contentDescription = contentDescription, modifier = modifier, contentScale = contentScale)
    } else {
        Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant))
    }
}
