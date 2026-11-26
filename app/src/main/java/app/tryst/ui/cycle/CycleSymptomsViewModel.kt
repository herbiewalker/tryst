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

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tryst.data.db.entity.CycleSymptomEntity
import app.tryst.data.repository.CycleOwner
import app.tryst.data.repository.CycleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One person's own symptom list (0.7.4, D-73), edited on the shared catalog screen. Nothing is preset. */
@HiltViewModel
class CycleSymptomsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: CycleRepository,
) : ViewModel() {

    private val owner = CycleOwner(
        kind = checkNotNull(savedStateHandle.get<String>(CyclePersonViewModel.ARG_KIND)),
        id = checkNotNull(savedStateHandle.get<String>(CyclePersonViewModel.ARG_ID)),
    )

    val symptoms: StateFlow<List<CycleSymptomEntity>> = repository.observeSymptoms(owner)
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun add(label: String) {
        viewModelScope.launch { repository.addSymptom(owner, label) }
    }

    fun rename(id: String, label: String) {
        viewModelScope.launch { repository.renameSymptom(id, label) }
    }

    fun delete(id: String) {
        viewModelScope.launch { repository.deleteSymptom(id) }
    }
}

// ⏣ HW ⏣ · code · tools · homelab · ⏣ HW ⏣
