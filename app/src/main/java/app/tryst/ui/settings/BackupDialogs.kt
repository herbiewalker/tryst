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
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.tryst.R
import app.tryst.data.backup.BackupProgress
import app.tryst.data.backup.BackupStage
import app.tryst.data.backup.BackupSummary
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Shown while an export or import runs (QOL-9): what stage it is at, a real bar once the photo count is known, and a
 * reminder to keep the app open. The screen is held awake for the duration so a long restore is never interrupted.
 */
@Composable
fun BackupProgressDialog(run: BackupRun, progress: BackupProgress) {
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    AlertDialog(
        onDismissRequest = {}, // not dismissible: leaving mid-way would only hide that it is still working
        title = {
            Text(
                stringResource(
                    when (run) {
                        BackupRun.EXPORT -> R.string.backup_progress_export_title
                        BackupRun.IMPORT -> R.string.backup_progress_import_title
                        BackupRun.CHECK -> R.string.backup_progress_check_title
                    },
                ),
            )
        },
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
            val parts = buildList {
                add(stringResource(R.string.backup_result_import_body, r.trysts, r.partners, r.photos))
                if (r.periods > 0) add(pluralStringResource(R.plurals.backup_result_import_periods, r.periods, r.periods))
                if (r.withSettings) add(stringResource(R.string.backup_result_import_settings))
            }
            stringResource(R.string.backup_result_import_title) to parts.joinToString(" ")
        }
        is BackupOutcome.Checked -> stringResource(R.string.backup_check_ok_title) to summaryText(outcome.summary, checked = true)
        is BackupOutcome.Failed -> {
            val title = stringResource(
                when (outcome.run) {
                    BackupRun.EXPORT -> R.string.backup_result_export_fail_title
                    BackupRun.IMPORT -> R.string.backup_result_import_fail_title
                    BackupRun.CHECK -> R.string.backup_check_fail_title
                },
            )
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

/** Describes a backup in a sentence or three: who made it and when, what's in it, and for a check, that it read cleanly. */
@Composable
private fun summaryText(s: BackupSummary, checked: Boolean): String = buildList {
    val made = s.createdAt?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate().format(madeOnFormatter) }
    add(
        when {
            made != null && s.appVersion != null -> stringResource(R.string.backup_summary_made, made, s.appVersion)
            s.appVersion != null -> stringResource(R.string.backup_summary_made_version, s.appVersion)
            else -> stringResource(R.string.backup_summary_made_unknown)
        },
    )
    add(stringResource(R.string.backup_summary_counts, s.trysts, s.partners, s.photos))
    if (s.periods > 0) add(pluralStringResource(R.plurals.backup_summary_periods, s.periods, s.periods))
    if (s.hasSettings) add(stringResource(R.string.backup_summary_settings))
    if (checked) add(stringResource(R.string.backup_check_ok_body, s.photosFound ?: 0))
}.joinToString(" ")

private val madeOnFormatter: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

/** What a "replace" would delete for good, in words: "12 periods, 3 album photos". */
@Composable
private fun lossText(removals: Map<String, Int>): String = buildList {
    removals["cycle_period"]?.let { add(pluralStringResource(R.plurals.backup_loss_periods, it, it)) }
    removals["cycle_symptom"]?.let { add(pluralStringResource(R.plurals.backup_loss_symptoms, it, it)) }
    removals["person_photo"]?.let { add(pluralStringResource(R.plurals.backup_loss_album_photos, it, it)) }
    removals["orientations"]?.let { add(pluralStringResource(R.plurals.backup_loss_orientations, it, it)) }
    removals["profile"]?.let { add(stringResource(R.string.backup_loss_profile)) }
    removals.filterKeys { it in CATALOG_TABLES }.values.sum().takeIf { it > 0 }
        ?.let { add(pluralStringResource(R.plurals.backup_loss_categories, it, it)) }
    // Days belong to their periods; anything else this list doesn't name yet still gets a mention.
    if (removals.keys.any { it !in NAMED_LOSSES }) add(stringResource(R.string.backup_loss_other))
}.joinToString(", ")

// The user-built category lists (D-41). A restore re-adopts the ones its own trysts use, so name them plainly.
private val CATALOG_TABLES = setOf("acts", "kinks", "positions", "toys", "occasions", "ejaculation_locations", "orgasm_types", "locations", "tags")

// Rows that only exist as part of a period; losing them is already said by "N periods".
private val PERIOD_CHILDREN = setOf("cycle_day", "cycle_symptom_log")

private val NAMED_LOSSES = setOf("cycle_period", "cycle_day", "cycle_symptom", "cycle_symptom_log", "person_photo", "orientations", "profile") + CATALOG_TABLES

/**
 * The go-ahead for a restore (BKP-3): what the backup holds and what the chosen mode will do. When a "replace"
 * would delete data the backup predates, it says what and offers to merge instead.
 */
@Composable
fun RestorePreviewDialog(preview: RestorePreview, onRestore: (wipeFirst: Boolean) -> Unit, onCancel: () -> Unit) {
    val s = preview.summary
    val losing = preview.wipeFirst && s.replaceWouldRemove.keys.any { it !in PERIOD_CHILDREN }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.backup_preview_title)) },
        text = {
            Column {
                Text(summaryText(s, checked = false))
                Text(
                    stringResource(if (preview.wipeFirst) R.string.backup_preview_replace else R.string.backup_preview_merge),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
                when {
                    losing -> Text(
                        stringResource(R.string.backup_preview_loss, lossText(s.replaceWouldRemove)),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    preview.wipeFirst && s.olderThanApp -> Text(
                        stringResource(R.string.backup_preview_older),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
        },
        confirmButton = {
            if (losing) {
                TextButton(onClick = { onRestore(true) }) {
                    Text(stringResource(R.string.backup_preview_replace_anyway), color = MaterialTheme.colorScheme.error)
                }
            } else {
                TextButton(onClick = { onRestore(preview.wipeFirst) }) { Text(stringResource(R.string.backup_preview_restore)) }
            }
        },
        dismissButton = {
            Row {
                if (losing) TextButton(onClick = { onRestore(false) }) { Text(stringResource(R.string.backup_preview_merge_instead)) }
                TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
            }
        },
    )
}

// ⏣ HW ⏣ · code · tools · homelab · ⏣ HW ⏣
