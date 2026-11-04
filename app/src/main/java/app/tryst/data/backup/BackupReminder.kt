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

/** What the "back up your data" nudge should say right now (BKP-2). */
sealed interface BackupNudge {
    data object None : BackupNudge

    /** The user has real data but has never exported a backup from this install. */
    data object Never : BackupNudge

    /** The last backup is [days] days old. */
    data class Stale(val days: Int) : BackupNudge
}

/**
 * Pure rule for the in-app backup reminder, so it can be unit-tested without Android. Backups are manual by design
 * (no network, no unattended keys), so the app's job is to make sure the user knows when the last one was.
 */
object BackupReminder {
    const val STALE_AFTER_DAYS = 30
    const val SNOOZE_DAYS = 7
    const val MIN_ENTRIES_TO_NAG = 5

    private val DAY_MS = TimeUnit.DAYS.toMillis(1)

    /**
     * @param lastBackupAt epoch millis of the last export from this install, or null if there never was one.
     * @param snoozedUntil epoch millis before which the nudge stays quiet (0 = not snoozed).
     * @param entryCount how many trysts exist; a near-empty app has nothing worth nagging about yet.
     */
    fun evaluate(now: Long, lastBackupAt: Long?, snoozedUntil: Long, entryCount: Int): BackupNudge {
        if (entryCount < MIN_ENTRIES_TO_NAG) return BackupNudge.None
        if (now < snoozedUntil) return BackupNudge.None
        if (lastBackupAt == null) return BackupNudge.Never
        val days = ((now - lastBackupAt) / DAY_MS).toInt().coerceAtLeast(0)
        return if (days >= STALE_AFTER_DAYS) BackupNudge.Stale(days) else BackupNudge.None
    }

    fun snoozeUntil(now: Long): Long = now + SNOOZE_DAYS * DAY_MS
}

// ⏣ HW ⏣ · code · tools · homelab · ⏣ HW ⏣
