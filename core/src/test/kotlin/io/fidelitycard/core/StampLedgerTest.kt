package io.fidelitycard.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StampLedgerTest {

    @Test
    fun `accepts the very first stamp of a new card`() {
        val result = StampLedger.evaluate(currentHighestAcceptedSerial = 0, incomingSerial = 1)

        assertTrue(result is StampAcceptance.Accepted)
        assertEquals(1, (result as StampAcceptance.Accepted).newHighestAcceptedSerial)
    }

    @Test
    fun `accepts the next stamp in sequence`() {
        val result = StampLedger.evaluate(currentHighestAcceptedSerial = 4, incomingSerial = 5)

        assertTrue(result is StampAcceptance.Accepted)
        assertEquals(5, (result as StampAcceptance.Accepted).newHighestAcceptedSerial)
    }

    @Test
    fun `rejects a replayed stamp with a serial already accepted`() {
        val result = StampLedger.evaluate(currentHighestAcceptedSerial = 5, incomingSerial = 5)

        assertTrue(result is StampAcceptance.Rejected)
    }

    @Test
    fun `rejects a stamp that skips ahead, leaving a gap`() {
        val result = StampLedger.evaluate(currentHighestAcceptedSerial = 5, incomingSerial = 7)

        assertTrue(result is StampAcceptance.Rejected)
    }

    @Test
    fun `rejects a stamp from before the current position`() {
        val result = StampLedger.evaluate(currentHighestAcceptedSerial = 5, incomingSerial = 3)

        assertTrue(result is StampAcceptance.Rejected)
    }
}
