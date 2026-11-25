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
import app.tryst.data.cycle.CycleOverlay
import app.tryst.data.db.entity.CyclePeriodEntity
import app.tryst.data.db.entity.ProfileEntity
import app.tryst.data.repository.CycleOwner
import app.tryst.data.repository.CycleRepository
import app.tryst.data.repository.PartnerRepository
import app.tryst.data.repository.ProfileRepository
import app.tryst.ui.common.Format
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A person the calendar overlay can show. [name] is null for the self profile with no name ("You"). */
data class CyclePersonChoice(val key: String, val name: String?)

/** The cycle-tracking switches and "Delete cycle data" in Settings (CYC-1). */
@HiltViewModel
class CycleSettingsViewModel @Inject constructor(
    private val prefs: GeneralPreferences,
    private val repository: CycleRepository,
    profileRepository: ProfileRepository,
    partnerRepository: PartnerRepository,
) : ViewModel() {

    val enabled: StateFlow<Boolean> = prefs.cycleEnabled

    fun setEnabled(enabled: Boolean) = prefs.setCycleEnabled(enabled)

    val predictions: StateFlow<Boolean> = prefs.cyclePredictions

    fun setPredictions(enabled: Boolean) = prefs.setCyclePredictions(enabled)

    val calendar: StateFlow<Boolean> = prefs.cycleCalendar

    fun setCalendar(enabled: Boolean) = prefs.setCycleCalendar(enabled)

    /** Chosen people as "kind:id" keys; empty means everyone. */
    val calendarOwners: StateFlow<Set<String>> = prefs.cycleCalendarOwners

    fun setCalendarOwners(keys: Set<String>) = prefs.setCycleCalendarOwners(keys)

    /** You plus every active partner, in the order the Cycle tab lists them. */
    val people: StateFlow<List<CyclePersonChoice>> = combine(
        profileRepository.observe(),
        partnerRepository.observeActive(),
    ) { profile, partners ->
        buildList {
            val self = CycleOwner(CyclePeriodEntity.OWNER_PROFILE, ProfileEntity.SELF_ID)
            add(CyclePersonChoice(CycleOverlay.key(self), profile?.displayName?.takeIf { it.isNotBlank() }))
            partners.forEach {
                add(CyclePersonChoice(CycleOverlay.key(CycleOwner(CyclePeriodEntity.OWNER_PARTNER, it.id)), Format.partnerName(it)))
            }
        }
    }
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun deleteAll(onDone: () -> Unit) {
        viewModelScope.launch {
            repository.deleteAll()
            onDone()
        }
    }
}

// ⏣ HW ⏣ · code · tools · homelab · ⏣ HW ⏣
