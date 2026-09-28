@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package io.fidelitycard.app.issuer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.fidelitycard.app.FidelityApplication
import io.fidelitycard.app.data.ProgramSummary
import io.fidelitycard.app.ui.QrDisplay
import io.fidelitycard.app.ui.rememberQrScanLauncher

@Composable
private fun issuerRepository() =
    (LocalContext.current.applicationContext as FidelityApplication).issuerRepository

@Composable
fun BusinessListScreen(onOpenBusiness: (String) -> Unit, onCreateBusiness: () -> Unit, onSwitchMode: () -> Unit) {
    val repo = issuerRepository()
    val viewModel: BusinessListViewModel = viewModel(
        factory = viewModelFactory { initializer { BusinessListViewModel(repo) } },
    )
    val programs by viewModel.programs.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My businesses") },
                actions = {
                    IconButton(onClick = onSwitchMode) {
                        Icon(Icons.Filled.SwapHoriz, contentDescription = "Switch mode")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onCreateBusiness) {
                Icon(Icons.Filled.Add, contentDescription = "Create a business")
            }
        },
    ) { padding: PaddingValues ->
        if (programs.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("No businesses yet.", style = MaterialTheme.typography.titleMedium)
                Text("Tap + to create your first loyalty card.")
            }
        } else {
            LazyColumn(modifier = Modifier.padding(padding).fillMaxSize()) {
                items(programs) { program: ProgramSummary ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        onClick = { onOpenBusiness(program.programId) },
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(program.name, style = MaterialTheme.typography.titleLarge)
                            Text("${program.threshold} stamps -> ${program.reward}")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CreateBusinessScreen(onBack: () -> Unit, onCreated: (String) -> Unit) {
    val repo = issuerRepository()
    val viewModel: CreateBusinessViewModel = viewModel(
        factory = viewModelFactory { initializer { CreateBusinessViewModel(repo) } },
    )

    var name by remember { mutableStateOf("") }
    var thresholdText by remember { mutableStateOf("10") }
    var reward by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New business card") },
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Business name") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = thresholdText,
                onValueChange = { thresholdText = it.filter(Char::isDigit) },
                label = { Text("Stamps needed for a reward") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = reward,
                onValueChange = { reward = it },
                label = { Text("Reward (e.g. \"Free coffee\")") },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Best for low-value rewards (a free coffee, a small discount) — " +
                    "not high-value goods. See the README for why.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            val threshold = thresholdText.toIntOrNull() ?: 0
            val canCreate = name.isNotBlank() && reward.isNotBlank() && threshold > 0

            Button(
                onClick = { viewModel.createProgram(name.trim(), threshold, reward.trim(), onCreated) },
                enabled = canCreate,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Text("Create")
            }
        }
    }
}

@Composable
fun BusinessDetailScreen(programId: String, onBack: () -> Unit) {
    val repo = issuerRepository()
    val viewModel: BusinessDetailViewModel = viewModel(
        factory = viewModelFactory { initializer { BusinessDetailViewModel(repo, programId) } },
    )
    val program by viewModel.program.collectAsState()
    val scanState by viewModel.scanState.collectAsState()

    val scanner = rememberQrScanLauncher { bytes -> viewModel.onCustomerScanned(bytes) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(program?.name ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding: PaddingValues ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (val state = scanState) {
                is ScanCustomerState.Responding -> {
                    QrDisplay(bytes = state.bytes, instruction = state.message)
                    Button(onClick = { viewModel.dismissResponse() }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                        Text("Done")
                    }
                }
                is ScanCustomerState.Failed -> {
                    Text(state.message, style = MaterialTheme.typography.titleMedium)
                    Button(onClick = { viewModel.dismissResponse() }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                        Text("Try again")
                    }
                }
                ScanCustomerState.Idle -> {
                    program?.let { p ->
                        QrDisplay(
                            bytes = p.programManifestBytes,
                            instruction = "New customers scan this to join \"${p.name}\"",
                        )
                    }
                    Button(
                        onClick = { scanner.launch() },
                        modifier = Modifier.fillMaxWidth().height(72.dp),
                    ) {
                        Text("Scan a customer", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}
