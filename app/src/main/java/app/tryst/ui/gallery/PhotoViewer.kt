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

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PersonPin
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import app.tryst.R
import app.tryst.core.prefs.CaptionEntryPoint
import app.tryst.data.gallery.GalleryPartner
import app.tryst.data.gallery.GalleryPhoto
import app.tryst.data.media.PhotoMeta
import app.tryst.ui.common.DecodedImage
import app.tryst.ui.common.TooltipIconButton
import app.tryst.ui.theme.GridTileShape
import kotlin.math.abs
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val VIEWER_PX = 1600
private const val FILMSTRIP_PX = 160
private const val MAX_ZOOM = 4f
private const val DOUBLE_TAP_ZOOM = 2f
private const val DOUBLE_TAP_MS = 220
private const val PAN_EPSILON = 0.5f
private val DISMISS_DISTANCE = 120.dp
private val INFO_PANEL_GUESS = 180.dp

/** What the current vertical drag is moving: the details sheet or the photo (to close). */
private enum class DragMode { NONE, DETAILS, CLOSE }

/** Keeps a zoomed photo's pan inside the room the zoom created, so it can't be dragged off screen. */
private fun clampPan(offset: Float, extent: Int, scale: Float): Float {
    val limit = extent * (scale - 1f) / 2f
    return offset.coerceIn(-limit, limit)
}
private val ACCENT = Color(0xFF80CBC4)

/**
 * A person the viewer can tag the current photo onto — every active partner plus "You" (self profile).
 * Distinct from [GalleryPartner] because the tag flow needs the storage kind (`partner` / `profile`),
 * which encounter photos don't carry.
 */
data class AssignablePerson(val kind: String, val ownerId: String, val displayName: String?)

/**
 * Full-screen photo viewer: swipe between the gallery's photos, pinch-to-zoom and pan the current one,
 * tap to toggle the chrome, and jump to the owning tryst. `FLAG_SECURE` (set app-wide) already blocks
 * screenshots here. A page change resets the zoom so the next photo starts fit-to-screen.
 *
 * Also the per-photo edit surface: a favourite star (GAL-3), a slideshow toggle, a filmstrip strip to
 * jump within the set, and "set as partner avatar" (GAL-5).
 */
