package app.tryst.ui.gallery

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.tryst.R
import app.tryst.data.gallery.GalleryPhoto
import app.tryst.ui.common.DecodedImage
import app.tryst.ui.common.Format

/**
 * A photo tile that participates in multi-select: it dims + shows a check when selected, and routes taps
 * through [interaction]. A small heart marks favourites (GAL-3). Used at any [shape]/aspect by the caller.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SelectablePhotoTile(
    photo: GalleryPhoto,
    reqPx: Int,
    onLoad: suspend (blobId: String, reqPx: Int) -> ImageBitmap?,
    interaction: TileInteraction,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(4.dp),
) {
    val selected = photo.id in interaction.selectedIds
    Box(
        modifier
            .clip(shape)
            .combinedClickable(
                onClick = { interaction.onClick(photo.id) },
                onLongClick = { interaction.onLongPress(photo.id) },
            ),
    ) {
        DecodedImage(
            model = photo.id,
            contentDescription = stringResource(R.string.cd_photo),
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            load = { onLoad(photo.blobId, reqPx) },
        )
        if (photo.favorite) {
            Icon(
                Icons.Filled.Favorite,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.align(Alignment.BottomStart).padding(4.dp).size(14.dp),
            )
        }
        if (interaction.selectionActive) {
            if (selected) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)))
            }
            Icon(
                if (selected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary else Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(3.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.25f))
                    .size(20.dp),
            )
        }
    }
}

/**
 * A one-line "date · partner names" caption for a photo tile — labelSmall, dimmed, single line.
 *
 * Set [showPartner] to `false` when the surrounding context already names the partner (e.g. a
 * by-partner section header, or a drilled-in "Photos of Alex" top bar) so the caption doesn't
 * echo the name on every single tile.
 */
@Composable
fun TileCaption(photo: GalleryPhoto, modifier: Modifier = Modifier, showPartner: Boolean = true) {
    val parts = buildList {
        add(Format.date(photo.takenAt))
        if (showPartner) photo.partnerNames.takeIf { it.isNotEmpty() }?.let { add(it.joinToString(", ")) }
    }
    Text(
        text = parts.joinToString("  ·  "),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.fillMaxWidth().padding(top = 2.dp, start = 2.dp, end = 2.dp),
    )
}
