package app.tryst.ui.common

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalView

/**
 * Whether haptic feedback is on (user setting, Settings → General). Provided at the activity root
 * from [app.tryst.core.prefs.GeneralPreferences]; defaults to on so any subtree without a provider
 * still buzzes.
 */
val LocalHapticsEnabled = staticCompositionLocalOf { true }

/**
 * Thin, intent-revealing wrapper over [View.performHapticFeedback] so call-sites say what a tap
 * *means* (confirm / reject / tick) rather than spelling out constants. All constants used here are
 * available on the app's minSdk (31): CONFIRM/REJECT landed in API 30, the rest much earlier.
 * No-ops entirely when [enabled] is false.
 */
class Haptics(private val view: View, private val enabled: Boolean) {
    /** A successful, committing action — saving, enabling, completing. */
    fun confirm() {
        if (enabled) view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
    }

    /** A destructive or rejected action — deleting, a wrong PIN. */
    fun reject() {
        if (enabled) view.performHapticFeedback(HapticFeedbackConstants.REJECT)
    }

    /** A light, repeated tick — a keypad digit, a stepper step, crossing a reorder slot. */
    fun tick() {
        if (enabled) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    /** Picking something up — the start of a drag-to-reorder. */
    fun pickUp() {
        if (enabled) view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }
}

@Composable
fun rememberHaptics(): Haptics {
    val view = LocalView.current
    val enabled = LocalHapticsEnabled.current
    return remember(view, enabled) { Haptics(view, enabled) }
}
