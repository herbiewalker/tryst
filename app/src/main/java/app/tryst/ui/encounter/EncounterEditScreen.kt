package app.tryst.ui.encounter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tryst.data.db.entity.EjaculationLocation
import app.tryst.data.db.entity.Initiator
import app.tryst.data.db.entity.Mood
import app.tryst.data.db.entity.Practice
import app.tryst.data.db.entity.Protection
import app.tryst.ui.common.Format
import app.tryst.ui.common.MultiSelectChips
import app.tryst.ui.common.SingleSelectChips
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
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

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

            Field("Protection") {
                MultiSelectChips(
                    options = Protection.entries,
                    selected = viewModel.protection,
                    label = { Format.enumLabel(it) },
                    onToggle = { viewModel.toggleProtection(it) },
                )
            }

            Field("Mood") {
                SingleSelectChips(
                    options = Mood.entries,
                    selected = viewModel.mood,
                    label = { Format.enumLabel(it) },
                    onSelect = { viewModel.mood = it },
                )
            }

            Field("Orgasms — you") {
                Stepper(value = viewModel.orgasmCountSelf, onChange = { viewModel.orgasmCountSelf = it })
            }

            Field("Orgasms — partner") {
                Stepper(value = viewModel.orgasmCountPartner, onChange = { viewModel.orgasmCountPartner = it })
            }

            Field("Ejaculation") {
                MultiSelectChips(
                    options = EjaculationLocation.entries,
                    selected = viewModel.ejaculationLocations,
                    label = { Format.enumLabel(it) },
                    onToggle = { viewModel.toggleEjaculation(it) },
                )
            }

            Field("Practices — performed (gave)") {
                MultiSelectChips(
                    options = Practice.entries,
                    selected = viewModel.practicesPerformed,
                    label = { Format.enumLabel(it) },
                    onToggle = { viewModel.togglePerformed(it) },
                )
            }

            Field("Practices — received (got)") {
                MultiSelectChips(
                    options = Practice.entries,
                    selected = viewModel.practicesReceived,
                    label = { Format.enumLabel(it) },
                    onToggle = { viewModel.toggleReceived(it) },
                )
            }

            Field("Who initiated") {
                SingleSelectChips(
                    options = Initiator.entries,
                    selected = viewModel.initiator,
                    label = { Format.enumLabel(it) },
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
