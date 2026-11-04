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
package app.tryst.data.backup

import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupReminderTest {
    private val day = TimeUnit.DAYS.toMillis(1)
    private val now = 1_800_000_000_000L

    @Test
    fun aNearlyEmptyAppIsNeverNagged() {
        assertEquals(BackupNudge.None, BackupReminder.evaluate(now, null, 0L, entryCount = 2))
    }

    @Test
    fun neverBackedUpWithRealDataNudges() {
        assertEquals(BackupNudge.Never, BackupReminder.evaluate(now, null, 0L, entryCount = 40))
    }

    @Test
    fun aRecentBackupStaysQuiet() {
        assertEquals(BackupNudge.None, BackupReminder.evaluate(now, now - 29 * day, 0L, entryCount = 40))
    }

    @Test
    fun aStaleBackupReportsItsAge() {
        val n = BackupReminder.evaluate(now, now - 45 * day, 0L, entryCount = 40)
        assertEquals(BackupNudge.Stale(45), n)
        assertTrue(BackupReminder.evaluate(now, now - 30 * day, 0L, entryCount = 40) is BackupNudge.Stale)
    }

    @Test
    fun snoozeSilencesBothKindsUntilItExpires() {
        val until = BackupReminder.snoozeUntil(now)
        assertEquals(now + 7 * day, until)
        assertEquals(BackupNudge.None, BackupReminder.evaluate(now + day, null, until, 40))
        assertEquals(BackupNudge.None, BackupReminder.evaluate(now + day, now - 90 * day, until, 40))
        assertEquals(BackupNudge.Never, BackupReminder.evaluate(until, null, until, 40))
    }

    @Test
    fun aClockSkewedFutureBackupNeverGoesNegative() {
        assertEquals(BackupNudge.None, BackupReminder.evaluate(now, now + 5 * day, 0L, 40))
    }
}

// ⏣ HW ⏣ · code · tools · homelab · ⏣ HW ⏣
