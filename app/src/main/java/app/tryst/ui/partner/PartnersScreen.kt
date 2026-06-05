package app.tryst.ui.partner

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tryst.data.db.entity.PartnerEntity
import app.tryst.ui.common.Format

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PartnersScreen(viewModel: PartnersViewModel = hiltViewModel()) {
    val partners by viewModel.partners.collectAsStateWithLifecycle()
    var dialogTarget by remember { mutableStateOf<DialogTarget?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Partners") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { dialogTarget = DialogTarget(null) }) {
                Text("+", style = MaterialTheme.typography.headlineMedium)
            }
        },
    ) { padding ->
        if (partners.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(
                    "No partners yet.\nTap + to add one.",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(partners, key = { it.id }) { partner ->
                    PartnerRow(
                        partner = partner,
                        onEdit = { dialogTarget = DialogTarget(partner) },
                        onArchive = { viewModel.archive(partner.id) },
                    )
                }
            }
        }
    }

    dialogTarget?.let { target ->
        PartnerDialog(
            initial = target.partner,
            onDismiss = { dialogTarget = null },
            onSave = { name, anonymous, note ->
                viewModel.save(target.partner?.id, name, anonymous, note)
                dialogTarget = null
            },
        )
    }
}

private data class DialogTarget(val partner: PartnerEntity?)

@Composable
private fun PartnerRow(partner: PartnerEntity, onEdit: () -> Unit, onArchive: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onEdit)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(Format.partnerName(partner), style = MaterialTheme.typography.titleMedium)
                partner.note?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            TextButton(onClick = onArchive) { Text("Archive") }
        }
    }
}

@Composable
private fun PartnerDialog(
    initial: PartnerEntity?,
    onDismiss: () -> Unit,
    onSave: (name: String, anonymous: Boolean, note: String) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.displayName ?: "") }
    var anonymous by remember { mutableStateOf(initial?.isAnonymous ?: false) }
    var note by remember { mutableStateOf(initial?.note ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add partner" else "Edit partner") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    enabled = !anonymous,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = anonymous, onCheckedChange = { anonymous = it })
                    Text("  Anonymous", style = MaterialTheme.typography.bodyMedium)
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name, anonymous, note) },
                enabled = anonymous || name.isNotBlank(),
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
