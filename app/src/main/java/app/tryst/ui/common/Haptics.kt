package app.tryst.ui.common

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/**
 * Thin, intent-revealing wrapper over [View.performHapticFeedback] so call-sites say what a tap
 * *means* (confirm / reject / tick) rather than spelling out constants. All constants used here are
 * available on the app's minSdk (31): CONFIRM/REJECT landed in API 30, the rest much earlier.
 */
class Haptics(private val view: View) {
    /** A successful, committing action — saving, enabling, completing. */
    fun confirm() = view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)

    /** A destructive or rejected action — deleting, a wrong PIN. */
    fun reject() = view.performHapticFeedback(HapticFeedbackConstants.REJECT)

    /** A light, repeated tick — a keypad digit, a stepper step, crossing a reorder slot. */
    fun tick() = view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)

    /** Picking something up — the start of a drag-to-reorder. */
    fun pickUp() = view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
}

@Composable
fun rememberHaptics(): Haptics {
    val view = LocalView.current
    return remember(view) { Haptics(view) }
}
