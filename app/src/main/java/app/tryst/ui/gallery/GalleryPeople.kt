// SPDX-License-Identifier: GPL-3.0-or-later
package app.tryst.ui.gallery

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

/**
 * The People layout (GAL-1a): partner and profile avatars as browsable items in their own right. Tapping
 * an avatar **drills into that person's photos** — the gallery becomes a filtered By-date view of their
 * encounters, and the standard system back gesture (or the drill top bar's back arrow) returns to People.
 * The self-profile row is currently excluded from drill (no encounters belong to "you"), so it just
 * displays as a face; only partners with photos are tappable in a useful way.
 */
@Composable
fun GalleryPeople(
    profile: ProfileEntity?,
    partners: List<PartnerEntity>,
    columns: Int,
    onLoadAvatar: suspend (photoMediaId: String, reqPx: Int) -> ImageBitmap?,
    onPersonClick: (partnerId: String) -> Unit,
) {
    val youLabel = stringResource(R.string.gallery_people_you)
    val people = remember(profile, partners, youLabel) { buildPeople(profile, partners, youLabel) }

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

    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(people, key = { it.id }) { person ->
            Column(
                // Both partner and self entries drill in — the caller resolves "self" to a profile-photo view.
                Modifier.clickable { onPersonClick(person.id) },
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
