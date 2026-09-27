package io.fidelitycard.app.qr

import android.util.Log
import io.fidelitycard.crypto.StampToken
import io.fidelitycard.crypto.wire.MalformedMessageException
import io.fidelitycard.crypto.wire.WireReader
import io.fidelitycard.crypto.wire.WireWriter

private const val LOG_TAG = "CustomerMessage"

private object Tag {
    const val STAMP_REQUEST = 101
    const val REDEMPTION_REQUEST = 102
    const val LARGE_REDEMPTION_REQUEST = 103
}

/**
 * The things a collector's phone can show an issuer's camera
 * (SPEC/SPECS.md §6.2-§6.3). Joining a program is purely local for the
 * collector (no message to the issuer at all - SPEC/SPECS.md §6.1), so
 * there is no "join" message here. None of these are signed - unlike the
 * `:crypto` message types, they're not proof of anything on their own, just
 * a request; whatever they ask for only becomes real once the issuer signs
 * a response. A leading tag byte (distinct from
 * `io.fidelitycard.crypto.wire.MessageTag`, a different message family) is
 * what lets the issuer's single "Scan a customer" button figure out which
 * one it's looking at without knowing in advance.
 */
sealed interface CustomerMessage {

    fun toWireBytes(): ByteArray

    data class StampRequest(
        val programId: String,
        val cardId: String,
    ) : CustomerMessage {
        override fun toWireBytes(): ByteArray =
            WireWriter()
                .writeByte(Tag.STAMP_REQUEST)
                .writeString(programId)
                .writeString(cardId)
                .toByteArray()
    }

    /**
     * [compactStampProofs] are each [StampToken.COMPACT_PROOF_LENGTH_BYTES]
     * (`stamp_id || issued_at || signature`, no `program_id`/`card_id`) -
     * every stamp in one redemption is for this same [programId]/[cardId],
     * so repeating those inside each one would be pure waste. This is what
     * keeps large-threshold redemptions inside one scannable QR
     * (SPEC/SPECS.md §6.3/§11); see `io.fidelitycard.crypto.StampToken`
     * §5.2.1 in CRYPTO_WIRE_FORMAT.md for the exact byte layout this saves.
     */
    data class RedemptionRequest(
        val programId: String,
        val cardId: String,
        val compactStampProofs: List<ByteArray>,
    ) : CustomerMessage {
        override fun toWireBytes(): ByteArray {
            val writer = WireWriter()
                .writeByte(Tag.REDEMPTION_REQUEST)
                .writeString(programId)
                .writeString(cardId)
                .writeInt32(compactStampProofs.size)
            compactStampProofs.forEach { writer.writeFixedBytes(it, StampToken.COMPACT_PROOF_LENGTH_BYTES) }
            return writer.toByteArray()
        }
    }

    /**
     * The large-threshold fallback for [RedemptionRequest]: bare
     * [StampToken.STAMP_ID_LENGTH_BYTES]-byte stamp ids, no signature and
     * no `issued_at` at all - not self-verifying the way a Compact Stamp
     * Proof is. Verified instead against the issuer's own local ledger of
     * every stamp id it has ever minted for this card
     * (`io.fidelitycard.app.data.IssuerMintedStampEntity`), which is what
     * makes this ~5x smaller again than [compactStampProofs] while staying
     * just as unforgeable - a raw id nobody minted for this card simply
     * won't be in that ledger. Only worth using once a redemption's stamp
     * count is large enough that even Compact Stamp Proofs would push the
     * QR past a comfortable size (SPEC/SPECS.md §6.3/§11, `TODO.md`).
     */
    data class LargeRedemptionRequest(
        val programId: String,
        val cardId: String,
        val stampIds: List<ByteArray>,
    ) : CustomerMessage {
        override fun toWireBytes(): ByteArray {
            val writer = WireWriter()
                .writeByte(Tag.LARGE_REDEMPTION_REQUEST)
                .writeString(programId)
                .writeString(cardId)
                .writeInt32(stampIds.size)
            stampIds.forEach { writer.writeFixedBytes(it, StampToken.STAMP_ID_LENGTH_BYTES) }
            return writer.toByteArray()
        }
    }

    companion object {
        /** Never throws: an unrecognized or corrupt scan is data an issuer's UI must show, not crash on. */
        fun parse(bytes: ByteArray): CustomerMessage? = try {
            val reader = WireReader(bytes)
            when (val tag = reader.readByte()) {
                Tag.STAMP_REQUEST -> {
                    val programId = reader.readString()
                    val cardId = reader.readString()
                    reader.requireFullyConsumed()
                    StampRequest(programId, cardId)
                }
                Tag.REDEMPTION_REQUEST -> {
                    val programId = reader.readString()
                    val cardId = reader.readString()
                    val count = reader.readInt32()
                    val proofs = (0 until count).map { reader.readFixedBytes(StampToken.COMPACT_PROOF_LENGTH_BYTES) }
                    reader.requireFullyConsumed()
                    RedemptionRequest(programId, cardId, proofs)
                }
                Tag.LARGE_REDEMPTION_REQUEST -> {
                    val programId = reader.readString()
                    val cardId = reader.readString()
                    val count = reader.readInt32()
                    val stampIds = (0 until count).map { reader.readFixedBytes(StampToken.STAMP_ID_LENGTH_BYTES) }
                    reader.requireFullyConsumed()
                    LargeRedemptionRequest(programId, cardId, stampIds)
                }
                else -> throw MalformedMessageException("Unknown customer message tag $tag")
            }
        } catch (e: MalformedMessageException) {
            // Routine: scanning any unrelated QR code (or the wrong side of
            // this app's own protocol) lands here every time, not a bug -
            // debug level, not a warning.
            Log.d(LOG_TAG, "Scanned bytes are not a recognized customer message", e)
            null
        } catch (e: IllegalArgumentException) {
            Log.d(LOG_TAG, "Scanned bytes are not a recognized customer message", e)
            null
        }
    }
}