@Composable
@Suppress("LongParameterList", "LongMethod") // A self-contained full-screen surface with several actions.
fun PhotoViewer(
    photos: List<GalleryPhoto>,
    initialIndex: Int,
    onClose: () -> Unit,
    onOpenEncounter: (String) -> Unit,
    onLoad: suspend (blobId: String, reqPx: Int) -> ImageBitmap?,
    onLoadMeta: suspend (blobId: String) -> PhotoMeta,
    onToggleFavorite: (GalleryPhoto) -> Unit,
    onSetAvatar: (blobId: String, partnerId: String) -> Unit,
    assignablePeople: List<AssignablePerson>,
    onAddToPerson: (blobId: String, kind: String, ownerId: String) -> Unit,
    slideshowIntervalSeconds: Int,
    slideshowShuffle: Boolean,
    captionEntryPoint: CaptionEntryPoint,
    onSetCaption: (GalleryPhoto, String?) -> Unit,
    bustKey: (String) -> Any,
    onRotate: (GalleryPhoto, Int) -> Unit,
    onCrop: (GalleryPhoto, app.tryst.data.media.FractionalRect) -> Unit,
    onDelete: (GalleryPhoto) -> Unit,
    peekThumb: (blobId: String) -> ImageBitmap? = { null },
    pendingDeleteCount: Int = 0,
    onUndoDelete: () -> Unit = {},
    onLeaveApp: () -> Unit = {},
) {
    if (photos.isEmpty()) return
    // The viewer is an overlay, not a route, so system back would otherwise pop the whole Photos tab.
    BackHandler(onBack = onClose)
    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, photos.lastIndex),
        pageCount = { photos.size },
    )
    // After a delete the photo list shrinks a frame before the pager re-clamps its page, so every
    // read of the current photo goes through this clamped index.
    val page = pagerState.currentPage.coerceIn(0, photos.lastIndex)
    var chromeVisible by remember { mutableStateOf(true) }
    var moreMenu by remember { mutableStateOf(false) }
    // Swipe-down-to-close: how far the photo has been dragged vertically (px), and the close threshold.
    var dismissY by remember { mutableFloatStateOf(0f) }
    val dismissDistance = with(LocalDensity.current) { DISMISS_DISTANCE.toPx() }
    // Only a downward pull fades the scrim and shrinks the photo; an upward pull is the details gesture.
    val dismissProgress = (dismissY.coerceAtLeast(0f) / (dismissDistance * 2.5f)).coerceIn(0f, 1f)
    var showInfo by remember { mutableStateOf(false) }
    // Details sheet progress, 0 (hidden) .. 1 (fully up). A finger drives it directly while dragging, so the
    // panel slides in under the photo and the photo lifts with it; releasing settles it open or shut.
    var infoProgress by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    var infoPanelPx by remember { mutableFloatStateOf(with(density) { INFO_PANEL_GUESS.toPx() }) }
    var infoJob by remember { mutableStateOf<Job?>(null) }
    var dragMode by remember { mutableStateOf(DragMode.NONE) }
    fun settleInfo(open: Boolean) {
        showInfo = open
        infoJob?.cancel()
        infoJob = scope.launch { animate(infoProgress, if (open) 1f else 0f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) { v, _ -> infoProgress = v } }
    }
    var slideshow by remember { mutableStateOf(false) }
    var avatarMenu by remember { mutableStateOf(false) }
    var addToPersonMenu by remember { mutableStateOf(false) }
    var editingCaption by remember { mutableStateOf<GalleryPhoto?>(null) }
    var editSheetOpen by remember { mutableStateOf(false) }
    var cropping by remember { mutableStateOf<GalleryPhoto?>(null) }

    // Shuffle order: a Fisher-Yates permutation regenerated every time slideshow flips on OR the set of
    // photos changes. Playing consumes it end-to-end (with the current page slotted first) before reshuffling,
    // so no repeats until every photo has shown.
    val shuffleOrder = remember(photos.size, slideshow, slideshowShuffle) {
        if (!slideshow || !slideshowShuffle) {
            emptyList()
        } else {
            val order = photos.indices.shuffled()
            val current = pagerState.currentPage
            listOf(current) + order.filter { it != current }
        }
    }
    // Slideshow: advance every [slideshowIntervalSeconds] seconds. Cancelled when the user turns it off;
    // a manual swipe simply feeds the next tick from the new page.
    LaunchedEffect(slideshow, slideshowIntervalSeconds, pagerState.currentPage, shuffleOrder) {
        if (!slideshow) return@LaunchedEffect
        delay(slideshowIntervalSeconds.coerceAtLeast(1) * 1000L)
        val next = if (slideshowShuffle && shuffleOrder.isNotEmpty()) {
            val idx = shuffleOrder.indexOf(pagerState.currentPage)
            shuffleOrder[(idx + 1).mod(shuffleOrder.size)]
        } else {
            (pagerState.currentPage + 1) % photos.size
        }
        pagerState.animateScrollToPage(next)
    }

    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 1f - 0.75f * dismissProgress))) {
        UndoDeleteBar(
            count = pendingDeleteCount,
            onUndo = onUndoDelete,
            modifier = Modifier.align(Alignment.BottomCenter).zIndex(2f),
        )
        HorizontalPager(
            state = pagerState,
            // Keep the neighbours composed so their images are decoded before you swipe to them.
            beyondViewportPageCount = 1,
            key = { index -> photos.getOrNull(index)?.blobId ?: index },
            modifier = Modifier.fillMaxSize().graphicsLayer {
                translationY = dismissY - infoProgress * infoPanelPx * 0.5f
                val shrink = 1f - 0.15f * dismissProgress
                scaleX = shrink
                scaleY = shrink
            },
        ) { index ->
            ZoomablePhoto(
                photo = photos[index],
                modelKey = bustKey(photos[index].blobId),
                onLoad = onLoad,
                placeholder = peekThumb(photos[index].blobId),
                onTap = { chromeVisible = !chromeVisible },
                // Only the settled page reads gestures, so a mid-swipe pinch doesn't fight the pager.
                zoomEnabled = index == pagerState.currentPage,
                onDismissDrag = { dy ->
                    if (dragMode == DragMode.NONE) {
                        infoJob?.cancel()
                        // Pull up, or pull down while the details are open, drags the details sheet; otherwise it drags the photo to close.
                        dragMode = if (dy < 0f || infoProgress > 0f) DragMode.DETAILS else DragMode.CLOSE
                    }
                    if (dragMode == DragMode.DETAILS) {
                        infoProgress = (infoProgress - dy / infoPanelPx).coerceIn(0f, 1f)
                        chromeVisible = true
                    } else {
                        dismissY = (dismissY + dy).coerceAtLeast(0f)
                    }
                },
                onDismissEnd = {
                    val mode = dragMode
                    dragMode = DragMode.NONE
                    if (mode == DragMode.DETAILS) {
                        settleInfo(infoProgress >= 0.5f)
                    } else if (dismissY > dismissDistance) {
                        onClose()
                    } else {
                        scope.launch { animate(dismissY, 0f) { value, _ -> dismissY = value } }
                    }
                },
            )
        }

        // Favourite pill sits on the photo itself (bottom-right, safe area) so the top chrome's
        // "N / M" counter is never covered. Stays visible whether or not the chrome is showing —
        // it's a photo-level control, not part of the top bar.
        val currentPhoto = photos[page]
        FavouritePill(
            favourite = currentPhoto.favorite,
            onToggle = { onToggleFavorite(currentPhoto) },
            modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(bottom = 84.dp, end = 16.dp),
        )

        if (chromeVisible && dismissY == 0f) {
            val current = photos[page]
            Box(
                Modifier.fillMaxWidth().align(Alignment.TopStart)
                    .background(Color.Black.copy(alpha = 0.35f))
                    .statusBarsPadding().padding(4.dp),
            ) {
                // Close + N/M counter live together on the left. Anchoring the counter to the
                // centre of the top bar bit us as the right-hand action row grew (slideshow,
                // add-to-person, avatar, info, open-tryst) — on narrower screens the icons ran
                // over the counter. Pinning it to Close means new actions can never overlap it.
                Row(
                    modifier = Modifier.align(Alignment.CenterStart),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TooltipIconButton(label = stringResource(R.string.action_close), onClick = onClose) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_close), tint = Color.White)
                    }
                    Text(
                        text = "${page + 1} / ${photos.size}",
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                Row(Modifier.align(Alignment.CenterEnd)) {
                    TooltipIconButton(label = stringResource(R.string.gallery_slideshow), onClick = { slideshow = !slideshow }) {
                        Icon(
                            Icons.Filled.Slideshow,
                            contentDescription = stringResource(R.string.gallery_slideshow),
                            tint = if (slideshow) ACCENT else Color.White,
                        )
                    }
                    if (captionEntryPoint.showsTopRowButton && current.media != null) {
                        TooltipIconButton(label = stringResource(R.string.gallery_caption_edit_action), onClick = { editingCaption = current }) {
                            Icon(
                                Icons.AutoMirrored.Outlined.Notes,
                                contentDescription = stringResource(R.string.gallery_caption_edit_action),
                                tint = if (!current.media?.caption.isNullOrBlank()) ACCENT else Color.White,
                            )
                        }
                    }
                    TooltipIconButton(label = stringResource(R.string.gallery_edit_photo), onClick = { editSheetOpen = true }) {
                        Icon(
                            Icons.Outlined.Edit,
                            contentDescription = stringResource(R.string.gallery_edit_photo),
                            tint = Color.White,
                        )
                    }
                    TooltipIconButton(label = stringResource(R.string.cd_photo_info), onClick = { settleInfo(!showInfo) }) {
                        Icon(
                            Icons.Outlined.Info,
                            contentDescription = stringResource(R.string.cd_photo_info),
                            tint = if (showInfo) ACCENT else Color.White,
                        )
                    }
                    TooltipIconButton(label = stringResource(R.string.action_delete), onClick = { onDelete(current) }) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = stringResource(R.string.action_delete),
                            tint = Color.White,
                        )
                    }
                    // Less-used actions live in an overflow menu so the bar never crowds the counter.
                    val enc = current.encounterId
                    Box {
                        TooltipIconButton(label = stringResource(R.string.action_more), onClick = { moreMenu = true }) {
                            Icon(
                                Icons.Filled.MoreVert,
                                contentDescription = stringResource(R.string.action_more),
                                tint = Color.White,
                            )
                        }
                        DropdownMenu(expanded = moreMenu, onDismissRequest = { moreMenu = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.gallery_add_to_person_photos)) },
                                leadingIcon = { Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null) },
                                enabled = assignablePeople.isNotEmpty(),
                                onClick = {
                                    moreMenu = false
                                    addToPersonMenu = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.gallery_set_avatar)) },
                                leadingIcon = { Icon(Icons.Filled.PersonPin, contentDescription = null) },
                                enabled = current.partners.isNotEmpty(),
                                onClick = {
                                    moreMenu = false
                                    avatarMenu = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.gallery_open_tryst)) },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null) },
                                enabled = enc != null,
                                onClick = {
                                    moreMenu = false
                                    enc?.let(onOpenEncounter)
                                },
                            )
                        }
                        // The two pickers open from the overflow button's anchor once chosen above.
                        AddToPersonMenu(
                            expanded = addToPersonMenu,
                            people = assignablePeople,
                            onDismiss = { addToPersonMenu = false },
                            onPick = { p ->
                                onAddToPerson(current.blobId, p.kind, p.ownerId)
                                addToPersonMenu = false
                            },
                        )
                        AvatarPartnerMenu(
                            expanded = avatarMenu,
                            partners = current.partners,
                            onDismiss = { avatarMenu = false },
                            onPick = { partnerId ->
                                onSetAvatar(current.blobId, partnerId)
                                avatarMenu = false
                            },
                        )
                    }
                }
            }

            Column(Modifier.align(Alignment.BottomStart).fillMaxWidth()) {
                if (infoProgress > 0f) {
                    Box(
                        Modifier
                            .onSizeChanged { infoPanelPx = it.height.toFloat() }
                            .graphicsLayer {
                                translationY = (1f - infoProgress) * size.height
                                alpha = infoProgress
                            },
                    ) {
                        PhotoInfoPanel(
                            photo = photos[page],
                            onLoadMeta = onLoadMeta,
                            showCaptionRow = captionEntryPoint.showsInfoPanelField,
                            onEditCaption = { editingCaption = photos[page] },
                            onLeaveApp = onLeaveApp,
                        )
                    }
                }
                Filmstrip(
                    photos = photos,
                    currentPage = page,
                    onLoad = onLoad,
                    bustKey = bustKey,
                    onSelect = { index -> scope.launch { pagerState.animateScrollToPage(index) } },
                )
            }
        }
    }

    editingCaption?.let { photo ->
        CaptionEditDialog(
            initial = photo.media?.caption.orEmpty(),
            onDismiss = { editingCaption = null },
            onSave = { text ->
                onSetCaption(photo, text)
                editingCaption = null
            },
        )
    }

    if (editSheetOpen) {
        val current = photos[page]
        EditPhotoSheet(
            onDismiss = { editSheetOpen = false },
            onRotate = { deg ->
                onRotate(current, deg)
                editSheetOpen = false
            },
            onCrop = {
                editSheetOpen = false
                cropping = current
            },
        )
    }

    cropping?.let { photo ->
        PhotoCropper(
            photo = photo,
            bustKey = bustKey,
            onLoad = onLoad,
            onCancel = { cropping = null },
            onSave = { rect ->
                onCrop(photo, rect)
                cropping = null
            },
        )
    }
}

