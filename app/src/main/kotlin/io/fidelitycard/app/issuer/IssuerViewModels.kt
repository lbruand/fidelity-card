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
    fun createProgram(name: String, threshold: Int, reward: String, color: Int, icon: String, onCreated: (String) -> Unit) {
        viewModelScope.launch {
            val program = repository.createProgram(name, threshold, reward, color, icon)
            onCreated(program.programId)
        }
    }
}

/** What the business detail screen's "Give a stamp"/"Scan a customer" actions are currently showing. */
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

    /** "Give a stamp": mints and shows it immediately, no scan needed first (SPEC/SPECS.md §6.2). */
    fun giveStamp() {
        viewModelScope.launch {
            _scanState.value = when (val bytes = repository.mintStamp(programId)) {
                null -> ScanCustomerState.Failed("This business could not be found")
                else -> ScanCustomerState.Responding(bytes, "Show this to your customer")
            }
        }
    }

    fun onCustomerScanned(bytes: ByteArray?) {
        if (bytes == null) {
            _scanState.value = ScanCustomerState.Idle
            return
        }
        viewModelScope.launch {
            _scanState.value = when (val outcome = repository.handleCustomerMessage(programId, bytes)) {
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
