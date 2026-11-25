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
package app.tryst.ui.history

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tryst.core.prefs.GeneralPreferences
import app.tryst.core.prefs.WeekStart
import app.tryst.data.backup.BackupNudge
import app.tryst.data.backup.BackupReminder
import app.tryst.data.cycle.CycleOverlay
import app.tryst.data.db.entity.CyclePeriodEntity
import app.tryst.data.db.entity.MediaEntity
import app.tryst.data.db.entity.ProfileEntity
import app.tryst.data.db.relation.EncounterWithDetails
import app.tryst.data.repository.CycleOwner
import app.tryst.data.repository.CycleRepository
import app.tryst.data.repository.EncounterRepository
import app.tryst.data.repository.PartnerRepository
import app.tryst.data.repository.ProfileRepository
import app.tryst.ui.common.Format
import app.tryst.ui.common.MediaImages
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val repository: EncounterRepository,
    private val generalPreferences: GeneralPreferences,
    cycleRepository: CycleRepository,
    profileRepository: ProfileRepository,
    partnerRepository: PartnerRepository,
) : ViewModel() {

    val encounters: StateFlow<List<EncounterWithDetails>> =
        repository.observeAll()
            // The DB closes on lock; swallow the resulting error so we don't crash mid-teardown.
            .catch { emit(emptyList()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val weekStart: StateFlow<WeekStart> = generalPreferences.weekStart

    /** Where the calendar is (QOL-8): kept here so opening a tryst and coming back does not lose the user's place. */
    var calendarNav by mutableStateOf(CalendarNav.today())
        private set

    /** The user's list-or-calendar choice this session; null means "follow the Settings default". */
    var calendarModeChoice by mutableStateOf<Boolean?>(null)
        private set
    private var leftAt: Long? = null

    fun updateCalendar(nav: CalendarNav) {
        calendarNav = nav
    }

    fun setCalendarMode(calendar: Boolean) {
        calendarModeChoice = calendar
    }

    fun onCalendarLeft() {
        if (leftAt == null) leftAt = System.currentTimeMillis()
    }

    fun onCalendarEntered() {
        val now = System.currentTimeMillis()
        val expired = leftAt?.let { now - it > CalendarResume.RESET_AFTER_MS } == true
        calendarNav = CalendarResume.resume(calendarNav, leftAt, now, LocalDate.now())
        if (expired) calendarModeChoice = null // after a few minutes away, open the way the user's setting says
        leftAt = null
    }

    /** Whether to nudge the user to export a backup, and how (BKP-2). */
    val backupNudge: StateFlow<BackupNudge> = combine(
        encounters,
        generalPreferences.lastBackupAt,
        generalPreferences.backupSnoozedUntil,
    ) { list, last, snoozed ->
        BackupReminder.evaluate(System.currentTimeMillis(), last, snoozed, list.size)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BackupNudge.None)

    /** "Later" on the reminder: stay quiet for a week. */
    fun snoozeBackupReminder() = generalPreferences.snoozeBackupReminder(BackupReminder.snoozeUntil(System.currentTimeMillis()))

    /** Whether the Trysts screen opens in calendar view by default (Settings → General). */
    val defaultToCalendar: StateFlow<Boolean> = generalPreferences.defaultToCalendar

    /**
     * Period marks for the calendar (CYC-1, 0.7.2): empty unless cycle tracking and its calendar switch are both on.
     * Archived partners are left out, like everywhere else on the calendar.
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val cycleMarks: StateFlow<Map<LocalDate, List<CalendarCycleMark>>> = combine(
        generalPreferences.cycleEnabled,
        generalPreferences.cycleCalendar,
    ) { enabled, calendar -> enabled && calendar }
        .flatMapLatest { on ->
            if (!on) {
                flowOf(emptyMap())
            } else {
                combine(
                    cycleRepository.observeAllPeriods(),
                    profileRepository.observe(),
                    partnerRepository.observeActive(),
                    generalPreferences.cyclePredictions,
                    generalPreferences.cycleCalendarOwners,
                ) { periods, profile, partners, predict, ownerKeys ->
                    val self = CycleOwner(CyclePeriodEntity.OWNER_PROFILE, ProfileEntity.SELF_ID)
                    val names: Map<CycleOwner, String?> = buildMap {
                        put(self, profile?.displayName?.takeIf { it.isNotBlank() })
                        partners.forEach { put(CycleOwner(CyclePeriodEntity.OWNER_PARTNER, it.id), Format.partnerName(it)) }
                    }
                    val colors: Map<CycleOwner, String?> = buildMap {
                        put(self, profile?.color)
                        partners.forEach { put(CycleOwner(CyclePeriodEntity.OWNER_PARTNER, it.id), it.color) }
                    }
                    val chosen = ownerKeys.mapNotNull(CycleOverlay::ownerFromKey).toSet().ifEmpty { null }
                    val visible = chosen?.intersect(names.keys) ?: names.keys
                    CycleOverlay.build(periods, LocalDate.now().toEpochDay(), predict, visible)
                        .entries.associate { (day, marks) ->
                            LocalDate.ofEpochDay(day) to marks.map { CalendarCycleMark(names[it.owner], it.owner.isSelf, it.predicted, colors[it.owner]) }
                        }
                }
            }
        }
        .catch { emit(emptyMap()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    suspend fun decode(media: MediaEntity, reqPx: Int): ImageBitmap? = MediaImages.decodeSampled(reqPx) { runCatching { repository.openMedia(media) }.getOrNull() }
}

/** One person's period mark on a calendar day. [name] is null for the self profile with no name (the UI says "You"); [colorHex] is their picked color, if any. */
data class CalendarCycleMark(val name: String?, val isSelf: Boolean, val predicted: Boolean, val colorHex: String? = null)

// ⏣ HW ⏣ · code · tools · homelab · ⏣ HW ⏣
