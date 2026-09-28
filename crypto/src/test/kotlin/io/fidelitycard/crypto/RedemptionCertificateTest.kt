package io.fidelitycard.crypto

import io.fidelitycard.crypto.wire.SIGNATURE_LENGTH_BYTES
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class RedemptionCertificateTest {

    private val issuer = SigningKeyPair.generate()

    @Test
    fun `issuing and parsing round trips every field`() {
        val cert = RedemptionCertificate.issue(
            issuer = issuer,
            programId = "PROGRAM123",
            redeemedCount = 10,
        )

        val parsed = RedemptionCertificate.parseAndVerify(cert.toWireBytes(), issuer.publicKey)

        assertEquals("PROGRAM123", parsed.programId)
        assertEquals(10, parsed.redeemedCount)
        assertEquals(cert.redeemedAt, parsed.redeemedAt)
    }

    @Test
    fun `rejects a certificate not signed by the expected issuer`() {
        val cert = RedemptionCertificate.issue(issuer, "PROGRAM123", redeemedCount = 10)
        val someoneElse = SigningKeyPair.generate().publicKey

        assertThrows(InvalidSignatureException::class.java) {
            RedemptionCertificate.parseAndVerify(cert.toWireBytes(), someoneElse)
        }
    }

    @Test
    fun `rejects a certificate whose signed payload was altered`() {
        val bytes = RedemptionCertificate.issue(issuer, "PROGRAM123", redeemedCount = 10)
            .toWireBytes()

        val tampered = bytes.copyOf()
        val lastPayloadByteIndex = tampered.size - 1 - SIGNATURE_LENGTH_BYTES
        tampered[lastPayloadByteIndex] = (tampered[lastPayloadByteIndex].toInt() xor 0x01).toByte()

        assertThrows(InvalidSignatureException::class.java) {
            RedemptionCertificate.parseAndVerify(tampered, issuer.publicKey)
        }
    }

    @Test
    fun `redeemedCount must be positive`() {
        assertThrows(IllegalArgumentException::class.java) {
            RedemptionCertificate.issue(issuer, "PROGRAM123", redeemedCount = 0)
        }
    }
}
