package app.tryst.ui.partner

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tryst.R
import app.tryst.data.db.entity.PartnerEntity
import app.tryst.data.db.entity.ProfileEntity
import app.tryst.ui.common.DecodedImage
import app.tryst.ui.common.Format
import app.tryst.ui.common.adaptiveContentWidth
import app.tryst.ui.common.rememberHaptics
import app.tryst.ui.profile.ProfileViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PartnersScreen(
    onOpenProfile: () -> Unit = {},
    onOpenGallery: () -> Unit = {},
    onOpenPartnerEdit: (partnerId: String?) -> Unit = {},
    viewModel: PartnersViewModel = hiltViewModel(),
    profileViewModel: ProfileViewModel = hiltViewModel(),
) {
    val partners by viewModel.partners.collectAsStateWithLifecycle()
    val archived by viewModel.archivedPartners.collectAsStateWithLifecycle()
    val profile by profileViewModel.profile.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.partners_title)) }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { onOpenPartnerEdit(null) }) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.partner_add))
            }
        },
    ) { padding ->
        LazyColumn(
            // Cap + centre on wide windows so partner rows don't stretch (Pass 5); no-op on phones.
            modifier = Modifier.fillMaxSize().padding(padding).wrapContentWidth().adaptiveContentWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // The user's own profile, pinned above the partner list.
            item(key = "you") {
                YouCard(
                    profile = profile,
                    onLoadPhoto = { profileViewModel.decodePhoto(it, AVATAR_PX) },
                    onClick = onOpenProfile,
                )
            }
            if (partners.isEmpty()) {
                item(key = "empty") {
                    Text(
                        stringResource(R.string.partners_empty),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                    )
                }
            } else {
                items(partners, key = { it.id }) { partner ->
                    PartnerRow(
                        partner = partner,
                        onLoadPhoto = { viewModel.decodePhoto(it, AVATAR_PX) },
                        onEdit = { onOpenPartnerEdit(partner.id) },
                        onArchive = { viewModel.archive(partner.id) },
                        onViewPhotos = {
                            viewModel.viewPhotosFor(partner.id)
                            onOpenGallery()
                        },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
            if (archived.isNotEmpty()) {
                item(key = "archived-header") {
                    Text(
                        stringResource(R.string.partners_archived_section),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(start = 4.dp, top = 20.dp, bottom = 4.dp),
                    )
                }
                items(archived, key = { "arch-${it.id}" }) { partner ->
                    ArchivedPartnerRow(
                        partner = partner,
                        onLoadPhoto = { viewModel.decodePhoto(it, AVATAR_PX) },
                        onUnarchive = { viewModel.unarchive(partner.id) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }
}

private const val AVATAR_PX = 200

/** The user's own profile, shown as a pinned card atop the Partners list; opens the profile editor. */
@Composable
private fun YouCard(
    profile: ProfileEntity?,
    onLoadPhoto: suspend (String) -> ImageBitmap?,
    onClick: () -> Unit,
) {
    val name = profile?.displayName?.takeIf { it.isNotBlank() } ?: stringResource(R.string.profile_you)
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PartnerAvatar(profile?.photoMediaId, name, 48.dp, onLoadPhoto)
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text(name, style = MaterialTheme.typography.titleMedium)
                val descriptor = listOfNotNull(
                    profile?.gender?.label ?: profile?.sex?.label,
                    profile?.birthDate?.let { Format.age(it) }?.let { stringResource(R.string.demo_age, it) },
                ).joinToString(" Â· ")
                Text(
                    descriptor.ifEmpty { stringResource(R.string.profile_you_cta) },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (descriptor.isEmpty()) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
            }
        }
    }
}

@Composable
private fun PartnerRow(
    partner: PartnerEntity,
    onLoadPhoto: suspend (String) -> ImageBitmap?,
    onEdit: () -> Unit,
    onArchive: () -> Unit,
    onViewPhotos: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    Card(onClick = onEdit, modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PartnerAvatar(partner.photoMediaId, Format.partnerName(partner), 48.dp, onLoadPhoto)
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text(Format.partnerName(partner), style = MaterialTheme.typography.titleMedium)
                val descriptor = listOfNotNull(
                    partner.relationshipType?.label,
                    partner.gender?.label ?: partner.sex?.label,
                    partner.birthDate?.let { Format.age(it) }?.let { stringResource(R.string.demo_age, it) },
                ).joinToString(" Â· ")
                if (descriptor.isNotEmpty()) {
                    Text(descriptor, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
                partner.note?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            IconButton(onClick = {
                haptics.tick()
                onViewPhotos()
            }) {
                Icon(Icons.Filled.PhotoLibrary, contentDescription = stringResource(R.string.partner_view_photos))
            }
            TextButton(onClick = {
                haptics.tick()
                onArchive()
            }) { Text(stringResource(R.string.partners_archive)) }
        }
    }
}

/** Dimmed row for an archived partner â€” only surfaces name + avatar + an Unarchive action. */
@Composable
private fun ArchivedPartnerRow(
    partner: PartnerEntity,
    onLoadPhoto: suspend (String) -> ImageBitmap?,
    onUnarchive: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    Card(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PartnerAvatar(partner.photoMediaId, Format.partnerName(partner), 40.dp, onLoadPhoto)
            Text(
                text = Format.partnerName(partner),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f).padding(start = 14.dp),
            )
            TextButton(onClick = {
                haptics.tick()
                onUnarchive()
            }) { Text(stringResource(R.string.partners_unarchive)) }
        }
    }
}

@Composable
private fun PartnerAvatar(
    photoId: String?,
    fallbackLabel: String,
    size: Dp,
    onLoadPhoto: suspend (String) -> ImageBitmap?,
) {
    if (photoId != null) {
        DecodedImage(
            model = photoId,
            contentDescription = stringResource(R.string.cd_partner_photo),
            modifier = Modifier.size(size).clip(CircleShape),
            contentScale = ContentScale.Crop,
            load = { onLoadPhoto(photoId) },
        )
    } else {
        Box(
            modifier = Modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                fallbackLabel.trim().firstOrNull()?.uppercase() ?: "?",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}
