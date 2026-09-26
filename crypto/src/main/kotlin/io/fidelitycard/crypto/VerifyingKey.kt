package io.fidelitycard.crypto

import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import java.util.Base64

/**
 * An Ed25519 public key: the identity an issuer or a collector presents to
 * the other side, and the key any signature is checked against.
 */
class VerifyingKey(bytes: ByteArray) {

    val bytes: ByteArray = bytes.copyOf()

    init {
        require(bytes.size == LENGTH_BYTES) {
            "VerifyingKey must be $LENGTH_BYTES bytes, was ${bytes.size}"
        }
    }

    fun verify(message: ByteArray, signature: ByteArray): Boolean {
        val signer = Ed25519Signer()
        signer.init(false, Ed25519PublicKeyParameters(bytes, 0))
        signer.update(message, 0, message.size)
        return signer.verifySignature(signature)
    }

    fun toBase64(): String = Base64.getEncoder().encodeToString(bytes)

    override fun equals(other: Any?): Boolean =
        other is VerifyingKey && bytes.contentEquals(other.bytes)

    override fun hashCode(): Int = bytes.contentHashCode()

    override fun toString(): String = "VerifyingKey(${toBase64()})"

    companion object {
        const val LENGTH_BYTES = 32

        fun fromBase64(encoded: String): VerifyingKey =
            VerifyingKey(Base64.getDecoder().decode(encoded))
    }
}
