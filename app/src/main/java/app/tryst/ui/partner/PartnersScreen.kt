package app.tryst.ui.partner

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tryst.R
import app.tryst.data.db.entity.Gender
import app.tryst.data.db.entity.PartnerEntity
import app.tryst.data.db.entity.ProfileEntity
import app.tryst.data.db.entity.RelationshipType
import app.tryst.data.db.entity.Sex
import app.tryst.ui.common.DecodedImage
import app.tryst.ui.common.DemographicFields
import app.tryst.ui.common.Format
import app.tryst.ui.common.MediaImages
import app.tryst.ui.common.OptionalChips
import app.tryst.ui.common.adaptiveContentWidth
import app.tryst.ui.common.rememberCameraCapture
import app.tryst.ui.common.rememberHaptics
import app.tryst.ui.common.rememberImagePicker
import app.tryst.ui.profile.ProfileViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PartnersScreen(
    onOpenProfile: () -> Unit = {},
    viewModel: PartnersViewModel = hiltViewModel(),
    profileViewModel: ProfileViewModel = hiltViewModel(),
) {
    val partners by viewModel.partners.collectAsStateWithLifecycle()
    val profile by profileViewModel.profile.collectAsStateWithLifecycle()
    var dialogTarget by remember { mutableStateOf<DialogTarget?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.partners_title)) }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { dialogTarget = DialogTarget(null) }) {
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
                        onEdit = { dialogTarget = DialogTarget(partner) },
                        onArchive = { viewModel.archive(partner.id) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }

    dialogTarget?.let { target ->
        PartnerDialog(
            initial = target.partner,
            onLoadPhoto = { viewModel.decodePhoto(it, AVATAR_PX) },
            onSuppressAutoLock = { viewModel.suppressAutoLock() },
            onDismiss = { dialogTarget = null },
            onSave = { draft ->
                viewModel.save(target.partner?.id, draft)
                dialogTarget = null
            },
        )
    }
}

