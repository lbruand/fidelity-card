package io.fidelitycard.app.backup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.fidelitycard.app.data.BackupImportOutcome
import io.fidelitycard.app.data.BackupRepository
import kotlinx.coroutines.launch

class BackupViewModel(private val repository: BackupRepository) : ViewModel() {

    fun export(passphrase: CharArray, onExported: (ByteArray) -> Unit) {
        viewModelScope.launch {
            onExported(repository.exportEncrypted(passphrase))
        }
    }

    fun import(envelope: ByteArray, passphrase: CharArray, onResult: (BackupImportOutcome) -> Unit) {
        viewModelScope.launch {
            onResult(repository.importEncrypted(envelope, passphrase))
        }
    }
}
