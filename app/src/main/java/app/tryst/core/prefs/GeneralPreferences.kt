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
package app.tryst.core.prefs

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** First day of the calendar week. */
enum class WeekStart { SUNDAY, MONDAY }

/**
 * General app settings: auto-lock timeout, haptics, and the calendar's first day. None of these are
 * sensitive, so plain SharedPreferences is fine (and it's excluded from backup/transfer like the
 * other prefs — see data_extraction_rules). Exposed as StateFlows so the UI recomposes on change.
 * Mirrors [ThemePreferences].
 */
@Singleton
class GeneralPreferences @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("tryst_general", Context.MODE_PRIVATE)

    /** Delay before locking after the app is backgrounded. 0 = lock immediately (default). */
    private val _autoLockTimeoutMs = MutableStateFlow(prefs.getLong(KEY_AUTO_LOCK_MS, 0L))
    val autoLockTimeoutMs: StateFlow<Long> = _autoLockTimeoutMs.asStateFlow()

    private val _hapticsEnabled = MutableStateFlow(prefs.getBoolean(KEY_HAPTICS, true))
    val hapticsEnabled: StateFlow<Boolean> = _hapticsEnabled.asStateFlow()

    private val _weekStart = MutableStateFlow(loadWeekStart())
    val weekStart: StateFlow<WeekStart> = _weekStart.asStateFlow()

    /** Whether the Trysts screen opens in calendar view by default (vs. the list). */
    private val _defaultToCalendar = MutableStateFlow(prefs.getBoolean(KEY_DEFAULT_CALENDAR, false))
    val defaultToCalendar: StateFlow<Boolean> = _defaultToCalendar.asStateFlow()

    fun setAutoLockTimeoutMs(ms: Long) {
        prefs.edit().putLong(KEY_AUTO_LOCK_MS, ms).apply()
        _autoLockTimeoutMs.value = ms
    }

    fun setHapticsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_HAPTICS, enabled).apply()
        _hapticsEnabled.value = enabled
    }

    fun setWeekStart(start: WeekStart) {
        prefs.edit().putString(KEY_WEEK_START, start.name).apply()
        _weekStart.value = start
    }

    fun setDefaultToCalendar(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DEFAULT_CALENDAR, enabled).apply()
        _defaultToCalendar.value = enabled
    }

    /** Resets the user-facing settings only — [KEY_LAST_SEEN_VERSION] is internal What's-new state, not a preference. */
    fun resetToDefaults() {
        prefs.edit().remove(KEY_AUTO_LOCK_MS).remove(KEY_HAPTICS).remove(KEY_WEEK_START).remove(KEY_DEFAULT_CALENDAR).remove(KEY_CYCLE_ENABLED).remove(KEY_CYCLE_PREDICTIONS).remove(KEY_CYCLE_CALENDAR).remove(KEY_CYCLE_CALENDAR_OWNERS).apply()
        _cycleEnabled.value = false
        _cyclePredictions.value = true
        _cycleCalendar.value = false
        _cycleCalendarOwners.value = emptySet()
        _autoLockTimeoutMs.value = 0L
        _hapticsEnabled.value = true
        _weekStart.value = WeekStart.SUNDAY
        _defaultToCalendar.value = false
    }

    /**
     * The app versionCode the user last saw "What's new" for. 0 = never recorded (fresh install or a
     * pre-feature upgrade), which deliberately suppresses the popup the first time — there's no prior
     * version to announce.
     */
    fun lastSeenVersionCode(): Long = prefs.getLong(KEY_LAST_SEEN_VERSION, 0L)

    fun setLastSeenVersionCode(code: Long) {
        prefs.edit().putLong(KEY_LAST_SEEN_VERSION, code).apply()
    }

    /** When the last encrypted backup was exported from this install (BKP-2); null if never. Rides the settings backup. */
    private val _lastBackupAt = MutableStateFlow(prefs.getLong(KEY_LAST_BACKUP_AT, 0L).takeIf { it > 0L })
    val lastBackupAt: StateFlow<Long?> = _lastBackupAt.asStateFlow()

    fun setLastBackupAt(at: Long?) {
        if (at == null) prefs.edit().remove(KEY_LAST_BACKUP_AT).apply() else prefs.edit().putLong(KEY_LAST_BACKUP_AT, at).apply()
        _lastBackupAt.value = at
    }

    /** The backup reminder stays quiet until this time (BKP-2 "Later"). */
    private val _backupSnoozedUntil = MutableStateFlow(prefs.getLong(KEY_BACKUP_SNOOZED_UNTIL, 0L))
    val backupSnoozedUntil: StateFlow<Long> = _backupSnoozedUntil.asStateFlow()

    fun snoozeBackupReminder(until: Long) {
        prefs.edit().putLong(KEY_BACKUP_SNOOZED_UNTIL, until).apply()
        _backupSnoozedUntil.value = until
    }

    /**
     * Optional cycle tracking (CYC-1). Off by default; turning it on adds the Cycle tab. Turning it off
     * hides the tab but keeps the data (Settings → "Delete cycle data" removes it). Rides the settings backup.
     */
    private val _cycleEnabled = MutableStateFlow(prefs.getBoolean(KEY_CYCLE_ENABLED, false))
    val cycleEnabled: StateFlow<Boolean> = _cycleEnabled.asStateFlow()

    fun setCycleEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_CYCLE_ENABLED, enabled).apply()
        _cycleEnabled.value = enabled
    }

    /** Next-period estimates on the Cycle screens (CYC-1, 0.7.1). On by default once tracking is on; rides the settings backup. */
    private val _cyclePredictions = MutableStateFlow(prefs.getBoolean(KEY_CYCLE_PREDICTIONS, true))
    val cyclePredictions: StateFlow<Boolean> = _cyclePredictions.asStateFlow()

    fun setCyclePredictions(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_CYCLE_PREDICTIONS, enabled).apply()
        _cyclePredictions.value = enabled
    }

    /** Period days and the predicted range on the Trysts calendar (CYC-1, 0.7.2). Off by default; rides the settings backup. */
    private val _cycleCalendar = MutableStateFlow(prefs.getBoolean(KEY_CYCLE_CALENDAR, false))
    val cycleCalendar: StateFlow<Boolean> = _cycleCalendar.asStateFlow()

    fun setCycleCalendar(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_CYCLE_CALENDAR, enabled).apply()
        _cycleCalendar.value = enabled
    }

    /** Whose periods the calendar shows, as "kind:id" keys. Empty means everyone. */
    private val _cycleCalendarOwners = MutableStateFlow(prefs.getStringSet(KEY_CYCLE_CALENDAR_OWNERS, emptySet()).orEmpty().toSet())
    val cycleCalendarOwners: StateFlow<Set<String>> = _cycleCalendarOwners.asStateFlow()

    fun setCycleCalendarOwners(keys: Set<String>) {
        prefs.edit().putStringSet(KEY_CYCLE_CALENDAR_OWNERS, keys).apply()
        _cycleCalendarOwners.value = keys
    }

    /** Coach-mark ids the user has dismissed (QOL-6); each tip shows once. */
    private val _seenTips = MutableStateFlow(prefs.getStringSet(KEY_SEEN_TIPS, emptySet()).orEmpty().toSet())
    val seenTips: StateFlow<Set<String>> = _seenTips.asStateFlow()

    fun markTipSeen(id: String) {
        val updated = _seenTips.value + id
        prefs.edit().putStringSet(KEY_SEEN_TIPS, updated).apply()
        _seenTips.value = updated
    }

    private fun loadWeekStart(): WeekStart = prefs.getString(KEY_WEEK_START, null)
        ?.let { runCatching { WeekStart.valueOf(it) }.getOrNull() }
        ?: WeekStart.SUNDAY

    /**
     * Re-reads every value from storage. A backup restore writes these prefs behind this object's back
     * (`PrefsBackup.restoreAll`), so it calls this to make restored settings take effect without a restart.
     */
    fun reload() {
        _autoLockTimeoutMs.value = prefs.getLong(KEY_AUTO_LOCK_MS, 0L)
        _hapticsEnabled.value = prefs.getBoolean(KEY_HAPTICS, true)
        _weekStart.value = loadWeekStart()
        _defaultToCalendar.value = prefs.getBoolean(KEY_DEFAULT_CALENDAR, false)
        _lastBackupAt.value = prefs.getLong(KEY_LAST_BACKUP_AT, 0L).takeIf { it > 0L }
        _backupSnoozedUntil.value = prefs.getLong(KEY_BACKUP_SNOOZED_UNTIL, 0L)
        _cycleEnabled.value = prefs.getBoolean(KEY_CYCLE_ENABLED, false)
        _cyclePredictions.value = prefs.getBoolean(KEY_CYCLE_PREDICTIONS, true)
        _cycleCalendar.value = prefs.getBoolean(KEY_CYCLE_CALENDAR, false)
        _cycleCalendarOwners.value = prefs.getStringSet(KEY_CYCLE_CALENDAR_OWNERS, emptySet()).orEmpty().toSet()
        _seenTips.value = prefs.getStringSet(KEY_SEEN_TIPS, emptySet()).orEmpty().toSet()
    }

    private companion object {
        const val KEY_AUTO_LOCK_MS = "auto_lock_ms"
        const val KEY_HAPTICS = "haptics_enabled"
        const val KEY_WEEK_START = "week_start"
        const val KEY_DEFAULT_CALENDAR = "default_to_calendar"
        const val KEY_LAST_SEEN_VERSION = "last_seen_version_code"
        const val KEY_SEEN_TIPS = "seen_tips"
        const val KEY_LAST_BACKUP_AT = "last_backup_at"
        const val KEY_BACKUP_SNOOZED_UNTIL = "backup_snoozed_until"
        const val KEY_CYCLE_ENABLED = "cycle_enabled"
        const val KEY_CYCLE_PREDICTIONS = "cycle_predictions"
        const val KEY_CYCLE_CALENDAR = "cycle_calendar"
        const val KEY_CYCLE_CALENDAR_OWNERS = "cycle_calendar_owners"
    }
}

// ⏣ HW ⏣ · code · tools · homelab · ⏣ HW ⏣
