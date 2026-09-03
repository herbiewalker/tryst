// SPDX-License-Identifier: GPL-3.0-or-later
package app.tryst.ui.gallery

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Rotate90DegreesCcw
import androidx.compose.material.icons.filled.Rotate90DegreesCw
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.tryst.R
import app.tryst.data.gallery.GalleryPhoto
import app.tryst.data.media.FractionalRect
import app.tryst.data.media.PhotoTransforms

private const val CROPPER_LOAD_PX = 1600
private val HANDLE_DP = 32.dp

/**
 * Bottom sheet from the photo viewer (EDIT-1) with the two edit affordances: rotate ±90°
 * (applies immediately) and crop (opens the full-screen [PhotoCropper]). Sheet dismisses on any
 * action to signal the change is in flight.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditPhotoSheet(
    onDismiss: () -> Unit,
    onRotate: (degrees: Int) -> Unit,
    onCrop: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp).navigationBarsPadding()) {
            Text(
                text = stringResource(R.string.gallery_edit_photo_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            EditActionRow(Icons.Filled.Rotate90DegreesCcw, stringResource(R.string.gallery_edit_rotate_left)) { onRotate(-90) }
            EditActionRow(Icons.Filled.Rotate90DegreesCw, stringResource(R.string.gallery_edit_rotate_right)) { onRotate(90) }
            EditActionRow(Icons.Filled.Crop, stringResource(R.string.gallery_edit_crop), onClick = onCrop)
            Text(
                text = stringResource(R.string.gallery_edit_photo_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
            )
        }
    }
}

@Composable
private fun EditActionRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(icon, contentDescription = null)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

/**
 * Full-screen crop editor (EDIT-1): the photo is shown fit-to-screen with a draggable rectangle
 * overlay + four corner handles + an aspect chip row (Free / 1:1 / 4:3 / 16:9). Save turns the
 * fractional rect into a pixel crop; Cancel dismisses without touching the blob.
 *
 * Uses a [Dialog] with `usePlatformDefaultWidth = false` so it truly fills the screen and can
 * exist as an overlay above the photo viewer without a nav-graph route (mirrors the pattern the
 * encounter editor's photo preview already uses).
 */
@Composable
@Suppress("LongMethod", "LongParameterList") // Self-contained editor with the crop math inline.
fun PhotoCropper(
    photo: GalleryPhoto,
    bustKey: (String) -> Any,
    onLoad: suspend (blobId: String, reqPx: Int) -> ImageBitmap?,
    onCancel: () -> Unit,
    onSave: (FractionalRect) -> Unit,
) {
    val key = bustKey(photo.blobId)
    var bitmap by remember(key) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(key) { bitmap = onLoad(photo.blobId, CROPPER_LOAD_PX) }
    val bmp = bitmap
    var aspect by remember { mutableStateOf<Float?>(null) } // null = free
    var rect by remember { mutableStateOf(FractionalRect.centeredSquare()) }

    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            if (bmp == null) {
                Text(stringResource(R.string.gallery_edit_loading), color = Color.White, modifier = Modifier.align(Alignment.Center))
            } else {
                CropperCanvas(
                    image = bmp,
                    rect = rect,
                    onRectChange = { newRect ->
                        rect = if (aspect != null) {
                            PhotoTransforms.snapToAspect(newRect, aspect, bmp.width.toFloat() / bmp.height)
                        } else {
                            PhotoTransforms.clampRect(newRect)
                        }
                    },
                )
            }

            Row(
                Modifier.fillMaxWidth().align(Alignment.TopStart)
                    .background(Color.Black.copy(alpha = 0.5f))
                    .statusBarsPadding().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(onClick = onCancel) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_cancel), tint = Color.White)
                }
                TextButton(onClick = { onSave(rect) }, enabled = bmp != null) {
                    Text(stringResource(R.string.action_save), color = Color.White)
                }
            }

            Row(
                Modifier.fillMaxWidth().align(Alignment.BottomStart)
                    .background(Color.Black.copy(alpha = 0.5f))
                    .navigationBarsPadding().padding(horizontal = 12.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            ) {
                AspectChip(stringResource(R.string.gallery_edit_aspect_free), aspect == null) { aspect = null }
                AspectChip(stringResource(R.string.gallery_edit_aspect_1x1), aspect == 1f) {
                    aspect = 1f
                    bmp?.let { rect = PhotoTransforms.snapToAspect(rect, 1f, it.width.toFloat() / it.height) }
                }
                AspectChip(stringResource(R.string.gallery_edit_aspect_4x3), aspect == 4f / 3f) {
                    aspect = 4f / 3f
                    bmp?.let { rect = PhotoTransforms.snapToAspect(rect, 4f / 3f, it.width.toFloat() / it.height) }
                }
                AspectChip(stringResource(R.string.gallery_edit_aspect_16x9), aspect == 16f / 9f) {
                    aspect = 16f / 9f
                    bmp?.let { rect = PhotoTransforms.snapToAspect(rect, 16f / 9f, it.width.toFloat() / it.height) }
                }
            }
        }
    }
}

@Composable
private fun AspectChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}

/**
 * The canvas that owns the crop rect. The image is drawn ContentScale-Fit inside a Box sized to the
 * available space; the rect is stored in fractional (0..1 × 0..1) coordinates and rendered as an
 * overlay whose pixel bounds are derived from the displayed image bounds. Corner handles resize;
 * dragging the rect body translates the whole selection.
 */
