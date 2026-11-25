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
package app.tryst.ui.cycle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tryst.core.prefs.GeneralPreferences
import app.tryst.data.repository.CycleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** The cycle-tracking switch and "Delete cycle data" in Settings (CYC-1). */
@HiltViewModel
class CycleSettingsViewModel @Inject constructor(
    private val prefs: GeneralPreferences,
    private val repository: CycleRepository,
) : ViewModel() {

    val enabled: StateFlow<Boolean> = prefs.cycleEnabled

    fun setEnabled(enabled: Boolean) = prefs.setCycleEnabled(enabled)

    fun deleteAll(onDone: () -> Unit) {
        viewModelScope.launch {
            repository.deleteAll()
            onDone()
        }
    }
}

// ⏣ HW ⏣ · code · tools · homelab · ⏣ HW ⏣
