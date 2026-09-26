package io.fidelitycard.app.qr

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import java.nio.charset.Charset

/**
 * Every byte value (0-255) maps one-to-one to a Latin-1 code point, so
 * round-tripping arbitrary binary through a ZXing QR code as ISO-8859-1
 * text needs no Base64 - which would cost ~33% more QR payload for the
 * same bytes, and these payloads are already close to a single QR code's
 * practical size limit for a large redemption request (SPEC/SPECS.md
 * §6.3).
 */
private val QR_BYTE_CHARSET: Charset = Charsets.ISO_8859_1

object QrCodec {

    fun encodeToBitmap(bytes: ByteArray, sizePx: Int = 900): Bitmap {
        val text = String(bytes, QR_BYTE_CHARSET)
        val hints = mapOf(
            EncodeHintType.CHARACTER_SET to QR_BYTE_CHARSET.name(),
            EncodeHintType.MARGIN to 1,
        )
        val matrix = MultiFormatWriter().encode(text, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
        return matrix.toBitmap()
    }

    fun decodeScannedText(scannedText: String): ByteArray = scannedText.toByteArray(QR_BYTE_CHARSET)

    private fun BitMatrix.toBitmap(): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap.setPixel(x, y, if (get(x, y)) Color.BLACK else Color.WHITE)
            }
        }
        return bitmap
    }
}
