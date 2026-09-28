package io.fidelitycard.crypto

import io.fidelitycard.crypto.wire.SIGNATURE_LENGTH_BYTES
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class ProgramManifestTest {

    private val issuer = SigningKeyPair.generate()

    @Test
    fun `issuing and parsing round trips every field`() {
        val manifest = ProgramManifest.issue(
            issuer = issuer,
            name = "Joe's Coffee",
            threshold = 10,
            reward = "Free coffee",
            color = 0xFF00897B.toInt(),
            icon = "☕",
        )

        val parsed = ProgramManifest.parseAndVerify(manifest.toWireBytes())

        assertEquals(manifest.programId, parsed.programId)
        assertEquals(issuer.publicKey, parsed.issuerPublicKey)
        assertEquals("Joe's Coffee", parsed.name)
        assertEquals(10, parsed.threshold)
        assertEquals("Free coffee", parsed.reward)
        assertEquals(0xFF00897B.toInt(), parsed.color)
        assertEquals("☕", parsed.icon)
    }

    @Test
    fun `rejects the manifest if a signed payload byte is changed after signing`() {
        val bytes = ProgramManifest.issue(issuer, "Joe's Coffee", threshold = 10, reward = "Free coffee", color = 0xFF00897B.toInt(), icon = "☕")
            .toWireBytes()

        // The signature is always the trailing SIGNATURE_LENGTH_BYTES bytes,
        // so the byte just before it is always part of the signed payload
        // (here, the last byte of "icon") regardless of field layout.
        val tampered = bytes.copyOf()
        val lastPayloadByteIndex = tampered.size - 1 - SIGNATURE_LENGTH_BYTES
        tampered[lastPayloadByteIndex] = (tampered[lastPayloadByteIndex].toInt() xor 0x01).toByte()

        assertThrows(InvalidSignatureException::class.java) {
            ProgramManifest.parseAndVerify(tampered)
        }
    }

    @Test
    fun `rejects the manifest if the signature itself is corrupted`() {
        val bytes = ProgramManifest.issue(issuer, "Joe's Coffee", threshold = 10, reward = "Free coffee", color = 0xFF00897B.toInt(), icon = "☕")
            .toWireBytes()

        val tampered = bytes.copyOf()
        tampered[tampered.size - 1] = (tampered[tampered.size - 1].toInt() xor 0x01).toByte()

        assertThrows(InvalidSignatureException::class.java) {
            ProgramManifest.parseAndVerify(tampered)
        }
    }

    @Test
    fun `threshold must be positive`() {
        assertThrows(IllegalArgumentException::class.java) {
            ProgramManifest.issue(issuer, "Joe's Coffee", threshold = 0, reward = "Free coffee", color = 0xFF00897B.toInt(), icon = "☕")
        }
    }
}
