package io.fidelitycard.crypto

import org.bouncycastle.util.encoders.Base32
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Derives a Program's public identifier, per SPEC/SPECS.md §5.1:
 * `base32( SHA-256( issuer_pubkey || program_name || nonce_16B ) )[:26]`.
 *
 * The nonce is what lets the same issuer run two programs with the same
 * name without their ids colliding; it is carried alongside the program so
 * anyone can recompute and check the id, but is not secret.
 */
object ProgramId {

    private const val NONCE_LENGTH_BYTES = 16
    private const val ID_LENGTH_CHARS = 26

    fun derive(
        issuerPublicKey: VerifyingKey,
        name: String,
        nonce: ByteArray = randomNonce(),
    ): String {
        require(nonce.size == NONCE_LENGTH_BYTES) {
            "Nonce must be $NONCE_LENGTH_BYTES bytes, was ${nonce.size}"
        }
        val digestInput = issuerPublicKey.bytes + name.toByteArray(StandardCharsets.UTF_8) + nonce
        val digest = MessageDigest.getInstance("SHA-256").digest(digestInput)
        val encoded = String(Base32.encode(digest), StandardCharsets.US_ASCII)
        return encoded.take(ID_LENGTH_CHARS)
    }

    private fun randomNonce(): ByteArray =
        ByteArray(NONCE_LENGTH_BYTES).also { SecureRandom().nextBytes(it) }
}
