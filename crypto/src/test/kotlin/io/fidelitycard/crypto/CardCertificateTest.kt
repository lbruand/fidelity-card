package io.fidelitycard.crypto

import io.fidelitycard.crypto.wire.SIGNATURE_LENGTH_BYTES
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class CardCertificateTest {

    private val issuer = SigningKeyPair.generate()
    private val collector = SigningKeyPair.generate()

    @Test
    fun `issuing and parsing round trips every field`() {
        val cert = CardCertificate.issue(
            issuer = issuer,
            programId = "PROGRAM123",
            cardId = "card-1",
            collectorPublicKey = collector.publicKey,
        )

        val parsed = CardCertificate.parseAndVerify(cert.toWireBytes(), issuer.publicKey)

        assertEquals("PROGRAM123", parsed.programId)
        assertEquals("card-1", parsed.cardId)
        assertEquals(collector.publicKey, parsed.collectorPublicKey)
        assertEquals(cert.issuedAt, parsed.issuedAt)
    }

    @Test
    fun `rejects a certificate not signed by the expected issuer`() {
        val cert = CardCertificate.issue(issuer, "PROGRAM123", "card-1", collector.publicKey)
        val someoneElse = SigningKeyPair.generate().publicKey

        assertThrows(InvalidSignatureException::class.java) {
            CardCertificate.parseAndVerify(cert.toWireBytes(), someoneElse)
        }
    }

    @Test
    fun `rejects a certificate whose signed payload was altered`() {
        val bytes = CardCertificate.issue(issuer, "PROGRAM123", "card-1", collector.publicKey).toWireBytes()

        val tampered = bytes.copyOf()
        val lastPayloadByteIndex = tampered.size - 1 - SIGNATURE_LENGTH_BYTES
        tampered[lastPayloadByteIndex] = (tampered[lastPayloadByteIndex].toInt() xor 0x01).toByte()

        assertThrows(InvalidSignatureException::class.java) {
            CardCertificate.parseAndVerify(tampered, issuer.publicKey)
        }
    }
}
