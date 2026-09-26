package io.fidelitycard.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StampIssuanceTest {

    @Test
    fun `mints a new stamp when the collector is exactly caught up`() {
        val decision = StampIssuance.decide(requestedLastAcceptedSerial = 4, issuedSerialCount = 4)

        assertTrue(decision is StampRequestDecision.MintNew)
        assertEquals(5, (decision as StampRequestDecision.MintNew).nextSerial)
    }

    @Test
    fun `resends the next already-issued stamp when the collector fell one behind`() {
        // The issuer already minted serial 5 (issuedSerialCount = 5) but the
        // collector never actually received it, so it still thinks it's at 4.
        val decision = StampIssuance.decide(requestedLastAcceptedSerial = 4, issuedSerialCount = 5)

        assertTrue(decision is StampRequestDecision.ResendPrevious)
        assertEquals(5, (decision as StampRequestDecision.ResendPrevious).serial)
    }

    @Test
    fun `catching up from several stamps behind still only resends the very next one`() {
        // Catching up happens one round trip at a time, same as normal use -
        // never grants more than one stamp per exchange.
        val decision = StampIssuance.decide(requestedLastAcceptedSerial = 2, issuedSerialCount = 6)

        assertTrue(decision is StampRequestDecision.ResendPrevious)
        assertEquals(3, (decision as StampRequestDecision.ResendPrevious).serial)
    }

    @Test
    fun `rejects a request claiming more stamps than were ever issued`() {
        val decision = StampIssuance.decide(requestedLastAcceptedSerial = 7, issuedSerialCount = 5)

        assertTrue(decision is StampRequestDecision.Rejected)
    }
}
