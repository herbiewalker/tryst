package app.tryst.ui.encounter

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.material3.minimumInteractiveComponentSize
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tryst.R
import app.tryst.data.db.entity.EjaculationLocation
import app.tryst.data.db.entity.Initiator
import app.tryst.data.db.entity.Mood
import app.tryst.data.db.entity.Occasion
import app.tryst.data.db.entity.Place
import app.tryst.data.db.entity.Protection
import app.tryst.data.db.entity.ToyType
import app.tryst.data.stats.mostUsedCommon
import app.tryst.ui.common.ActOptions
import app.tryst.ui.common.DecodedImage
import app.tryst.ui.common.Format
import app.tryst.ui.common.KinkOptions
import app.tryst.ui.common.MultiSelectChips
import app.tryst.ui.common.MultiSelectField
import app.tryst.ui.common.PositionOptions
import app.tryst.ui.common.SingleSelectChips
import app.tryst.ui.common.SingleSelectField
import app.tryst.ui.common.adaptiveContentWidth
import app.tryst.ui.common.encounterSharedKey
import app.tryst.ui.common.rememberCameraCapture
import app.tryst.ui.common.rememberHaptics
import app.tryst.ui.common.rememberImagePicker
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun EncounterEditScreen(
    encounterId: String?,
    onClose: () -> Unit,
    // Non-null only when launched as a full-screen destination, where the editor's container is
    // the target of the card / FAB shared-element transform. In the expanded-width two-pane layout
    // the editor lives in a side pane with no morph, so both scopes are null and sharedBounds is skipped.
    sharedScope: SharedTransitionScope?,
    animatedScope: AnimatedVisibilityScope?,
    viewModel: EncounterEditViewModel = hiltViewModel(),
) {
    LaunchedEffect(encounterId) { viewModel.load(encounterId) }

    val haptics = rememberHaptics()
    val ui = viewModel.uiState
    val partners by viewModel.availablePartners.collectAsStateWithLifecycle()
    val customPositions by viewModel.customPositions.collectAsStateWithLifecycle()
    val customActs by viewModel.customActs.collectAsStateWithLifecycle()
    val customKinks by viewModel.customKinks.collectAsStateWithLifecycle()
    // Per-category pick frequency, used to surface the user's most-used options inline (ENC-1).
    val usage by viewModel.optionUsage.collectAsStateWithLifecycle()
    // Solo = no partner selected (matches the history "Solo" badge). Partner-only fields are hidden so
    // the editor reads cleanly as a solo entry; the per-partner orgasm counters already auto-hide.
    val solo = ui.solo
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showDiscardConfirm by remember { mutableStateOf(false) }
    var viewer by remember { mutableStateOf<PhotoView?>(null) }

    // Guard accidental exits (predictive-back swipe, the Cancel chevron) once the form is touched —
    // a stray gesture while reaching for Save shouldn't silently drop the entry and its photos.
    val dirty = viewModel.hasUnsavedChanges()
    val attemptClose = { if (dirty) showDiscardConfirm = true else onClose() }
    BackHandler(enabled = dirty) { showDiscardConfirm = true }
    val pickImage = rememberImagePicker(onLaunch = { viewModel.suppressAutoLock() }) { viewModel.addPhoto(it) }
    val captureImage = rememberCameraCapture(onLaunch = { viewModel.suppressAutoLock() }) { uri, file ->
        viewModel.addCapturedPhoto(uri, file)
    }

    Scaffold(
        // The editor's container is the destination of the card / FAB container transform. A surface
        // background keeps the morph opaque while the form fades in over it.
        modifier = if (sharedScope != null && animatedScope != null) {
            with(sharedScope) {
                Modifier.sharedBounds(
                    rememberSharedContentState(encounterSharedKey(encounterId)),
                    animatedVisibilityScope = animatedScope,
                    resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
                )
            }
        } else {
            Modifier
        },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (ui.isEditing) R.string.encounter_title_edit else R.string.encounter_title_new)) },
                navigationIcon = { TextButton(onClick = attemptClose) { Text(stringResource(R.string.action_cancel)) } },
                actions = {
                    TextButton(onClick = {
                        haptics.confirm()
                        viewModel.save(onClose)
                    }) { Text(stringResource(R.string.action_save)) }
                },
            )
        },
    ) { padding ->
        // Cap + centre the form on wide windows (tablet / medium-width foldable) so the fields
        // don't stretch into very long rows. A no-op on phones and in the narrow two-pane pane.
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier
                    .adaptiveContentWidth()
                    .fillMaxHeight()
                    // Lift the scrolling content above the soft keyboard so the focused field (Duration,
                    // Note) stays visible while typing.
                    .imePadding()
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState())
                    // Dynamic rows (ejaculation per orgasm, per-partner orgasm counters, the delete
                    // button) appear and disappear as you edit — let the form resize smoothly.
                    .animateContentSize(),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Spacer(Modifier.height(4.dp))

                Field(stringResource(R.string.encounter_field_when)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { showDatePicker = true }) {
                            Text(Format.date(ui.startAt))
                        }
                        OutlinedButton(onClick = { showTimePicker = true }) {
                            Text(Format.time(ui.startAt))
                        }
                    }
                }

                Field(stringResource(R.string.encounter_field_duration)) {
                    OutlinedTextField(
                        value = ui.durationText,
                        onValueChange = { viewModel.setDuration(it) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Field(stringResource(R.string.encounter_field_partners)) {
                    if (partners.isEmpty()) {
                        Text(
                            stringResource(R.string.encounter_no_partners),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        MultiSelectChips(
                            options = partners,
                            selected = partners.filter { it.id in ui.selectedPartnerIds }.toSet(),
                            label = { Format.partnerName(it) },
                            onToggle = { viewModel.togglePartner(it.id) },
                        )
                    }
                }

                MultiSelectField(
                    label = stringResource(R.string.encounter_field_protection),
                    all = Protection.entries,
                    common = mostUsedCommon(CommonOptions.PROTECTION, Protection.entries) { usage.protection[it] ?: 0 },
                    selected = ui.protection,
                    labelOf = { it.label },
                    onToggle = { viewModel.toggleProtection(it) },
                )

                SingleSelectField(
                    label = stringResource(R.string.encounter_field_mood),
                    all = Mood.entries,
                    common = mostUsedCommon(CommonOptions.MOOD, Mood.entries) { usage.moods[it] ?: 0 },
                    selected = ui.mood,
                    labelOf = { it.label },
                    onSelect = { viewModel.setMood(it) },
                )

                Field(stringResource(R.string.encounter_orgasms_you)) {
                    Stepper(value = ui.orgasmCountSelf, onChange = { viewModel.setSelfOrgasms(it) })
                }

                // Ejaculation location(s) per orgasm you had — multi-select (e.g. chest + stomach).
                repeat(ui.orgasmCountSelf) { i ->
                    MultiSelectField(
                        label = if (ui.orgasmCountSelf > 1) {
                            stringResource(R.string.encounter_ejaculation_n, i + 1)
                        } else {
                            stringResource(R.string.encounter_ejaculation)
                        },
                        all = EjaculationLocation.entries,
                        common = mostUsedCommon(CommonOptions.EJACULATION, EjaculationLocation.entries) {
                            usage.ejaculation[it] ?: 0
                        },
                        selected = ui.ejaculations[i] ?: emptySet(),
                        labelOf = { it.label },
                        onToggle = { viewModel.toggleEjaculation(i, it) },
                    )
                }

                // One orgasm counter per selected partner, ordered by name.
                partners
                    .filter { it.id in ui.selectedPartnerIds }
                    .sortedBy { Format.partnerName(it).lowercase() }
                    .forEach { partner ->
                        Field(stringResource(R.string.encounter_orgasms_partner, Format.partnerName(partner))) {
                            Stepper(
                                value = ui.partnerOrgasms[partner.id] ?: 0,
                                onChange = { viewModel.setPartnerOrgasms(partner.id, it) },
                            )
                        }
                    }

                val positionOptions = PositionOptions.builtIns + PositionOptions.custom(customPositions)
                val positionCommon = mostUsedCommon(PositionOptions.common, positionOptions) { usage.positions[it.id] ?: 0 }
                MultiSelectField(
                    label = stringResource(R.string.encounter_field_positions),
                    all = positionOptions,
                    common = positionCommon,
                    selected = positionOptions.filter { it.id in ui.selectedPositionIds }.toSet(),
                    labelOf = { it.label },
                    onToggle = { viewModel.togglePosition(it.id) },
                )

                val actOptions = ActOptions.builtIns + ActOptions.custom(customActs)
                val actCommon = mostUsedCommon(ActOptions.common, actOptions) { usage.acts[it.id] ?: 0 }
                MultiSelectField(
                    label = stringResource(R.string.encounter_field_acts_gave),
                    all = actOptions,
                    common = actCommon,
                    selected = actOptions.filter { it.id in ui.practicesPerformed }.toSet(),
                    labelOf = { it.label },
                    onToggle = { viewModel.togglePerformed(it.id) },
                )

                if (!solo) {
                    MultiSelectField(
                        label = stringResource(R.string.encounter_field_acts_received),
                        all = actOptions,
                        common = actCommon,
                        selected = actOptions.filter { it.id in ui.practicesReceived }.toSet(),
                        labelOf = { it.label },
                        onToggle = { viewModel.toggleReceived(it.id) },
                    )
                }

                val kinkOptions = KinkOptions.builtIns + KinkOptions.custom(customKinks)
                MultiSelectField(
                    label = stringResource(R.string.encounter_field_kink),
                    all = kinkOptions,
                    common = mostUsedCommon(KinkOptions.common, kinkOptions) { usage.kinks[it.id] ?: 0 },
                    selected = kinkOptions.filter { it.id in ui.kinks }.toSet(),
                    labelOf = { it.label },
                    onToggle = { viewModel.toggleKink(it.id) },
                )

                MultiSelectField(
                    label = stringResource(R.string.encounter_field_setting),
                    all = Place.entries,
                    common = mostUsedCommon(CommonOptions.PLACE, Place.entries) { usage.places[it] ?: 0 },
                    selected = ui.contexts,
                    labelOf = { it.label },
                    onToggle = { viewModel.toggleContext(it) },
                )

                MultiSelectField(
                    label = stringResource(R.string.encounter_field_occasion),
                    all = Occasion.entries,
                    common = mostUsedCommon(CommonOptions.OCCASION, Occasion.entries) { usage.occasions[it] ?: 0 },
                    selected = ui.occasions,
                    labelOf = { it.label },
                    onToggle = { viewModel.toggleOccasion(it) },
                )

                MultiSelectField(
                    label = stringResource(R.string.encounter_field_toys),
                    all = ToyType.entries,
                    common = mostUsedCommon(CommonOptions.TOY, ToyType.entries) { usage.toys[it] ?: 0 },
                    selected = ui.toys,
                    labelOf = { it.label },
                    onToggle = { viewModel.toggleToy(it) },
                )

                if (!solo) {
                    Field(stringResource(R.string.encounter_field_initiator)) {
                        SingleSelectChips(
                            options = Initiator.entries,
                            selected = ui.initiator,
                            label = { it.label },
                            onSelect = { viewModel.setInitiator(it) },
                        )
                    }
                }

                Field(stringResource(R.string.encounter_field_rating)) {
                    SingleSelectChips(
                        options = (1..5).toList(),
                        selected = ui.rating,
                        label = { "$it" },
                        onSelect = { viewModel.setRating(it) },
                    )
                }

                Field(stringResource(R.string.encounter_field_note)) {
                    OutlinedTextField(
                        value = ui.note,
                        onValueChange = { viewModel.setNote(it) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                    )
                }

                Field(stringResource(R.string.encounter_field_photos)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ui.existingPhotos.forEach { media ->
                            PhotoThumb(
                                key = media.id,
                                load = { viewModel.decodeExisting(media, THUMB_PX) },
                                onClick = { viewer = PhotoView(media.id) { viewModel.decodeExisting(media, FULL_PX) } },
                                onRemove = { viewModel.removeExisting(media) },
                            )
                        }
                        ui.pendingPhotos.forEach { photo ->
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

                if (ui.isEditing) {
                    OutlinedButton(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.encounter_delete_button)) }
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (showDatePicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = ui.startAt)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { viewModel.setStartAt(combineDate(ui.startAt, it)) }
                    showDatePicker = false
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.action_cancel)) } },
        ) { DatePicker(state = state) }
    }

    if (showTimePicker) {
        val zoned = Instant.ofEpochMilli(ui.startAt).atZone(ZoneId.systemDefault())
        val state = rememberTimePickerState(initialHour = zoned.hour, initialMinute = zoned.minute)
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setStartAt(combineTime(ui.startAt, state.hour, state.minute))
                    showTimePicker = false
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text(stringResource(R.string.action_cancel)) } },
            text = { TimePicker(state = state) },
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.encounter_delete_title)) },
            text = { Text(stringResource(R.string.encounter_delete_message)) },
            confirmButton = {
                TextButton(onClick = {
                    haptics.reject()
                    showDeleteConfirm = false
                    viewModel.delete(onClose)
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
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

    viewer?.let { v ->
        Dialog(
            onDismissRequest = { viewer = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(onClickLabel = stringResource(R.string.cd_close)) { viewer = null },
                contentAlignment = Alignment.Center,
            ) {
                DecodedImage(
                    model = v.key,
                    contentDescription = stringResource(R.string.cd_photo),
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
    val haptics = rememberHaptics()
    val decreaseDesc = stringResource(R.string.cd_stepper_decrease)
    val increaseDesc = stringResource(R.string.cd_stepper_increase)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        OutlinedButton(
            onClick = {
                if (value > min) {
                    haptics.tick()
                    onChange(value - 1)
                }
            },
            enabled = value > min,
            modifier = Modifier.semantics { contentDescription = decreaseDesc },
        ) { Text("−") }
        // Roll the number up when it grows and down when it shrinks.
        AnimatedContent(
            targetState = value,
            transitionSpec = {
                if (targetState > initialState) {
                    slideInVertically { it } + fadeIn() togetherWith slideOutVertically { -it } + fadeOut()
                } else {
                    slideInVertically { -it } + fadeIn() togetherWith slideOutVertically { it } + fadeOut()
                }
            },
            label = "stepperValue",
        ) { v ->
            Text("$v", style = MaterialTheme.typography.titleLarge)
        }
        OutlinedButton(
            onClick = {
                if (value < max) {
                    haptics.tick()
                    onChange(value + 1)
                }
            },
            enabled = value < max,
            modifier = Modifier.semantics { contentDescription = increaseDesc },
        ) { Text("+") }
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
    val removeDesc = stringResource(R.string.cd_remove_photo)
    Box(modifier = Modifier.size(84.dp)) {
        DecodedImage(
            model = key,
            contentDescription = stringResource(R.string.cd_photo),
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onClick),
            contentScale = ContentScale.Crop,
            load = load,
        )
        // Small visual badge, but a full 48dp touch target + a proper label for TalkBack.
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .minimumInteractiveComponentSize()
                .clip(CircleShape)
                .clickable(onClick = onRemove, role = Role.Button)
                .semantics { contentDescription = removeDesc },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center,
            ) {
                Text("×", color = Color.White, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun AddPhotoTile(onCamera: () -> Unit, onGallery: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    val addPhotoDesc = stringResource(R.string.cd_add_photo)
    Box {
        Box(
            modifier = Modifier
                .size(84.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(role = Role.Button) { menuOpen = true }
                .semantics { contentDescription = addPhotoDesc },
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
                text = { Text(stringResource(R.string.encounter_add_photo_camera)) },
                onClick = {
                    menuOpen = false
                    onCamera()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.encounter_add_photo_gallery)) },
                onClick = {
                    menuOpen = false
                    onGallery()
                },
            )
        }
    }
}

/** The handful of most-common options shown inline per category; the rest live in "More…". */
private object CommonOptions {
    val PROTECTION = listOf(
        Protection.NONE,
        Protection.CONDOM,
        Protection.PILL,
        Protection.IUD,
        Protection.PREP,
        Protection.WITHDRAWAL,
        Protection.INTERNAL_CONDOM,
        Protection.EMERGENCY_CONTRACEPTION,
    )
    val MOOD = listOf(
        Mood.AMAZING,
        Mood.HORNY,
        Mood.PASSIONATE,
        Mood.PLAYFUL,
        Mood.ROMANTIC,
        Mood.CONNECTED,
        Mood.RELAXED,
        Mood.GOOD,
    )
    val EJACULATION = listOf(
        EjaculationLocation.NONE,
        EjaculationLocation.IN_CONDOM,
        EjaculationLocation.VAGINAL,
        EjaculationLocation.ANAL,
        EjaculationLocation.ORAL,
        EjaculationLocation.SWALLOWED,
        EjaculationLocation.ON_FACE,
        EjaculationLocation.ON_CHEST,
        EjaculationLocation.ON_STOMACH,
        EjaculationLocation.IN_SHOWER,
    )
    val PLACE = listOf(
        Place.HOME,
        Place.BEDROOM,
        Place.SHOWER,
        Place.CAR,
        Place.HOTEL,
        Place.OUTDOORS,
        Place.LIVING_ROOM,
        Place.HOT_TUB,
    )
    val OCCASION = listOf(
        Occasion.REGULAR,
        Occasion.NONE,
        Occasion.QUICKIE,
        Occasion.MORNING_SEX,
        Occasion.MAKEUP_SEX,
        Occasion.SPONTANEOUS,
        Occasion.DATE_NIGHT,
        Occasion.DRUNK_HIGH,
    )
    val TOY = listOf(
        ToyType.NONE,
        ToyType.VIBRATOR,
        ToyType.DILDO,
        ToyType.BUTT_PLUG,
        ToyType.COCK_RING,
        ToyType.STRAP_ON,
        ToyType.WAND,
        ToyType.ANAL_BEADS,
    )
}
