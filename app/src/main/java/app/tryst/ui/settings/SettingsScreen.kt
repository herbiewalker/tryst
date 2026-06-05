package app.tryst.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
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
}
