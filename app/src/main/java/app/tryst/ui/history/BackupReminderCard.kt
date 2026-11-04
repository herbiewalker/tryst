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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.tryst.R
import app.tryst.data.backup.BackupNudge

/**
 * The "back up your data" reminder at the top of the Trysts tab (BKP-2). Shows only when [nudge] says so; "Later" snoozes
 * it for a week so it never nags. Backups are manual by design, so this is the only prompt the app gives.
 */
@Composable
fun BackupReminderCard(nudge: BackupNudge, onBackUp: () -> Unit, onLater: () -> Unit, modifier: Modifier = Modifier) {
    if (nudge is BackupNudge.None) return
    Card(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = when (nudge) {
                    BackupNudge.Never -> stringResource(R.string.backup_reminder_never_title)
                    is BackupNudge.Stale -> pluralStringResource(R.plurals.backup_reminder_stale_title, nudge.days, nudge.days)
                    BackupNudge.None -> ""
                },
                style = MaterialTheme.typography.titleSmall,
            )
            Text(stringResource(R.string.backup_reminder_body), style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = onBackUp) { Text(stringResource(R.string.backup_reminder_action)) }
                TextButton(onClick = onLater) { Text(stringResource(R.string.backup_reminder_later)) }
            }
        }
    }
}

// ⏣ HW ⏣ · code · tools · homelab · ⏣ HW ⏣
