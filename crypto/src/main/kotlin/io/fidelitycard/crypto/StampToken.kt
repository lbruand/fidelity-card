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
 * A single, unforgeable credit toward a card (SPEC/SPECS.md §5.3). The
 * [serial] is assigned by the issuer and must increase by exactly one per
 * card; enforcing that contiguity is the collector app's job (it is what
 * makes replaying an old stamp detectable), not this type's — a
 * [StampToken] on its own only proves "the issuer signed this exact
 * (program, card, serial, moment)," nothing about ordering relative to
 * other stamps.
 */
class StampToken private constructor(
    val programId: String,
    val cardId: String,
    val serial: Int,
    val issuedAt: Instant,
    private val nonce: ByteArray,
    private val signature: ByteArray,
) {

    fun toWireBytes(): ByteArray =
        WireWriter()
            .apply { writeSignedPayload(this, programId, cardId, serial, issuedAt, nonce) }
            .writeFixedBytes(signature, SIGNATURE_LENGTH_BYTES)
            .toByteArray()

    companion object {
        private const val NONCE_LENGTH_BYTES = 8

        fun mint(
            issuer: SigningKeyPair,
            programId: String,
            cardId: String,
            serial: Int,
            issuedAt: Instant = Instant.now(),
            nonce: ByteArray = randomNonce(),
        ): StampToken {
            require(serial > 0) { "serial must be positive, was $serial" }
            require(nonce.size == NONCE_LENGTH_BYTES) {
                "Nonce must be $NONCE_LENGTH_BYTES bytes, was ${nonce.size}"
            }
            val issuedAt = Instant.ofEpochMilli(issuedAt.toEpochMilli())

            val payload = WireWriter()
                .apply { writeSignedPayload(this, programId, cardId, serial, issuedAt, nonce) }
                .toByteArray()
            val signature = issuer.sign(payload)

            return StampToken(programId, cardId, serial, issuedAt, nonce, signature)
        }

        fun parseAndVerify(bytes: ByteArray, issuerPublicKey: VerifyingKey): StampToken {
            val reader = WireReader(bytes)
            reader.readAndVerifyHeader(MessageTag.STAMP)

            val programId = reader.readString()
            val cardId = reader.readString()
            val serial = reader.readInt32()
            val issuedAt = Instant.ofEpochMilli(reader.readInt64())
            val nonce = reader.readFixedBytes(NONCE_LENGTH_BYTES)
            val signature = reader.readFixedBytes(SIGNATURE_LENGTH_BYTES)
            reader.requireFullyConsumed()

            val payload = WireWriter()
                .apply { writeSignedPayload(this, programId, cardId, serial, issuedAt, nonce) }
                .toByteArray()
            if (!issuerPublicKey.verify(payload, signature)) {
                throw InvalidSignatureException("StampToken signature does not verify against the given issuer key")
            }

            return StampToken(programId, cardId, serial, issuedAt, nonce, signature)
        }

        private fun writeSignedPayload(
            writer: WireWriter,
            programId: String,
            cardId: String,
            serial: Int,
            issuedAt: Instant,
            nonce: ByteArray,
        ) {
            writer.writeByte(WIRE_VERSION)
            writer.writeByte(MessageTag.STAMP)
            writer.writeString(programId)
            writer.writeString(cardId)
            writer.writeInt32(serial)
            writer.writeInt64(issuedAt.toEpochMilli())
            writer.writeFixedBytes(nonce, NONCE_LENGTH_BYTES)
        }

        private fun randomNonce(): ByteArray =
            ByteArray(NONCE_LENGTH_BYTES).also { SecureRandom().nextBytes(it) }
    }
}
