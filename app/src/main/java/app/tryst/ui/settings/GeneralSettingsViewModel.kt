package app.tryst.ui.settings

import androidx.lifecycle.ViewModel
import app.tryst.core.prefs.GeneralPreferences
import app.tryst.core.prefs.WeekStart
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

@HiltViewModel
class GeneralSettingsViewModel @Inject constructor(
    private val prefs: GeneralPreferences,
) : ViewModel() {

    val autoLockTimeoutMs: StateFlow<Long> = prefs.autoLockTimeoutMs
    val hapticsEnabled: StateFlow<Boolean> = prefs.hapticsEnabled
    val weekStart: StateFlow<WeekStart> = prefs.weekStart

    fun setAutoLockTimeoutMs(ms: Long) = prefs.setAutoLockTimeoutMs(ms)

    fun setHapticsEnabled(enabled: Boolean) = prefs.setHapticsEnabled(enabled)

    fun setWeekStart(start: WeekStart) = prefs.setWeekStart(start)
}
