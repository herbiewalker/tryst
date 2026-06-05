package app.tryst.ui.lock

import androidx.compose.runtime.Composable

/** Unlock screen shown when the vault exists but the app is locked. */
@Composable
fun LockScreen(viewModel: LockViewModel) {
    PinPad(
        title = "Enter your PIN",
        subtitle = "Unlock Tryst.",
        error = viewModel.error,
        enabled = !viewModel.busy,
        onPinComplete = { entered -> viewModel.unlock(entered) },
    )
}
