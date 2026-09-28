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
            "0501001a4f554253493746464e524f425a49544948504b524c583748464c03a107bff3ce10be1d70dd18e74bc09967e4d6309ba50d5f1ddc8664125531b8000c4a6f65277320436f666665650000000a000b4672656520636f66666565ff00897b0003e29895cb5ed34bb2bb84806f425bd8d74f90b471b89235a29f58172d8611ea9408a71ee8a24e7eaf46e7a735c70388eeea5e2a5e85936b5c5c3daf170190508f980b0b",
            manifest.toWireBytes(),
        )
    }

    @Test
    fun `stamp token matches the published vector`() {
        val stamp = StampToken.mint(issuer, programId, issuedAt = fixedInstant, stampId = stampId)

        assertHexEquals(
            "0502001a4f554253493746464e524f425a49544948504b524c583748464c000102030405060708090a0b0c0d0e0f0000018bcfe568008ecd7d3da43852d6e8b13e868245e8a1becf8048cd2b955fff47ea20a084f1dd1ccdcebb4c5a8db6380adb9b35e9c1254bc25cf82f392c32b690011ee7ff2c00",
            stamp.toWireBytes(),
        )
    }

    @Test
    fun `compact stamp proof matches the published vector`() {
        val stamp = StampToken.mint(issuer, programId, issuedAt = fixedInstant, stampId = stampId)

        assertHexEquals(
            "000102030405060708090a0b0c0d0e0f0000018bcfe568008ecd7d3da43852d6e8b13e868245e8a1becf8048cd2b955fff47ea20a084f1dd1ccdcebb4c5a8db6380adb9b35e9c1254bc25cf82f392c32b690011ee7ff2c00",
            stamp.toCompactProofBytes(),
        )
    }

    @Test
    fun `redemption certificate matches the published vector`() {
        val redemption = RedemptionCertificate.issue(
            issuer, programId, redeemedCount = 10, redeemedAt = fixedInstant, redemptionId = redemptionId,
        )

        assertHexEquals(
            "0503001a4f554253493746464e524f425a49544948504b524c583748464c0000000a0000018bcfe568000102030405060708090a0b0c0d0e0f107bf38703b2c79c275b65a6a15fb51da735ef491d7cdbe29a1b7fec3c00af681f56ae6f3d2f39afe6a249bfc940740c54e94ae36977d6e87761736ca5792e6508",
            redemption.toWireBytes(),
        )
    }

    private fun assertHexEquals(expectedHex: String, actual: ByteArray) {
        assertEquals(expectedHex, actual.joinToString("") { "%02x".format(it) })
    }
}
