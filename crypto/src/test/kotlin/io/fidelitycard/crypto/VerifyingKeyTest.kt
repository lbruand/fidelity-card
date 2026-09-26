package io.fidelitycard.crypto

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class VerifyingKeyTest {

    @Test
    fun `base64 round trip preserves the key`() {
        val original = SigningKeyPair.generate().publicKey

        val decoded = VerifyingKey.fromBase64(original.toBase64())

        assertEquals(original, decoded)
    }

    @Test
    fun `keys with equal bytes are equal even if not the same instance`() {
        val bytes = SigningKeyPair.generate().publicKey.bytes

        assertEquals(VerifyingKey(bytes.copyOf()), VerifyingKey(bytes.copyOf()))
    }

    @Test
    fun `rejects a byte array that is not 32 bytes`() {
        assertThrows(IllegalArgumentException::class.java) {
            VerifyingKey(ByteArray(31))
        }
    }
}