/**
 * Modal editor for a photo's caption (CAP-1). Blank text saves as NULL (clears the caption); the
 * dialog exposes a dedicated Clear action so a user with an existing caption can wipe it in one tap
 * without selecting the whole string.
 */
@Composable
private fun CaptionEditDialog(initial: String, onDismiss: () -> Unit, onSave: (String?) -> Unit) {
    var text by remember(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.gallery_caption_edit_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = false,
                placeholder = { Text(stringResource(R.string.gallery_caption_placeholder)) },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(text.takeIf { it.isNotBlank() }) }) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            Row {
                if (initial.isNotBlank()) {
                    TextButton(onClick = { onSave(null) }) {
                        Text(stringResource(R.string.gallery_caption_clear))
                    }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        },
    )
}

/**
 * A round favourite pill overlaying the photo — filled + accent when favourited, outlined + white
 * when not. Fixed position (bottom-end above where the filmstrip sits when chrome is up); stays
 * visible whether or not the top chrome is showing, so it never covers the "N / M" counter.
 */
@Composable
private fun FavouritePill(favourite: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(onClick = onToggle),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (favourite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
            contentDescription = stringResource(if (favourite) R.string.gallery_unfavorite else R.string.gallery_favorite),
            tint = if (favourite) ACCENT else Color.White,
            modifier = Modifier.size(22.dp),
        )
    }
}

