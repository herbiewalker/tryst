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
    val defaultToCalendar: StateFlow<Boolean> = prefs.defaultToCalendar

    fun setAutoLockTimeoutMs(ms: Long) = prefs.setAutoLockTimeoutMs(ms)

    fun setHapticsEnabled(enabled: Boolean) = prefs.setHapticsEnabled(enabled)

    fun setWeekStart(start: WeekStart) = prefs.setWeekStart(start)

    fun setDefaultToCalendar(enabled: Boolean) = prefs.setDefaultToCalendar(enabled)

    /** versionCode the user last saw release notes for; 0 = never (fresh install / pre-feature upgrade). */
    fun lastSeenVersionCode(): Long = prefs.lastSeenVersionCode()

    fun markVersionSeen(code: Long) = prefs.setLastSeenVersionCode(code)
}
