// SPDX-License-Identifier: GPL-3.0-or-later
package app.tryst.ui.profile

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tryst.R
import app.tryst.data.db.entity.Gender
import app.tryst.data.db.entity.ProfileEntity
import app.tryst.data.db.entity.Sex
import app.tryst.ui.common.DemographicFields
import app.tryst.ui.common.OptionalChips
import app.tryst.ui.common.PersonPhotoStrip
import app.tryst.ui.common.adaptiveContentWidth
import app.tryst.ui.common.rememberHaptics

private const val AVATAR_PX = 320

/**
 * The user's own profile editor — photo album + name + sex/gender + demographics + note. Reached from
 * Settings → Your profile and the "You" card on Partners. The photo album lives in the shared
 * [PersonPhotoStrip] (v15+): add/delete/promote-to-avatar all auto-save, so a discard-changes prompt
 * on the field edits never rolls back the photos. Loaded profile is re-seeded via [key] on arrival.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(onBack: () -> Unit, viewModel: ProfileViewModel = hiltViewModel()) {
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val photos by viewModel.photos.collectAsStateWithLifecycle()
    key(profile) {
        ProfileEditor(
            initial = profile,
            photos = photos,
            onSuppressAutoLock = { viewModel.suppressAutoLock() },
            onAddPhotos = viewModel::addPhotos,
            onDeletePhoto = viewModel::deletePhoto,
            onSetAsAvatar = viewModel::setAsAvatar,
            decodePhoto = { id -> viewModel.decodePhoto(id, AVATAR_PX) },
            onBack = onBack,
            onSave = { draft -> viewModel.save(draft, onBack) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("LongParameterList", "LongMethod")
private fun ProfileEditor(
    initial: ProfileEntity?,
    photos: List<app.tryst.data.db.entity.PersonPhotoEntity>,
    onSuppressAutoLock: () -> Unit,
    onAddPhotos: (List<android.net.Uri>) -> Unit,
    onDeletePhoto: (app.tryst.data.db.entity.PersonPhotoEntity) -> Unit,
    onSetAsAvatar: (app.tryst.data.db.entity.PersonPhotoEntity) -> Unit,
    decodePhoto: suspend (String) -> androidx.compose.ui.graphics.ImageBitmap?,
    onBack: () -> Unit,
    onSave: (ProfileDraft) -> Unit,
) {
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
    var showDiscardConfirm by remember { mutableStateOf(false) }

    // Photos auto-save via the strip — they never contribute to isDirty.
    val isDirty = displayName != (initial?.displayName ?: "") ||
        sex != initial?.sex ||
        gender != initial?.gender ||
        birthDate != initial?.birthDate ||
        ethnicity != initial?.ethnicity ||
        height != (initial?.height ?: "") ||
        bodyType != initial?.bodyType ||
        location != (initial?.location ?: "") ||
        note != (initial?.note ?: "")
    val attemptClose = { if (isDirty) showDiscardConfirm = true else onBack() }
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
                                // The strip owns photo lifecycle now; the draft's photo fields are unused.
                                newPhotoUri = null,
                                removePhoto = false,
                                captureTempFile = null,
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
            Text(stringResource(R.string.partner_photos_title), style = MaterialTheme.typography.titleMedium)
            PersonPhotoStrip(
                photos = photos,
                currentAvatarBlobId = initial?.photoMediaId,
                onSuppressAutoLock = onSuppressAutoLock,
                onAdd = onAddPhotos,
                onSetAsAvatar = onSetAsAvatar,
                onDelete = onDeletePhoto,
                decode = decodePhoto,
            )

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
                    onBack()
                }) { Text(stringResource(R.string.action_discard)) }
            },
            dismissButton = { TextButton(onClick = { showDiscardConfirm = false }) { Text(stringResource(R.string.action_keep_editing)) } },
        )
    }
}