/** A menu to pick which of the photo's partners this photo becomes the avatar for (GAL-5). */
@Composable
private fun AvatarPartnerMenu(
    expanded: Boolean,
    partners: List<GalleryPartner>,
    onDismiss: () -> Unit,
    onPick: (partnerId: String) -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        partners.forEach { p ->
            val name = p.name?.takeIf { it.isNotBlank() } ?: stringResource(R.string.gallery_group_anonymous)
            DropdownMenuItem(
                text = { Text(stringResource(R.string.gallery_set_avatar_for, name)) },
                onClick = { onPick(p.id) },
            )
        }
    }
}

/**
 * Menu of every person the current photo can be tagged onto — active partners plus You. Picking one
 * copies the photo (its blob is re-encrypted into a new portrait row) into that person's album; the
 * original encounter photo stays exactly where it was. Cheap way to fix a mis-attributed photo without
 * touching the encounter's partner list.
 */
@Composable
private fun AddToPersonMenu(
    expanded: Boolean,
    people: List<AssignablePerson>,
    onDismiss: () -> Unit,
    onPick: (AssignablePerson) -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        people.forEach { p ->
            val name = p.displayName?.takeIf { it.isNotBlank() } ?: stringResource(R.string.gallery_group_anonymous)
            DropdownMenuItem(
                text = { Text(stringResource(R.string.gallery_add_to_person_photos_for, name)) },
                onClick = { onPick(p) },
            )
        }
    }
}

