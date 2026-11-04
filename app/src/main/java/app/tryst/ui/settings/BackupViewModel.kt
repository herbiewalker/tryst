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
import app.tryst.core.session.SessionManager
import app.tryst.data.backup.BackupError
import app.tryst.data.backup.BackupException
import app.tryst.data.backup.BackupManager
import app.tryst.data.backup.BackupProgress
import app.tryst.data.backup.BackupStage
import app.tryst.data.backup.ExportResult
import app.tryst.data.backup.ImportResult
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** What to tell the user once an export or import has ended (QOL-9). */
sealed interface BackupOutcome {
    data class Exported(val result: ExportResult) : BackupOutcome

    data class Restored(val result: ImportResult) : BackupOutcome

    /** [messageRes] explains the cause; [detail] fills its `%1$s` when it has one. */
    data class Failed(val exporting: Boolean, val messageRes: Int, val detail: String? = null) : BackupOutcome
}

@HiltViewModel
class BackupViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val session: SessionManager,
    private val backup: BackupManager,
    private val generalPreferences: GeneralPreferences,
) : ViewModel() {

    /** When the last backup was exported from this install (BKP-2); null if never. */
    val lastBackupAt: StateFlow<Long?> = generalPreferences.lastBackupAt

    /** The summary or error shown after a run, until the user dismisses it. */
    var outcome by mutableStateOf<BackupOutcome?>(null)
        private set

    /** Live progress while an export or import runs; null when idle. */
    var progress by mutableStateOf<BackupProgress?>(null)
        private set

    /** True while an export (not an import) is the thing running, for the progress title. */
    var exporting by mutableStateOf(false)
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
            exporting = true
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
                    BackupOutcome.Failed(exporting = true, messageRes = R.string.backup_error_disk_full)
                } else {
                    BackupOutcome.Failed(exporting = true, messageRes = R.string.backup_error_export_other, detail = e.message)
                }
            } finally {
                progress = null
                busy = false
            }
        }
    }

    fun import(uri: Uri, password: String, wipeFirst: Boolean) {
        viewModelScope.launch {
            busy = true
            exporting = false
            outcome = null
            progress = BackupProgress(BackupStage.PREPARING)
            try {
                val result = context.contentResolver.openInputStream(uri)?.use { input ->
                    backup.import(password, input, wipeFirst) { progress = it }
                } ?: throw IOException("Couldn't open the file")
                outcome = BackupOutcome.Restored(result)
            } catch (e: Exception) {
                Log.e("TRYSTIMPORT", "import failed: ${e.javaClass.name}: ${e.message}", e)
                outcome = BackupOutcome.Failed(exporting = false, messageRes = messageFor(e), detail = e.message)
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