private data class DialogTarget(val partner: PartnerEntity?)

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
                ).joinToString(" · ")
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
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    Card(onClick = onEdit, modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PartnerAvatar(partner.photoMediaId, Format.partnerName(partner), 48.dp, onLoadPhoto)
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text(Format.partnerName(partner), style = MaterialTheme.typography.titleMedium)
                val descriptor = listOfNotNull(
                    partner.relationshipType?.label,
                    partner.gender?.label ?: partner.sex?.label,
                    partner.birthDate?.let { Format.age(it) }?.let { stringResource(R.string.demo_age, it) },
                ).joinToString(" · ")
                if (descriptor.isNotEmpty()) {
                    Text(descriptor, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
                partner.note?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            TextButton(onClick = {
                haptics.tick()
                onArchive()
            }) { Text(stringResource(R.string.partners_archive)) }
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

@Composable
private fun PartnerDialog(
    initial: PartnerEntity?,
    onLoadPhoto: suspend (String) -> ImageBitmap?,
    onSuppressAutoLock: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (PartnerDraft) -> Unit,
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(initial?.displayName ?: "") }
    var anonymous by remember { mutableStateOf(initial?.isAnonymous ?: false) }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    var sex by remember { mutableStateOf(initial?.sex) }
    var gender by remember { mutableStateOf(initial?.gender) }
    var relationship by remember { mutableStateOf(initial?.relationshipType) }
    var birthDate by remember { mutableStateOf(initial?.birthDate) }
    var ethnicity by remember { mutableStateOf(initial?.ethnicity) }
    var height by remember { mutableStateOf(initial?.height ?: "") }
    var bodyType by remember { mutableStateOf(initial?.bodyType) }
    var location by remember { mutableStateOf(initial?.location ?: "") }
    var photoUri by remember { mutableStateOf<Uri?>(null) }
    var photoRemoved by remember { mutableStateOf(false) }
    var captureTempFile by remember { mutableStateOf<File?>(null) }
    var photoMenu by remember { mutableStateOf(false) }
    val existingPhotoId = initial?.photoMediaId?.takeIf { !photoRemoved }
    val hasPhoto = photoUri != null || existingPhotoId != null
    val pickImage = rememberImagePicker(onLaunch = onSuppressAutoLock) {
        captureTempFile?.delete()
        captureTempFile = null
        photoUri = it
        photoRemoved = false
    }
    val captureImage = rememberCameraCapture(onLaunch = onSuppressAutoLock) { uri, file ->
        captureTempFile?.delete()
        photoUri = uri
        photoRemoved = false
        captureTempFile = file
    }
    var showDiscardConfirm by remember { mutableStateOf(false) }
    // On cancel, drop any unsaved camera temp (on Save the ViewModel deletes it after encrypting).
    val dismiss = {
        captureTempFile?.delete()
        onDismiss()
    }
    // Has the user touched anything worth protecting? Outside-taps are disabled outright (below); a
    // back-press or Cancel on a touched form routes through the "Discard changes?" prompt so a stray
    // tap while reaching past the keyboard for Save can't wipe a just-taken photo and typed details.
    val isDirty = name != (initial?.displayName ?: "") ||
        anonymous != (initial?.isAnonymous ?: false) ||
        note != (initial?.note ?: "") ||
        sex != initial?.sex ||
        gender != initial?.gender ||
        relationship != initial?.relationshipType ||
        birthDate != initial?.birthDate ||
        ethnicity != initial?.ethnicity ||
        height != (initial?.height ?: "") ||
        bodyType != initial?.bodyType ||
        location != (initial?.location ?: "") ||
        photoUri != null ||
        photoRemoved
    val attemptDismiss = { if (isDirty) showDiscardConfirm = true else dismiss() }

    AlertDialog(
        // Don't dismiss on an outside-scrim tap — far too easy to hit while the keyboard covers Save.
        properties = DialogProperties(dismissOnClickOutside = false),
        onDismissRequest = attemptDismiss,
        title = { Text(stringResource(if (initial == null) R.string.partner_add else R.string.partner_dialog_edit_title)) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState())
                    // The avatar / "Remove" controls grow and shrink as a photo is added or cleared.
                    .animateContentSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (photoUri != null) {
                        val uri = photoUri!!
                        DecodedImage(
                            model = uri,
                            contentDescription = stringResource(R.string.cd_partner_photo),
                            modifier = Modifier.size(72.dp).clip(CircleShape),
                            contentScale = ContentScale.Crop,
                            load = { MediaImages.decodeSampled(AVATAR_PX) { context.contentResolver.openInputStream(uri) } },
                        )
                    } else {
                        PartnerAvatar(existingPhotoId, name, 72.dp, onLoadPhoto)
                    }
                    Column {
                        Box {
                            TextButton(onClick = { photoMenu = true }) {
                                Text(stringResource(if (hasPhoto) R.string.partner_change_photo else R.string.partner_add_photo))
                            }
                            DropdownMenu(expanded = photoMenu, onDismissRequest = { photoMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.encounter_add_photo_camera)) },
                                    onClick = {
                                        photoMenu = false
                                        captureImage()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.encounter_add_photo_gallery)) },
                                    onClick = {
                                        photoMenu = false
                                        pickImage()
                                    },
                                )
                            }
                        }
                        AnimatedVisibility(visible = hasPhoto) {
                            TextButton(onClick = {
                                captureTempFile?.delete()
                                captureTempFile = null
                                photoUri = null
                                photoRemoved = true
                            }) { Text(stringResource(R.string.partner_remove_photo)) }
                        }
                    }
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.partner_name_label)) },
                    singleLine = true,
                    enabled = !anonymous,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value = anonymous,
                            role = Role.Switch,
                            onValueChange = { anonymous = it },
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Switch(checked = anonymous, onCheckedChange = null)
                    Text(stringResource(R.string.partner_anonymous), style = MaterialTheme.typography.bodyMedium)
                }
                OptionalChips(stringResource(R.string.partner_sex), Sex.entries, sex) { sex = it }
                OptionalChips(stringResource(R.string.partner_gender), Gender.entries, gender) { gender = it }
                OptionalChips(stringResource(R.string.partner_relationship), RelationshipType.entries, relationship) { relationship = it }
                DemographicFields(
                    birthDate = birthDate,
                    onBirthDate = { birthDate = it },
                    ethnicity = ethnicity,
                    onEthnicity = { ethnicity = it },
                    height = height,
                    onHeight = { height = it },
                    bodyType = bodyType,
                    onBodyType = { bodyType = it },
                    location = location,
                    onLocation = { location = it },
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(stringResource(R.string.partner_note_label)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        PartnerDraft(
                            name = name,
                            anonymous = anonymous,
                            note = note,
                            sex = sex,
                            gender = gender,
                            relationshipType = relationship,
                            birthDate = birthDate,
                            ethnicity = ethnicity,
                            height = height,
                            bodyType = bodyType,
                            location = location,
                            newPhotoUri = photoUri,
                            removePhoto = photoRemoved,
                            captureTempFile = captureTempFile,
                        ),
                    )
                },
                enabled = anonymous || name.isNotBlank(),
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = attemptDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )

    if (showDiscardConfirm) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirm = false },
            title = { Text(stringResource(R.string.discard_changes_title)) },
            text = { Text(stringResource(R.string.discard_changes_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showDiscardConfirm = false
                    dismiss()
                }) { Text(stringResource(R.string.action_discard)) }
            },
            dismissButton = { TextButton(onClick = { showDiscardConfirm = false }) { Text(stringResource(R.string.action_keep_editing)) } },
        )
    }
}
