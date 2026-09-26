package io.fidelitycard.app.issuer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.fidelitycard.app.data.IssuerRepository
import io.fidelitycard.app.data.IssuerScanOutcome
import io.fidelitycard.app.data.ProgramSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BusinessListViewModel(repository: IssuerRepository) : ViewModel() {
    val programs: StateFlow<List<ProgramSummary>> = repository.observePrograms()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

class CreateBusinessViewModel(private val repository: IssuerRepository) : ViewModel() {
    fun createProgram(name: String, threshold: Int, reward: String, onCreated: (String) -> Unit) {
        viewModelScope.launch {
            val program = repository.createProgram(name, threshold, reward)
            onCreated(program.programId)
        }
    }
}

/** What the "Scan a customer" button is currently doing. */
sealed interface ScanCustomerState {
    data object Idle : ScanCustomerState
    data class Responding(val bytes: ByteArray, val message: String) : ScanCustomerState
    data class Failed(val message: String) : ScanCustomerState
}

class BusinessDetailViewModel(
    private val repository: IssuerRepository,
    private val programId: String,
) : ViewModel() {

    private val _program = MutableStateFlow<ProgramSummary?>(null)
    val program: StateFlow<ProgramSummary?> = _program

    private val _scanState = MutableStateFlow<ScanCustomerState>(ScanCustomerState.Idle)
    val scanState: StateFlow<ScanCustomerState> = _scanState

    init {
        viewModelScope.launch { _program.value = repository.findProgram(programId) }
    }

    fun onCustomerScanned(bytes: ByteArray?) {
        if (bytes == null) {
            _scanState.value = ScanCustomerState.Idle
            return
        }
        viewModelScope.launch {
            _scanState.value = when (val outcome = repository.handleCustomerMessage(programId, bytes)) {
                is IssuerScanOutcome.Enrolled ->
                    ScanCustomerState.Responding(outcome.responseBytes, "New card created - show this back to your customer")
                is IssuerScanOutcome.Stamped ->
                    ScanCustomerState.Responding(outcome.responseBytes, "Stamp added! Show this back to your customer")
                is IssuerScanOutcome.Redeemed ->
                    ScanCustomerState.Responding(outcome.responseBytes, "Reward redeemed! Show this back to your customer")
                is IssuerScanOutcome.Failed -> ScanCustomerState.Failed(outcome.message)
            }
        }
    }

    fun dismissResponse() {
        _scanState.value = ScanCustomerState.Idle
    }
}
