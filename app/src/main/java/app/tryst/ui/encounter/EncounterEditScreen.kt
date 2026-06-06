package app.tryst.ui.encounter

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tryst.data.db.entity.EjaculationLocation
import app.tryst.data.db.entity.Initiator
import app.tryst.data.db.entity.Kink
import app.tryst.data.db.entity.Mood
import app.tryst.data.db.entity.Occasion
import app.tryst.data.db.entity.Protection
import app.tryst.data.db.entity.Setting
import app.tryst.data.db.entity.ToyType
import app.tryst.ui.common.ActOptions
import app.tryst.ui.common.DecodedImage
import app.tryst.ui.common.Format
import app.tryst.ui.common.MultiSelectChips
import app.tryst.ui.common.MultiSelectField
import app.tryst.ui.common.PositionOptions
import app.tryst.ui.common.SingleSelectChips
import app.tryst.ui.common.SingleSelectField
import app.tryst.ui.common.rememberCameraCapture
import app.tryst.ui.common.rememberImagePicker
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EncounterEditScreen(
    encounterId: String?,
    onClose: () -> Unit,
    viewModel: EncounterEditViewModel = hiltViewModel(),
) {
    LaunchedEffect(encounterId) { viewModel.load(encounterId) }

    val partners by viewModel.availablePartners.collectAsStateWithLifecycle()
    val customPositions by viewModel.customPositions.collectAsStateWithLifecycle()
    val customActs by viewModel.customActs.collectAsStateWithLifecycle()
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var viewer by remember { mutableStateOf<PhotoView?>(null) }
    val pickImage = rememberImagePicker { viewModel.addPhoto(it) }
    val captureImage = rememberCameraCapture { uri, file -> viewModel.addCapturedPhoto(uri, file) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (viewModel.isEditing) "Edit encounter" else "Log encounter") },
                navigationIcon = { TextButton(onClick = onClose) { Text("Cancel") } },
                actions = { TextButton(onClick = { viewModel.save(onClose) }) { Text("Save") } },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Spacer(Modifier.height(4.dp))

            Field("When") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { showDatePicker = true }) {
                        Text(Format.date(viewModel.startAt))
                    }
                    OutlinedButton(onClick = { showTimePicker = true }) {
                        Text(Format.time(viewModel.startAt))
                    }
                }
            }

            Field("Duration (minutes)") {
                OutlinedTextField(
                    value = viewModel.durationText,
                    onValueChange = { viewModel.durationText = it.filter(Char::isDigit) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Field("Partners") {
                if (partners.isEmpty()) {
                    Text(
                        "No partners yet — add them in the Partners tab.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    MultiSelectChips(
                        options = partners,
                        selected = partners.filter { it.id in viewModel.selectedPartnerIds }.toSet(),
                        label = { Format.partnerName(it) },
                        onToggle = { viewModel.togglePartner(it.id) },
                    )
                }
            }

            MultiSelectField(
                label = "Protection",
                all = Protection.entries,
                common = CommonOptions.PROTECTION,
                selected = viewModel.protection,
                labelOf = { it.label },
                onToggle = { viewModel.toggleProtection(it) },
            )

            SingleSelectField(
                label = "Mood",
                all = Mood.entries,
                common = CommonOptions.MOOD,
                selected = viewModel.mood,
                labelOf = { it.label },
                onSelect = { viewModel.mood = it },
            )

            Field("Orgasms — you") {
                Stepper(value = viewModel.orgasmCountSelf, onChange = { viewModel.setSelfOrgasms(it) })
            }

            // One ejaculation location per orgasm you had.
            repeat(viewModel.orgasmCountSelf) { i ->
                SingleSelectField(
                    label = if (viewModel.orgasmCountSelf > 1) "Ejaculation ${i + 1}" else "Ejaculation",
                    all = EjaculationLocation.entries,
                    common = CommonOptions.EJACULATION,
                    selected = viewModel.ejaculations[i],
                    labelOf = { it.label },
                    onSelect = { viewModel.setEjaculation(i, it) },
                )
            }

            // One orgasm counter per selected partner, ordered by name.
            partners
                .filter { it.id in viewModel.selectedPartnerIds }
                .sortedBy { Format.partnerName(it).lowercase() }
                .forEach { partner ->
                    Field("Orgasms — ${Format.partnerName(partner)}") {
                        Stepper(
                            value = viewModel.partnerOrgasms[partner.id] ?: 0,
                            onChange = { viewModel.setPartnerOrgasms(partner.id, it) },
                        )
                    }
                }

            val positionOptions = PositionOptions.builtIns + PositionOptions.custom(customPositions)
            MultiSelectField(
                label = "Positions",
                all = positionOptions,
                common = PositionOptions.common,
                selected = positionOptions.filter { it.id in viewModel.selectedPositionIds }.toSet(),
                labelOf = { it.label },
                onToggle = { viewModel.togglePosition(it.id) },
            )

            val actOptions = ActOptions.builtIns + ActOptions.custom(customActs)
            MultiSelectField(
                label = "Acts — gave",
                all = actOptions,
                common = ActOptions.common,
                selected = actOptions.filter { it.id in viewModel.practicesPerformed }.toSet(),
                labelOf = { it.label },
                onToggle = { viewModel.togglePerformed(it.id) },
            )

            MultiSelectField(
                label = "Acts — received",
                all = actOptions,
                common = ActOptions.common,
                selected = actOptions.filter { it.id in viewModel.practicesReceived }.toSet(),
                labelOf = { it.label },
                onToggle = { viewModel.toggleReceived(it.id) },
            )

            MultiSelectField(
                label = "Kink & BDSM",
                all = Kink.entries,
                common = CommonOptions.KINK,
                selected = viewModel.kinks,
                labelOf = { it.label },
                onToggle = { viewModel.toggleKink(it) },
            )

            MultiSelectField(
                label = "Setting & location",
                all = Setting.entries,
                common = CommonOptions.SETTING,
                selected = viewModel.contexts,
                labelOf = { it.label },
                onToggle = { viewModel.toggleContext(it) },
            )

            MultiSelectField(
                label = "Occasion",
                all = Occasion.entries,
                common = CommonOptions.OCCASION,
                selected = viewModel.occasions,
                labelOf = { it.label },
                onToggle = { viewModel.toggleOccasion(it) },
            )

            MultiSelectField(
                label = "Toys",
                all = ToyType.entries,
                common = CommonOptions.TOY,
                selected = viewModel.toys,
                labelOf = { it.label },
                onToggle = { viewModel.toggleToy(it) },
            )

            Field("Who initiated") {
                SingleSelectChips(
                    options = Initiator.entries,
                    selected = viewModel.initiator,
                    label = { it.label },
                    onSelect = { viewModel.initiator = it },
                )
            }

            Field("Rating") {
                SingleSelectChips(
                    options = (1..5).toList(),
                    selected = viewModel.rating,
                    label = { "$it" },
                    onSelect = { viewModel.rating = it },
                )
            }

            Field("Note") {
                OutlinedTextField(
                    value = viewModel.note,
                    onValueChange = { viewModel.note = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                )
            }

            Field("Photos") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    viewModel.existingPhotos.forEach { media ->
                        PhotoThumb(
                            key = media.id,
                            load = { viewModel.decodeExisting(media, THUMB_PX) },
                            onClick = { viewer = PhotoView(media.id) { viewModel.decodeExisting(media, FULL_PX) } },
                            onRemove = { viewModel.removeExisting(media) },
                        )
                    }
                    viewModel.pendingPhotos.forEach { photo ->
                        PhotoThumb(
                            key = photo.uri,
                            load = { viewModel.decodePending(photo.uri, THUMB_PX) },
                            onClick = { viewer = PhotoView(photo.uri) { viewModel.decodePending(photo.uri, FULL_PX) } },
                            onRemove = { viewModel.removePending(photo) },
                        )
                    }
                    AddPhotoTile(onCamera = { captureImage() }, onGallery = { pickImage() })
                }
            }

            if (viewModel.isEditing) {
                OutlinedButton(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Delete encounter") }
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    if (showDatePicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = viewModel.startAt)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { viewModel.startAt = combineDate(viewModel.startAt, it) }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } },
        ) { DatePicker(state = state) }
    }

    if (showTimePicker) {
        val zoned = Instant.ofEpochMilli(viewModel.startAt).atZone(ZoneId.systemDefault())
        val state = rememberTimePickerState(initialHour = zoned.hour, initialMinute = zoned.minute)
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.startAt = combineTime(viewModel.startAt, state.hour, state.minute)
                    showTimePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text("Cancel") } },
            text = { TimePicker(state = state) },
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete this encounter?") },
            text = { Text("This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    viewModel.delete(onClose)
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } },
        )
    }

    viewer?.let { v ->
        Dialog(
            onDismissRequest = { viewer = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { viewer = null },
                contentAlignment = Alignment.Center,
            ) {
                DecodedImage(
                    model = v.key,
                    contentDescription = "Photo",
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = ContentScale.Fit,
                    load = v.load,
                )
            }
        }
    }
}

