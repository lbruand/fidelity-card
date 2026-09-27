package io.fidelitycard.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** The only fork in the whole app: are you the business, or the customer? Everything else follows one path. */
@Composable
fun HomeScreen(onOpenBusinesses: () -> Unit, onOpenCards: () -> Unit, onOpenBackup: () -> Unit) {
    Scaffold { padding: PaddingValues ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "Fidelity Card",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = 48.dp),
            )

            Button(
                onClick = onOpenBusinesses,
                modifier = Modifier.fillMaxWidth().height(72.dp),
            ) {
                Text("I run a business", style = MaterialTheme.typography.titleMedium)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onOpenCards,
                modifier = Modifier.fillMaxWidth().height(72.dp),
            ) {
                Text("I collect stamps", style = MaterialTheme.typography.titleMedium)
            }

            Spacer(modifier = Modifier.height(24.dp))

            TextButton(onClick = onOpenBackup, modifier = Modifier.fillMaxWidth()) {
                Text("Backup & restore")
            }
        }
    }
}
