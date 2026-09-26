package io.fidelitycard.core

/**
 * The issuer-side gate for a redemption request (SPEC/SPECS.md §6.3 /
 * §7.1). Every submitted stamp is assumed to already have a verified
 * signature (via `io.fidelitycard.crypto.StampToken.parseAndVerify`) before
 * it reaches this validator - what's checked here is everything a lone
 * valid signature can't prove on its own: that the serials form one
 * unbroken run starting exactly where the last redemption left off, that
 * there are enough of them, and that none exceeds what this issuer ever
 * actually minted for the card.
 */
object RedemptionValidator {

    sealed interface Result {
        data class Valid(val redeemedThroughSerial: Int) : Result
        data class Invalid(val reason: String) : Result
    }

    fun validate(
        submittedSerialsSortedAscending: List<Int>,
        alreadyRedeemedThroughSerial: Int,
        issuedSerialCount: Int,
        threshold: Int,
    ): Result {
        if (submittedSerialsSortedAscending.isEmpty()) {
            return Result.Invalid("no stamps submitted")
        }

        val expectedStart = alreadyRedeemedThroughSerial + 1
        submittedSerialsSortedAscending.forEachIndexed { index, serial ->
            val expected = expectedStart + index
            if (serial != expected) {
                return Result.Invalid("expected serial $expected at position $index, got $serial")
            }
        }

        val submittedCount = submittedSerialsSortedAscending.size
        if (submittedCount < threshold) {
            return Result.Invalid("only $submittedCount stamp(s) submitted, need at least $threshold")
        }

        val highestSubmitted = submittedSerialsSortedAscending.last()
        if (highestSubmitted > issuedSerialCount) {
            return Result.Invalid("serial $highestSubmitted was never issued for this card")
        }

        return Result.Valid(highestSubmitted)
    }
}