@Composable
private fun Field(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        content()
    }
}

@Composable
private fun Stepper(value: Int, onChange: (Int) -> Unit, min: Int = 0, max: Int = 20) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        OutlinedButton(onClick = { if (value > min) onChange(value - 1) }, enabled = value > min) {
            Text("−")
        }
        Text("$value", style = MaterialTheme.typography.titleLarge)
        OutlinedButton(onClick = { if (value < max) onChange(value + 1) }, enabled = value < max) {
            Text("+")
        }
    }
}

/** Replace the date portion of [base] (millis) with the date picked (UTC-midnight millis). */
private fun combineDate(base: Long, pickedUtcMidnight: Long): Long {
    val zone = ZoneId.systemDefault()
    val time = Instant.ofEpochMilli(base).atZone(zone)
    val date = Instant.ofEpochMilli(pickedUtcMidnight).atZone(ZoneOffset.UTC).toLocalDate()
    return time.with(date).toInstant().toEpochMilli()
}

private fun combineTime(base: Long, hour: Int, minute: Int): Long {
    val zone = ZoneId.systemDefault()
    return Instant.ofEpochMilli(base).atZone(zone)
        .withHour(hour).withMinute(minute).withSecond(0).withNano(0)
        .toInstant().toEpochMilli()
}

