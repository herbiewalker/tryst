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

import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId

enum class CalView { MONTH, WEEK }

/**
 * Where the calendar is: its view, the month or week on screen, and the day the user tapped (QOL-8). Held in the
 * ViewModel so it survives opening a tryst and coming back, instead of snapping to the current month.
 */
data class CalendarNav(
    val view: CalView,
    val month: YearMonth,
    val weekAnchor: LocalDate,
    val selectedDay: LocalDate? = null,
) {
    companion object {
        fun today(today: LocalDate = LocalDate.now()) = CalendarNav(CalView.MONTH, YearMonth.from(today), today)
    }
}

/** How the calendar comes back after being away. */
object CalendarResume {
    /** Away longer than this and the calendar starts on today again, like a fresh open. */
    const val RESET_AFTER_MS = 2 * 60_000L

    fun resume(nav: CalendarNav, leftAt: Long?, now: Long, today: LocalDate): CalendarNav = if (leftAt != null && now - leftAt > RESET_AFTER_MS) CalendarNav.today(today) else nav
}

/** The start time a new tryst gets when it is begun from a calendar day (QOL-8). */
object NewTrystStart {
    private val NOON = LocalTime.NOON

    /** Today keeps "right now"; any other day starts at noon, a neutral time the user can change. */
    fun forDay(day: LocalDate?, now: Long, zone: ZoneId = ZoneId.systemDefault()): Long {
        if (day == null) return now
        val today = java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        return if (day == today) now else day.atTime(NOON).atZone(zone).toInstant().toEpochMilli()
    }
}

// ⏣ HW ⏣ · code · tools · homelab · ⏣ HW ⏣
