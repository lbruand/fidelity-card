package io.fidelitycard.crypto

import io.fidelitycard.crypto.wire.MessageTag
import io.fidelitycard.crypto.wire.SIGNATURE_LENGTH_BYTES
import io.fidelitycard.crypto.wire.WIRE_VERSION
import io.fidelitycard.crypto.wire.WireReader
import io.fidelitycard.crypto.wire.WireWriter
import io.fidelitycard.crypto.wire.readAndVerifyHeader

/**
 * The terms of a loyalty Program, self-signed by the issuer (SPEC/SPECS.md
 * §5.1). This is the root of trust for one program: a collector who has
 * verified a `ProgramManifest` has cryptographic proof of exactly what the
 * issuer offered (name, threshold, reward), and pins [issuerPublicKey] as
 * the key every later Card Certificate, Stamp Token and Redemption
 * Certificate for this program must be signed by.
 */
class ProgramManifest private constructor(
    val programId: String,
    val issuerPublicKey: VerifyingKey,
    val name: String,
    val threshold: Int,
    val reward: String,
    private val signature: ByteArray,
) {

    fun toWireBytes(): ByteArray =
        WireWriter()
            .apply { writeSignedPayload(this, programId, issuerPublicKey, name, threshold, reward) }
            .writeFixedBytes(signature, SIGNATURE_LENGTH_BYTES)
            .toByteArray()

    companion object {

        fun issue(
            issuer: SigningKeyPair,
            name: String,
            threshold: Int,
            reward: String,
            programIdNonce: ByteArray? = null,
        ): ProgramManifest {
            require(threshold > 0) { "threshold must be positive, was $threshold" }

            val programId = if (programIdNonce == null) {
                ProgramId.derive(issuer.publicKey, name)
            } else {
                ProgramId.derive(issuer.publicKey, name, programIdNonce)
            }
            val payload = WireWriter()
                .apply { writeSignedPayload(this, programId, issuer.publicKey, name, threshold, reward) }
                .toByteArray()
            val signature = issuer.sign(payload)

            return ProgramManifest(programId, issuer.publicKey, name, threshold, reward, signature)
        }

        fun parseAndVerify(bytes: ByteArray): ProgramManifest {
            val reader = WireReader(bytes)
            val (programId, issuerPublicKey, name, threshold, reward) = readSignedFields(reader)
            val signature = reader.readFixedBytes(SIGNATURE_LENGTH_BYTES)
            reader.requireFullyConsumed()

            val payload = WireWriter()
                .apply { writeSignedPayload(this, programId, issuerPublicKey, name, threshold, reward) }
                .toByteArray()
            if (!issuerPublicKey.verify(payload, signature)) {
                throw InvalidSignatureException("ProgramManifest signature does not verify against its own issuer key")
            }

            return ProgramManifest(programId, issuerPublicKey, name, threshold, reward, signature)
        }

        private fun writeSignedPayload(
            writer: WireWriter,
            programId: String,
            issuerPublicKey: VerifyingKey,
            name: String,
            threshold: Int,
            reward: String,
        ) {
            writer.writeByte(WIRE_VERSION)
            writer.writeByte(MessageTag.PROGRAM)
            writer.writeString(programId)
            writer.writeFixedBytes(issuerPublicKey.bytes, VerifyingKey.LENGTH_BYTES)
            writer.writeString(name)
            writer.writeInt32(threshold)
            writer.writeString(reward)
        }

        private data class SignedFields(
            val programId: String,
            val issuerPublicKey: VerifyingKey,
            val name: String,
            val threshold: Int,
            val reward: String,
        )

        private fun readSignedFields(reader: WireReader): SignedFields {
            reader.readAndVerifyHeader(MessageTag.PROGRAM)

            val programId = reader.readString()
            val issuerPublicKey = VerifyingKey(reader.readFixedBytes(VerifyingKey.LENGTH_BYTES))
            val name = reader.readString()
            val threshold = reader.readInt32()
            val reward = reader.readString()
            return SignedFields(programId, issuerPublicKey, name, threshold, reward)
        }
    }
}
