// SPDX-License-Identifier: GPL-3.0-or-later
package app.tryst.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.tryst.R

/**
 * Full-screen CSV import route (QOL-7): picks a CSV, previews the detected columns, lets the user
 * map each Tryst field to a column, then imports. Was an `AlertDialog` cramped inside Settings —
 * promoting to its own route removes the 460dp height cap so the full mapping list fits without
 * an inner scroll, and adds room for a clear "detected N rows" summary + a real progress state.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("LongMethod") // Straight-line: pick / loading / mapping / done. Splitting would fragment the state machine.
fun CsvImportScreen(
    onBack: () -> Unit,
    viewModel: CsvImportViewModel = hiltViewModel(),
) {
    val openCsv = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.parse(uri)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.csv_import_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val hasFile = viewModel.headers.isNotEmpty()

            // File picker / status header — always visible so the user can re-pick another CSV.
            Text(
                text = stringResource(R.string.csv_import_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                onClick = {
                    viewModel.suppressAutoLock()
                    openCsv.launch(arrayOf("*/*"))
                },
                enabled = !viewModel.busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    stringResource(
                        if (hasFile) R.string.csv_import_pick_another else R.string.csv_import_pick,
                    ),
                )
            }

            viewModel.status?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            }

            if (viewModel.busy && !hasFile) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 24.dp),
                    horizontalArrangement = Arrangement.Center,
                ) { CircularProgressIndicator() }
            }

            if (hasFile) {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))

                // Detected-rows summary.
                Row(
                    Modifier.fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.fillMaxWidth()) {
                        Text(
                            stringResource(R.string.csv_rows_found, viewModel.rowCount),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            stringResource(R.string.csv_import_map_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Text(
                    stringResource(R.string.csv_map_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )

                CsvField.entries.forEach { field ->
                    CsvFieldMappingRow(
                        field = field,
                        headers = viewModel.headers,
                        selected = viewModel.mapping[field],
                        onSet = { col -> viewModel.setMapping(field, col) },
                    )
                }

                // Action row at the bottom — Import is primary, Cancel returns without wiping mapping.
                Row(
                    Modifier.fillMaxWidth().padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = { viewModel.cancel() },
                        modifier = Modifier.weight(1f),
                        enabled = !viewModel.busy,
                    ) { Text(stringResource(R.string.csv_import_clear)) }
                    Button(
                        onClick = { viewModel.import() },
                        modifier = Modifier.weight(1f),
                        enabled = !viewModel.busy && viewModel.mapping[CsvField.DATE] != null,
                        colors = ButtonDefaults.buttonColors(),
                    ) {
                        if (viewModel.busy) {
                            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.padding(end = 8.dp))
                        }
                        Text(stringResource(R.string.csv_import))
                    }
                }
            } else {
                // No file loaded yet — a hint about what format we accept.
                Box(
                    Modifier.fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                        .padding(14.dp),
                ) {
                    Text(
                        text = stringResource(R.string.csv_import_supported),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Per-field mapping row: label + a header-dropdown to pick which CSV column feeds this field. */
@Composable
private fun CsvFieldMappingRow(
    field: CsvField,
    headers: List<String>,
    selected: Int?,
    onSet: (Int?) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            field.label + if (field.required) " *" else "",
            style = MaterialTheme.typography.bodyMedium,
        )
        Box {
            OutlinedButton(onClick = { open = true }) {
                Text(selected?.let { idx -> headers.getOrNull(idx)?.ifBlank { stringResource(R.string.csv_column_n, idx + 1) } } ?: "—")
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.csv_none)) }, onClick = {
                    onSet(null)
                    open = false
                })
                headers.forEachIndexed { i, h ->
                    DropdownMenuItem(
                        text = { Text(h.ifBlank { stringResource(R.string.csv_column_n, i + 1) }) },
                        onClick = {
                            onSet(i)
                            open = false
                        },
                    )
                }
            }
        }
    }
}
