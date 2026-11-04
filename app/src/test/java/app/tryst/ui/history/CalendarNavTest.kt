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

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class CalendarNavTest {
    private val zone = ZoneId.of("America/New_York")
    private val today = LocalDate.of(2026, 11, 3)

    @Test
    fun staysWhereItWasWhenBackQuickly() {
        val away = CalendarNav(CalView.MONTH, YearMonth.of(2026, 6), LocalDate.of(2026, 6, 12), selectedDay = LocalDate.of(2026, 6, 12))
        val back = CalendarResume.resume(away, leftAt = 1_000L, now = 1_000L + 90_000L, today = today)
        assertEquals(away, back)
    }

    @Test
    fun resetsToTodayAfterAFewMinutesAway() {
        val away = CalendarNav(CalView.WEEK, YearMonth.of(2026, 6), LocalDate.of(2026, 6, 12), selectedDay = LocalDate.of(2026, 6, 12))
        val back = CalendarResume.resume(away, leftAt = 0L, now = CalendarResume.RESET_AFTER_MS + 1, today = today)
        assertEquals(CalendarNav.today(today), back)
        assertEquals(YearMonth.of(2026, 11), back.month)
        assertEquals(null, back.selectedDay)
    }

    @Test
    fun aFreshStartHasNothingToResume() {
        val nav = CalendarNav.today(today)
        assertEquals(nav, CalendarResume.resume(nav, leftAt = null, now = 9_999_999L, today = today))
    }

    @Test
    fun aNewTrystFromADayStartsAtNoonThatDay() {
        val now = LocalDate.of(2026, 11, 3).atTime(15, 30).atZone(zone).toInstant().toEpochMilli()
        val start = NewTrystStart.forDay(LocalDate.of(2026, 6, 12), now, zone)
        val local = Instant.ofEpochMilli(start).atZone(zone)
        assertEquals(LocalDate.of(2026, 6, 12), local.toLocalDate())
        assertEquals(12, local.hour)
        assertEquals(0, local.minute)
    }

    @Test
    fun aNewTrystFromTodayKeepsTheCurrentTime() {
        val now = LocalDate.of(2026, 11, 3).atTime(15, 30).atZone(zone).toInstant().toEpochMilli()
        assertEquals(now, NewTrystStart.forDay(LocalDate.of(2026, 11, 3), now, zone))
        assertEquals(now, NewTrystStart.forDay(null, now, zone))
    }
}

// ⏣ HW ⏣ · code · tools · homelab · ⏣ HW ⏣
