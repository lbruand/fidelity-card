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
 * A single, unforgeable credit toward a card (SPEC/SPECS.md §5.3).
 * Deliberately unordered: stamps are independent, uniquely-identified
 * grants rather than a numbered sequence. A [StampToken] on its own only
 * proves "the issuer signed this exact (program, card, stamp, moment)" -
 * nothing about how many other stamps this card has, or in what order.
 * Preventing the same purchase from minting more than one stamp is treated
 * as an operational/trust matter for the issuer (same as a paper card),
 * not something the protocol enforces; preventing the same stamp from
 * being redeemed twice is enforced (see [RedemptionCertificate]).
 */
class StampToken private constructor(
    val programId: String,
    val cardId: String,
    val stampId: ByteArray,
    val issuedAt: Instant,
    private val signature: ByteArray,
) {

    fun toWireBytes(): ByteArray =
        WireWriter()
            .apply { writeSignedPayload(this, programId, cardId, stampId, issuedAt) }
            .writeFixedBytes(signature, SIGNATURE_LENGTH_BYTES)
            .toByteArray()

    /**
     * `stamp_id || issued_at || signature`, without `program_id`/`card_id` -
     * for batching many stamps for the same card into one redemption request
     * without repeating fields that are identical across the whole batch
     * (SPEC/SPECS.md §6.3/§11). Pairs with [parseAndVerifyCompactProof],
     * which the caller must supply the correct shared `program_id`/`card_id`
     * to - that's what a full [toWireBytes] token would otherwise carry
     * inline, and its absence here is exactly the space saving.
     */
    fun toCompactProofBytes(): ByteArray =
        WireWriter()
            .writeFixedBytes(stampId, STAMP_ID_LENGTH_BYTES)
            .writeInt64(issuedAt.toEpochMilli())
            .writeFixedBytes(signature, SIGNATURE_LENGTH_BYTES)
            .toByteArray()

    companion object {
        const val STAMP_ID_LENGTH_BYTES = 16
        const val COMPACT_PROOF_LENGTH_BYTES = STAMP_ID_LENGTH_BYTES + 8 + SIGNATURE_LENGTH_BYTES

        fun mint(
            issuer: SigningKeyPair,
            programId: String,
            cardId: String,
            issuedAt: Instant = Instant.now(),
            stampId: ByteArray = randomStampId(),
        ): StampToken {
            require(stampId.size == STAMP_ID_LENGTH_BYTES) {
                "stampId must be $STAMP_ID_LENGTH_BYTES bytes, was ${stampId.size}"
            }
            val issuedAt = Instant.ofEpochMilli(issuedAt.toEpochMilli())

            val payload = WireWriter()
                .apply { writeSignedPayload(this, programId, cardId, stampId, issuedAt) }
                .toByteArray()
            val signature = issuer.sign(payload)

            return StampToken(programId, cardId, stampId, issuedAt, signature)
        }

        fun parseAndVerify(bytes: ByteArray, issuerPublicKey: VerifyingKey): StampToken {
            val reader = WireReader(bytes)
            reader.readAndVerifyHeader(MessageTag.STAMP)

            val programId = reader.readString()
            val cardId = reader.readString()
            val stampId = reader.readFixedBytes(STAMP_ID_LENGTH_BYTES)
            val issuedAt = Instant.ofEpochMilli(reader.readInt64())
            val signature = reader.readFixedBytes(SIGNATURE_LENGTH_BYTES)
            reader.requireFullyConsumed()

            val payload = WireWriter()
                .apply { writeSignedPayload(this, programId, cardId, stampId, issuedAt) }
                .toByteArray()
            if (!issuerPublicKey.verify(payload, signature)) {
                throw InvalidSignatureException("StampToken signature does not verify against the given issuer key")
            }

            return StampToken(programId, cardId, stampId, issuedAt, signature)
        }

        /** Pairs with [StampToken.toCompactProofBytes] - see that method's doc for why `programId`/`cardId` are separate parameters. */
        fun parseAndVerifyCompactProof(
            compactProofBytes: ByteArray,
            programId: String,
            cardId: String,
            issuerPublicKey: VerifyingKey,
        ): StampToken {
            require(compactProofBytes.size == COMPACT_PROOF_LENGTH_BYTES) {
                "Compact proof must be $COMPACT_PROOF_LENGTH_BYTES bytes, was ${compactProofBytes.size}"
            }
            val reader = WireReader(compactProofBytes)
            val stampId = reader.readFixedBytes(STAMP_ID_LENGTH_BYTES)
            val issuedAt = Instant.ofEpochMilli(reader.readInt64())
            val signature = reader.readFixedBytes(SIGNATURE_LENGTH_BYTES)
            reader.requireFullyConsumed()

            val payload = WireWriter()
                .apply { writeSignedPayload(this, programId, cardId, stampId, issuedAt) }
                .toByteArray()
            if (!issuerPublicKey.verify(payload, signature)) {
                throw InvalidSignatureException("StampToken signature does not verify against the given issuer key")
            }

            return StampToken(programId, cardId, stampId, issuedAt, signature)
        }

        private fun writeSignedPayload(
            writer: WireWriter,
            programId: String,
            cardId: String,
            stampId: ByteArray,
            issuedAt: Instant,
        ) {
            writer.writeByte(WIRE_VERSION)
            writer.writeByte(MessageTag.STAMP)
            writer.writeString(programId)
            writer.writeString(cardId)
            writer.writeFixedBytes(stampId, STAMP_ID_LENGTH_BYTES)
            writer.writeInt64(issuedAt.toEpochMilli())
        }

        private fun randomStampId(): ByteArray =
            ByteArray(STAMP_ID_LENGTH_BYTES).also { SecureRandom().nextBytes(it) }
    }
}
