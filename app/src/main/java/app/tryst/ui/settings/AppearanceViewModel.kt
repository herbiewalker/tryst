package app.tryst.ui.settings

import androidx.lifecycle.ViewModel
import app.tryst.core.prefs.ThemeMode
import app.tryst.core.prefs.ThemePreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class AppearanceViewModel @Inject constructor(
    private val prefs: ThemePreferences,
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = prefs.themeMode
    val dynamicColor: StateFlow<Boolean> = prefs.dynamicColor

    fun setThemeMode(mode: ThemeMode) = prefs.setThemeMode(mode)

    fun setDynamicColor(enabled: Boolean) = prefs.setDynamicColor(enabled)
}
