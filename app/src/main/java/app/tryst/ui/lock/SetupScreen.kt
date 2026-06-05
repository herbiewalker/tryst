package app.tryst.ui.lock

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * First-run PIN creation: enter a 6-digit PIN, then confirm it. On match, sets up the vault.
 */
@Composable
fun SetupScreen(viewModel: LockViewModel) {
    var firstPin by remember { mutableStateOf<String?>(null) }
    var mismatchError by remember { mutableStateOf<String?>(null) }

    val confirming = firstPin != null
    val title = if (confirming) "Confirm your PIN" else "Create a PIN"
    val subtitle = if (confirming) {
        "Enter the same 6 digits again."
    } else {
        "Choose a 6-digit PIN to lock Tryst. It's separate from your phone's PIN, and can't be recovered if forgotten."
    }

    PinPad(
        title = title,
        subtitle = subtitle,
        error = mismatchError ?: viewModel.error,
        enabled = !viewModel.busy,
        onPinComplete = { entered ->
            mismatchError = null
            val first = firstPin
            if (first == null) {
                firstPin = entered
            } else if (entered == first) {
                viewModel.setupPin(entered)
            } else {
                firstPin = null
                mismatchError = "PINs didn't match — start over."
            }
        },
    )
}
