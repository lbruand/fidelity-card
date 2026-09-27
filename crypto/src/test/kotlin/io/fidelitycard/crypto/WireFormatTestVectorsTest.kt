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
            "0201001a4f554253493746464e524f425a49544948504b524c583748464c03a107bff3ce10be1d70dd18e74bc09967e4d6309ba50d5f1ddc8664125531b8000c4a6f65277320436f666665650000000a000b4672656520636f66666565f8d100cbe2c683d690955cebf633890fa9c239cd54c3d8258e7cf2a828050345ac81bbb0a04c035941d5ea626f8958ccd5690754d2c7c61eb1b3c33333fc1904",
            manifest.toWireBytes(),
        )
    }

    @Test
    fun `card certificate matches the published vector`() {
        val cert = CardCertificate.issue(issuer, programId, cardId, collector.publicKey, fixedInstant)

        assertHexEquals(
            "0202001a4f554253493746464e524f425a49544948504b524c583748464c002431313131313131312d313131312d313131312d313131312d3131313131313131313131310bbc346a57667c380120bd9c7fd7e51d2c5fdfea37cd2f5bf405b2c6bf6f2d780000018bcfe56800ab4e332931464c265ea19130a2dd9b1b287ac4ed63e9c6960f64727cab119a87df60fead0b8126e6d6685d897980e8daa7a7cdd174ac9993eb9f2f2f11ad2d06",
            cert.toWireBytes(),
        )
    }

    @Test
    fun `stamp token matches the published vector`() {
        val stamp = StampToken.mint(issuer, programId, cardId, issuedAt = fixedInstant, stampId = stampId)

        assertHexEquals(
            "0203001a4f554253493746464e524f425a49544948504b524c583748464c002431313131313131312d313131312d313131312d313131312d313131313131313131313131000102030405060708090a0b0c0d0e0f0000018bcfe56800ffaffc87d11cae25271f6fdac5e40aa2607289e82e4a0040ace36669d6f6a473316b4aa67a37d615161e671e50d04edc423311227542f57b86aa3243a7e5360f",
            stamp.toWireBytes(),
        )
    }

    @Test
    fun `redemption certificate matches the published vector`() {
        val redemption = RedemptionCertificate.issue(
            issuer, programId, cardId, redeemedCount = 10, redeemedAt = fixedInstant, redemptionId = redemptionId,
        )

        assertHexEquals(
            "0204001a4f554253493746464e524f425a49544948504b524c583748464c002431313131313131312d313131312d313131312d313131312d3131313131313131313131310000000a0000018bcfe568000102030405060708090a0b0c0d0e0f10193303e4d6e3e68552af9b7a8b3ac936d1a420955a561f1f08b0a0c9cf9c1d9d1b4f5bc924fd369ff7256ab63509eab6361988a091eed55af4bc7a91f700ea00",
            redemption.toWireBytes(),
        )
    }

    private fun assertHexEquals(expectedHex: String, actual: ByteArray) {
        assertEquals(expectedHex, actual.joinToString("") { "%02x".format(it) })
    }
}
