// SPDX-License-Identifier: GPL-3.0-or-later
package app.tryst.ui.common

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.unit.dp
import app.tryst.R
import app.tryst.data.db.entity.PersonPhotoEntity

/**
 * The person-photo album strip shown in the partner + profile editors (v15+). A "+" tile opens a menu
 * to capture / multi-pick / single-pick; existing portraits are shown in a horizontal LazyRow with a
 * checkmark on the current avatar; tapping a portrait opens an action sheet with Set-as-avatar / Delete.
 *
 * Photo actions are auto-saved (they hit the repo directly) — so a discard-changes on the surrounding
 * form only rolls back the fields, never the photos.
 */
@Composable
@Suppress("LongParameterList") // The strip needs the surrounding editor's callbacks + the current avatar id.
fun PersonPhotoStrip(
    photos: List<PersonPhotoEntity>,
    currentAvatarBlobId: String?,
    onSuppressAutoLock: () -> Unit,
    onAdd: (List<Uri>) -> Unit,
    onSetAsAvatar: (PersonPhotoEntity) -> Unit,
    onDelete: (PersonPhotoEntity) -> Unit,
    decode: suspend (blobId: String) -> ImageBitmap?,
    modifier: Modifier = Modifier,
) {
    val pickMulti = rememberMultiImagePicker(onLaunch = onSuppressAutoLock) { onAdd(it) }
    val pickSingle = rememberImagePicker(onLaunch = onSuppressAutoLock) { onAdd(listOf(it)) }
    val capture = rememberCameraCapture(onLaunch = onSuppressAutoLock) { uri, _ -> onAdd(listOf(uri)) }
    var addMenu by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<PersonPhotoEntity?>(null) }

    LazyRow(
        modifier.fillMaxWidth(),
        contentPadding = PaddingValues(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "add") {
            Box {
                Box(
                    Modifier
                        .size(96.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .clickable { addMenu = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = stringResource(R.string.partner_add_photo),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp),
                    )
                }
                DropdownMenu(expanded = addMenu, onDismissRequest = { addMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.encounter_add_photo_camera)) },
                        onClick = {
                            addMenu = false
                            capture()
                        },
                        leadingIcon = { Icon(Icons.Filled.CameraAlt, contentDescription = null) },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.encounter_add_photo_gallery)) },
                        onClick = {
                            addMenu = false
                            pickMulti()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.partner_add_photo_single)) },
                        onClick = {
                            addMenu = false
                            pickSingle()
                        },
                    )
                }
            }
        }
        items(photos, key = { it.id }) { photo ->
            val isCurrent = currentAvatarBlobId == photo.mediaBlobId
            Box {
                DecodedImage(
                    model = "portrait:${photo.id}",
                    contentDescription = stringResource(R.string.cd_partner_photo),
                    modifier = Modifier
                        .size(96.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { selected = photo },
                    contentScale = ContentScale.Crop,
                    load = { decode(photo.mediaBlobId) },
                )
                if (isCurrent) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
    }

    selected?.let { photo ->
        val isCurrent = currentAvatarBlobId == photo.mediaBlobId
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text(stringResource(R.string.partner_photo_actions_title)) },
            text = {
                Column {
                    if (!isCurrent) {
                        TextButton(onClick = {
                            onSetAsAvatar(photo)
                            selected = null
                        }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.partner_photo_set_avatar), textAlign = TextAlign.Start)
                        }
                    }
                    TextButton(onClick = {
                        onDelete(photo)
                        selected = null
                    }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { selected = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}