@Composable
@Suppress("LongMethod")
private fun CropperCanvas(
    image: ImageBitmap,
    rect: FractionalRect,
    onRectChange: (FractionalRect) -> Unit,
) {
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val handlePx = with(density) { HANDLE_DP.toPx() }

    Box(Modifier.fillMaxSize().onSizeChanged { canvasSize = it }, contentAlignment = Alignment.Center) {
        Image(
            bitmap = image,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
        )

        if (canvasSize.width == 0 || canvasSize.height == 0) return@Box

        val displayed = fitInside(Size(image.width.toFloat(), image.height.toFloat()), canvasSize)
        val px = displayed.pxRect(rect)

        // The dim + border + handles live in a fill overlay so their offsets are in canvas-pixel space
        // (offsets are relative to the overlay's top-left, which equals the canvas's top-left).
        Box(Modifier.fillMaxSize()) {
            // Four dim rectangles framing the selection (above / below / left / right).
            DimBox(0f, 0f, canvasSize.width.toFloat(), displayed.topLeft.y + px.top, density)
            DimBox(0f, displayed.topLeft.y + px.bottom, canvasSize.width.toFloat(), canvasSize.height.toFloat(), density)
            DimBox(0f, displayed.topLeft.y + px.top, displayed.topLeft.x + px.left, displayed.topLeft.y + px.bottom, density)
            DimBox(displayed.topLeft.x + px.right, displayed.topLeft.y + px.top, canvasSize.width.toFloat(), displayed.topLeft.y + px.bottom, density)

            // The crop border + body-drag surface.
            Box(
                Modifier
                    .absoluteOffset(
                        x = with(density) { (displayed.topLeft.x + px.left).toDp() },
                        y = with(density) { (displayed.topLeft.y + px.top).toDp() },
                    )
                    .size(
                        width = with(density) { (px.right - px.left).toDp() },
                        height = with(density) { (px.bottom - px.top).toDp() },
                    )
                    .border(2.dp, Color.White)
                    .pointerInput(image, canvasSize) {
                        detectDragGestures { change, drag ->
                            change.consume()
                            onRectChange(rect.translate(drag.x / displayed.size.width, drag.y / displayed.size.height))
                        }
                    },
            )

            // Four corner handles. Each drag adjusts just that corner in fractional space.
            CornerHandle(displayed.topLeft.x + px.left, displayed.topLeft.y + px.top, handlePx, density) { dx, dy ->
                onRectChange(rect.copy(left = rect.left + dx / displayed.size.width, top = rect.top + dy / displayed.size.height))
            }
            CornerHandle(displayed.topLeft.x + px.right, displayed.topLeft.y + px.top, handlePx, density) { dx, dy ->
                onRectChange(rect.copy(right = rect.right + dx / displayed.size.width, top = rect.top + dy / displayed.size.height))
            }
            CornerHandle(displayed.topLeft.x + px.left, displayed.topLeft.y + px.bottom, handlePx, density) { dx, dy ->
                onRectChange(rect.copy(left = rect.left + dx / displayed.size.width, bottom = rect.bottom + dy / displayed.size.height))
            }
            CornerHandle(displayed.topLeft.x + px.right, displayed.topLeft.y + px.bottom, handlePx, density) { dx, dy ->
                onRectChange(rect.copy(right = rect.right + dx / displayed.size.width, bottom = rect.bottom + dy / displayed.size.height))
            }
        }
    }
}

@Composable
private fun DimBox(fromX: Float, fromY: Float, toX: Float, toY: Float, density: Density) {
    val w = (toX - fromX).coerceAtLeast(0f)
    val h = (toY - fromY).coerceAtLeast(0f)
    if (w <= 0f || h <= 0f) return
    Box(
        Modifier
            .absoluteOffset(x = with(density) { fromX.toDp() }, y = with(density) { fromY.toDp() })
            .size(width = with(density) { w.toDp() }, height = with(density) { h.toDp() })
            .background(Color.Black.copy(alpha = 0.5f)),
    )
}

@Composable
private fun CornerHandle(cx: Float, cy: Float, handlePx: Float, density: Density, onDrag: (Float, Float) -> Unit) {
    Box(
        Modifier
            .absoluteOffset(
                x = with(density) { (cx - handlePx / 2f).toDp() },
                y = with(density) { (cy - handlePx / 2f).toDp() },
            )
            .size(with(density) { handlePx.toDp() })
            .background(Color.White.copy(alpha = 0.9f), CircleShape)
            .pointerInput(Unit) {
                detectDragGestures { change, drag ->
                    change.consume()
                    onDrag(drag.x, drag.y)
                }
            },
    )
}

private data class DisplayedRect(val topLeft: Offset, val size: Size) {
    fun pxRect(fractional: FractionalRect): PxRect = PxRect(
        left = fractional.left * size.width,
        top = fractional.top * size.height,
        right = fractional.right * size.width,
        bottom = fractional.bottom * size.height,
    )
}
private data class PxRect(val left: Float, val top: Float, val right: Float, val bottom: Float)

/** ContentScale.Fit math: returns where inside [canvas] an image of [image] size ends up. */
private fun fitInside(image: Size, canvas: IntSize): DisplayedRect {
    val imageAspect = image.width / image.height
    val canvasAspect = canvas.width.toFloat() / canvas.height
    return if (imageAspect >= canvasAspect) {
        val w = canvas.width.toFloat()
        val h = w / imageAspect
        val top = (canvas.height - h) / 2f
        DisplayedRect(Offset(0f, top), Size(w, h))
    } else {
        val h = canvas.height.toFloat()
        val w = h * imageAspect
        val left = (canvas.width - w) / 2f
        DisplayedRect(Offset(left, 0f), Size(w, h))
    }
}
