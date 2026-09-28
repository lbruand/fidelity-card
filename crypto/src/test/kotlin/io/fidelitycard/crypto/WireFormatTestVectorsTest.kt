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
            color = 0xFF00897B.toInt(), icon = "☕",
            programIdNonce = programIdNonce,
        )

        assertEquals(programId, manifest.programId)
        assertHexEquals(
            "0401001a4f554253493746464e524f425a49544948504b524c583748464c03a107bff3ce10be1d70dd18e74bc09967e4d6309ba50d5f1ddc8664125531b8000c4a6f65277320436f666665650000000a000b4672656520636f66666565ff00897b0003e29895e775aaea02d626b305216ba5becf86a6f1b5d39e179f7f3e34f9f61be4d5199fde76bf8a695160fecd2de7ed3b0d644f3a37de391e3bb628f6e1e041eb067002",
            manifest.toWireBytes(),
        )
    }

    @Test
    fun `stamp token matches the published vector`() {
        val stamp = StampToken.mint(issuer, programId, cardId, issuedAt = fixedInstant, stampId = stampId)

        assertHexEquals(
            "0402001a4f554253493746464e524f425a49544948504b524c583748464c002431313131313131312d313131312d313131312d313131312d313131313131313131313131000102030405060708090a0b0c0d0e0f0000018bcfe568006e69fe20b4acf3a2f23562a6c4ae6eb301658e375762e02da2a2232d5cbd8f062437662ea747566bb544b93b95c3f50dc02d8c460f1b079ad6fe79ee2877a00f",
            stamp.toWireBytes(),
        )
    }

    @Test
    fun `compact stamp proof matches the published vector`() {
        val stamp = StampToken.mint(issuer, programId, cardId, issuedAt = fixedInstant, stampId = stampId)

        assertHexEquals(
            "000102030405060708090a0b0c0d0e0f0000018bcfe568006e69fe20b4acf3a2f23562a6c4ae6eb301658e375762e02da2a2232d5cbd8f062437662ea747566bb544b93b95c3f50dc02d8c460f1b079ad6fe79ee2877a00f",
            stamp.toCompactProofBytes(),
        )
    }

    @Test
    fun `redemption certificate matches the published vector`() {
        val redemption = RedemptionCertificate.issue(
            issuer, programId, cardId, redeemedCount = 10, redeemedAt = fixedInstant, redemptionId = redemptionId,
        )

        assertHexEquals(
            "0403001a4f554253493746464e524f425a49544948504b524c583748464c002431313131313131312d313131312d313131312d313131312d3131313131313131313131310000000a0000018bcfe568000102030405060708090a0b0c0d0e0f108044ce188c8c0f3658e68ce1fc0d7356a06b23df11fa2423fa32144411e6f8a235a1b1cf4cfcfb5c9d168a70868ab373bf730d07e4633b34c0c461e070214101",
            redemption.toWireBytes(),
        )
    }

    private fun assertHexEquals(expectedHex: String, actual: ByteArray) {
        assertEquals(expectedHex, actual.joinToString("") { "%02x".format(it) })
    }
}
