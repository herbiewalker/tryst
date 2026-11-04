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

import android.text.format.Formatter
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.tryst.R
import app.tryst.data.backup.BackupProgress
import app.tryst.data.backup.BackupStage

/**
 * Shown while an export or import runs (QOL-9): what stage it is at, a real bar once the photo count is known, and a
 * reminder to keep the app open. The screen is held awake for the duration so a long restore is never interrupted.
 */
@Composable
fun BackupProgressDialog(exporting: Boolean, progress: BackupProgress) {
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    AlertDialog(
        onDismissRequest = {}, // not dismissible: leaving mid-way would only hide that it is still working
        title = { Text(stringResource(if (exporting) R.string.backup_progress_export_title else R.string.backup_progress_import_title)) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Text(stageText(progress), style = MaterialTheme.typography.bodyLarge)
                val total = progress.total
                if (progress.stage == BackupStage.PHOTOS && total != null && total > 0) {
                    LinearProgressIndicator(
                        progress = { (progress.done.toFloat() / total).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 16.dp))
                }
                Text(
                    stringResource(R.string.backup_progress_keep_open),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
        },
        confirmButton = {},
    )
}

@Composable
private fun stageText(progress: BackupProgress): String = when (progress.stage) {
    BackupStage.PREPARING -> stringResource(R.string.backup_stage_preparing)
    BackupStage.DATABASE -> stringResource(R.string.backup_stage_database)
    BackupStage.PHOTOS -> progress.total?.takeIf { it > 0 }
        ?.let { stringResource(R.string.backup_stage_photos_known, progress.done, it) }
        ?: stringResource(R.string.backup_stage_photos_unknown, progress.done)
    BackupStage.FINISHING -> stringResource(R.string.backup_stage_finishing)
}

/** The summary or the specific error once a run has ended (QOL-9). */
@Composable
fun BackupOutcomeDialog(outcome: BackupOutcome, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val (title, body) = when (outcome) {
        is BackupOutcome.Exported -> {
            val r = outcome.result
            stringResource(R.string.backup_result_export_title) to
                stringResource(R.string.backup_result_export_body, r.trysts, r.partners, r.photos, Formatter.formatShortFileSize(context, r.bytes))
        }
        is BackupOutcome.Restored -> {
            val r = outcome.result
            val base = stringResource(R.string.backup_result_import_body, r.trysts, r.partners, r.photos)
            stringResource(R.string.backup_result_import_title) to
                if (r.withSettings) base + " " + stringResource(R.string.backup_result_import_settings) else base
        }
        is BackupOutcome.Failed -> {
            val title = stringResource(if (outcome.exporting) R.string.backup_result_export_fail_title else R.string.backup_result_import_fail_title)
            title to (outcome.detail?.let { stringResource(outcome.messageRes, it) } ?: stringResource(outcome.messageRes))
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_ok)) } },
    )
}

// ⏣ HW ⏣ · code · tools · homelab · ⏣ HW ⏣
