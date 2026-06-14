package app.tryst.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.tryst.R
import app.tryst.data.db.entity.BodyType
import app.tryst.data.db.entity.Ethnicity
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * The shared demographic block (date of birth → age, ethnicity, height, body type, location) used by
 * both the partner editor and the self-profile editor, so the two stay identical. Values are held by
 * the caller; this is a stateless group of fields plus a local date-picker dialog.
 *
 * Birth date is stored as **noon, system-default zone** for the picked calendar day: the Material
 * picker hands back UTC-midnight millis, which—displayed in a behind-UTC zone—would render as the day
 * before, so we normalise to local noon (DST/offset-safe) on the way in and back to UTC-midnight when
 * re-seeding the picker.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DemographicFields(
    birthDate: Long?,
    onBirthDate: (Long?) -> Unit,
    ethnicity: Ethnicity?,
    onEthnicity: (Ethnicity?) -> Unit,
    height: String,
    onHeight: (String) -> Unit,
    bodyType: BodyType?,
    onBodyType: (BodyType?) -> Unit,
    location: String,
    onLocation: (String) -> Unit,
) {
    var showDatePicker by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.demo_birth_date), style = MaterialTheme.typography.labelLarge)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(onClick = { showDatePicker = true }) {
                Text(birthDate?.let { Format.date(it) } ?: stringResource(R.string.demo_set_birth_date))
            }
            birthDate?.let { bd ->
                Format.age(bd)?.let { years ->
                    Text(
                        stringResource(R.string.demo_age, years),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { onBirthDate(null) }) { Text(stringResource(R.string.action_clear)) }
            }
        }
    }

    OptionalChips(stringResource(R.string.demo_ethnicity), Ethnicity.entries, ethnicity, onEthnicity)

    OutlinedTextField(
        value = height,
        onValueChange = onHeight,
        label = { Text(stringResource(R.string.demo_height)) },
        placeholder = { Text(stringResource(R.string.demo_height_hint)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )

    OptionalChips(stringResource(R.string.demo_body_type), BodyType.entries, bodyType, onBodyType)

    OutlinedTextField(
        value = location,
        onValueChange = onLocation,
        label = { Text(stringResource(R.string.demo_location)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )

    if (showDatePicker) {
        val today = remember { System.currentTimeMillis() }
        val state = rememberDatePickerState(
            initialSelectedDateMillis = birthDate?.let { localNoonToPickerMillis(it) },
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis <= today
            },
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    onBirthDate(state.selectedDateMillis?.let { pickerMillisToLocalNoon(it) })
                    showDatePicker = false
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.action_cancel)) } },
        ) { DatePicker(state = state) }
    }
}

/** Picked calendar day (UTC-midnight millis from the picker) → noon in the local zone. */
private fun pickerMillisToLocalNoon(pickerUtcMidnight: Long): Long {
    val date = Instant.ofEpochMilli(pickerUtcMidnight).atZone(ZoneOffset.UTC).toLocalDate()
    return date.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
}

/** Stored local-noon millis → the UTC-midnight millis the picker expects for that calendar day. */
private fun localNoonToPickerMillis(localNoon: Long): Long {
    val date = Instant.ofEpochMilli(localNoon).atZone(ZoneId.systemDefault()).toLocalDate()
    return date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
}
