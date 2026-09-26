package io.fidelitycard.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RedemptionValidatorTest {

    @Test
    fun `accepts exactly a fresh, contiguous run meeting the threshold`() {
        val result = RedemptionValidator.validate(
            submittedSerialsSortedAscending = (1..10).toList(),
            alreadyRedeemedThroughSerial = 0,
            issuedSerialCount = 10,
            threshold = 10,
        )

        assertTrue(result is RedemptionValidator.Result.Valid)
        assertEquals(10, (result as RedemptionValidator.Result.Valid).redeemedThroughSerial)
    }

    @Test
    fun `accepts a run starting after a previous redemption`() {
        val result = RedemptionValidator.validate(
            submittedSerialsSortedAscending = (11..20).toList(),
            alreadyRedeemedThroughSerial = 10,
            issuedSerialCount = 25,
            threshold = 10,
        )

        assertTrue(result is RedemptionValidator.Result.Valid)
        assertEquals(20, (result as RedemptionValidator.Result.Valid).redeemedThroughSerial)
    }

    @Test
    fun `accepts more stamps than the bare threshold, if the collector has them`() {
        val result = RedemptionValidator.validate(
            submittedSerialsSortedAscending = (1..15).toList(),
            alreadyRedeemedThroughSerial = 0,
            issuedSerialCount = 15,
            threshold = 10,
        )

        assertTrue(result is RedemptionValidator.Result.Valid)
        assertEquals(15, (result as RedemptionValidator.Result.Valid).redeemedThroughSerial)
    }

    @Test
    fun `rejects fewer stamps than the threshold`() {
        val result = RedemptionValidator.validate(
            submittedSerialsSortedAscending = (1..9).toList(),
            alreadyRedeemedThroughSerial = 0,
            issuedSerialCount = 9,
            threshold = 10,
        )

        assertTrue(result is RedemptionValidator.Result.Invalid)
    }

    @Test
    fun `rejects an empty submission`() {
        val result = RedemptionValidator.validate(
            submittedSerialsSortedAscending = emptyList(),
            alreadyRedeemedThroughSerial = 0,
            issuedSerialCount = 10,
            threshold = 10,
        )

        assertTrue(result is RedemptionValidator.Result.Invalid)
    }

    @Test
    fun `rejects a run that does not start right after the last redemption - replaying an already redeemed run`() {
        val result = RedemptionValidator.validate(
            submittedSerialsSortedAscending = (1..10).toList(),
            alreadyRedeemedThroughSerial = 10,
            issuedSerialCount = 20,
            threshold = 10,
        )

        assertTrue(result is RedemptionValidator.Result.Invalid)
    }

    @Test
    fun `rejects a run with a gap in the middle`() {
        val result = RedemptionValidator.validate(
            submittedSerialsSortedAscending = listOf(1, 2, 3, 4, 6, 7, 8, 9, 10, 11),
            alreadyRedeemedThroughSerial = 0,
            issuedSerialCount = 11,
            threshold = 10,
        )

        assertTrue(result is RedemptionValidator.Result.Invalid)
    }

    @Test
    fun `rejects a serial higher than anything this issuer ever minted for the card`() {
        // Ten serials submitted, but one of them (11) was never actually
        // issued - each individual token's signature might still verify
        // against something else entirely; this is the check that catches
        // a submission claiming more than the issuer's own records show.
        val result = RedemptionValidator.validate(
            submittedSerialsSortedAscending = (2..11).toList(),
            alreadyRedeemedThroughSerial = 1,
            issuedSerialCount = 10,
            threshold = 10,
        )

        assertTrue(result is RedemptionValidator.Result.Invalid)
    }
}
