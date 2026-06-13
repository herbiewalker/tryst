package app.tryst.core.prefs

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** First day of the calendar week. */
enum class WeekStart { SUNDAY, MONDAY }

/**
 * General app settings: auto-lock timeout, haptics, and the calendar's first day. None of these are
 * sensitive, so plain SharedPreferences is fine (and it's excluded from backup/transfer like the
 * other prefs — see data_extraction_rules). Exposed as StateFlows so the UI recomposes on change.
 * Mirrors [ThemePreferences].
 */
@Singleton
class GeneralPreferences @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("tryst_general", Context.MODE_PRIVATE)

    /** Delay before locking after the app is backgrounded. 0 = lock immediately (default). */
    private val _autoLockTimeoutMs = MutableStateFlow(prefs.getLong(KEY_AUTO_LOCK_MS, 0L))
    val autoLockTimeoutMs: StateFlow<Long> = _autoLockTimeoutMs.asStateFlow()

    private val _hapticsEnabled = MutableStateFlow(prefs.getBoolean(KEY_HAPTICS, true))
    val hapticsEnabled: StateFlow<Boolean> = _hapticsEnabled.asStateFlow()

    private val _weekStart = MutableStateFlow(loadWeekStart())
    val weekStart: StateFlow<WeekStart> = _weekStart.asStateFlow()

    fun setAutoLockTimeoutMs(ms: Long) {
        prefs.edit().putLong(KEY_AUTO_LOCK_MS, ms).apply()
        _autoLockTimeoutMs.value = ms
    }

    fun setHapticsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_HAPTICS, enabled).apply()
        _hapticsEnabled.value = enabled
    }

    fun setWeekStart(start: WeekStart) {
        prefs.edit().putString(KEY_WEEK_START, start.name).apply()
        _weekStart.value = start
    }

    private fun loadWeekStart(): WeekStart = prefs.getString(KEY_WEEK_START, null)
        ?.let { runCatching { WeekStart.valueOf(it) }.getOrNull() }
        ?: WeekStart.SUNDAY

    private companion object {
        const val KEY_AUTO_LOCK_MS = "auto_lock_ms"
        const val KEY_HAPTICS = "haptics_enabled"
        const val KEY_WEEK_START = "week_start"
    }
}
