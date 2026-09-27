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
    private val programIdNonce = ByteArray(16) { it.toByte() }
    private val stampId = ByteArray(16) { it.toByte() }
    private val redemptionId = ByteArray(16) { (it + 1).toByte() }
    private val fixedInstant = Instant.ofEpochMilli(1_700_000_000_000L)
    private val cardId = "11111111-1111-1111-1111-111111111111"
    private val programId = "OUBSI7FFNROBZITIHPKRLX7HFL"

    @Test
    fun `program manifest matches the published vector`() {
        val manifest = ProgramManifest.issue(
            issuer, "Joe's Coffee", threshold = 10, reward = "Free coffee",
            programIdNonce = programIdNonce,
        )

        assertEquals(programId, manifest.programId)
        assertHexEquals(
            "0301001a4f554253493746464e524f425a49544948504b524c583748464c03a107bff3ce10be1d70dd18e74bc09967e4d6309ba50d5f1ddc8664125531b8000c4a6f65277320436f666665650000000a000b4672656520636f666665658265cd8ec9abc25d04c51d51a3a549257d362e0412237c750531e36969d2f948c0d2628278075f6bb64575d44b9f1e1e2512ee92873a8bc56b328fcf2983c60d",
            manifest.toWireBytes(),
        )
    }

    @Test
    fun `stamp token matches the published vector`() {
        val stamp = StampToken.mint(issuer, programId, cardId, issuedAt = fixedInstant, stampId = stampId)

        assertHexEquals(
            "0302001a4f554253493746464e524f425a49544948504b524c583748464c002431313131313131312d313131312d313131312d313131312d313131313131313131313131000102030405060708090a0b0c0d0e0f0000018bcfe568004f7dd4988cc61a5ac52dce5a32cd0bdfcf193909d77e079aa9da0dfa5a27b04fda16d160a8b38d15f393b919691b886982b5e86264f5ccbee9d24b434f78a001",
            stamp.toWireBytes(),
        )
    }

    @Test
    fun `redemption certificate matches the published vector`() {
        val redemption = RedemptionCertificate.issue(
            issuer, programId, cardId, redeemedCount = 10, redeemedAt = fixedInstant, redemptionId = redemptionId,
        )

        assertHexEquals(
            "0303001a4f554253493746464e524f425a49544948504b524c583748464c002431313131313131312d313131312d313131312d313131312d3131313131313131313131310000000a0000018bcfe568000102030405060708090a0b0c0d0e0f100c1dabfb7aed26214027ea7288cffae26ffb5ed5b2a762f1c023e3abdb8eaa934dc9f226dafbd8551178656294765c36e175095093a664b6cba7e478ca30b906",
            redemption.toWireBytes(),
        )
    }

    private fun assertHexEquals(expectedHex: String, actual: ByteArray) {
        assertEquals(expectedHex, actual.joinToString("") { "%02x".format(it) })
    }
}
