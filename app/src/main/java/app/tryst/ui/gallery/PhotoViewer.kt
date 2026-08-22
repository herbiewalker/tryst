// SPDX-License-Identifier: GPL-3.0-or-later
package app.tryst.ui.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PersonPin
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.tryst.R
import app.tryst.data.gallery.GalleryPartner
import app.tryst.data.gallery.GalleryPhoto
import app.tryst.data.media.PhotoMeta
import app.tryst.data.media.PhotoMetadata
import app.tryst.ui.common.DecodedImage
import app.tryst.ui.common.Format
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val VIEWER_PX = 1600
private const val FILMSTRIP_PX = 160
private const val MAX_ZOOM = 4f
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
) {
    if (photos.isEmpty()) return
    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, photos.lastIndex),
        pageCount = { photos.size },
    )
    var chromeVisible by remember { mutableStateOf(true) }
    var showInfo by remember { mutableStateOf(false) }
    var slideshow by remember { mutableStateOf(false) }
    var avatarMenu by remember { mutableStateOf(false) }
    var addToPersonMenu by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

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

        // Favourite pill sits on the photo itself (bottom-right, safe area) so the top chrome's
        // "N / M" counter is never covered. Stays visible whether or not the chrome is showing —
        // it's a photo-level control, not part of the top bar.
        val currentPhoto = photos[pagerState.currentPage]
        FavouritePill(
            favourite = currentPhoto.favorite,
            onToggle = { onToggleFavorite(currentPhoto) },
            modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(bottom = 84.dp, end = 16.dp),
        )

        if (chromeVisible) {
            val current = photos[pagerState.currentPage]
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
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_close), tint = Color.White)
                    }
                    Text(
                        text = "${pagerState.currentPage + 1} / ${photos.size}",
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                Row(Modifier.align(Alignment.CenterEnd)) {
                    IconButton(onClick = { slideshow = !slideshow }) {
                        Icon(
                            Icons.Filled.Slideshow,
                            contentDescription = stringResource(R.string.gallery_slideshow),
                            tint = if (slideshow) ACCENT else Color.White,
                        )
                    }
                    Box {
                        IconButton(
                            onClick = { addToPersonMenu = true },
                            enabled = assignablePeople.isNotEmpty(),
                        ) {
                            Icon(
                                Icons.Filled.AddPhotoAlternate,
                                contentDescription = stringResource(R.string.gallery_add_to_person_photos),
                                tint = if (assignablePeople.isNotEmpty()) Color.White else Color.White.copy(alpha = 0.4f),
                            )
                        }
                        AddToPersonMenu(
                            expanded = addToPersonMenu,
                            people = assignablePeople,
                            onDismiss = { addToPersonMenu = false },
                            onPick = { p ->
                                onAddToPerson(current.blobId, p.kind, p.ownerId)
                                addToPersonMenu = false
                            },
                        )
                    }
                    Box {
                        IconButton(
                            onClick = { avatarMenu = true },
                            enabled = current.partners.isNotEmpty(),
                        ) {
                            Icon(
                                Icons.Filled.PersonPin,
                                contentDescription = stringResource(R.string.gallery_set_avatar),
                                tint = if (current.partners.isNotEmpty()) Color.White else Color.White.copy(alpha = 0.4f),
                            )
                        }
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
                    IconButton(onClick = { showInfo = !showInfo }) {
                        Icon(
                            Icons.Outlined.Info,
                            contentDescription = stringResource(R.string.cd_photo_info),
                            tint = if (showInfo) ACCENT else Color.White,
                        )
                    }
                    val enc = current.encounterId
                    IconButton(onClick = { enc?.let(onOpenEncounter) }, enabled = enc != null) {
                        Icon(
                            Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = stringResource(R.string.gallery_open_tryst),
                            tint = if (enc != null) Color.White else Color.White.copy(alpha = 0.4f),
                        )
                    }
                }
            }

            Column(Modifier.align(Alignment.BottomStart).fillMaxWidth()) {
                if (showInfo) {
                    PhotoInfoPanel(photo = photos[pagerState.currentPage], onLoadMeta = onLoadMeta)
                }
                Filmstrip(
                    photos = photos,
                    currentPage = pagerState.currentPage,
                    onLoad = onLoad,
                    onSelect = { index -> scope.launch { pagerState.animateScrollToPage(index) } },
                )
            }
        }
    }
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
                model = "strip:${photo.id}",
                contentDescription = null,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .graphicsLayer { alpha = if (index == currentPage) 1f else 0.55f }
                    .clickable { onSelect(index) },
                contentScale = ContentScale.Crop,
                load = { onLoad(photo.blobId, FILMSTRIP_PX) },
            )
        }
    }
}

/** A photo's embedded metadata, loaded lazily for the current page. Absent fields are simply omitted. */
@Composable
private fun PhotoInfoPanel(
    photo: GalleryPhoto,
    onLoadMeta: suspend (blobId: String) -> PhotoMeta,
    modifier: Modifier = Modifier,
) {
    val meta by produceState(PhotoMeta(), photo.id) { value = onLoadMeta(photo.blobId) }
    Column(
        modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.45f))
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        meta.capturedAt?.let { InfoRow(stringResource(R.string.gallery_meta_taken), Format.dateTime(it)) }
        if (meta.width != null && meta.height != null) {
            InfoRow(stringResource(R.string.gallery_meta_dimensions), "${meta.width} × ${meta.height}")
        }
        meta.camera?.let { InfoRow(stringResource(R.string.gallery_meta_camera), it) }
        // "50 mm · f/1.8 · 1/125 · ISO 200" — condensed shot-settings row when we have any of them.
        val shot = buildList {
            meta.focalLengthMm?.let { add("${it.toInt()} mm") }
            meta.aperture?.let { add("f/%.1f".format(it)) }
            meta.shutterSeconds?.let { add(PhotoMetadata.formatShutter(it)) }
            meta.iso?.let { add("ISO $it") }
        }.joinToString(" · ")
        if (shot.isNotBlank()) InfoRow(stringResource(R.string.gallery_meta_shot), shot)
        meta.orientation?.let { InfoRow(stringResource(R.string.gallery_meta_orientation), PhotoMetadata.orientationLabel(it)) }
        meta.latLon?.let { (lat, lon) ->
            InfoRow(stringResource(R.string.gallery_meta_location), "%.5f, %.5f".format(lat, lon))
        }
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

@Composable
private fun ZoomablePhoto(
    photo: GalleryPhoto,
    onLoad: suspend (blobId: String, reqPx: Int) -> ImageBitmap?,
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
            load = { onLoad(photo.blobId, VIEWER_PX) },
        )
    }
}
