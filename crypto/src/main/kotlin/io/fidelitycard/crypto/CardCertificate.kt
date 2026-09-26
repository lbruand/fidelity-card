package io.fidelitycard.crypto

import io.fidelitycard.crypto.wire.MessageTag
import io.fidelitycard.crypto.wire.SIGNATURE_LENGTH_BYTES
import io.fidelitycard.crypto.wire.WIRE_VERSION
import io.fidelitycard.crypto.wire.WireReader
import io.fidelitycard.crypto.wire.WireWriter
import io.fidelitycard.crypto.wire.readAndVerifyHeader
import java.time.Instant

/**
 * Issued once, at enrollment (SPEC/SPECS.md §5.2 / §6.1): binds a specific
 * collector key to a specific card within a program, so a stamp meant for
 * one collector can never be grafted onto another's card. Verified against
 * the issuer's [VerifyingKey], which the caller must already trust (pinned
 * from that program's [ProgramManifest] — this type carries no key of its
 * own to bootstrap trust from).
 */
class CardCertificate private constructor(
    val programId: String,
    val cardId: String,
    val collectorPublicKey: VerifyingKey,
    val issuedAt: Instant,
    private val signature: ByteArray,
) {

    fun toWireBytes(): ByteArray =
        WireWriter()
            .apply { writeSignedPayload(this, programId, cardId, collectorPublicKey, issuedAt) }
            .writeFixedBytes(signature, SIGNATURE_LENGTH_BYTES)
            .toByteArray()

    companion object {

        fun issue(
            issuer: SigningKeyPair,
            programId: String,
            cardId: String,
            collectorPublicKey: VerifyingKey,
            issuedAt: Instant = Instant.now(),
        ): CardCertificate {
            val issuedAt = Instant.ofEpochMilli(issuedAt.toEpochMilli())
            val payload = WireWriter()
                .apply { writeSignedPayload(this, programId, cardId, collectorPublicKey, issuedAt) }
                .toByteArray()
            val signature = issuer.sign(payload)
            return CardCertificate(programId, cardId, collectorPublicKey, issuedAt, signature)
        }

        fun parseAndVerify(bytes: ByteArray, issuerPublicKey: VerifyingKey): CardCertificate {
            val reader = WireReader(bytes)
            reader.readAndVerifyHeader(MessageTag.CARD_CERT)

            val programId = reader.readString()
            val cardId = reader.readString()
            val collectorPublicKey = VerifyingKey(reader.readFixedBytes(VerifyingKey.LENGTH_BYTES))
            val issuedAt = Instant.ofEpochMilli(reader.readInt64())
            val signature = reader.readFixedBytes(SIGNATURE_LENGTH_BYTES)
            reader.requireFullyConsumed()

            val payload = WireWriter()
                .apply { writeSignedPayload(this, programId, cardId, collectorPublicKey, issuedAt) }
                .toByteArray()
            if (!issuerPublicKey.verify(payload, signature)) {
                throw InvalidSignatureException("CardCertificate signature does not verify against the given issuer key")
            }

            return CardCertificate(programId, cardId, collectorPublicKey, issuedAt, signature)
        }

        private fun writeSignedPayload(
            writer: WireWriter,
            programId: String,
            cardId: String,
            collectorPublicKey: VerifyingKey,
            issuedAt: Instant,
        ) {
            writer.writeByte(WIRE_VERSION)
            writer.writeByte(MessageTag.CARD_CERT)
            writer.writeString(programId)
            writer.writeString(cardId)
            writer.writeFixedBytes(collectorPublicKey.bytes, VerifyingKey.LENGTH_BYTES)
            writer.writeInt64(issuedAt.toEpochMilli())
        }
    }
}
