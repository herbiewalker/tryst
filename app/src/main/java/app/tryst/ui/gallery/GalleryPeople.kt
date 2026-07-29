package app.tryst.ui.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.tryst.R
import app.tryst.data.db.entity.PartnerEntity
import app.tryst.data.db.entity.ProfileEntity
import app.tryst.ui.common.DecodedImage
import app.tryst.ui.common.Format

private const val AVATAR_TILE_PX = 320
private const val AVATAR_FULL_PX = 1200

/**
 * The People layout (GAL-1a): partner and profile avatars as browsable items in their own right, separate
 * from the encounter-photo pipeline (they carry no date and aren't filterable). A grid of circular avatars;
 * tapping one opens a full-screen, swipeable view across everyone.
 */
@Composable
fun GalleryPeople(
    profile: ProfileEntity?,
    partners: List<PartnerEntity>,
    columns: Int,
    onLoadAvatar: suspend (photoMediaId: String, reqPx: Int) -> ImageBitmap?,
) {
    val youLabel = stringResource(R.string.gallery_people_you)
    val people = remember(profile, partners, youLabel) { buildPeople(profile, partners, youLabel) }
    var viewerIndex by remember { mutableIntStateOf(-1) }

    if (people.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.gallery_people_empty),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    // Wrap in a Box so the AvatarViewer overlays the grid — otherwise the caller's Column would stack the
    // viewer below the fillMaxSize grid at zero height and the tap would appear to do nothing.
    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(people, key = { it.id }) { person ->
                Column(
                    Modifier.clickable { viewerIndex = people.indexOf(person) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    DecodedImage(
                        model = "avatar:${person.photoMediaId}",
                        contentDescription = person.name,
                        modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(CircleShape),
                        contentScale = ContentScale.Crop,
                        load = { onLoadAvatar(person.photoMediaId, AVATAR_TILE_PX) },
                    )
                    Text(
                        text = person.name,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }

        if (viewerIndex in people.indices) {
            AvatarViewer(
                people = people,
                initialIndex = viewerIndex,
                onClose = { viewerIndex = -1 },
                onLoadAvatar = onLoadAvatar,
            )
        }
    }
}

@Composable
private fun AvatarViewer(
    people: List<GalleryPerson>,
    initialIndex: Int,
    onClose: () -> Unit,
    onLoadAvatar: suspend (photoMediaId: String, reqPx: Int) -> ImageBitmap?,
) {
    val pagerState = rememberPagerState(initialPage = initialIndex.coerceIn(0, people.lastIndex), pageCount = { people.size })
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            val person = people[page]
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                DecodedImage(
                    model = "avatarFull:${person.photoMediaId}",
                    contentDescription = person.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                    load = { onLoadAvatar(person.photoMediaId, AVATAR_FULL_PX) },
                )
            }
        }
        Box(Modifier.fillMaxWidth().align(Alignment.TopStart).background(Color.Black.copy(alpha = 0.35f)).statusBarsPadding().padding(4.dp)) {
            IconButton(onClick = onClose, modifier = Modifier.align(Alignment.CenterStart)) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_close), tint = Color.White)
            }
            Text(
                text = people[pagerState.currentPage].name,
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

private fun buildPeople(profile: ProfileEntity?, partners: List<PartnerEntity>, youLabel: String): List<GalleryPerson> {
    val out = mutableListOf<GalleryPerson>()
    profile?.photoMediaId?.let { photo ->
        out += GalleryPerson(id = "self", photoMediaId = photo, name = profile.displayName?.takeIf { it.isNotBlank() } ?: youLabel, isSelf = true)
    }
    partners.filter { it.photoMediaId != null }.forEach { p ->
        out += GalleryPerson(id = p.id, photoMediaId = p.photoMediaId!!, name = Format.partnerName(p), isSelf = false)
    }
    return out
}