private const val THUMB_PX = 300
private const val FULL_PX = 1600

/** A photo selected for full-screen viewing: its [key] for recomposition + its decode [load]. */
private class PhotoView(val key: Any, val load: suspend () -> ImageBitmap?)

@Composable
private fun PhotoThumb(
    key: Any,
    load: suspend () -> ImageBitmap?,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    Box(modifier = Modifier.size(84.dp)) {
        DecodedImage(
            model = key,
            contentDescription = "Photo",
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onClick),
            contentScale = ContentScale.Crop,
            load = load,
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(22.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f))
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) {
            Text("×", color = Color.White, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun AddPhotoTile(onCamera: () -> Unit, onGallery: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        Box(
            modifier = Modifier
                .size(84.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable { menuOpen = true },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "＋",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text("Take photo") },
                onClick = { menuOpen = false; onCamera() },
            )
            DropdownMenuItem(
                text = { Text("Choose from gallery") },
                onClick = { menuOpen = false; onGallery() },
            )
        }
    }
}

/** The handful of most-common options shown inline per category; the rest live in "More…". */
private object CommonOptions {
    val PROTECTION = listOf(
        Protection.NONE, Protection.CONDOM, Protection.PILL, Protection.IUD,
        Protection.PREP, Protection.WITHDRAWAL, Protection.INTERNAL_CONDOM,
        Protection.EMERGENCY_CONTRACEPTION,
    )
    val MOOD = listOf(
        Mood.AMAZING, Mood.HORNY, Mood.PASSIONATE, Mood.PLAYFUL,
        Mood.ROMANTIC, Mood.CONNECTED, Mood.RELAXED, Mood.GOOD,
    )
    val EJACULATION = listOf(
        EjaculationLocation.NONE, EjaculationLocation.IN_CONDOM, EjaculationLocation.VAGINAL,
        EjaculationLocation.ANAL, EjaculationLocation.ORAL, EjaculationLocation.SWALLOWED,
        EjaculationLocation.ON_FACE, EjaculationLocation.ON_CHEST,
    )
    val KINK = listOf(
        Kink.DOMINATION, Kink.SUBMISSION, Kink.BONDAGE, Kink.SPANKING,
        Kink.CHOKING, Kink.DIRTY_TALK, Kink.ROLEPLAY, Kink.EDGING,
    )
    val SETTING = listOf(
        Setting.HOME, Setting.BEDROOM, Setting.SHOWER, Setting.CAR,
        Setting.HOTEL, Setting.OUTDOORS, Setting.LIVING_ROOM, Setting.HOT_TUB,
    )
    val OCCASION = listOf(
        Occasion.REGULAR, Occasion.NONE, Occasion.QUICKIE, Occasion.MORNING_SEX,
        Occasion.MAKEUP_SEX, Occasion.SPONTANEOUS, Occasion.DATE_NIGHT, Occasion.DRUNK_HIGH,
    )
    val TOY = listOf(
        ToyType.NONE, ToyType.VIBRATOR, ToyType.DILDO, ToyType.BUTT_PLUG,
        ToyType.COCK_RING, ToyType.STRAP_ON, ToyType.WAND, ToyType.ANAL_BEADS,
    )
}
