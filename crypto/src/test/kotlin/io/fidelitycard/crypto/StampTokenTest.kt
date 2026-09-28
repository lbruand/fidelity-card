package io.fidelitycard.crypto

import io.fidelitycard.crypto.wire.SIGNATURE_LENGTH_BYTES
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class StampTokenTest {

    private val issuer = SigningKeyPair.generate()

    @Test
    fun `minting and parsing round trips every field`() {
        val stamp = StampToken.mint(issuer, programId = "PROGRAM123")

        val parsed = StampToken.parseAndVerify(stamp.toWireBytes(), issuer.publicKey)

        assertEquals("PROGRAM123", parsed.programId)
        assertEquals(stamp.stampId.toList(), parsed.stampId.toList())
        assertEquals(stamp.issuedAt, parsed.issuedAt)
    }

    @Test
    fun `two stamps for the same program are still different tokens, because of the random stamp id`() {
        val a = StampToken.mint(issuer, "PROGRAM123")
        val b = StampToken.mint(issuer, "PROGRAM123")

        assertNotEquals(a.stampId.toList(), b.stampId.toList())
        assertNotEquals(a.toWireBytes().toList(), b.toWireBytes().toList())
    }

    @Test
    fun `rejects a stamp not signed by the expected issuer`() {
        val stamp = StampToken.mint(issuer, "PROGRAM123")
        val someoneElse = SigningKeyPair.generate().publicKey

        assertThrows(InvalidSignatureException::class.java) {
            StampToken.parseAndVerify(stamp.toWireBytes(), someoneElse)
        }
    }

    @Test
    fun `rejects a stamp whose signed payload was altered`() {
        val bytes = StampToken.mint(issuer, "PROGRAM123").toWireBytes()

        val tampered = bytes.copyOf()
        val lastPayloadByteIndex = tampered.size - 1 - SIGNATURE_LENGTH_BYTES
        tampered[lastPayloadByteIndex] = (tampered[lastPayloadByteIndex].toInt() xor 0x01).toByte()

        assertThrows(InvalidSignatureException::class.java) {
            StampToken.parseAndVerify(tampered, issuer.publicKey)
        }
    }

    @Test
    fun `stampId must be exactly STAMP_ID_LENGTH_BYTES`() {
        assertThrows(IllegalArgumentException::class.java) {
            StampToken.mint(issuer, "PROGRAM123", stampId = ByteArray(4))
        }
    }

    @Test
    fun `compact proof is exactly COMPACT_PROOF_LENGTH_BYTES`() {
        val stamp = StampToken.mint(issuer, "PROGRAM123")

        assertEquals(StampToken.COMPACT_PROOF_LENGTH_BYTES, stamp.toCompactProofBytes().size)
    }

    @Test
    fun `compact proof round trips when given back the same program id`() {
        val stamp = StampToken.mint(issuer, "PROGRAM123")

        val parsed = StampToken.parseAndVerifyCompactProof(stamp.toCompactProofBytes(), "PROGRAM123", issuer.publicKey)

        assertEquals("PROGRAM123", parsed.programId)
        assertEquals(stamp.stampId.toList(), parsed.stampId.toList())
        assertEquals(stamp.issuedAt, parsed.issuedAt)
    }

    @Test
    fun `compact proof verification fails if reconstructed against the wrong program id`() {
        // This is the security-critical case: a compact proof only carries
        // stamp_id + issued_at + signature, trusting the caller to supply the
        // matching program_id from shared batch context. Supplying the wrong
        // one must not silently verify - it must reconstruct different signed
        // bytes than were actually signed, and fail.
        val stamp = StampToken.mint(issuer, "PROGRAM123")

        assertThrows(InvalidSignatureException::class.java) {
            StampToken.parseAndVerifyCompactProof(stamp.toCompactProofBytes(), "SOMEONE-ELSES-PROGRAM", issuer.publicKey)
        }
    }

    @Test
    fun `compact proof bytes must be exactly COMPACT_PROOF_LENGTH_BYTES`() {
        assertThrows(IllegalArgumentException::class.java) {
            StampToken.parseAndVerifyCompactProof(ByteArray(10), "PROGRAM123", issuer.publicKey)
        }
    }
}
