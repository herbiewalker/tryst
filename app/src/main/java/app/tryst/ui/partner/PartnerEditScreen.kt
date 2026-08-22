// SPDX-License-Identifier: GPL-3.0-or-later
package app.tryst.ui.partner

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tryst.R
import app.tryst.data.db.entity.Gender
import app.tryst.data.db.entity.RelationshipType
import app.tryst.data.db.entity.Sex
import app.tryst.ui.common.DemographicFields
import app.tryst.ui.common.OptionalChips
import app.tryst.ui.common.PersonPhotoStrip

private const val AVATAR_PX = 320

/**
 * Full-screen add/edit for a partner (QOL-3 Ã¢â‚¬â€ was an AlertDialog). Includes a **photo strip** backed by
 * the v15+ `person_photo` table: attach several photos to a partner, tap one to promote it as the
 * current avatar, or delete individual portraits. Photo actions auto-save (they're separate from the
 * field edits), so a discard-changes prompt only guards the fields Ã¢â‚¬â€ you never lose photos.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("LongMethod") // The editor is one straight-line form; splitting adds indirection without value.
fun PartnerEditScreen(
    onClose: () -> Unit,
    viewModel: PartnerEditViewModel = hiltViewModel(),
) {
    val ui = viewModel.uiState
    val photos by viewModel.photos.collectAsStateWithLifecycle()
    var showDiscardConfirm by remember { mutableStateOf(false) }
    val attemptClose = { if (viewModel.hasUnsavedChanges()) showDiscardConfirm = true else onClose() }
    BackHandler(enabled = viewModel.hasUnsavedChanges()) { showDiscardConfirm = true }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (ui.existing == null) R.string.partner_add else R.string.partner_dialog_edit_title)) },
                navigationIcon = {
                    IconButton(onClick = attemptClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                actions = {
                    TextButton(
                        onClick = { viewModel.save { onClose() } },
                        enabled = ui.hasAnyName,
                    ) { Text(stringResource(R.string.action_save)) }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // --- Photo strip ------------------------------------------------------------------
            Text(stringResource(R.string.partner_photos_title), style = MaterialTheme.typography.titleMedium)
            if (viewModel.partnerId == null) {
                Text(
                    text = stringResource(R.string.partner_photos_save_first),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                PersonPhotoStrip(
                    photos = photos,
                    currentAvatarBlobId = ui.existing?.photoMediaId,
                    onSuppressAutoLock = viewModel::suppressAutoLock,
                    onAdd = viewModel::addPhotos,
                    onSetAsAvatar = viewModel::setAsAvatar,
                    onDelete = viewModel::deletePhoto,
                    decode = { blobId -> viewModel.decodePartnerPhoto(blobId, AVATAR_PX) },
                )
            }

            // --- Fields ----------------------------------------------------------------------
            OutlinedTextField(
                value = ui.name,
                onValueChange = viewModel::setName,
                label = { Text(stringResource(R.string.partner_name_label)) },
                singleLine = true,
                enabled = !ui.anonymous,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth().toggleable(
                    value = ui.anonymous,
                    role = Role.Switch,
                    onValueChange = viewModel::setAnonymous,
                ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Switch(checked = ui.anonymous, onCheckedChange = null)
                Text(stringResource(R.string.partner_anonymous), style = MaterialTheme.typography.bodyMedium)
            }
            OptionalChips(stringResource(R.string.partner_sex), Sex.entries, ui.sex, viewModel::setSex)
            OptionalChips(stringResource(R.string.partner_gender), Gender.entries, ui.gender, viewModel::setGender)
            OptionalChips(stringResource(R.string.partner_relationship), RelationshipType.entries, ui.relationshipType, viewModel::setRelationship)
            DemographicFields(
                birthDate = ui.birthDate,
                onBirthDate = viewModel::setBirthDate,
                ethnicity = ui.ethnicity,
                onEthnicity = viewModel::setEthnicity,
                height = ui.height,
                onHeight = viewModel::setHeight,
                bodyType = ui.bodyType,
                onBodyType = viewModel::setBodyType,
                location = ui.location,
                onLocation = viewModel::setLocation,
            )
            OutlinedTextField(
                value = ui.note,
                onValueChange = viewModel::setNote,
                label = { Text(stringResource(R.string.partner_note_label)) },
                modifier = Modifier.fillMaxWidth(),
            )
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
                    onClose()
                }) { Text(stringResource(R.string.action_discard)) }
            },
            dismissButton = { TextButton(onClick = { showDiscardConfirm = false }) { Text(stringResource(R.string.action_keep_editing)) } },
        )
    }
}
