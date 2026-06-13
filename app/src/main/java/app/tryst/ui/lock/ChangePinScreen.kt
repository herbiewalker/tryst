package app.tryst.ui.lock

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import app.tryst.R

private enum class ChangePinStep { CURRENT, NEW, CONFIRM }

/**
 * Change the app PIN: confirm the current PIN, then enter + confirm a new one. The current PIN is
 * verified up front **without** counting toward the self-wipe, and the new PIN re-wraps the existing
 * in-memory DEK — so a mistyped current PIN can never wipe or lose data. Cancel via system back.
 */
@Composable
fun ChangePinScreen(onClose: () -> Unit, viewModel: LockViewModel = hiltViewModel()) {
    var step by remember { mutableStateOf(ChangePinStep.CURRENT) }
    var current by remember { mutableStateOf("") }
    var firstNew by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    val wrongCurrent = stringResource(R.string.change_pin_wrong_current)
    val mismatch = stringResource(R.string.change_pin_mismatch)
    val title = stringResource(
        when (step) {
            ChangePinStep.CURRENT -> R.string.change_pin_current_title
            ChangePinStep.NEW -> R.string.change_pin_new_title
            ChangePinStep.CONFIRM -> R.string.change_pin_confirm_title
        },
    )

    PinPad(
        title = title,
        subtitle = null,
        error = error,
        enabled = !viewModel.busy,
        onPinComplete = { entered ->
            error = null
            when (step) {
                ChangePinStep.CURRENT -> viewModel.verifyCurrentPin(entered) { ok ->
                    if (ok) {
                        current = entered
                        step = ChangePinStep.NEW
                    } else {
                        error = wrongCurrent
                    }
                }

                ChangePinStep.NEW -> {
                    firstNew = entered
                    step = ChangePinStep.CONFIRM
                }

                ChangePinStep.CONFIRM -> if (entered == firstNew) {
                    viewModel.changePin(current, entered) { ok ->
                        if (ok) {
                            onClose()
                        } else {
                            error = wrongCurrent
                            current = ""
                            firstNew = ""
                            step = ChangePinStep.CURRENT
                        }
                    }
                } else {
                    error = mismatch
                    firstNew = ""
                    step = ChangePinStep.NEW
                }
            }
        },
    )
}
