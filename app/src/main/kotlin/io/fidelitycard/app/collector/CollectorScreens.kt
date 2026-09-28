@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package io.fidelitycard.app.collector

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.fidelitycard.app.FidelityApplication
import io.fidelitycard.app.data.CardSummary
import io.fidelitycard.app.ui.CardColorHeader
import io.fidelitycard.app.ui.CardStyleBadge
import io.fidelitycard.app.ui.ColorSurfaceTray
import io.fidelitycard.app.ui.QrDisplay
import io.fidelitycard.app.ui.StampProgressDots
import io.fidelitycard.app.ui.rememberQrScanLauncher
import kotlinx.coroutines.flow.StateFlow

@Composable
private fun collectorRepository() =
    (LocalContext.current.applicationContext as FidelityApplication).collectorRepository

@Composable
private fun BackButton(onBack: () -> Unit) {
    IconButton(onClick = onBack) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
    }
}

@Composable
fun CardListScreen(onOpenCard: (String) -> Unit, onJoinBusiness: () -> Unit, onSwitchMode: () -> Unit) {
    val repo = collectorRepository()
    val viewModel: CardListViewModel = viewModel(factory = viewModelFactory { initializer { CardListViewModel(repo) } })
    val cards by viewModel.cards.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My cards") },
                actions = {
                    IconButton(onClick = onSwitchMode) {
                        Icon(Icons.Filled.SwapHoriz, contentDescription = "Switch mode")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onJoinBusiness) {
                Icon(Icons.Filled.Add, contentDescription = "Scan a business")
            }
        },
    ) { padding: PaddingValues ->
        if (cards.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("No cards yet.", style = MaterialTheme.typography.titleMedium)
                Text("Tap + to scan a business - joining and getting your first stamp are the same scan.")
            }
        } else {
            LazyColumn(modifier = Modifier.padding(padding).fillMaxSize()) {
                items(cards) { card: CardSummary ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        onClick = { onOpenCard(card.cardId) },
                        colors = CardDefaults.cardColors(containerColor = Color(card.color)),
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            CardStyleBadge(card.icon)
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(card.programName, style = MaterialTheme.typography.titleLarge, color = Color.White)
                                Spacer(modifier = Modifier.height(8.dp))
                                ColorSurfaceTray {
                                    StampProgressDots(card.progress, card.color, card.icon)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * One scanner for both "scan a new business" (no [cardId] - joins, and
 * credits a stamp too if the business's screen was showing one) and "get
 * a stamp" from an already-open card (has [cardId] - scoped to that one
 * business). See [ScanBusinessViewModel] and SPEC/SPECS.md §6.1/§6.2.
 */
@Composable
fun ScanBusinessScreen(cardId: String?, onBack: () -> Unit, onDone: (String) -> Unit) {
    val repo = collectorRepository()
    val viewModel: ScanBusinessViewModel = viewModel(
        factory = viewModelFactory { initializer { ScanBusinessViewModel(repo, cardId) } },
    )
    val state by viewModel.state.collectAsState()
    val scanner = rememberQrScanLauncher { bytes -> viewModel.onScanned(bytes) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(if (cardId == null) "Scan a business" else "Get a stamp") }, navigationIcon = { BackButton(onBack) })
        },
    ) { padding: PaddingValues ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            when (val s = state) {
                ScanBusinessState.Scan -> {
                    Text(
                        if (cardId == null) {
                            "Scan a business's QR code to join, or to get your next stamp"
                        } else {
                            "Scan the business's screen"
                        },
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(onClick = { scanner.launch() }, modifier = Modifier.fillMaxWidth().height(72.dp)) {
                        Text("Scan")
                    }
                }
                is ScanBusinessState.Done -> {
                    Text(s.message, style = MaterialTheme.typography.headlineSmall)
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(onClick = { onDone(s.cardId) }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                        Text(if (cardId == null) "View my card" else "Done")
                    }
                }
                is ScanBusinessState.Failed -> {
                    Text(s.message, style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(onClick = { viewModel.retry() }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                        Text("Try again")
                    }
                }
            }
        }
    }
}

@Composable
fun CardDetailScreen(
    cardId: String,
    onBack: () -> Unit,
    onGetStamp: (String) -> Unit,
    onRedeem: (String) -> Unit,
    onLeft: () -> Unit,
) {
    val repo = collectorRepository()
    val viewModel: CardDetailViewModel = viewModel(
        factory = viewModelFactory { initializer { CardDetailViewModel(repo, cardId) } },
    )
    val card by viewModel.card.collectAsState()
    var showLeaveConfirmation by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text(card?.programName ?: "") }, navigationIcon = { BackButton(onBack) }) },
    ) { padding: PaddingValues ->
        val c = card ?: return@Scaffold
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CardColorHeader(c.color, c.icon, c.programName, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(24.dp))
            Text(c.reward, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(24.dp))
            StampProgressDots(c.progress, c.color, c.icon)
            Spacer(modifier = Modifier.height(32.dp))

            if (c.progress.isRedeemable) {
                Button(onClick = { onRedeem(cardId) }, modifier = Modifier.fillMaxWidth().height(72.dp)) {
                    Text("Redeem reward", style = MaterialTheme.typography.titleMedium)
                }
            } else {
                Text("${c.progress.remainingForNextReward} more to go")
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { onGetStamp(cardId) }, modifier = Modifier.fillMaxWidth().height(72.dp)) {
                    Text("Get a stamp", style = MaterialTheme.typography.titleMedium)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            TextButton(onClick = { showLeaveConfirmation = true }) {
                Text("Leave this business")
            }
        }
    }

    if (showLeaveConfirmation) {
        AlertDialog(
            onDismissRequest = { showLeaveConfirmation = false },
            title = { Text("Leave this business?") },
            text = { Text("This removes the card and its stamps from this phone. You can rejoin later, but you'll start over from zero.") },
            confirmButton = {
                TextButton(onClick = { viewModel.leaveBusiness(onLeft) }) {
                    Text("Leave")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveConfirmation = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
fun RedeemFlowScreen(cardId: String, onBack: () -> Unit, onDone: () -> Unit) {
    val repo = collectorRepository()
    val viewModel: RedeemFlowViewModel = viewModel(
        factory = viewModelFactory { initializer { RedeemFlowViewModel(repo, cardId) } },
    )
    val card by viewModel.card.collectAsState()
    ExchangeFlowScreen(
        title = "Redeem reward",
        state = viewModel.state,
        qrColor = card?.color ?: android.graphics.Color.BLACK,
        onBack = onBack,
        onDone = onDone,
        onNextTapped = viewModel::onNextTapped,
        onScanned = viewModel::onScanned,
        onRetry = viewModel::retry,
    )
}

@Composable
private fun ExchangeFlowScreen(
    title: String,
    state: StateFlow<ExchangeState>,
    qrColor: Int,
    onBack: () -> Unit,
    onDone: () -> Unit,
    onNextTapped: () -> Unit,
    onScanned: (ByteArray?) -> Unit,
    onRetry: () -> Unit,
) {
    val currentState by state.collectAsState()
    val scanner = rememberQrScanLauncher(onScanned)

    Scaffold(
        topBar = { TopAppBar(title = { Text(title) }, navigationIcon = { BackButton(onBack) }) },
    ) { padding: PaddingValues ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            when (val s = currentState) {
                ExchangeState.Preparing -> Text("One moment...")
                is ExchangeState.ShowRequest -> {
                    // No centerIcon here on purpose: this QR's payload size
                    // depends on how many stamps are being redeemed, up to
                    // the large-threshold fallback (SPEC/SPECS.md §6.3) -
                    // tint only, so a center logo's extra error-correction
                    // cost never risks pushing a big redemption over a
                    // scannable size.
                    QrDisplay(bytes = s.bytes, instruction = s.instruction, color = qrColor)
                    Button(
                        onClick = {
                            onNextTapped()
                            scanner.launch()
                        },
                        modifier = Modifier.fillMaxWidth().height(72.dp),
                    ) {
                        Text("Next: scan their reply")
                    }
                }
                ExchangeState.Scanning -> Text("Scanning...", style = MaterialTheme.typography.titleMedium)
                is ExchangeState.Done -> {
                    Text(s.message, style = MaterialTheme.typography.headlineSmall)
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(onClick = onDone, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                        Text("Done")
                    }
                }
                is ExchangeState.Failed -> {
                    Text(s.message, style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(onClick = onRetry, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                        Text("Try again")
                    }
                }
            }
        }
    }
}
