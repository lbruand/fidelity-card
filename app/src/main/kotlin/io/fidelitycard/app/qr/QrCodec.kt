package io.fidelitycard.app.qr

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
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

    /**
     * [foregroundColor] tints the QR's modules (card personalization,
     * TODO.md "Product / UX") - purely cosmetic, no capacity cost.
     * [centerIcon], if given, is drawn as text over a small white badge in
     * the middle of the code. That *does* cost capacity: it only renders
     * correctly with high error correction, which is requested only when
     * [centerIcon] is non-null, on purpose - callers with a
     * size-sensitive payload (a large redemption request's compact
     * proofs, SPEC/SPECS.md §6.3) should leave it `null` and tint only.
     */
    fun encodeToBitmap(bytes: ByteArray, sizePx: Int = 900, foregroundColor: Int = Color.BLACK, centerIcon: String? = null): Bitmap {
        val text = String(bytes, QR_BYTE_CHARSET)
        val hints = buildMap {
            put(EncodeHintType.CHARACTER_SET, QR_BYTE_CHARSET.name())
            put(EncodeHintType.MARGIN, 1)
            if (centerIcon != null) put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H)
        }
        val matrix = MultiFormatWriter().encode(text, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
        val bitmap = matrix.toBitmap(foregroundColor)
        if (centerIcon != null) {
            bitmap.drawCenterIcon(centerIcon)
        }
        return bitmap
    }

    fun decodeScannedText(scannedText: String): ByteArray = scannedText.toByteArray(QR_BYTE_CHARSET)

    private fun BitMatrix.toBitmap(foregroundColor: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap.setPixel(x, y, if (get(x, y)) foregroundColor else Color.WHITE)
            }
        }
        return bitmap
    }

    private fun Bitmap.drawCenterIcon(icon: String) {
        val canvas = Canvas(this)
        val badgeSize = width * 0.22f
        val cx = width / 2f
        val cy = height / 2f

        val badgeRect = RectF(cx - badgeSize / 2, cy - badgeSize / 2, cx + badgeSize / 2, cy + badgeSize / 2)
        val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        canvas.drawRoundRect(badgeRect, badgeSize * 0.2f, badgeSize * 0.2f, badgePaint)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            textSize = badgeSize * 0.65f
        }
        val metrics = textPaint.fontMetrics
        val textY = cy - (metrics.ascent + metrics.descent) / 2f
        canvas.drawText(icon, cx, textY, textPaint)
    }
}
