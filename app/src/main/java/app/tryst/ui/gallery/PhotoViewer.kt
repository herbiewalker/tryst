package app.tryst.ui.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.tryst.R
import app.tryst.data.db.entity.MediaEntity
import app.tryst.data.gallery.GalleryPhoto
import app.tryst.ui.common.DecodedImage
import kotlin.math.abs

private const val VIEWER_PX = 1600
private const val MAX_ZOOM = 4f

/**
 * Full-screen photo viewer: swipe between the gallery's photos, pinch-to-zoom and pan the current one,
 * tap to toggle the chrome, and jump to the owning tryst. `FLAG_SECURE` (set app-wide) already blocks
 * screenshots here. A page change resets the zoom so the next photo starts fit-to-screen.
 */
@Composable
fun PhotoViewer(
    photos: List<GalleryPhoto>,
    initialIndex: Int,
    onClose: () -> Unit,
    onOpenEncounter: (String) -> Unit,
    onLoad: suspend (media: MediaEntity, reqPx: Int) -> ImageBitmap?,
) {
    if (photos.isEmpty()) return
    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, photos.lastIndex),
        pageCount = { photos.size },
    )
    var chromeVisible by remember { mutableStateOf(true) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            ZoomablePhoto(
                photo = photos[page],
                onLoad = onLoad,
                onTap = { chromeVisible = !chromeVisible },
                // Only the settled page reads gestures, so a mid-swipe pinch doesn't fight the pager.
                zoomEnabled = page == pagerState.currentPage,
            )
        }

        if (chromeVisible) {
            val current = photos[pagerState.currentPage]
            Box(
                Modifier.fillMaxWidth().align(Alignment.TopStart)
                    .background(Color.Black.copy(alpha = 0.35f))
                    .statusBarsPadding().padding(4.dp),
            ) {
                IconButton(onClick = onClose, modifier = Modifier.align(Alignment.CenterStart)) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_close), tint = Color.White)
                }
                Text(
                    text = "${pagerState.currentPage + 1} / ${photos.size}",
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.align(Alignment.Center),
                )
                IconButton(
                    onClick = { onOpenEncounter(current.encounterId) },
                    modifier = Modifier.align(Alignment.CenterEnd),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = stringResource(R.string.gallery_open_tryst),
                        tint = Color.White,
                    )
                }
            }
        }
    }
}

@Composable
private fun ZoomablePhoto(
    photo: GalleryPhoto,
    onLoad: suspend (media: MediaEntity, reqPx: Int) -> ImageBitmap?,
    onTap: () -> Unit,
    zoomEnabled: Boolean,
) {
    var scale by remember(photo.id) { mutableFloatStateOf(1f) }
    var offsetX by remember(photo.id) { mutableFloatStateOf(0f) }
    var offsetY by remember(photo.id) { mutableFloatStateOf(0f) }

    Box(
        Modifier.fillMaxSize()
            .pointerInput(photo.id, zoomEnabled) {
                if (!zoomEnabled) return@pointerInput
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, MAX_ZOOM)
                    if (scale > 1f) {
                        offsetX += pan.x
                        offsetY += pan.y
                    } else {
                        offsetX = 0f
                        offsetY = 0f
                    }
                }
            }
            .pointerInput(photo.id) {
                detectTapGestures(
                    onTap = { onTap() },
                    // Double-tap toggles between fit and 2x.
                    onDoubleTap = {
                        if (abs(scale - 1f) < 0.01f) {
                            scale = 2f
                        } else {
                            scale = 1f
                            offsetX = 0f
                            offsetY = 0f
                        }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        DecodedImage(
            model = photo.id,
            contentDescription = stringResource(R.string.cd_photo),
            modifier = Modifier.fillMaxSize().graphicsLayer(
                scaleX = scale,
                scaleY = scale,
                translationX = offsetX,
                translationY = offsetY,
            ),
            contentScale = ContentScale.Fit,
            load = { onLoad(photo.media, VIEWER_PX) },
        )
    }
}
