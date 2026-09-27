package io.fidelitycard.crypto

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

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
        // no server. The collector holds no cryptographic identity at all:
        // a card id is just a locally-generated opaque string, since nothing
        // in the protocol ever needs to verify who the collector is.
        val issuer = SigningKeyPair.generate()

        // 1. Issuer creates a Program and shows it as a QR code.
        val issuedProgram = ProgramManifest.issue(issuer, name = "Joe's Coffee", threshold = 10, reward = "Free coffee")
        val programQrBytes = issuedProgram.toWireBytes()

        // Collector scans it. From here on the collector only trusts what
        // this verified manifest says - the issuer's key is now pinned.
        val program = ProgramManifest.parseAndVerify(programQrBytes)
        assertEquals("Free coffee", program.reward)

        // 2. Joining is purely local: the collector picks a card id and
        // starts tracking a card for this program. No round trip with the
        // issuer, no certificate - the issuer only ever learns a card id
        // exists the first time it mints a stamp for it (SPEC/SPECS.md §6.1).
        val cardId = UUID.randomUUID().toString()

        // 3. Stamping: ten purchases, ten QR round trips. Stamps are
        // unordered - each is independently valid, deduplicated by its own
        // random stamp id, not by position in a sequence.
        val acceptedStampIds = mutableSetOf<List<Byte>>()
        repeat(program.threshold) {
            val stampBytes = StampToken.mint(issuer, program.programId, cardId).toWireBytes()

            val stamp = StampToken.parseAndVerify(stampBytes, program.issuerPublicKey)
            assertTrue(acceptedStampIds.add(stamp.stampId.toList()), "every minted stamp id must be unique")
        }
        assertEquals(program.threshold, acceptedStampIds.size)

        // 4. Redemption: collector has enough stamps, issuer closes them out.
        val redemptionBytes = RedemptionCertificate.issue(
            issuer,
            program.programId,
            cardId,
            redeemedCount = acceptedStampIds.size,
        ).toWireBytes()
        val redemption = RedemptionCertificate.parseAndVerify(redemptionBytes, program.issuerPublicKey)

        assertEquals(cardId, redemption.cardId)
        assertTrue(redemption.redeemedCount >= program.threshold)
    }
}
