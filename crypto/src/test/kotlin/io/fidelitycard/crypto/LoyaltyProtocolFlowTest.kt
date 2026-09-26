package io.fidelitycard.crypto

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * Walks the whole protocol end to end (SPEC/SPECS.md §6): a business issues
 * a program, a customer enrolls, earns enough stamps, and redeems them.
 * Every step only uses each type's public `issue`/`mint`/`parseAndVerify`
 * API, exactly as an app would after scanning a QR code — this test is the
 * executable version of the usage the library is meant to make simple.
 */
class LoyaltyProtocolFlowTest {

    @Test
    fun `enroll, earn ten stamps and redeem`() {
        // The issuer (e.g. a coffee shop till) and the collector (a customer's
        // phone) each hold nothing but a key pair — no accounts, no server.
        val issuer = SigningKeyPair.generate()
        val collector = SigningKeyPair.generate()

        // 1. Issuer creates a Program and shows it as a QR code.
        val issuedProgram = ProgramManifest.issue(issuer, name = "Joe's Coffee", threshold = 10, reward = "Free coffee")
        val programQrBytes = issuedProgram.toWireBytes()

        // Collector scans it. From here on the collector only trusts what
        // this verified manifest says - the issuer's key is now pinned.
        val program = ProgramManifest.parseAndVerify(programQrBytes)
        assertEquals("Free coffee", program.reward)

        // 2. Enrollment: collector generates a card id, issuer certifies it.
        val cardId = UUID.randomUUID().toString()
        val cardCertBytes = CardCertificate.issue(issuer, program.programId, cardId, collector.publicKey)
            .toWireBytes()
        val cardCert = CardCertificate.parseAndVerify(cardCertBytes, program.issuerPublicKey)
        assertEquals(cardId, cardCert.cardId)

        // 3. Stamping: ten purchases, ten QR round trips. The collector
        // enforces serial contiguity itself - a lone StampToken only proves
        // "the issuer signed this", not "this is the next one in order".
        var lastAcceptedSerial = 0
        repeat(program.threshold) {
            val nextSerial = lastAcceptedSerial + 1
            val stampBytes = StampToken.mint(issuer, program.programId, cardId, serial = nextSerial).toWireBytes()

            val stamp = StampToken.parseAndVerify(stampBytes, program.issuerPublicKey)
            assertEquals(nextSerial, stamp.serial, "issuer and collector must agree on the next serial")
            lastAcceptedSerial = stamp.serial
        }
        assertEquals(program.threshold, lastAcceptedSerial)

        // 4. Redemption: collector has enough stamps, issuer closes the run out.
        val redemptionBytes = RedemptionCertificate.issue(
            issuer,
            program.programId,
            cardId,
            redeemedThroughSerial = lastAcceptedSerial,
        ).toWireBytes()
        val redemption = RedemptionCertificate.parseAndVerify(redemptionBytes, program.issuerPublicKey)

        assertEquals(cardId, redemption.cardId)
        assertTrue(redemption.redeemedThroughSerial >= program.threshold)
    }
}
