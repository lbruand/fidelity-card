package io.fidelitycard.crypto

import io.fidelitycard.crypto.wire.MessageTag
import io.fidelitycard.crypto.wire.SIGNATURE_LENGTH_BYTES
import io.fidelitycard.crypto.wire.WIRE_VERSION
import io.fidelitycard.crypto.wire.WireReader
import io.fidelitycard.crypto.wire.WireWriter
import io.fidelitycard.crypto.wire.readAndVerifyHeader
import java.security.SecureRandom
import java.time.Instant

/**
 * Closes out a redemption (SPEC/SPECS.md §5.4 / §6.3): the issuer's receipt
 * that [redeemedCount] stamps for this program were verified and exchanged
 * for the reward. Since stamps are unordered (see [StampToken]) and not
 * bound to a collector identity, this certifies a count, not a range or a
 * card - the collector already knows exactly which stamp IDs it submitted
 * and deletes those locally; the issuer's own record of which specific
 * stamp IDs are now spent lives in its redeemed-stamps store, not in this
 * certificate.
 */
class RedemptionCertificate private constructor(
    val programId: String,
    val redeemedCount: Int,
    val redeemedAt: Instant,
    private val redemptionId: ByteArray,
    private val signature: ByteArray,
) {

    fun toWireBytes(): ByteArray =
        WireWriter()
            .apply { writeSignedPayload(this, programId, redeemedCount, redeemedAt, redemptionId) }
            .writeFixedBytes(signature, SIGNATURE_LENGTH_BYTES)
            .toByteArray()

    companion object {
        private const val REDEMPTION_ID_LENGTH_BYTES = 16

        fun issue(
            issuer: SigningKeyPair,
            programId: String,
            redeemedCount: Int,
            redeemedAt: Instant = Instant.now(),
            redemptionId: ByteArray = randomRedemptionId(),
        ): RedemptionCertificate {
            require(redeemedCount > 0) { "redeemedCount must be positive, was $redeemedCount" }
            require(redemptionId.size == REDEMPTION_ID_LENGTH_BYTES) {
                "redemptionId must be $REDEMPTION_ID_LENGTH_BYTES bytes, was ${redemptionId.size}"
            }
            val redeemedAt = Instant.ofEpochMilli(redeemedAt.toEpochMilli())

            val payload = WireWriter()
                .apply { writeSignedPayload(this, programId, redeemedCount, redeemedAt, redemptionId) }
                .toByteArray()
            val signature = issuer.sign(payload)

            return RedemptionCertificate(programId, redeemedCount, redeemedAt, redemptionId, signature)
        }

        fun parseAndVerify(bytes: ByteArray, issuerPublicKey: VerifyingKey): RedemptionCertificate {
            val reader = WireReader(bytes)
            reader.readAndVerifyHeader(MessageTag.REDEMPTION)

            val programId = reader.readString()
            val redeemedCount = reader.readInt32()
            val redeemedAt = Instant.ofEpochMilli(reader.readInt64())
            val redemptionId = reader.readFixedBytes(REDEMPTION_ID_LENGTH_BYTES)
            val signature = reader.readFixedBytes(SIGNATURE_LENGTH_BYTES)
            reader.requireFullyConsumed()

            val payload = WireWriter()
                .apply { writeSignedPayload(this, programId, redeemedCount, redeemedAt, redemptionId) }
                .toByteArray()
            if (!issuerPublicKey.verify(payload, signature)) {
                throw InvalidSignatureException(
                    "RedemptionCertificate signature does not verify against the given issuer key",
                )
            }

            return RedemptionCertificate(programId, redeemedCount, redeemedAt, redemptionId, signature)
        }

        private fun writeSignedPayload(
            writer: WireWriter,
            programId: String,
            redeemedCount: Int,
            redeemedAt: Instant,
            redemptionId: ByteArray,
        ) {
            writer.writeByte(WIRE_VERSION)
            writer.writeByte(MessageTag.REDEMPTION)
            writer.writeString(programId)
            writer.writeInt32(redeemedCount)
            writer.writeInt64(redeemedAt.toEpochMilli())
            writer.writeFixedBytes(redemptionId, REDEMPTION_ID_LENGTH_BYTES)
        }

        private fun randomRedemptionId(): ByteArray =
            ByteArray(REDEMPTION_ID_LENGTH_BYTES).also { SecureRandom().nextBytes(it) }
    }
}
