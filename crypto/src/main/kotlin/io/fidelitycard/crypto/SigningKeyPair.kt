package io.fidelitycard.crypto

import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import java.security.SecureRandom

/**
 * An Ed25519 key pair. This is the whole of an issuer's or a collector's
 * identity: there is no separate account, no server-issued credential.
 * Whoever holds the private key material behind a [SigningKeyPair] can sign
 * as that identity; whoever only has the [VerifyingKey] can check, but not
 * produce, signatures.
 */
class SigningKeyPair private constructor(
    private val privateKey: Ed25519PrivateKeyParameters,
    val publicKey: VerifyingKey,
) {

    fun sign(message: ByteArray): ByteArray {
        val signer = Ed25519Signer()
        signer.init(true, privateKey)
        signer.update(message, 0, message.size)
        return signer.generateSignature()
    }

    companion object {
        private const val SEED_LENGTH_BYTES = 32

        fun generate(): SigningKeyPair {
            val seed = ByteArray(SEED_LENGTH_BYTES)
            SecureRandom().nextBytes(seed)
            return fromSeed(seed)
        }

        fun fromSeed(seed: ByteArray): SigningKeyPair {
            require(seed.size == SEED_LENGTH_BYTES) {
                "Seed must be $SEED_LENGTH_BYTES bytes, was ${seed.size}"
            }
            val privateKey = Ed25519PrivateKeyParameters(seed, 0)
            val publicKey = VerifyingKey(privateKey.generatePublicKey().encoded)
            return SigningKeyPair(privateKey, publicKey)
        }
    }
}
