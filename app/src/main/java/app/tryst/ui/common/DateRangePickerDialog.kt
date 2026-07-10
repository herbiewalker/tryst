package app.tryst.ui.common

import androidx.compose.foundation.layout.height
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.tryst.R
import app.tryst.data.filter.DateRange
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Picks an inclusive [DateRange]. Shared by Search's "Custom range…" filter and the Insights time scope.
 *
 * The Material picker speaks UTC-midnight epoch millis, so both edges convert through [ZoneOffset.UTC] —
 * using the local zone here would shift the chosen day by one either side of midnight.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateRangePickerDialog(
    initial: DateRange?,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate, LocalDate) -> Unit,
) {
    val state = rememberDateRangePickerState(
        initialSelectedStartDateMillis = initial?.start?.toEpochMillisUtc(),
        initialSelectedEndDateMillis = initial?.end?.toEpochMillisUtc(),
    )
    val start = state.selectedStartDateMillis
    val end = state.selectedEndDateMillis

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = start != null && end != null,
                onClick = { if (start != null && end != null) onConfirm(start.toLocalDateUtc(), end.toLocalDateUtc()) },
            ) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    ) {
        DateRangePicker(state = state, modifier = Modifier.height(520.dp))
    }
}

private fun LocalDate.toEpochMillisUtc(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.toLocalDateUtc(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
