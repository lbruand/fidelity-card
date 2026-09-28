package io.fidelitycard.app.collector

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.fidelitycard.app.data.CardSummary
import io.fidelitycard.app.data.CollectorRepository
import io.fidelitycard.app.data.PendingRedemption
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

/** Joining is a single scan - no round trip (SPEC/SPECS.md §6.1): the collector never sends the issuer anything. */
sealed interface JoinState {
    data object ScanProgram : JoinState
    data class Done(val cardId: String, val programName: String) : JoinState
    data class Failed(val message: String) : JoinState
}

class JoinFlowViewModel(private val repository: CollectorRepository) : ViewModel() {

    private val _state = MutableStateFlow<JoinState>(JoinState.ScanProgram)
    val state: StateFlow<JoinState> = _state

    fun onScanned(bytes: ByteArray?) {
        if (bytes == null) return
        val program = repository.parseProgramQr(bytes)
        if (program == null) {
            _state.value = JoinState.Failed("That doesn't look like a business code")
            return
        }
        viewModelScope.launch {
            val summary = repository.joinProgram(program)
            _state.value = JoinState.Done(summary.cardId, summary.programName)
        }
    }

    fun retry() {
        _state.value = JoinState.ScanProgram
    }
}

/** Shared shape for "show a request, then scan the business's reply" - redemption only (SPEC/SPECS.md §6.3). */
sealed interface ExchangeState {
    data object Preparing : ExchangeState
    data class ShowRequest(val bytes: ByteArray, val instruction: String) : ExchangeState
    data object Scanning : ExchangeState
    data class Done(val message: String) : ExchangeState
    data class Failed(val message: String) : ExchangeState
}

/**
 * Getting a stamp is a single one-way scan (SPEC/SPECS.md §6.2): the
 * issuer mints and shows a Stamp Token unprompted, this device just scans
 * whatever it's shown - there is no request to build or show first.
 */
sealed interface GetStampState {
    data object ReadyToScan : GetStampState
    data class Done(val message: String) : GetStampState
    data class Failed(val message: String) : GetStampState
}

class StampFlowViewModel(private val repository: CollectorRepository, private val cardId: String) : ViewModel() {

    private val _state = MutableStateFlow<GetStampState>(GetStampState.ReadyToScan)
    val state: StateFlow<GetStampState> = _state

    fun onScanned(bytes: ByteArray?) {
        if (bytes == null) return
        viewModelScope.launch {
            _state.value = when (val outcome = repository.acceptStampResponse(cardId, bytes)) {
                is StampAcceptOutcome.Accepted -> GetStampState.Done("Stamp added!")
                is StampAcceptOutcome.Rejected -> GetStampState.Failed(outcome.reason)
            }
        }
    }

    fun retry() {
        _state.value = GetStampState.ReadyToScan
    }
}

class RedeemFlowViewModel(private val repository: CollectorRepository, private val cardId: String) : ViewModel() {

    private val _state = MutableStateFlow<ExchangeState>(ExchangeState.Preparing)
    val state: StateFlow<ExchangeState> = _state
    private var pending: PendingRedemption? = null

    /** For tinting the redemption request QR with the card's own color (TODO.md "Product / UX"). */
    val card: StateFlow<CardSummary?> = repository.observeCard(cardId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        prepareRequest()
    }

    fun onNextTapped() {
        _state.value = ExchangeState.Scanning
    }

    fun onScanned(bytes: ByteArray?) {
        if (bytes == null) return
        val currentPending = pending ?: return
        viewModelScope.launch {
            _state.value = when (val outcome = repository.acceptRedemptionResponse(cardId, currentPending, bytes)) {
                RedemptionAcceptOutcome.Accepted -> ExchangeState.Done("Enjoy your reward!")
                is RedemptionAcceptOutcome.Rejected -> ExchangeState.Failed(outcome.reason)
            }
        }
    }

    fun retry() = prepareRequest()

    private fun prepareRequest() {
        viewModelScope.launch {
            val request = repository.buildRedemptionRequest(cardId)
            pending = request
            _state.value = if (request != null) {
                ExchangeState.ShowRequest(request.requestBytes, "Show this to the cashier to claim your reward")
            } else {
                ExchangeState.Failed("Not enough stamps yet")
            }
        }
    }
}
