package io.fidelitycard.app.qr

import io.fidelitycard.crypto.wire.MalformedMessageException
import io.fidelitycard.crypto.wire.WireReader
import io.fidelitycard.crypto.wire.WireWriter

private object Tag {
    const val STAMP_REQUEST = 101
    const val REDEMPTION_REQUEST = 102
}

/**
 * The two things a collector's phone can show an issuer's camera
 * (SPEC/SPECS.md §6.2-§6.3). Joining a program is purely local for the
 * collector (no message to the issuer at all - SPEC/SPECS.md §6.1), so
 * there is no third, "join," message here. Neither of these is signed -
 * unlike the `:crypto` message types, they're not proof of anything on
 * their own, just a request; whatever they ask for only becomes real once
 * the issuer signs a response. A leading tag byte (distinct from
 * `io.fidelitycard.crypto.wire.MessageTag`, a different message family) is
 * what lets the issuer's single "Scan a customer" button figure out which
 * of the two it's looking at without knowing in advance.
 */
sealed interface CustomerMessage {

    data class StampRequest(
        val programId: String,
        val cardId: String,
    ) : CustomerMessage {
        fun toWireBytes(): ByteArray =
            WireWriter()
                .writeByte(Tag.STAMP_REQUEST)
                .writeString(programId)
                .writeString(cardId)
                .toByteArray()
    }

    data class RedemptionRequest(
        val programId: String,
        val cardId: String,
        val stampTokenWireBytes: List<ByteArray>,
    ) : CustomerMessage {
        fun toWireBytes(): ByteArray {
            val writer = WireWriter()
                .writeByte(Tag.REDEMPTION_REQUEST)
                .writeString(programId)
                .writeString(cardId)
                .writeInt32(stampTokenWireBytes.size)
            stampTokenWireBytes.forEach { writer.writeVarBytes(it) }
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
                    val stamps = (0 until count).map { reader.readVarBytes() }
                    reader.requireFullyConsumed()
                    RedemptionRequest(programId, cardId, stamps)
                }
                else -> throw MalformedMessageException("Unknown customer message tag $tag")
            }
        } catch (e: MalformedMessageException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
    }
}
