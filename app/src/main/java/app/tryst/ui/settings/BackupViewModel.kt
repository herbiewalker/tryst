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
package app.tryst.ui.settings

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tryst.R
import app.tryst.core.prefs.GeneralPreferences
import app.tryst.core.prefs.PreferenceStores
import app.tryst.core.session.SessionManager
import app.tryst.data.backup.BackupError
import app.tryst.data.backup.BackupException
import app.tryst.data.backup.BackupManager
import app.tryst.data.backup.BackupProgress
import app.tryst.data.backup.BackupStage
import app.tryst.data.backup.BackupSummary
import app.tryst.data.backup.ExportResult
import app.tryst.data.backup.ImportResult
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Which kind of backup run is going on, for the progress and result titles. */
enum class BackupRun { EXPORT, IMPORT, CHECK }

/** What to tell the user once an export, import or check has ended (QOL-9, BKP-3). */
sealed interface BackupOutcome {
    data class Exported(val result: ExportResult) : BackupOutcome

    data class Restored(val result: ImportResult) : BackupOutcome

    /** "Check a backup" read the whole file and it's fine. */
    data class Checked(val summary: BackupSummary) : BackupOutcome

    /** [messageRes] explains the cause; [detail] fills its `%1$s` when it has one. */
    data class Failed(val run: BackupRun, val messageRes: Int, val detail: String? = null) : BackupOutcome
}

/** A restore that has been read and described but not started: the user confirms it from the preview. */
data class RestorePreview(val uri: Uri, val password: String, val wipeFirst: Boolean, val summary: BackupSummary)

