package app.tryst.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tryst.R
import app.tryst.core.prefs.ThemeMode
import app.tryst.core.prefs.WeekStart
import app.tryst.ui.common.SingleSelectChips
import app.tryst.ui.common.adaptiveContentWidth
import app.tryst.ui.common.rememberHaptics
import app.tryst.ui.lock.BiometricPromptHelper
import app.tryst.ui.lock.LockViewModel
import app.tryst.ui.lock.findFragmentActivity
import java.time.LocalDate

// Auto-lock delay options (ms). 0 = lock immediately on background (default, strongest privacy).
private const val AUTO_LOCK_30S = 30_000L
private const val AUTO_LOCK_1M = 60_000L
private const val AUTO_LOCK_5M = 300_000L
private val AUTO_LOCK_OPTIONS = listOf(0L, AUTO_LOCK_30S, AUTO_LOCK_1M, AUTO_LOCK_5M)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onCustomizeInsights: () -> Unit = {},
    onOpenAbout: () -> Unit = {},
    onChangePin: () -> Unit = {},
    onOpenReset: () -> Unit = {},
    onOpenWhatsNew: () -> Unit = {},
    onOpenProfile: () -> Unit = {},
    viewModel: LockViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val activity = remember(context) { context.findFragmentActivity() }
    val biometricAvailable = remember { viewModel.canUseBiometrics() }
    var biometricEnabled by remember { mutableStateOf(viewModel.isBiometricEnabled()) }
    var showPositions by remember { mutableStateOf(false) }
    var showActs by remember { mutableStateOf(false) }
    var showKinks by remember { mutableStateOf(false) }
    val positionsViewModel: CustomPositionsViewModel = hiltViewModel()
    val actsViewModel: CustomActsViewModel = hiltViewModel()
    val kinksViewModel: CustomKinksViewModel = hiltViewModel()
    val appearanceViewModel: AppearanceViewModel = hiltViewModel()
    val themeMode by appearanceViewModel.themeMode.collectAsStateWithLifecycle()
    val dynamicColor by appearanceViewModel.dynamicColor.collectAsStateWithLifecycle()
    val generalViewModel: GeneralSettingsViewModel = hiltViewModel()
    val hapticsEnabled by generalViewModel.hapticsEnabled.collectAsStateWithLifecycle()
    val weekStart by generalViewModel.weekStart.collectAsStateWithLifecycle()
    val defaultToCalendar by generalViewModel.defaultToCalendar.collectAsStateWithLifecycle()
    val autoLockMs by generalViewModel.autoLockTimeoutMs.collectAsStateWithLifecycle()
    val backupViewModel: BackupViewModel = hiltViewModel()
    var showExportPw by remember { mutableStateOf(false) }
    var showImportPw by remember { mutableStateOf(false) }
    var pendingExportPassword by remember { mutableStateOf("") }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }
    val createBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        uri?.let { backupViewModel.export(it, pendingExportPassword) }
        pendingExportPassword = ""
    }
    val openBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            pendingImportUri = uri
            showImportPw = true
        }
    }
    val csvViewModel: CsvImportViewModel = hiltViewModel()
    val openCsv = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) csvViewModel.parse(uri)
    }

    val biometricEnableTitle = stringResource(R.string.settings_biometric_enable)
    val biometricEnableSubtitle = stringResource(R.string.settings_biometric_enable_subtitle)
    val biometricErrorFmt = stringResource(R.string.settings_biometric_error)
    val themeSystemLabel = stringResource(R.string.settings_theme_system)
    val themeLightLabel = stringResource(R.string.settings_theme_light)
    val themeDarkLabel = stringResource(R.string.settings_theme_dark)
    val weekStartSundayLabel = stringResource(R.string.settings_week_start_sunday)
    val weekStartMondayLabel = stringResource(R.string.settings_week_start_monday)
    val autoLockImmediateLabel = stringResource(R.string.settings_autolock_immediate)
    val autoLock30sLabel = stringResource(R.string.settings_autolock_30s)
    val autoLock1mLabel = stringResource(R.string.settings_autolock_1m)
    val autoLock5mLabel = stringResource(R.string.settings_autolock_5m)

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.settings_title)) }) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                // Cap + centre on wide windows so settings rows don't stretch (Pass 5); no-op on phones.
                .wrapContentWidth()
                .adaptiveContentWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.settings_general), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.settings_about_app_blurb),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedButton(onClick = onOpenProfile, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.settings_profile))
            }
            Text(
                stringResource(R.string.settings_profile_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedButton(onClick = onChangePin, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.settings_change_pin))
            }

            Text(stringResource(R.string.settings_autolock), style = MaterialTheme.typography.labelLarge)
            SingleSelectChips(
                options = AUTO_LOCK_OPTIONS,
                selected = autoLockMs,
                label = {
                    when (it) {
                        AUTO_LOCK_30S -> autoLock30sLabel
                        AUTO_LOCK_1M -> autoLock1mLabel
                        AUTO_LOCK_5M -> autoLock5mLabel
                        else -> autoLockImmediateLabel
                    }
                },
                onSelect = { generalViewModel.setAutoLockTimeoutMs(it) },
            )
            Text(
                stringResource(R.string.settings_autolock_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(
                        value = hapticsEnabled,
                        role = Role.Switch,
                        onValueChange = { generalViewModel.setHapticsEnabled(it) },
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Switch(checked = hapticsEnabled, onCheckedChange = null)
                Text(stringResource(R.string.settings_haptics), style = MaterialTheme.typography.bodyMedium)
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(
                        value = defaultToCalendar,
                        role = Role.Switch,
                        onValueChange = { generalViewModel.setDefaultToCalendar(it) },
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Switch(checked = defaultToCalendar, onCheckedChange = null)
                Text(stringResource(R.string.settings_default_calendar), style = MaterialTheme.typography.bodyMedium)
            }

            Text(stringResource(R.string.settings_week_start), style = MaterialTheme.typography.labelLarge)
            SingleSelectChips(
                options = WeekStart.entries,
                selected = weekStart,
                label = {
                    when (it) {
                        WeekStart.SUNDAY -> weekStartSundayLabel
                        WeekStart.MONDAY -> weekStartMondayLabel
                    }
                },
                onSelect = { generalViewModel.setWeekStart(it) },
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            Text(stringResource(R.string.settings_security), style = MaterialTheme.typography.titleMedium)

            when {
                !biometricAvailable -> Text(
                    stringResource(R.string.settings_biometric_unavailable),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                biometricEnabled -> OutlinedButton(
                    onClick = {
                        viewModel.disableBiometric()
                        biometricEnabled = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.settings_biometric_disable)) }

                else -> Button(
                    onClick = {
                        val cipher = try {
                            viewModel.biometricEncryptCipher()
                        } catch (e: Exception) {
                            viewModel.reportError(biometricErrorFmt.format(e.message))
                            return@Button
                        }
                        BiometricPromptHelper.authenticate(
                            activity = activity,
                            cipher = cipher,
                            title = biometricEnableTitle,
                            subtitle = biometricEnableSubtitle,
                            onSuccess = { authed -> if (viewModel.enableBiometric(authed)) biometricEnabled = true },
                            onError = { viewModel.reportError(it) },
                            onCancel = { },
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.settings_biometric_enable)) }
            }

            OutlinedButton(
                onClick = {
                    haptics.tick()
                    viewModel.lock()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.settings_lock_now))
            }

            AnimatedVisibility(visible = viewModel.error != null) {
                viewModel.error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            Text(stringResource(R.string.settings_appearance), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.labelLarge)
            SingleSelectChips(
                options = ThemeMode.entries,
                selected = themeMode,
                label = {
                    when (it) {
                        ThemeMode.SYSTEM -> themeSystemLabel
                        ThemeMode.LIGHT -> themeLightLabel
                        ThemeMode.DARK -> themeDarkLabel
                    }
                },
                onSelect = { appearanceViewModel.setThemeMode(it) },
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(
                        value = dynamicColor,
                        role = Role.Switch,
                        onValueChange = { appearanceViewModel.setDynamicColor(it) },
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Switch(checked = dynamicColor, onCheckedChange = null)
                Text(stringResource(R.string.settings_material_you), style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                stringResource(R.string.settings_material_you_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            Text(stringResource(R.string.settings_insights), style = MaterialTheme.typography.titleMedium)
            OutlinedButton(onClick = onCustomizeInsights, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.settings_customize_insights))
            }
            Text(
                stringResource(R.string.settings_insights_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            Text(stringResource(R.string.settings_categories), style = MaterialTheme.typography.titleMedium)
            OutlinedButton(onClick = { showPositions = true }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.settings_manage_positions))
            }
            OutlinedButton(onClick = { showActs = true }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.settings_manage_acts))
            }
            OutlinedButton(onClick = { showKinks = true }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.settings_manage_kinks))
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            Text(stringResource(R.string.settings_backup), style = MaterialTheme.typography.titleMedium)
            OutlinedButton(
                onClick = { showExportPw = true },
                enabled = !backupViewModel.busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.settings_export)) }
            OutlinedButton(
                onClick = {
                    backupViewModel.suppressAutoLock()
                    openBackup.launch(arrayOf("*/*"))
                },
                enabled = !backupViewModel.busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.settings_import)) }
            Text(
                stringResource(R.string.settings_backup_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AnimatedVisibility(visible = backupViewModel.status != null) {
                backupViewModel.status?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }
            OutlinedButton(
                onClick = {
                    csvViewModel.suppressAutoLock()
                    openCsv.launch(arrayOf("*/*"))
                },
                enabled = !csvViewModel.busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.settings_import_csv)) }
            Text(
                stringResource(R.string.settings_csv_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AnimatedVisibility(visible = csvViewModel.status != null) {
                csvViewModel.status?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            Text(stringResource(R.string.settings_danger_zone), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
            OutlinedButton(
                onClick = onOpenReset,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.settings_delete_all)) }
            Text(
                stringResource(R.string.settings_delete_all_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            Text(stringResource(R.string.settings_about), style = MaterialTheme.typography.titleMedium)
            OutlinedButton(onClick = onOpenWhatsNew, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.settings_whats_new))
            }
            Text(
                stringResource(R.string.settings_whats_new_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = onOpenAbout, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.settings_about_button))
            }
            Text(
                stringResource(R.string.settings_about_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (showPositions) {
        CustomPositionsDialog(viewModel = positionsViewModel, onDismiss = { showPositions = false })
    }

    if (showActs) {
        CustomActsDialog(viewModel = actsViewModel, onDismiss = { showActs = false })
    }

    if (showKinks) {
        CustomKinksDialog(viewModel = kinksViewModel, onDismiss = { showKinks = false })
    }

    if (showExportPw) {
        BackupPasswordDialog(
            title = stringResource(R.string.settings_backup_pw_set_title),
            requireConfirm = true,
            onConfirm = { pw ->
                showExportPw = false
                pendingExportPassword = pw
                backupViewModel.suppressAutoLock()
                createBackup.launch("tryst-backup-${LocalDate.now()}.tryst")
            },
            onDismiss = { showExportPw = false },
        )
    }

    if (showImportPw) {
        BackupPasswordDialog(
            title = stringResource(R.string.settings_backup_pw_enter_title),
            requireConfirm = false,
            onConfirm = { pw ->
                showImportPw = false
                pendingImportUri?.let { backupViewModel.import(it, pw) }
                pendingImportUri = null
            },
            onDismiss = {
                showImportPw = false
                pendingImportUri = null
            },
        )
    }

    if (csvViewModel.showMapping) {
        CsvMappingDialog(
            headers = csvViewModel.headers,
            rowCount = csvViewModel.rowCount,
            mapping = csvViewModel.mapping,
            busy = csvViewModel.busy,
            onSet = { field, col -> csvViewModel.setMapping(field, col) },
            onImport = { csvViewModel.import() },
            onDismiss = { csvViewModel.cancel() },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CsvMappingDialog(
    headers: List<String>,
    rowCount: Int,
    mapping: Map<CsvField, Int?>,
    busy: Boolean,
    onSet: (CsvField, Int?) -> Unit,
    onImport: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.csv_map_title)) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    stringResource(R.string.csv_rows_found, rowCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                CsvField.entries.forEach { field ->
                    CsvFieldRow(field, headers, mapping[field]) { onSet(field, it) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onImport, enabled = !busy && mapping[CsvField.DATE] != null) { Text(stringResource(R.string.csv_import)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun CsvFieldRow(field: CsvField, headers: List<String>, selected: Int?, onSet: (Int?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            field.label + if (field.required) " *" else "",
            style = MaterialTheme.typography.bodyMedium,
        )
        Box {
            OutlinedButton(onClick = { open = true }) {
                Text(selected?.let { idx -> headers.getOrNull(idx)?.ifBlank { stringResource(R.string.csv_column_n, idx + 1) } } ?: "—")
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.csv_none)) }, onClick = {
                    onSet(null)
                    open = false
                })
                headers.forEachIndexed { i, h ->
                    DropdownMenuItem(
                        text = { Text(h.ifBlank { stringResource(R.string.csv_column_n, i + 1) }) },
                        onClick = {
                            onSet(i)
                            open = false
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BackupPasswordDialog(
    title: String,
    requireConfirm: Boolean,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    val mismatch = requireConfirm && confirm.isNotEmpty() && password != confirm
    val valid = password.length >= 6 && (!requireConfirm || password == confirm)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.settings_password_label)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (requireConfirm) {
                    OutlinedTextField(
                        value = confirm,
                        onValueChange = { confirm = it },
                        label = { Text(stringResource(R.string.settings_confirm_password_label)) },
                        singleLine = true,
                        isError = mismatch,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        stringResource(if (mismatch) R.string.settings_password_mismatch else R.string.settings_password_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (mismatch) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(password) }, enabled = valid) { Text(stringResource(R.string.action_ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun CustomActsDialog(viewModel: CustomActsViewModel, onDismiss: () -> Unit) {
    val acts by viewModel.customActs.collectAsStateWithLifecycle()
    CustomCatalogDialog(
        title = R.string.custom_acts_title,
        description = R.string.custom_acts_desc,
        addLabel = R.string.custom_acts_add_label,
        emptyText = R.string.custom_acts_empty,
        entries = acts.map { it.id to it.label },
        onAdd = viewModel::add,
        onRename = viewModel::rename,
        onDelete = viewModel::delete,
        onDismiss = onDismiss,
    )
}

/**
 * One shared manage-custom-entries dialog for acts / kinks / positions: add, rename in place
 * (the id — and so every encounter ref — is untouched), and remove. [entries] is id → label.
 */
@Composable
private fun CustomCatalogDialog(
    @StringRes title: Int,
    @StringRes description: Int,
    @StringRes addLabel: Int,
    @StringRes emptyText: Int,
    entries: List<Pair<String, String>>,
    onAdd: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var newLabel by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(description),
                    style = MaterialTheme.typography.bodySmall,
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
                if (entries.isEmpty()) {
                    Text(
                        stringResource(emptyText),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Column(
                        modifier = Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        entries.forEach { (id, label) ->
                            key(id) {
                                CustomEntryRow(
                                    label = label,
                                    onRename = { onRename(id, it) },
                                    onDelete = { onDelete(id) },
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_done)) } },
    )
}

@Composable
private fun CustomEntryRow(label: String, onRename: (String) -> Unit, onDelete: () -> Unit) {
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

@Composable
private fun CustomKinksDialog(viewModel: CustomKinksViewModel, onDismiss: () -> Unit) {
    val kinks by viewModel.customKinks.collectAsStateWithLifecycle()
    CustomCatalogDialog(
        title = R.string.custom_kinks_title,
        description = R.string.custom_kinks_desc,
        addLabel = R.string.custom_kinks_add_label,
        emptyText = R.string.custom_kinks_empty,
        entries = kinks.map { it.id to it.label },
        onAdd = viewModel::add,
        onRename = viewModel::rename,
        onDelete = viewModel::delete,
        onDismiss = onDismiss,
    )
}

@Composable
private fun CustomPositionsDialog(viewModel: CustomPositionsViewModel, onDismiss: () -> Unit) {
    val positions by viewModel.customPositions.collectAsStateWithLifecycle()
    CustomCatalogDialog(
        title = R.string.custom_positions_title,
        description = R.string.custom_positions_desc,
        addLabel = R.string.custom_positions_add_label,
        emptyText = R.string.custom_positions_empty,
        entries = positions.map { it.id to it.label },
        onAdd = viewModel::add,
        onRename = viewModel::rename,
        onDelete = viewModel::delete,
        onDismiss = onDismiss,
    )
}
