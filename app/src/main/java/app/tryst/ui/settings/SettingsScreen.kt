package app.tryst.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tryst.core.prefs.ThemeMode
import app.tryst.data.db.entity.ActEntity
import app.tryst.data.db.entity.PositionEntity
import app.tryst.ui.common.SingleSelectChips
import app.tryst.ui.lock.BiometricPromptHelper
import app.tryst.ui.lock.LockViewModel
import app.tryst.ui.lock.findFragmentActivity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: LockViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val activity = remember(context) { context.findFragmentActivity() }
    val biometricAvailable = remember { viewModel.canUseBiometrics() }
    var biometricEnabled by remember { mutableStateOf(viewModel.isBiometricEnabled()) }
    var showDeleteAll by remember { mutableStateOf(false) }
    var showPositions by remember { mutableStateOf(false) }
    var showActs by remember { mutableStateOf(false) }
    val positionsViewModel: CustomPositionsViewModel = hiltViewModel()
    val actsViewModel: CustomActsViewModel = hiltViewModel()
    val appearanceViewModel: AppearanceViewModel = hiltViewModel()
    val themeMode by appearanceViewModel.themeMode.collectAsStateWithLifecycle()
    val dynamicColor by appearanceViewModel.dynamicColor.collectAsStateWithLifecycle()

    Scaffold(topBar = { TopAppBar(title = { Text("Settings") }) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Security", style = MaterialTheme.typography.titleMedium)

            when {
                !biometricAvailable -> Text(
                    "Biometric unlock isn't available on this device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                biometricEnabled -> OutlinedButton(
                    onClick = {
                        viewModel.disableBiometric()
                        biometricEnabled = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Disable biometric unlock") }

                else -> Button(
                    onClick = {
                        val cipher = try {
                            viewModel.biometricEncryptCipher()
                        } catch (e: Exception) {
                            viewModel.reportError("Biometric unavailable: ${e.message}")
                            return@Button
                        }
                        BiometricPromptHelper.authenticate(
                            activity = activity,
                            cipher = cipher,
                            title = "Enable biometric unlock",
                            subtitle = "Confirm to allow unlocking Tryst with your fingerprint",
                            onSuccess = { authed -> if (viewModel.enableBiometric(authed)) biometricEnabled = true },
                            onError = { viewModel.reportError(it) },
                            onCancel = { },
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Enable biometric unlock") }
            }

            OutlinedButton(onClick = viewModel::lock, modifier = Modifier.fillMaxWidth()) {
                Text("Lock now")
            }

            viewModel.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            Text("Appearance", style = MaterialTheme.typography.titleMedium)
            Text("Theme", style = MaterialTheme.typography.labelLarge)
            SingleSelectChips(
                options = ThemeMode.entries,
                selected = themeMode,
                label = {
                    when (it) {
                        ThemeMode.SYSTEM -> "System"
                        ThemeMode.LIGHT -> "Light"
                        ThemeMode.DARK -> "Dark"
                    }
                },
                onSelect = { appearanceViewModel.setThemeMode(it) },
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = dynamicColor, onCheckedChange = { appearanceViewModel.setDynamicColor(it) })
                Text("  Use Material You colors", style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                "Off uses Tryst's purple & green palette; on follows your phone's wallpaper colors.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            Text("Categories", style = MaterialTheme.typography.titleMedium)
            OutlinedButton(onClick = { showPositions = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Manage custom positions")
            }
            OutlinedButton(onClick = { showActs = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Manage custom acts")
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            Text("Danger zone", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
            Button(
                onClick = { showDeleteAll = true },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Delete all data", color = Color.White) }
            Text(
                "Permanently erases every encounter, partner, photo, and your PIN. This cannot be undone.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (showDeleteAll) {
        AlertDialog(
            onDismissRequest = { showDeleteAll = false },
            title = { Text("Delete all data?") },
            text = { Text("This permanently erases everything in Tryst and returns to first-run setup. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteAll = false
                    viewModel.deleteAllData()
                }) { Text("Delete everything") }
            },
            dismissButton = { TextButton(onClick = { showDeleteAll = false }) { Text("Cancel") } },
        )
    }

    if (showPositions) {
        CustomPositionsDialog(viewModel = positionsViewModel, onDismiss = { showPositions = false })
    }

    if (showActs) {
        CustomActsDialog(viewModel = actsViewModel, onDismiss = { showActs = false })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomActsDialog(viewModel: CustomActsViewModel, onDismiss: () -> Unit) {
    val acts by viewModel.customActs.collectAsStateWithLifecycle()
    var newLabel by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom acts") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Custom acts appear alongside the built-in ones (gave & received) when logging a tryst.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = newLabel,
                        onValueChange = { newLabel = it },
                        label = { Text("Add an act") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = {
                            viewModel.add(newLabel)
                            newLabel = ""
                        },
                        enabled = newLabel.isNotBlank(),
                    ) { Text("Add") }
                }
                if (acts.isEmpty()) {
                    Text(
                        "No custom acts yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Column(
                        modifier = Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        acts.forEach { act ->
                            CustomActRow(act, onDelete = { viewModel.delete(act.id) })
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

@Composable
private fun CustomActRow(act: ActEntity, onDelete: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(act.label, style = MaterialTheme.typography.bodyLarge)
        TextButton(onClick = onDelete) { Text("Remove") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomPositionsDialog(viewModel: CustomPositionsViewModel, onDismiss: () -> Unit) {
    val positions by viewModel.customPositions.collectAsStateWithLifecycle()
    var newLabel by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom positions") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Custom positions appear alongside the built-in ones when logging a tryst.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = newLabel,
                        onValueChange = { newLabel = it },
                        label = { Text("Add a position") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = {
                            viewModel.add(newLabel)
                            newLabel = ""
                        },
                        enabled = newLabel.isNotBlank(),
                    ) { Text("Add") }
                }
                if (positions.isEmpty()) {
                    Text(
                        "No custom positions yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Column(
                        modifier = Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        positions.forEach { position ->
                            CustomPositionRow(position, onDelete = { viewModel.delete(position.id) })
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

@Composable
private fun CustomPositionRow(position: PositionEntity, onDelete: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(position.label, style = MaterialTheme.typography.bodyLarge)
        TextButton(onClick = onDelete) { Text("Remove") }
    }
}