/** A thumbnail strip along the bottom for jumping within the current set. */
@Composable
private fun Filmstrip(
    photos: List<GalleryPhoto>,
    currentPage: Int,
    onLoad: suspend (blobId: String, reqPx: Int) -> ImageBitmap?,
    bustKey: (String) -> Any,
    onSelect: (Int) -> Unit,
) {
    val listState = rememberLazyListState()
    // Keep the current thumbnail in view as pages change (swipe or slideshow).
    LaunchedEffect(currentPage) { listState.animateScrollToItem(currentPage.coerceAtLeast(0)) }
    LazyRow(
        state = listState,
        modifier = Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.45f)).navigationBarsPadding().padding(vertical = 6.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        itemsIndexed(photos, key = { _, p -> p.id }) { index, photo ->
            DecodedImage(
                model = "strip:${bustKey(photo.blobId)}",
                contentDescription = null,
                modifier = Modifier
                    .size(48.dp)
                    .clip(GridTileShape)
                    .graphicsLayer { alpha = if (index == currentPage) 1f else 0.55f }
                    .clickable { onSelect(index) },
                contentScale = ContentScale.Crop,
                load = { onLoad(photo.blobId, FILMSTRIP_PX) },
            )
        }
    }
}

@Composable
private fun ZoomablePhoto(
    photo: GalleryPhoto,
    modelKey: Any,
    onLoad: suspend (blobId: String, reqPx: Int) -> ImageBitmap?,
    placeholder: ImageBitmap?,
    onTap: () -> Unit,
    zoomEnabled: Boolean,
    onDismissDrag: (dy: Float) -> Unit,
    onDismissEnd: () -> Unit,
) {
    var scale by remember(modelKey) { mutableFloatStateOf(1f) }
    var offsetX by remember(modelKey) { mutableFloatStateOf(0f) }
    var offsetY by remember(modelKey) { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
    var zoomJob by remember(modelKey) { mutableStateOf<Job?>(null) }

    Box(
        Modifier.fillMaxSize()
            .pointerInput(photo.id, zoomEnabled) {
                if (!zoomEnabled) return@pointerInput
                // Hand-rolled instead of detectTransformGestures: that detector consumes EVERY drag,
                // including a one-finger swipe on an un-zoomed photo, so the pager never saw it and
                // swiping between photos was dead. Here a gesture is only consumed when it is a pinch,
                // or a pan the zoomed photo can still absorb; otherwise it falls through to the pager.
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    zoomJob?.cancel() // a touch takes over from a running double-tap animation
                    var dragX = 0f
                    var dragY = 0f
                    var dismissing = false
                    var panned = false // this gesture has already moved the zoomed photo
                    do {
                        val event = awaitPointerEvent()
                        if (event.changes.none { it.isConsumed }) {
                            val pinching = event.changes.count { it.pressed } > 1
                            if (pinching || scale > 1f) {
                                val pan = event.calculatePan()
                                val newScale = (scale * event.calculateZoom()).coerceIn(1f, MAX_ZOOM)
                                val ratio = newScale / scale
                                val centroid = event.calculateCentroid(useCurrent = false)
                                // Zoom about the fingers, not the screen centre.
                                val focus = if (centroid.isSpecified) {
                                    centroid - Offset(size.width / 2f, size.height / 2f)
                                } else {
                                    Offset.Zero
                                }
                                val newX = clampPan(focus.x + (offsetX - focus.x) * ratio + pan.x, size.width, newScale)
                                val newY = clampPan(focus.y + (offsetY - focus.y) * ratio + pan.y, size.height, newScale)
                                // One finger on a zoomed photo: keep it only while it can still move that way,
                                // so a swipe at the photo's edge turns into a page change.
                                val absorbed = pinching ||
                                    abs(newX - offsetX) > PAN_EPSILON ||
                                    (abs(pan.y) > abs(pan.x) && abs(newY - offsetY) > PAN_EPSILON)
                                scale = newScale
                                offsetX = newX
                                offsetY = newY
                                // Once a gesture has panned the photo it stays a pan to its end, so overshooting
                                // an edge never flips the page; only a swipe that starts at the edge does.
                                if (absorbed) panned = true
                                if (absorbed || panned) event.changes.forEach { it.consume() }
                            } else {
                                // One finger on an un-zoomed photo: a mostly-vertical drag pulls the photo down to close;
                                // anything else is left alone for the pager.
                                val pan = event.calculatePan()
                                dragX += pan.x
                                dragY += pan.y
                                if (!dismissing && abs(dragY) > viewConfiguration.touchSlop && abs(dragY) > abs(dragX) * 1.5f) {
                                    dismissing = true
                                    onDismissDrag(dragY)
                                } else if (dismissing) {
                                    onDismissDrag(pan.y)
                                }
                                if (dismissing) event.changes.forEach { it.consume() }
                            }
                        }
                    } while (event.changes.any { it.pressed })
                    if (dismissing) onDismissEnd()
                }
            }
            .pointerInput(photo.id) {
                detectTapGestures(
                    onTap = { onTap() },
                    // Double-tap toggles between fit and 2x, animated.
                    onDoubleTap = { tap ->
                        val zoomIn = abs(scale - 1f) < 0.01f
                        val focus = tap - Offset(size.width / 2f, size.height / 2f)
                        val toScale = if (zoomIn) DOUBLE_TAP_ZOOM else 1f
                        // Zoom in about the tapped point; zoom out recentres.
                        val toX = if (zoomIn) clampPan(-focus.x, size.width, toScale) else 0f
                        val toY = if (zoomIn) clampPan(-focus.y, size.height, toScale) else 0f
                        val (fromScale, fromX, fromY) = Triple(scale, offsetX, offsetY)
                        zoomJob?.cancel()
                        zoomJob = scope.launch {
                            animate(0f, 1f, animationSpec = tween(DOUBLE_TAP_MS)) { t, _ ->
                                scale = fromScale + (toScale - fromScale) * t
                                offsetX = fromX + (toX - fromX) * t
                                offsetY = fromY + (toY - fromY) * t
                            }
                        }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        DecodedImage(
            model = modelKey,
            contentDescription = stringResource(R.string.cd_photo),
            modifier = Modifier.fillMaxSize().graphicsLayer(
                scaleX = scale,
                scaleY = scale,
                translationX = offsetX,
                translationY = offsetY,
            ),
            contentScale = ContentScale.Fit,
            placeholder = placeholder,
            load = { onLoad(photo.blobId, VIEWER_PX) },
        )
    }
}

// ⏣ HW ⏣ · code · tools · homelab · ⏣ HW ⏣
