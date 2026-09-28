@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package io.fidelitycard.app.issuer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.CardDefaults
import androidx.compose.ui.graphics.Color
import io.fidelitycard.app.data.ProgramSummary
import io.fidelitycard.app.qr.IssuerMessage
import io.fidelitycard.app.ui.CardColorHeader
import io.fidelitycard.app.ui.CardStyle
import io.fidelitycard.app.ui.CardStyleBadge
import io.fidelitycard.app.ui.ColorPickerRow
import io.fidelitycard.app.ui.IconPickerRow
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
                        colors = CardDefaults.cardColors(containerColor = Color(program.color)),
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            CardStyleBadge(program.icon)
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(program.name, style = MaterialTheme.typography.titleLarge, color = Color.White)
                                Text("${program.threshold} stamps -> ${program.reward}", color = Color.White)
                            }
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
    var color by remember { mutableStateOf(CardStyle.defaultColor) }
    var icon by remember { mutableStateOf(CardStyle.defaultIcon) }

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

            Text("Color", style = MaterialTheme.typography.labelLarge)
            ColorPickerRow(selected = color, onSelect = { color = it })

            Text("Icon", style = MaterialTheme.typography.labelLarge)
            IconPickerRow(selected = icon, onSelect = { icon = it })

            val threshold = thresholdText.toIntOrNull() ?: 0
            val canCreate = name.isNotBlank() && reward.isNotBlank() && threshold > 0

            Button(
                onClick = { viewModel.createProgram(name.trim(), threshold, reward.trim(), color, icon, onCreated) },
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
            program?.let { p ->
                CardColorHeader(p.color, p.icon, p.name, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(16.dp))
            }
            when (val state = scanState) {
                is ScanCustomerState.Responding -> {
                    QrDisplay(bytes = state.bytes, instruction = state.message, color = program?.color ?: android.graphics.Color.BLACK, centerIcon = program?.icon)
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
                            bytes = IssuerMessage.ProgramInvite(p.programManifestBytes).toWireBytes(),
                            instruction = "New customers scan this to join \"${p.name}\"",
                            color = p.color,
                            centerIcon = p.icon,
                        )
                    }
                    Button(
                        onClick = { viewModel.giveStamp() },
                        modifier = Modifier.fillMaxWidth().height(72.dp),
                    ) {
                        Text("Give a stamp", style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { scanner.launch() },
                        modifier = Modifier.fillMaxWidth().height(72.dp),
                    ) {
                        Text("Scan a customer (redeem reward)", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}
