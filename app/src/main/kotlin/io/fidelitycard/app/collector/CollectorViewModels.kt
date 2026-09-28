package io.fidelitycard.app.collector

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.fidelitycard.app.data.CardSummary
import io.fidelitycard.app.data.CollectorRepository
import io.fidelitycard.app.data.PendingRedemption
import io.fidelitycard.app.data.RedemptionAcceptOutcome
import io.fidelitycard.app.data.ScanBusinessOutcome
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

/**
 * Scanning a business's screen is always a single scan - no round trip
 * (SPEC/SPECS.md §6.1/§6.2): the collector never sends the issuer
 * anything, whether this turns out to be a plain join or a stamp grant
 * (which creates the card too, if this is the first time - a brand new
 * customer's very first stamp needs no separate "join" scan beforehand).
 */
sealed interface ScanBusinessState {
    data object Scan : ScanBusinessState
    data class Done(val cardId: String, val message: String) : ScanBusinessState
    data class Failed(val message: String) : ScanBusinessState
}

/**
 * [cardId], if given, scopes this scan to that card's own business - used
 * when reached from an already-open card's "Get a stamp" button, so a
 * stray scan of a different business is rejected instead of silently
 * creating or crediting a different card. `null` for the top-level "scan
 * a business" action (reachable with no card open yet).
 */
class ScanBusinessViewModel(private val repository: CollectorRepository, private val cardId: String?) : ViewModel() {

    private val _state = MutableStateFlow<ScanBusinessState>(ScanBusinessState.Scan)
    val state: StateFlow<ScanBusinessState> = _state

    fun onScanned(bytes: ByteArray?) {
        if (bytes == null) return
        viewModelScope.launch {
            _state.value = when (val outcome = repository.acceptIssuerMessage(bytes, cardId)) {
                is ScanBusinessOutcome.Joined ->
                    ScanBusinessState.Done(outcome.cardId, "You joined ${outcome.programName}!")
                is ScanBusinessOutcome.Stamped ->
                    ScanBusinessState.Done(
                        outcome.cardId,
                        if (outcome.justJoined) "Welcome! Your first stamp is in." else "Stamp added!",
                    )
                is ScanBusinessOutcome.Rejected -> ScanBusinessState.Failed(outcome.reason)
            }
        }
    }

    fun retry() {
        _state.value = ScanBusinessState.Scan
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
