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
import app.tryst.data.db.entity.MediaEntity
import app.tryst.data.db.relation.EncounterWithDetails
import app.tryst.data.repository.EncounterRepository
import app.tryst.ui.common.MediaImages
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val repository: EncounterRepository,
    private val generalPreferences: GeneralPreferences,
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

    suspend fun decode(media: MediaEntity, reqPx: Int): ImageBitmap? = MediaImages.decodeSampled(reqPx) { runCatching { repository.openMedia(media) }.getOrNull() }
}

// ⏣ HW ⏣ · code · tools · homelab · ⏣ HW ⏣
