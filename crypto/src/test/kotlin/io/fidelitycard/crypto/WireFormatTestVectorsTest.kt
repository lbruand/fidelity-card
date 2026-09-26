package io.fidelitycard.crypto

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * Byte-exact test vectors published in SPEC/CRYPTO_WIRE_FORMAT.md §7. If
 * this test ever fails after a code change, the wire format changed and
 * that spec document must be regenerated and updated in the same commit -
 * that is the whole point of publishing fixed vectors: any other language
 * implementing this protocol depends on these exact bytes never moving
 * without notice.
 */
class WireFormatTestVectorsTest {

    private val issuer = SigningKeyPair.fromSeed(ByteArray(32) { it.toByte() })
    private val collector = SigningKeyPair.fromSeed(ByteArray(32) { (it + 100).toByte() })
    private val programIdNonce = ByteArray(16) { it.toByte() }
    private val stampNonce = ByteArray(8) { it.toByte() }
    private val fixedInstant = Instant.ofEpochMilli(1_700_000_000_000L)
    private val cardId = "11111111-1111-1111-1111-111111111111"

    @Test
    fun `program manifest matches the published vector`() {
        val manifest = ProgramManifest.issue(
            issuer, "Joe's Coffee", threshold = 10, reward = "Free coffee",
            programIdNonce = programIdNonce,
        )

        assertEquals("OUBSI7FFNROBZITIHPKRLX7HFL", manifest.programId)
        assertHexEquals(
            "0101001a4f554253493746464e524f425a49544948504b524c583748464c03a107bff3ce10be1d70dd18e74bc09967e4d6309ba50d5f1ddc8664125531b8000c4a6f65277320436f666665650000000a000b4672656520636f66666565a01f4b6598f7030d6adca448fa56c6494319e4cbf1c8669866d746758913f3ddea270a028133c5165039383c8efda55c4f826431f559eb642960095c31382c02",
            manifest.toWireBytes(),
        )
    }

    @Test
    fun `card certificate matches the published vector`() {
        val programId = "OUBSI7FFNROBZITIHPKRLX7HFL"

        val cert = CardCertificate.issue(issuer, programId, cardId, collector.publicKey, fixedInstant)

        assertHexEquals(
            "0102001a4f554253493746464e524f425a49544948504b524c583748464c002431313131313131312d313131312d313131312d313131312d3131313131313131313131310bbc346a57667c380120bd9c7fd7e51d2c5fdfea37cd2f5bf405b2c6bf6f2d780000018bcfe56800f5898365c41e739b5805935d288cb98cd5d7314e7a67bccb0080533b195a4c049eaa998ea85f97b2122d32441f9c75a45929ddcf3eba4cc264cfe8df8aedbd03",
            cert.toWireBytes(),
        )
    }

    @Test
    fun `stamp token matches the published vector`() {
        val programId = "OUBSI7FFNROBZITIHPKRLX7HFL"

        val stamp = StampToken.mint(issuer, programId, cardId, serial = 4, issuedAt = fixedInstant, nonce = stampNonce)

        assertHexEquals(
            "0103001a4f554253493746464e524f425a49544948504b524c583748464c002431313131313131312d313131312d313131312d313131312d313131313131313131313131000000040000018bcfe56800000102030405060741ba741823d6e9e87d994ee377aa4587e50bf4171b79cc548d672bada6bd0a7aed1c8ba2c7354a885562c1cce8b98d6f8caf0b91868fccbdb50ef328b885d709",
            stamp.toWireBytes(),
        )
    }

    @Test
    fun `redemption certificate matches the published vector`() {
        val programId = "OUBSI7FFNROBZITIHPKRLX7HFL"

        val redemption = RedemptionCertificate.issue(
            issuer, programId, cardId, redeemedThroughSerial = 10, redeemedAt = fixedInstant,
        )

        assertHexEquals(
            "0104001a4f554253493746464e524f425a49544948504b524c583748464c002431313131313131312d313131312d313131312d313131312d3131313131313131313131310000000a0000018bcfe56800733b800cbe3017b5679280934ca3f3703c900210b7ef7984f006d97544879f80879e35519b58ed7baefae2b2ac0e53462ac1b6c1d693443e0ce7d299f862a701",
            redemption.toWireBytes(),
        )
    }

    private fun assertHexEquals(expectedHex: String, actual: ByteArray) {
        assertEquals(expectedHex, actual.joinToString("") { "%02x".format(it) })
    }
}
