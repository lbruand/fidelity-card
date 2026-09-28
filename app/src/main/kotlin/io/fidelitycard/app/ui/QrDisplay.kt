package io.fidelitycard.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.fidelitycard.app.qr.QrCodec

/**
 * A big, unmissable QR code plus one short line telling the person what
 * to do with it. [color]/[centerIcon] apply the program's personalization
 * (TODO.md "Product / UX") - [centerIcon] should be left `null` for a
 * size-sensitive payload (see [QrCodec.encodeToBitmap]).
 */
@Composable
fun QrDisplay(
    bytes: ByteArray,
    instruction: String,
    modifier: Modifier = Modifier,
    color: Int = android.graphics.Color.BLACK,
    centerIcon: String? = null,
) {
    val bitmap = remember(bytes, color, centerIcon) {
        QrCodec.encodeToBitmap(bytes, foregroundColor = color, centerIcon = centerIcon)
    }

    Column(
        modifier = modifier.fillMaxWidth().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "QR code to show",
            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
        )
        Text(text = instruction, textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium)
    }
}
