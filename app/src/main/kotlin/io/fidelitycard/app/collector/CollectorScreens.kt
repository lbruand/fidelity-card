package io.fidelitycard.app.collector

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.fidelitycard.app.FidelityApplication
import io.fidelitycard.app.data.CardSummary
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
fun CardListScreen(onOpenCard: (String) -> Unit, onJoinBusiness: () -> Unit) {
    val repo = collectorRepository()
    val viewModel: CardListViewModel = viewModel(factory = viewModelFactory { initializer { CardListViewModel(repo) } })
    val cards by viewModel.cards.collectAsState()

    Scaffold(
        topBar = { TopAppBar(title = { Text("My cards") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = onJoinBusiness) {
                Icon(Icons.Filled.Add, contentDescription = "Join a business")
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
                Text("Tap + to join a business.")
            }
        } else {
            LazyColumn(modifier = Modifier.padding(padding).fillMaxSize()) {
                items(cards) { card: CardSummary ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        onClick = { onOpenCard(card.cardId) },
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(card.programName, style = MaterialTheme.typography.titleLarge)
                            Spacer(modifier = Modifier.height(8.dp))
                            StampProgressDots(card.progress)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun JoinBusinessScreen(onBack: () -> Unit, onDone: (String) -> Unit) {
    val repo = collectorRepository()
    val viewModel: JoinFlowViewModel = viewModel(factory = viewModelFactory { initializer { JoinFlowViewModel(repo) } })
    val state by viewModel.state.collectAsState()
    val scanner = rememberQrScanLauncher { bytes -> viewModel.onScanned(bytes) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Join a business") }, navigationIcon = { BackButton(onBack) })
        },
    ) { padding: PaddingValues ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            when (val s = state) {
                JoinState.ScanProgram -> {
                    Text("Scan the business's QR code to join", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(onClick = { scanner.launch() }, modifier = Modifier.fillMaxWidth().height(72.dp)) {
                        Text("Scan business")
                    }
                }
                is JoinState.ShowJoinRequest -> {
                    QrDisplay(bytes = s.requestBytes, instruction = "Show this to the business")
                    Button(
                        onClick = {
                            viewModel.onNextTapped()
                            scanner.launch()
                        },
                        modifier = Modifier.fillMaxWidth().height(72.dp),
                    ) {
                        Text("Next: scan their reply")
                    }
                }
                is JoinState.ScanReply -> {
                    Text("Scanning...", style = MaterialTheme.typography.titleMedium)
                }
                is JoinState.Done -> {
                    Text("You joined ${s.programName}!", style = MaterialTheme.typography.headlineSmall)
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(onClick = { onDone(s.cardId) }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                        Text("View my card")
                    }
                }
                is JoinState.Failed -> {
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
fun CardDetailScreen(cardId: String, onBack: () -> Unit, onGetStamp: (String) -> Unit, onRedeem: (String) -> Unit) {
    val repo = collectorRepository()
    val viewModel: CardDetailViewModel = viewModel(
        factory = viewModelFactory { initializer { CardDetailViewModel(repo, cardId) } },
    )
    val card by viewModel.card.collectAsState()

    Scaffold(
        topBar = { TopAppBar(title = { Text(card?.programName ?: "") }, navigationIcon = { BackButton(onBack) }) },
    ) { padding: PaddingValues ->
        val c = card ?: return@Scaffold
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(c.reward, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(24.dp))
            StampProgressDots(c.progress)
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
        }
    }
}

@Composable
fun StampFlowScreen(cardId: String, onBack: () -> Unit, onDone: () -> Unit) {
    val repo = collectorRepository()
    val viewModel: StampFlowViewModel = viewModel(
        factory = viewModelFactory { initializer { StampFlowViewModel(repo, cardId) } },
    )
    ExchangeFlowScreen(
        title = "Get a stamp",
        state = viewModel.state,
        onBack = onBack,
        onDone = onDone,
        onNextTapped = viewModel::onNextTapped,
        onScanned = viewModel::onScanned,
        onRetry = viewModel::retry,
    )
}

@Composable
fun RedeemFlowScreen(cardId: String, onBack: () -> Unit, onDone: () -> Unit) {
    val repo = collectorRepository()
    val viewModel: RedeemFlowViewModel = viewModel(
        factory = viewModelFactory { initializer { RedeemFlowViewModel(repo, cardId) } },
    )
    ExchangeFlowScreen(
        title = "Redeem reward",
        state = viewModel.state,
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
                    QrDisplay(bytes = s.bytes, instruction = s.instruction)
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
