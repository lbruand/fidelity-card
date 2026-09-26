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
        val stamp = StampToken.mint(issuer, programId = "PROGRAM123", cardId = "card-1", serial = 4)

        val parsed = StampToken.parseAndVerify(stamp.toWireBytes(), issuer.publicKey)

        assertEquals("PROGRAM123", parsed.programId)
        assertEquals("card-1", parsed.cardId)
        assertEquals(4, parsed.serial)
        assertEquals(stamp.issuedAt, parsed.issuedAt)
    }

    @Test
    fun `two stamps for the same card and serial still differ, because of the nonce`() {
        val a = StampToken.mint(issuer, "PROGRAM123", "card-1", serial = 1)
        val b = StampToken.mint(issuer, "PROGRAM123", "card-1", serial = 1)

        assertNotEquals(a.toWireBytes().toList(), b.toWireBytes().toList())
    }

    @Test
    fun `rejects a stamp not signed by the expected issuer`() {
        val stamp = StampToken.mint(issuer, "PROGRAM123", "card-1", serial = 1)
        val someoneElse = SigningKeyPair.generate().publicKey

        assertThrows(InvalidSignatureException::class.java) {
            StampToken.parseAndVerify(stamp.toWireBytes(), someoneElse)
        }
    }

    @Test
    fun `rejects a stamp whose serial was changed after signing`() {
        val bytes = StampToken.mint(issuer, "PROGRAM123", "card-1", serial = 1).toWireBytes()

        val tampered = bytes.copyOf()
        val lastPayloadByteIndex = tampered.size - 1 - SIGNATURE_LENGTH_BYTES
        tampered[lastPayloadByteIndex] = (tampered[lastPayloadByteIndex].toInt() xor 0x01).toByte()

        assertThrows(InvalidSignatureException::class.java) {
            StampToken.parseAndVerify(tampered, issuer.publicKey)
        }
    }

    @Test
    fun `serial must be positive`() {
        assertThrows(IllegalArgumentException::class.java) {
            StampToken.mint(issuer, "PROGRAM123", "card-1", serial = 0)
        }
    }
}
