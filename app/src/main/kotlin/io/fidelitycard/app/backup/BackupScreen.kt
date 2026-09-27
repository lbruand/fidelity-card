@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package io.fidelitycard.app.backup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.fidelitycard.app.FidelityApplication
import io.fidelitycard.app.data.BackupImportOutcome

private const val BACKUP_MIME_TYPE = "application/octet-stream"
private const val BACKUP_FILE_NAME = "fidelity-card-backup.fcbk"

@Composable
private fun backupRepository() =
    (LocalContext.current.applicationContext as FidelityApplication).backupRepository

@Composable
fun BackupScreen(onBack: () -> Unit) {
    val repo = backupRepository()
    val viewModel: BackupViewModel = viewModel(factory = viewModelFactory { initializer { BackupViewModel(repo) } })
    val context = LocalContext.current

    var showExportDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var pendingExportBytes by remember { mutableStateOf<ByteArray?>(null) }
    var pendingImportBytes by remember { mutableStateOf<ByteArray?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    val createDocumentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(BACKUP_MIME_TYPE)) { uri ->
        val bytes = pendingExportBytes
        pendingExportBytes = null
        if (uri != null && bytes != null) {
            context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
            statusMessage = "Backup exported"
        }
    }

    val openDocumentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val bytes = uri?.let { context.contentResolver.openInputStream(it)?.use { stream -> stream.readBytes() } }
        if (bytes != null) {
            pendingImportBytes = bytes
            showImportDialog = true
        } else if (uri != null) {
            statusMessage = "Couldn't read that file"
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Backup & restore") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding: PaddingValues ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                "Export everything on this phone - businesses you run, and cards you collect - into one encrypted file you control.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.height(32.dp))

            Button(onClick = { showExportDialog = true }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text("Export backup")
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { openDocumentLauncher.launch(arrayOf("*/*")) },
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Text("Restore from backup")
            }

            statusMessage?.let { message ->
                Spacer(modifier = Modifier.height(24.dp))
                Text(message, style = MaterialTheme.typography.titleMedium)
            }
        }
    }

    if (showExportDialog) {
        PassphraseDialog(
            title = "Choose a passphrase",
            description = "You'll need this exact passphrase to restore this backup later - if you lose it, the backup can't be recovered.",
            confirmLabel = "Export",
            requireConfirmation = true,
            onDismiss = { showExportDialog = false },
            onConfirm = { passphrase ->
                showExportDialog = false
                viewModel.export(passphrase) { bytes ->
                    pendingExportBytes = bytes
                    createDocumentLauncher.launch(BACKUP_FILE_NAME)
                }
            },
        )
    }

    if (showImportDialog) {
        PassphraseDialog(
            title = "Enter the backup's passphrase",
            description = "Restoring replaces everything currently on this phone with what's in this backup. This can't be undone.",
            confirmLabel = "Restore",
            requireConfirmation = false,
            onDismiss = {
                showImportDialog = false
                pendingImportBytes = null
            },
            onConfirm = { passphrase ->
                showImportDialog = false
                val bytes = pendingImportBytes
                pendingImportBytes = null
                if (bytes != null) {
                    viewModel.import(bytes, passphrase) { outcome ->
                        statusMessage = when (outcome) {
                            BackupImportOutcome.Success -> "Backup restored"
                            is BackupImportOutcome.Failed -> outcome.reason
                        }
                    }
                }
            },
        )
    }
}

@Composable
private fun PassphraseDialog(
    title: String,
    description: String,
    confirmLabel: String,
    requireConfirmation: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (CharArray) -> Unit,
) {
    var passphrase by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    val mismatch = requireConfirmation && confirmation.isNotEmpty() && passphrase != confirmation
    val canConfirm = passphrase.isNotEmpty() && (!requireConfirmation || passphrase == confirmation)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(description, style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = passphrase,
                    onValueChange = { passphrase = it },
                    label = { Text("Passphrase") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (requireConfirmation) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = confirmation,
                        onValueChange = { confirmation = it },
                        label = { Text("Confirm passphrase") },
                        visualTransformation = PasswordVisualTransformation(),
                        isError = mismatch,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(passphrase.toCharArray()) }, enabled = canConfirm) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
