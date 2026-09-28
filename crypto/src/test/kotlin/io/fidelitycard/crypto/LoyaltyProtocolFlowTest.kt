package io.fidelitycard.crypto

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Walks the whole protocol end to end (SPEC/SPECS.md §6): a business issues
 * a program, a customer joins, earns enough stamps, and redeems them.
 * Every step only uses each type's public `issue`/`mint`/`parseAndVerify`
 * API, exactly as an app would after scanning a QR code — this test is the
 * executable version of the usage the library is meant to make simple.
 */
class LoyaltyProtocolFlowTest {

    @Test
    fun `join, earn ten stamps and redeem`() {
        // The issuer (e.g. a coffee shop till) holds a key pair - no account,
        // no server. The collector holds no cryptographic identity at all,
        // and neither does a stamp: nothing in the protocol ever needs to
        // verify who the collector is, or tie a stamp to one particular
        // collector (SPEC/SPECS.md §4/§6.2) - a stamp's value lives in
        // holding its bytes, the same way a physical stamp card's value
        // lives in holding the card.
        val issuer = SigningKeyPair.generate()

        // 1. Issuer creates a Program and shows it as a QR code.
        val issuedProgram = ProgramManifest.issue(
            issuer, name = "Joe's Coffee", threshold = 10, reward = "Free coffee",
            color = 0xFF00897B.toInt(), icon = "☕",
        )
        val programQrBytes = issuedProgram.toWireBytes()

        // Collector scans it. From here on the collector only trusts what
        // this verified manifest says - the issuer's key is now pinned.
        val program = ProgramManifest.parseAndVerify(programQrBytes)
        assertEquals("Free coffee", program.reward)

        // 2. Joining is purely local (SPEC/SPECS.md §6.1): no round trip
        // with the issuer, no certificate.

        // 3. Stamping: ten purchases, ten one-way QRs - the issuer mints and
        // shows a stamp, the collector scans it, no reply needed (SPEC/SPECS.md
        // §6.2). Stamps are unordered - each is independently valid,
        // deduplicated by its own random stamp id, not by position in a
        // sequence.
        val acceptedStampIds = mutableSetOf<List<Byte>>()
        repeat(program.threshold) {
            val stampBytes = StampToken.mint(issuer, program.programId).toWireBytes()

            val stamp = StampToken.parseAndVerify(stampBytes, program.issuerPublicKey)
            assertTrue(acceptedStampIds.add(stamp.stampId.toList()), "every minted stamp id must be unique")
        }
        assertEquals(program.threshold, acceptedStampIds.size)

        // 4. Redemption: collector has enough stamps, issuer closes them out.
        val redemptionBytes = RedemptionCertificate.issue(
            issuer,
            program.programId,
            redeemedCount = acceptedStampIds.size,
        ).toWireBytes()
        val redemption = RedemptionCertificate.parseAndVerify(redemptionBytes, program.issuerPublicKey)

        assertTrue(redemption.redeemedCount >= program.threshold)
    }
}
