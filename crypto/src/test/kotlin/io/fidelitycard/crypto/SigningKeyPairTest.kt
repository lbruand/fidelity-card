package io.fidelitycard.crypto

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SigningKeyPairTest {

    @Test
    fun `generated public key is 32 bytes`() {
        val keyPair = SigningKeyPair.generate()

        assertEquals(32, keyPair.publicKey.bytes.size)
    }

    @Test
    fun `two generated key pairs are different`() {
        val a = SigningKeyPair.generate()
        val b = SigningKeyPair.generate()

        assertNotEquals(a.publicKey, b.publicKey)
    }

    @Test
    fun `signature verifies against the signer's own public key`() {
        val keyPair = SigningKeyPair.generate()
        val message = "hello fidelity card".toByteArray()

        val signature = keyPair.sign(message)

        assertTrue(keyPair.publicKey.verify(message, signature))
    }

    @Test
    fun `signature fails to verify if the message is tampered with`() {
        val keyPair = SigningKeyPair.generate()
        val message = "hello fidelity card".toByteArray()
        val signature = keyPair.sign(message)

        val tamperedMessage = "hello fidelity carD".toByteArray()

        assertFalse(keyPair.publicKey.verify(tamperedMessage, signature))
    }

    @Test
    fun `signature fails to verify against a different signer's public key`() {
        val signer = SigningKeyPair.generate()
        val impostor = SigningKeyPair.generate()
        val message = "hello fidelity card".toByteArray()

        val signature = signer.sign(message)

        assertFalse(impostor.publicKey.verify(message, signature))
    }

    @Test
    fun `signature fails to verify if a signature byte is flipped`() {
        val keyPair = SigningKeyPair.generate()
        val message = "hello fidelity card".toByteArray()
        val signature = keyPair.sign(message)
        signature[0] = (signature[0].toInt() xor 0x01).toByte()

        assertFalse(keyPair.publicKey.verify(message, signature))
    }

    @Test
    fun `fromSeed is deterministic`() {
        val seed = ByteArray(32) { it.toByte() }

        val a = SigningKeyPair.fromSeed(seed)
        val b = SigningKeyPair.fromSeed(seed)

        assertEquals(a.publicKey, b.publicKey)
        val message = "determinism check".toByteArray()
        assertArrayEquals(a.sign(message), b.sign(message))
    }

    @Test
    fun `fromSeed rejects a seed that is not 32 bytes`() {
        assertThrows(IllegalArgumentException::class.java) {
            SigningKeyPair.fromSeed(ByteArray(16))
        }
    }
}
