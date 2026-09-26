package io.fidelitycard.app.collector

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.fidelitycard.app.data.CardSummary
import io.fidelitycard.app.data.CollectorRepository
import io.fidelitycard.app.data.PendingJoin
import io.fidelitycard.app.data.RedemptionAcceptOutcome
import io.fidelitycard.app.data.StampAcceptOutcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CardListViewModel(repository: CollectorRepository) : ViewModel() {
    val cards: StateFlow<List<CardSummary>> = repository.observeCards()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

class CardDetailViewModel(private val repository: CollectorRepository, private val cardId: String) : ViewModel() {
    val card: StateFlow<CardSummary?> = repository.observeCard(cardId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun leaveBusiness(onLeft: () -> Unit) {
        viewModelScope.launch {
            repository.leaveBusiness(cardId)
            onLeft()
        }
    }
}

/** Joining a business is its own little state machine because it carries a not-yet-persisted [PendingJoin] between its two scans. */
sealed interface JoinState {
    data object ScanProgram : JoinState
    data class ShowJoinRequest(val pending: PendingJoin, val requestBytes: ByteArray) : JoinState
    data class ScanReply(val pending: PendingJoin) : JoinState
    data class Done(val cardId: String, val programName: String) : JoinState
    data class Failed(val message: String) : JoinState
}

class JoinFlowViewModel(private val repository: CollectorRepository) : ViewModel() {

    private val _state = MutableStateFlow<JoinState>(JoinState.ScanProgram)
    val state: StateFlow<JoinState> = _state

    fun onNextTapped() {
        val current = _state.value
        if (current is JoinState.ShowJoinRequest) {
            _state.value = JoinState.ScanReply(current.pending)
        }
    }

    fun onScanned(bytes: ByteArray?) {
        if (bytes == null) return
        when (val current = _state.value) {
            JoinState.ScanProgram -> {
                val program = repository.parseProgramQr(bytes)
                _state.value = if (program != null) {
                    val (pending, requestBytes) = repository.buildJoinRequest(program)
                    JoinState.ShowJoinRequest(pending, requestBytes)
                } else {
                    JoinState.Failed("That doesn't look like a business code")
                }
            }
            is JoinState.ScanReply -> {
                viewModelScope.launch {
                    val summary = repository.completeJoin(current.pending, bytes)
                    _state.value = if (summary != null) {
                        JoinState.Done(summary.cardId, summary.programName)
                    } else {
                        JoinState.Failed("That confirmation didn't match - ask the business to try again")
                    }
                }
            }
            else -> Unit
        }
    }

    fun retry() {
        _state.value = JoinState.ScanProgram
    }
}

/** Shared shape for "show a request, then scan the business's reply" - used by both stamping and redeeming. */
sealed interface ExchangeState {
    data object Preparing : ExchangeState
    data class ShowRequest(val bytes: ByteArray, val instruction: String) : ExchangeState
    data object Scanning : ExchangeState
    data class Done(val message: String) : ExchangeState
    data class Failed(val message: String) : ExchangeState
}

class StampFlowViewModel(private val repository: CollectorRepository, private val cardId: String) : ViewModel() {

    private val _state = MutableStateFlow<ExchangeState>(ExchangeState.Preparing)
    val state: StateFlow<ExchangeState> = _state

    init {
        prepareRequest()
    }

    fun onNextTapped() {
        _state.value = ExchangeState.Scanning
    }

    fun onScanned(bytes: ByteArray?) {
        if (bytes == null) return
        viewModelScope.launch {
            _state.value = when (val outcome = repository.acceptStampResponse(cardId, bytes)) {
                is StampAcceptOutcome.Accepted -> ExchangeState.Done("Stamp added!")
                is StampAcceptOutcome.Rejected -> ExchangeState.Failed(outcome.reason)
            }
        }
    }

    fun retry() = prepareRequest()

    private fun prepareRequest() {
        viewModelScope.launch {
            val bytes = repository.buildStampRequest(cardId)
            _state.value = if (bytes != null) {
                ExchangeState.ShowRequest(bytes, "Show this to the cashier")
            } else {
                ExchangeState.Failed("This card could not be found")
            }
        }
    }
}

class RedeemFlowViewModel(private val repository: CollectorRepository, private val cardId: String) : ViewModel() {

    private val _state = MutableStateFlow<ExchangeState>(ExchangeState.Preparing)
    val state: StateFlow<ExchangeState> = _state

    init {
        prepareRequest()
    }

    fun onNextTapped() {
        _state.value = ExchangeState.Scanning
    }

    fun onScanned(bytes: ByteArray?) {
        if (bytes == null) return
        viewModelScope.launch {
            _state.value = when (val outcome = repository.acceptRedemptionResponse(cardId, bytes)) {
                RedemptionAcceptOutcome.Accepted -> ExchangeState.Done("Enjoy your reward!")
                is RedemptionAcceptOutcome.Rejected -> ExchangeState.Failed(outcome.reason)
            }
        }
    }

    fun retry() = prepareRequest()

    private fun prepareRequest() {
        viewModelScope.launch {
            val bytes = repository.buildRedemptionRequest(cardId)
            _state.value = if (bytes != null) {
                ExchangeState.ShowRequest(bytes, "Show this to the cashier to claim your reward")
            } else {
                ExchangeState.Failed("Not enough stamps yet")
            }
        }
    }
}
