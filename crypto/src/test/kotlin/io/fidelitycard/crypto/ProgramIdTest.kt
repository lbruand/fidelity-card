package io.fidelitycard.crypto

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class ProgramIdTest {

    private val issuer = SigningKeyPair.generate().publicKey
    private val fixedNonce = ByteArray(16) { it.toByte() }

    @Test
    fun `derives a 26 character id`() {
        val id = ProgramId.derive(issuer, "Joe's Coffee", fixedNonce)

        assertEquals(26, id.length)
    }

    @Test
    fun `is deterministic for the same key, name and nonce`() {
        val a = ProgramId.derive(issuer, "Joe's Coffee", fixedNonce)
        val b = ProgramId.derive(issuer, "Joe's Coffee", fixedNonce)

        assertEquals(a, b)
    }

    @Test
    fun `differs when the nonce differs`() {
        val a = ProgramId.derive(issuer, "Joe's Coffee", fixedNonce)
        val b = ProgramId.derive(issuer, "Joe's Coffee", ByteArray(16) { (it + 1).toByte() })

        assertNotEquals(a, b)
    }

    @Test
    fun `differs when the issuer key differs`() {
        val otherIssuer = SigningKeyPair.generate().publicKey

        val a = ProgramId.derive(issuer, "Joe's Coffee", fixedNonce)
        val b = ProgramId.derive(otherIssuer, "Joe's Coffee", fixedNonce)

        assertNotEquals(a, b)
    }

    @Test
    fun `differs when the name differs`() {
        val a = ProgramId.derive(issuer, "Joe's Coffee", fixedNonce)
        val b = ProgramId.derive(issuer, "Jane's Tea", fixedNonce)

        assertNotEquals(a, b)
    }

    @Test
    fun `rejects a nonce that is not 16 bytes`() {
        assertThrows(IllegalArgumentException::class.java) {
            ProgramId.derive(issuer, "Joe's Coffee", ByteArray(8))
        }
    }

    @Test
    fun `a random nonce is used when none is supplied, so two derivations differ`() {
        val a = ProgramId.derive(issuer, "Joe's Coffee")
        val b = ProgramId.derive(issuer, "Joe's Coffee")

        assertNotEquals(a, b)
    }
}
