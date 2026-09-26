package io.fidelitycard.crypto

import io.fidelitycard.crypto.wire.MessageTag
import io.fidelitycard.crypto.wire.SIGNATURE_LENGTH_BYTES
import io.fidelitycard.crypto.wire.WIRE_VERSION
import io.fidelitycard.crypto.wire.WireReader
import io.fidelitycard.crypto.wire.WireWriter
import io.fidelitycard.crypto.wire.readAndVerifyHeader
import java.time.Instant

/**
 * Closes out a redemption (SPEC/SPECS.md §5.4 / §6.3): the issuer's proof
 * that stamps `1..redeemedThroughSerial` for this card were verified and
 * exchanged for the reward. The collector app archives those stamps
 * locally; the issuer device records this card's highest redeemed serial
 * so it refuses to redeem the same run twice (subject to the multi-device
 * limitation in SPEC/SPECS.md §7.4).
 */
class RedemptionCertificate private constructor(
    val programId: String,
    val cardId: String,
    val redeemedThroughSerial: Int,
    val redeemedAt: Instant,
    private val signature: ByteArray,
) {

    fun toWireBytes(): ByteArray =
        WireWriter()
            .apply { writeSignedPayload(this, programId, cardId, redeemedThroughSerial, redeemedAt) }
            .writeFixedBytes(signature, SIGNATURE_LENGTH_BYTES)
            .toByteArray()

    companion object {

        fun issue(
            issuer: SigningKeyPair,
            programId: String,
            cardId: String,
            redeemedThroughSerial: Int,
            redeemedAt: Instant = Instant.now(),
        ): RedemptionCertificate {
            require(redeemedThroughSerial > 0) {
                "redeemedThroughSerial must be positive, was $redeemedThroughSerial"
            }
            val redeemedAt = Instant.ofEpochMilli(redeemedAt.toEpochMilli())

            val payload = WireWriter()
                .apply { writeSignedPayload(this, programId, cardId, redeemedThroughSerial, redeemedAt) }
                .toByteArray()
            val signature = issuer.sign(payload)

            return RedemptionCertificate(programId, cardId, redeemedThroughSerial, redeemedAt, signature)
        }

        fun parseAndVerify(bytes: ByteArray, issuerPublicKey: VerifyingKey): RedemptionCertificate {
            val reader = WireReader(bytes)
            reader.readAndVerifyHeader(MessageTag.REDEMPTION)

            val programId = reader.readString()
            val cardId = reader.readString()
            val redeemedThroughSerial = reader.readInt32()
            val redeemedAt = Instant.ofEpochMilli(reader.readInt64())
            val signature = reader.readFixedBytes(SIGNATURE_LENGTH_BYTES)
            reader.requireFullyConsumed()

            val payload = WireWriter()
                .apply { writeSignedPayload(this, programId, cardId, redeemedThroughSerial, redeemedAt) }
                .toByteArray()
            if (!issuerPublicKey.verify(payload, signature)) {
                throw InvalidSignatureException(
                    "RedemptionCertificate signature does not verify against the given issuer key",
                )
            }

            return RedemptionCertificate(programId, cardId, redeemedThroughSerial, redeemedAt, signature)
        }

        private fun writeSignedPayload(
            writer: WireWriter,
            programId: String,
            cardId: String,
            redeemedThroughSerial: Int,
            redeemedAt: Instant,
        ) {
            writer.writeByte(WIRE_VERSION)
            writer.writeByte(MessageTag.REDEMPTION)
            writer.writeString(programId)
            writer.writeString(cardId)
            writer.writeInt32(redeemedThroughSerial)
            writer.writeInt64(redeemedAt.toEpochMilli())
        }
    }
}
