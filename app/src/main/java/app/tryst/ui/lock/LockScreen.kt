package app.tryst.ui.lock

import android.security.keystore.KeyPermanentlyInvalidatedException
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** Unlock screen: biometric (if enabled) with a PIN fallback always available. */
@Composable
fun LockScreen(viewModel: LockViewModel) {
    val context = LocalContext.current
    val activity = remember(context) { context.findFragmentActivity() }
    val biometricReady = remember { viewModel.isBiometricEnabled() && viewModel.canUseBiometrics() }

    fun launchBiometric() {
        val cipher = try {
            viewModel.biometricDecryptCipher()
        } catch (e: KeyPermanentlyInvalidatedException) {
            viewModel.onBiometricInvalidated()
            return
        } catch (e: Exception) {
            viewModel.reportError("Biometric unavailable — use your PIN.")
            return
        }
        BiometricPromptHelper.authenticate(
            activity = activity,
            cipher = cipher,
            title = "Unlock Tryst",
            subtitle = "Confirm your fingerprint",
            onSuccess = { authed -> viewModel.unlockWithBiometric(authed) },
            onError = { viewModel.reportError(it) },
            onCancel = { /* user chose PIN */ },
        )
    }

    // Offer biometric automatically when arriving at the lock screen.
    LaunchedEffect(Unit) {
        if (biometricReady) launchBiometric()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        PinPad(
            title = "Enter your PIN",
            subtitle = "Unlock Tryst.",
            error = viewModel.error,
            enabled = !viewModel.busy,
            onPinComplete = { entered -> viewModel.unlock(entered) },
        )
        if (biometricReady) {
            TextButton(
                onClick = { launchBiometric() },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp),
            ) {
                Text("Use biometric")
            }
        }
    }
}
