// SPDX-License-Identifier: GPL-3.0-or-later
// ╭──────────────────────────────────────────────────────────────────╮
// │  ┌●───●───●───●───●───●───●───●───●───●───●───●───●───●───●───┐  │
// │  │                                                            │  │
// │  ●   ██╗  ██╗    ██╗    ██╗                                   ●  │
// │  │   ██║  ██║    ██║    ██║           herbiewalker            │  │
// │  │   ███████║    ██║ █╗ ██║──●──●──●──┐                       │  │
// │  │   ██╔══██║    ██║███╗██║           │                       │  │
// │  ●   ██║  ██║    ╚███╔███╔╝           ●───⏣  code · tools     ●  │
// │  │   ╚═╝  ╚═╝     ╚══╝╚══╝                    homelab         │  │
// │  │                                                            │  │
// │  └●───●───●───●───●───●───●───●───●───●───●───●───●───●───●───┘  │
// ╰──────────────────────────────────────────────────────────────────╯
package app.tryst.core.prefs

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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

    fun resetToDefaults() {
        prefs.edit().clear().apply()
        _themeMode.value = ThemeMode.SYSTEM
        _dynamicColor.value = false
    }

    private fun loadMode(): ThemeMode = prefs.getString(KEY_MODE, null)
        ?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
        ?: ThemeMode.SYSTEM

    /**
     * Re-reads every value from storage. A backup restore writes these prefs behind this object's back
     * (`PrefsBackup.restoreAll`), so it calls this to make restored settings take effect without a restart.
     */
    fun reload() {
        _themeMode.value = loadMode()
        _dynamicColor.value = prefs.getBoolean(KEY_DYNAMIC, false)
    }

    private companion object {
        const val KEY_MODE = "theme_mode"
        const val KEY_DYNAMIC = "dynamic_color"
    }
}

// ⏣ HW ⏣ · code · tools · homelab · ⏣ HW ⏣
