package app.tryst.ui.lock

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import app.tryst.R

/**
 * First-run PIN creation: enter a 6-digit PIN, then confirm it. On match, sets up the vault.
 */
@Composable
fun SetupScreen(viewModel: LockViewModel) {
    var firstPin by remember { mutableStateOf<String?>(null) }
    var mismatchError by remember { mutableStateOf<String?>(null) }

    val confirming = firstPin != null
    val title = stringResource(if (confirming) R.string.setup_confirm_title else R.string.setup_create_title)
    val subtitle = stringResource(
        if (confirming) R.string.setup_confirm_subtitle else R.string.setup_create_subtitle,
    )
    val mismatchMessage = stringResource(R.string.setup_pin_mismatch)

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
                mismatchError = mismatchMessage
            }
        },
    )
}
