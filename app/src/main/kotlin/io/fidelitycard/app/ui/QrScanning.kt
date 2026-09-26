package io.fidelitycard.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import io.fidelitycard.app.qr.QrCodec

/**
 * Wraps zxing-android-embedded's ready-made scan Activity (camera preview,
 * viewfinder, decoding) behind one function call, so every "scan a QR"
 * moment in the app is a single line: `scanner.launch()`. Cancelling the
 * scan (back button, or the camera permission being denied) reports
 * `null`, never a crash - callers just show "scan cancelled" and let the
 * person try again.
 */
class QrScanLauncher internal constructor(
    private val launcher: androidx.activity.result.ActivityResultLauncher<ScanOptions>,
) {
    fun launch() {
        launcher.launch(
            ScanOptions()
                .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                .setBeepEnabled(false)
                .setOrientationLocked(false)
                .setPrompt(""),
        )
    }
}

@Composable
fun rememberQrScanLauncher(onScanned: (ByteArray?) -> Unit): QrScanLauncher {
    val activityLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        val text = result.contents
        onScanned(if (text != null) QrCodec.decodeScannedText(text) else null)
    }
    return remember(activityLauncher) { QrScanLauncher(activityLauncher) }
}
