package app.tryst.core.prefs

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Light / Dark / follow-the-phone. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * Persisted appearance settings. Theme choice is not sensitive, so plain SharedPreferences is
 * fine (and it's excluded from backup/transfer like everything else — see data_extraction_rules).
 * Exposed as StateFlows so the UI recomposes when the user changes them in Settings.
 */
@Singleton
class ThemePreferences @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("tryst_appearance", Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(loadMode())
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _dynamicColor = MutableStateFlow(prefs.getBoolean(KEY_DYNAMIC, false))
    val dynamicColor: StateFlow<Boolean> = _dynamicColor.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_MODE, mode.name).apply()
        _themeMode.value = mode
    }

    fun setDynamicColor(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DYNAMIC, enabled).apply()
        _dynamicColor.value = enabled
    }

    private fun loadMode(): ThemeMode =
        prefs.getString(KEY_MODE, null)
            ?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
            ?: ThemeMode.SYSTEM

    private companion object {
        const val KEY_MODE = "theme_mode"
        const val KEY_DYNAMIC = "dynamic_color"
    }
}
