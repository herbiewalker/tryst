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
package app.tryst.ui.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tryst.data.color.PersonColorAssigner
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

/** Gives anyone still without a color their own as soon as the app is unlocked (D-70): older data, older backups. */
@HiltViewModel
class PersonColorsViewModel @Inject constructor(assigner: PersonColorAssigner) : ViewModel() {
    init {
        viewModelScope.launch { runCatching { assigner.fillMissing() } }
    }
}

// ⏣ HW ⏣ · code · tools · homelab · ⏣ HW ⏣
