package app.tryst.ui.partner

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tryst.data.db.entity.DisplayLabel
import app.tryst.data.db.entity.Gender
import app.tryst.data.db.entity.PartnerEntity
import app.tryst.data.db.entity.RelationshipType
import app.tryst.data.db.entity.Sex
import app.tryst.ui.common.DecodedImage
import app.tryst.ui.common.Format
import app.tryst.ui.common.MediaImages
import app.tryst.ui.common.rememberCameraCapture
import app.tryst.ui.common.rememberImagePicker
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PartnersScreen(viewModel: PartnersViewModel = hiltViewModel()) {
    val partners by viewModel.partners.collectAsStateWithLifecycle()
    var dialogTarget by remember { mutableStateOf<DialogTarget?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Partners") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { dialogTarget = DialogTarget(null) }) {
                Text("+", style = MaterialTheme.typography.headlineMedium)
            }
        },
    ) { padding ->
        if (partners.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(
                    "No partners yet.\nTap + to add one.",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(partners, key = { it.id }) { partner ->
                    PartnerRow(
                        partner = partner,
                        onLoadPhoto = { viewModel.decodePhoto(it, AVATAR_PX) },
                        onEdit = { dialogTarget = DialogTarget(partner) },
                        onArchive = { viewModel.archive(partner.id) },
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
            onSave = { name, anonymous, note, sex, gender, rel, photoUri, removePhoto, tempFile ->
                viewModel.save(target.partner?.id, name, anonymous, note, sex, gender, rel, photoUri, removePhoto, tempFile)
                dialogTarget = null
            },
        )
    }
}

private data class DialogTarget(val partner: PartnerEntity?)

private const val AVATAR_PX = 200

@Composable
private fun PartnerRow(
    partner: PartnerEntity,
    onLoadPhoto: suspend (String) -> ImageBitmap?,
    onEdit: () -> Unit,
    onArchive: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onEdit)) {
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
                ).joinToString(" · ")
                if (descriptor.isNotEmpty()) {
                    Text(descriptor, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
                partner.note?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            TextButton(onClick = onArchive) { Text("Archive") }
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
            contentDescription = "Partner photo",
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
    onSave: (
        name: String, anonymous: Boolean, note: String,
        sex: Sex?, gender: Gender?, rel: RelationshipType?,
        photoUri: Uri?, removePhoto: Boolean, captureTempFile: File?,
    ) -> Unit,
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(initial?.displayName ?: "") }
    var anonymous by remember { mutableStateOf(initial?.isAnonymous ?: false) }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    var sex by remember { mutableStateOf(initial?.sex) }
    var gender by remember { mutableStateOf(initial?.gender) }
    var relationship by remember { mutableStateOf(initial?.relationshipType) }
    var photoUri by remember { mutableStateOf<Uri?>(null) }
    var photoRemoved by remember { mutableStateOf(false) }
    var captureTempFile by remember { mutableStateOf<File?>(null) }
    var photoMenu by remember { mutableStateOf(false) }
    val existingPhotoId = initial?.photoMediaId?.takeIf { !photoRemoved }
    val hasPhoto = photoUri != null || existingPhotoId != null
    val pickImage = rememberImagePicker(onLaunch = onSuppressAutoLock) {
        captureTempFile?.delete(); captureTempFile = null; photoUri = it; photoRemoved = false
    }
    val captureImage = rememberCameraCapture(onLaunch = onSuppressAutoLock) { uri, file ->
        captureTempFile?.delete(); photoUri = uri; photoRemoved = false; captureTempFile = file
    }
    // On cancel, drop any unsaved camera temp (on Save the ViewModel deletes it after encrypting).
    val dismiss = { captureTempFile?.delete(); onDismiss() }

    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(if (initial == null) "Add partner" else "Edit partner") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
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
                            contentDescription = "Partner photo",
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
                                Text(if (hasPhoto) "Change photo" else "Add photo")
                            }
                            DropdownMenu(expanded = photoMenu, onDismissRequest = { photoMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text("Take photo") },
                                    onClick = { photoMenu = false; captureImage() },
                                )
                                DropdownMenuItem(
                                    text = { Text("Choose from gallery") },
                                    onClick = { photoMenu = false; pickImage() },
                                )
                            }
                        }
                        if (hasPhoto) {
                            TextButton(onClick = {
                                captureTempFile?.delete(); captureTempFile = null; photoUri = null; photoRemoved = true
                            }) { Text("Remove") }
                        }
                    }
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    enabled = !anonymous,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = anonymous, onCheckedChange = { anonymous = it })
                    Text("  Anonymous", style = MaterialTheme.typography.bodyMedium)
                }
                OptionalChips("Sex", Sex.entries, sex) { sex = it }
                OptionalChips("Gender", Gender.entries, gender) { gender = it }
                OptionalChips("Relationship", RelationshipType.entries, relationship) { relationship = it }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name, anonymous, note, sex, gender, relationship, photoUri, photoRemoved, captureTempFile) },
                enabled = anonymous || name.isNotBlank(),
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } },
    )
}

/** A labelled optional single-select: tapping the selected chip again clears it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> OptionalChips(
    label: String,
    options: List<T>,
    selected: T?,
    onSelect: (T?) -> Unit,
) where T : Enum<T>, T : DisplayLabel {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { option ->
                FilterChip(
                    selected = option == selected,
                    onClick = { onSelect(if (option == selected) null else option) },
                    label = { Text(option.label) },
                )
            }
        }
    }
}