@HiltViewModel
class BackupViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val session: SessionManager,
    private val backup: BackupManager,
    private val generalPreferences: GeneralPreferences,
    private val preferenceStores: PreferenceStores,
    private val colors: app.tryst.data.color.PersonColorAssigner,
) : ViewModel() {

    /** When the last backup was exported from this install (BKP-2); null if never. */
    val lastBackupAt: StateFlow<Long?> = generalPreferences.lastBackupAt

    /** The summary or error shown after a run, until the user dismisses it. */
    var outcome by mutableStateOf<BackupOutcome?>(null)
        private set

    /** Live progress while an export or import runs; null when idle. */
    var progress by mutableStateOf<BackupProgress?>(null)
        private set

    /** What is running, for the progress title. */
    var running by mutableStateOf(BackupRun.EXPORT)
        private set

    /** Set once a backup has been read for a restore and is waiting on the user's go-ahead. */
    var preview by mutableStateOf<RestorePreview?>(null)
        private set

    var busy by mutableStateOf(false)
        private set

    fun dismissOutcome() {
        outcome = null
    }

    /** Keep the app unlocked across the system file-picker handoff (same as the photo picker). */
    fun suppressAutoLock() = session.suppressNextAutoLock()

    fun export(uri: Uri, password: String, includeSettings: Boolean = true) {
        viewModelScope.launch {
            busy = true
            running = BackupRun.EXPORT
            outcome = null
            progress = BackupProgress(BackupStage.PREPARING)
            // Stamp the time first so the exported settings carry it too; put the old value back if the export fails.
            val previous = generalPreferences.lastBackupAt.value
            generalPreferences.setLastBackupAt(System.currentTimeMillis())
            try {
                val result = context.contentResolver.openOutputStream(uri)?.use { out ->
                    backup.export(password, out, includeSettings) { progress = it }
                } ?: throw IOException("Couldn't open the destination file")
                outcome = BackupOutcome.Exported(result)
            } catch (e: Exception) {
                generalPreferences.setLastBackupAt(previous)
                Log.e("TRYSTEXPORT", "export failed: ${e.javaClass.name}", e)
                outcome = if (isDiskFull(e)) {
                    BackupOutcome.Failed(BackupRun.EXPORT, messageRes = R.string.backup_error_disk_full)
                } else {
                    BackupOutcome.Failed(BackupRun.EXPORT, messageRes = R.string.backup_error_export_other, detail = e.message)
                }
            } finally {
                progress = null
                busy = false
            }
        }
    }

    /**
     * Step one of a restore (BKP-3): read the backup's description without changing anything, then show it.
     * A backup from a newer Tryst is refused right here, naming the version it needs when it says.
     */
    fun prepareRestore(uri: Uri, password: String, wipeFirst: Boolean) {
        viewModelScope.launch {
            busy = true
            outcome = null
            try {
                val summary = inspect(uri, password, fullCheck = false)
                preview = if (summary.newerThanApp) {
                    outcome = newerVersionOutcome(BackupRun.IMPORT, summary.appVersion)
                    null
                } else {
                    RestorePreview(uri, password, wipeFirst, summary)
                }
            } catch (e: Exception) {
                outcome = failure(BackupRun.IMPORT, e)
            } finally {
                busy = false
            }
        }
    }

    /** The user went ahead from the preview, possibly switching between replace and merge there. */
    fun confirmRestore(wipeFirst: Boolean) {
        val p = preview ?: return
        preview = null
        import(p.uri, p.password, wipeFirst)
    }

    fun cancelRestore() {
        preview = null
    }

    /** "Check a backup": decrypt every byte of the file and describe it, changing nothing (BKP-3). */
    fun check(uri: Uri, password: String) {
        viewModelScope.launch {
            busy = true
            running = BackupRun.CHECK
            outcome = null
            progress = BackupProgress(BackupStage.PREPARING)
            try {
                val summary = inspect(uri, password, fullCheck = true) { progress = it }
                outcome = if (summary.newerThanApp) newerVersionOutcome(BackupRun.CHECK, summary.appVersion) else BackupOutcome.Checked(summary)
            } catch (e: Exception) {
                outcome = failure(BackupRun.CHECK, e)
            } finally {
                progress = null
                busy = false
            }
        }
    }

    private suspend fun inspect(uri: Uri, password: String, fullCheck: Boolean, onProgress: (BackupProgress) -> Unit = {}): BackupSummary = context.contentResolver.openInputStream(uri)?.use { input -> backup.inspect(password, input, fullCheck, onProgress) }
        ?: throw IOException("Couldn't open the file")

    private fun newerVersionOutcome(run: BackupRun, madeWith: String?) = if (madeWith != null) {
        BackupOutcome.Failed(run, R.string.backup_error_newer_version_named, madeWith)
    } else {
        BackupOutcome.Failed(run, R.string.backup_error_newer_version)
    }

    private fun failure(run: BackupRun, e: Exception): BackupOutcome {
        Log.e("TRYSTIMPORT", "backup read failed: ${e.javaClass.name}", e)
        val be = e as? BackupException
        return if (be?.kind == BackupError.NEWER_VERSION) newerVersionOutcome(run, be.madeWith) else BackupOutcome.Failed(run, messageFor(e), e.message)
    }

    private fun import(uri: Uri, password: String, wipeFirst: Boolean) {
        viewModelScope.launch {
            busy = true
            running = BackupRun.IMPORT
            outcome = null
            progress = BackupProgress(BackupStage.PREPARING)
            try {
                val result = context.contentResolver.openInputStream(uri)?.use { input ->
                    backup.import(password, input, wipeFirst) { progress = it }
                } ?: throw IOException("Couldn't open the file")
                // The restore wrote the settings stores directly; refresh what the app holds in memory so the
                // restored theme, cycle switches and the rest apply now rather than after a restart.
                if (result.withSettings) preferenceStores.reloadAll()
                runCatching { colors.fillMissing() } // a restored backup may have people without a color (D-70)
                outcome = BackupOutcome.Restored(result)
            } catch (e: Exception) {
                outcome = failure(BackupRun.IMPORT, e)
            } finally {
                progress = null
                busy = false
            }
        }
    }

    private fun isDiskFull(e: Exception) = e.message?.contains("No space left", ignoreCase = true) == true ||
        e.message?.contains("ENOSPC", ignoreCase = true) == true

    private fun messageFor(e: Exception): Int = when ((e as? BackupException)?.kind) {
        BackupError.WRONG_PASSWORD -> R.string.backup_error_wrong_password
        BackupError.NOT_A_BACKUP -> R.string.backup_error_not_a_backup
        BackupError.NEWER_VERSION -> R.string.backup_error_newer_version
        BackupError.DAMAGED -> R.string.backup_error_damaged
        BackupError.DISK_FULL -> R.string.backup_error_disk_full
        BackupError.OTHER, null -> R.string.backup_error_import_other
    }
}

// ⏣ HW ⏣ · code · tools · homelab · ⏣ HW ⏣
