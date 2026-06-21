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
 *
 * Every call passes [HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING]: without it, a View whose
 * `isHapticFeedbackEnabled` is false (common default on Compose-hosted views / many OEM skins)
 * silently swallows the feedback, so the in-app toggle would do nothing even when on. The flag makes
 * our own setting authoritative — when the user enables haptics, taps buzz regardless of the host
 * view's flag. (The device-level "vibrate on touch" system setting still wins; that's not ours to
 * override.)
 */
class Haptics(private val view: View, private val enabled: Boolean) {
    private fun perform(feedbackConstant: Int) {
        if (enabled) view.performHapticFeedback(feedbackConstant, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING)
    }

    /** A successful, committing action — saving, enabling, completing. */
    fun confirm() = perform(HapticFeedbackConstants.CONFIRM)

    /** A destructive or rejected action — deleting, a wrong PIN. */
    fun reject() = perform(HapticFeedbackConstants.REJECT)

    /** A light, repeated tick — a keypad digit, a stepper step, crossing a reorder slot. */
    fun tick() = perform(HapticFeedbackConstants.CLOCK_TICK)

    /** Picking something up — the start of a drag-to-reorder. */
    fun pickUp() = perform(HapticFeedbackConstants.LONG_PRESS)
}

@Composable
fun rememberHaptics(): Haptics {
    val view = LocalView.current
    val enabled = LocalHapticsEnabled.current
    return remember(view, enabled) { Haptics(view, enabled) }
}
