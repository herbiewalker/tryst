package app.tryst.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tryst.R
import app.tryst.ui.common.adaptiveContentWidth

/**
 * Dispatches the parameterized manage-category route to the matching category's ViewModel and the
 * shared [CatalogManageScreen]. Each category edits its own custom-entry table (add / rename in place /
 * remove); the two shipped seed entries per seeded category are built-in and simply aren't listed here.
 */
@Composable
fun CatalogManageRoute(category: String?, onBack: () -> Unit) {
    when (category) {
        CatalogCategory.ACTS -> {
            val vm: CustomActsViewModel = hiltViewModel()
            val rows by vm.customActs.collectAsStateWithLifecycle()
            CatalogManageScreen(
                R.string.custom_acts_title, R.string.custom_acts_desc, R.string.custom_acts_add_label, R.string.custom_acts_empty,
                rows.map { it.id to it.label }, vm::add, vm::rename, vm::delete, onBack,
            )
        }
        CatalogCategory.KINKS -> {
            val vm: CustomKinksViewModel = hiltViewModel()
            val rows by vm.customKinks.collectAsStateWithLifecycle()
            CatalogManageScreen(
                R.string.custom_kinks_title, R.string.custom_kinks_desc, R.string.custom_kinks_add_label, R.string.custom_kinks_empty,
                rows.map { it.id to it.label }, vm::add, vm::rename, vm::delete, onBack,
            )
        }
        CatalogCategory.POSITIONS -> {
            val vm: CustomPositionsViewModel = hiltViewModel()
            val rows by vm.customPositions.collectAsStateWithLifecycle()
            CatalogManageScreen(
                R.string.custom_positions_title, R.string.custom_positions_desc, R.string.custom_positions_add_label, R.string.custom_positions_empty,
                rows.map { it.id to it.label }, vm::add, vm::rename, vm::delete, onBack,
            )
        }
        CatalogCategory.TOYS -> {
            val vm: CustomToysViewModel = hiltViewModel()
            val rows by vm.customToys.collectAsStateWithLifecycle()
            CatalogManageScreen(
                R.string.custom_toys_title, R.string.custom_toys_desc, R.string.custom_toys_add_label, R.string.custom_toys_empty,
                rows.map { it.id to it.label }, vm::add, vm::rename, vm::delete, onBack,
            )
        }
        CatalogCategory.OCCASIONS -> {
            val vm: CustomOccasionsViewModel = hiltViewModel()
            val rows by vm.customOccasions.collectAsStateWithLifecycle()
            CatalogManageScreen(
                R.string.custom_occasions_title, R.string.custom_occasions_desc, R.string.custom_occasions_add_label, R.string.custom_occasions_empty,
                rows.map { it.id to it.label }, vm::add, vm::rename, vm::delete, onBack,
            )
        }
        CatalogCategory.EJACULATION -> {
            val vm: CustomEjaculationsViewModel = hiltViewModel()
            val rows by vm.customEjaculations.collectAsStateWithLifecycle()
            CatalogManageScreen(
                R.string.custom_ejaculation_title, R.string.custom_ejaculation_desc, R.string.custom_ejaculation_add_label, R.string.custom_ejaculation_empty,
                rows.map { it.id to it.label }, vm::add, vm::rename, vm::delete, onBack,
            )
        }
        else -> onBack()
    }
}

/**
 * One full-screen category-management page: a header blurb, an add-entry row, and the list of the
 * user's custom entries with in-place rename (the id — and so every encounter ref — is untouched) and
 * remove. [entries] is id → label.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CatalogManageScreen(
    @StringRes title: Int,
    @StringRes description: Int,
    @StringRes addLabel: Int,
    @StringRes emptyText: Int,
    entries: List<Pair<String, String>>,
    onAdd: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onBack: () -> Unit,
) {
    var newLabel by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .adaptiveContentWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = newLabel,
                    onValueChange = { newLabel = it },
                    label = { Text(stringResource(addLabel)) },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = {
                        onAdd(newLabel)
                        newLabel = ""
                    },
                    enabled = newLabel.isNotBlank(),
                ) { Text(stringResource(R.string.action_add)) }
            }

            HorizontalDivider()

            if (entries.isEmpty()) {
                Text(
                    stringResource(emptyText),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                entries.forEach { (id, label) ->
                    key(id) {
                        CatalogEntryRow(
                            label = label,
                            onRename = { onRename(id, it) },
                            onDelete = { onDelete(id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CatalogEntryRow(label: String, onRename: (String) -> Unit, onDelete: () -> Unit) {
    var editing by remember { mutableStateOf(false) }
    var editLabel by remember { mutableStateOf("") }

    if (editing) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = editLabel,
                onValueChange = { editLabel = it },
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Done),
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = {
                    onRename(editLabel)
                    editing = false
                },
                enabled = editLabel.isNotBlank(),
            ) { Text(stringResource(R.string.action_save)) }
            TextButton(onClick = { editing = false }) { Text(stringResource(R.string.action_cancel)) }
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = {
                editLabel = label
                editing = true
            }) { Text(stringResource(R.string.action_rename)) }
            TextButton(onClick = onDelete) { Text(stringResource(R.string.action_remove)) }
        }
    }
}
