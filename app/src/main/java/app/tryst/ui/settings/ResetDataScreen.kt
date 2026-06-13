package app.tryst.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.tryst.R
import app.tryst.ui.common.adaptiveContentWidth
import app.tryst.ui.common.rememberHaptics
import app.tryst.ui.lock.LockViewModel

/**
 * The "Delete all data" flow, on its own destination off Settings → Danger zone (no longer a button
 * sitting one stray tap away in the main scroll). The erase button stays disabled until the user types
 * the confirmation word exactly — a deliberate, GitHub-style guard against accidental, unrecoverable
 * wipes. On confirm, [LockViewModel.deleteAllData] flips the session to NeedsSetup, which tears this
 * whole nav graph down back to first-run setup, so there's nothing to navigate afterward.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResetDataScreen(onBack: () -> Unit, viewModel: LockViewModel = hiltViewModel()) {
    val haptics = rememberHaptics()
    val confirmWord = stringResource(R.string.reset_confirm_word)
    var typed by remember { mutableStateOf("") }
    val matches = typed.trim().equals(confirmWord, ignoreCase = true)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.reset_title)) },
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
                .wrapContentWidth()
                .adaptiveContentWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                stringResource(R.string.reset_warning_headline),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error,
            )
            Text(
                stringResource(R.string.reset_message),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                stringResource(R.string.reset_confirm_instruction, confirmWord),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = typed,
                onValueChange = { typed = it },
                label = { Text(stringResource(R.string.reset_confirm_field_label, confirmWord)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    imeAction = ImeAction.Done,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = {
                    haptics.reject()
                    viewModel.deleteAllData()
                },
                enabled = matches,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.reset_confirm_button)) }
            Spacer(Modifier.height(8.dp))
        }
    }
}
