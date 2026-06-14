package app.tryst.ui.profile

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tryst.R
import app.tryst.data.db.entity.Gender
import app.tryst.data.db.entity.ProfileEntity
import app.tryst.data.db.entity.Sex
import app.tryst.ui.common.DecodedImage
import app.tryst.ui.common.DemographicFields
import app.tryst.ui.common.MediaImages
import app.tryst.ui.common.OptionalChips
import app.tryst.ui.common.adaptiveContentWidth
import app.tryst.ui.common.rememberCameraCapture
import app.tryst.ui.common.rememberHaptics
import app.tryst.ui.common.rememberImagePicker
import java.io.File

private const val AVATAR_PX = 200

/**
 * The user's own profile editor (photo + name + sex/gender + demographics + note), reached from
 * Settings → Your profile and the "You" card on Partners. Mirrors the partner editor's photo staging
 * and the [app.tryst.ui.encounter.EncounterEditScreen] discard-changes guard. The single profile row
 * loads asynchronously, so the form is re-seeded via [key] when it first arrives.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(onBack: () -> Unit, viewModel: ProfileViewModel = hiltViewModel()) {
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    key(profile) {
        ProfileEditor(
            initial = profile,
            onSuppressAutoLock = { viewModel.suppressAutoLock() },
            onLoadPhoto = { id -> viewModel.decodePhoto(id, AVATAR_PX) },
            onBack = onBack,
            onSave = { draft -> viewModel.save(draft, onBack) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileEditor(
    initial: ProfileEntity?,
    onSuppressAutoLock: () -> Unit,
    onLoadPhoto: suspend (String) -> androidx.compose.ui.graphics.ImageBitmap?,
    onBack: () -> Unit,
    onSave: (ProfileDraft) -> Unit,
) {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    var displayName by remember { mutableStateOf(initial?.displayName ?: "") }
    var sex by remember { mutableStateOf(initial?.sex) }
    var gender by remember { mutableStateOf(initial?.gender) }
    var birthDate by remember { mutableStateOf(initial?.birthDate) }
    var ethnicity by remember { mutableStateOf(initial?.ethnicity) }
    var height by remember { mutableStateOf(initial?.height ?: "") }
    var bodyType by remember { mutableStateOf(initial?.bodyType) }
    var location by remember { mutableStateOf(initial?.location ?: "") }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    var photoUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var photoRemoved by remember { mutableStateOf(false) }
    var captureTempFile by remember { mutableStateOf<File?>(null) }
    var photoMenu by remember { mutableStateOf(false) }
    var showDiscardConfirm by remember { mutableStateOf(false) }

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

    val isDirty = displayName != (initial?.displayName ?: "") ||
        sex != initial?.sex ||
        gender != initial?.gender ||
        birthDate != initial?.birthDate ||
        ethnicity != initial?.ethnicity ||
        height != (initial?.height ?: "") ||
        bodyType != initial?.bodyType ||
        location != (initial?.location ?: "") ||
        note != (initial?.note ?: "") ||
        photoUri != null ||
        photoRemoved
    val attemptClose = {
        if (isDirty) {
            showDiscardConfirm = true
        } else {
            captureTempFile?.delete()
            onBack()
        }
    }
    BackHandler(enabled = isDirty) { showDiscardConfirm = true }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.profile_title)) },
                navigationIcon = {
                    IconButton(onClick = attemptClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                actions = {
                    TextButton(onClick = {
                        haptics.confirm()
                        onSave(
                            ProfileDraft(
                                displayName = displayName,
                                sex = sex,
                                gender = gender,
                                birthDate = birthDate,
                                ethnicity = ethnicity,
                                height = height,
                                bodyType = bodyType,
                                location = location,
                                note = note,
                                newPhotoUri = photoUri,
                                removePhoto = photoRemoved,
                                captureTempFile = captureTempFile,
                            ),
                        )
                    }) { Text(stringResource(R.string.action_save)) }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .wrapContentWidth()
                .adaptiveContentWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ProfileAvatar(
                    pendingUri = photoUri,
                    existingPhotoId = existingPhotoId,
                    fallbackLabel = displayName,
                    onLoadPhoto = onLoadPhoto,
                )
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
                value = displayName,
                onValueChange = { displayName = it },
                label = { Text(stringResource(R.string.partner_name_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OptionalChips(stringResource(R.string.partner_sex), Sex.entries, sex) { sex = it }
            OptionalChips(stringResource(R.string.partner_gender), Gender.entries, gender) { gender = it }
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
                label = { Text(stringResource(R.string.profile_note_label)) },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
        }
    }

    if (showDiscardConfirm) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirm = false },
            title = { Text(stringResource(R.string.discard_changes_title)) },
            text = { Text(stringResource(R.string.discard_changes_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showDiscardConfirm = false
                    captureTempFile?.delete()
                    onBack()
                }) { Text(stringResource(R.string.action_discard)) }
            },
            dismissButton = { TextButton(onClick = { showDiscardConfirm = false }) { Text(stringResource(R.string.action_keep_editing)) } },
        )
    }
}

@Composable
private fun ProfileAvatar(
    pendingUri: android.net.Uri?,
    existingPhotoId: String?,
    fallbackLabel: String,
    onLoadPhoto: suspend (String) -> androidx.compose.ui.graphics.ImageBitmap?,
) {
    val context = LocalContext.current
    val size = 72.dp
    when {
        pendingUri != null -> DecodedImage(
            model = pendingUri,
            contentDescription = stringResource(R.string.cd_photo),
            modifier = Modifier.size(size).clip(CircleShape),
            contentScale = ContentScale.Crop,
            load = { MediaImages.decodeSampled(AVATAR_PX) { context.contentResolver.openInputStream(pendingUri) } },
        )
        existingPhotoId != null -> DecodedImage(
            model = existingPhotoId,
            contentDescription = stringResource(R.string.cd_photo),
            modifier = Modifier.size(size).clip(CircleShape),
            contentScale = ContentScale.Crop,
            load = { onLoadPhoto(existingPhotoId) },
        )
        else -> Box(
            modifier = Modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                fallbackLabel.trim().firstOrNull()?.uppercase() ?: stringResource(R.string.profile_you_initial),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}
