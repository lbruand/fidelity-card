package io.fidelitycard.core

/**
 * The issuer-side gate for a redemption request (SPEC/SPECS.md §6.3).
 * Every submitted stamp is assumed to already have a verified signature
 * (via `io.fidelitycard.crypto.StampToken.parseAndVerify`) and to belong to
 * the right program/card before it reaches this validator - what's checked
 * here is everything a lone valid signature can't prove: that there are
 * enough distinct stamps, none repeated within this one submission, and
 * none of them already spent in a previous redemption. Stamps are
 * unordered (SPEC/SPECS.md §6.2/§7 - a stamp id, not a serial), so there is
 * no contiguity to check.
 */
object RedemptionValidator {

    sealed interface Result {
        data object Valid : Result
        data class Invalid(val reason: String) : Result
    }

    fun validate(
        submittedStampIds: List<String>,
        alreadyRedeemedStampIds: Set<String>,
        threshold: Int,
    ): Result {
        if (submittedStampIds.size < threshold) {
            return Result.Invalid("only ${submittedStampIds.size} stamp(s) submitted, need at least $threshold")
        }
        if (submittedStampIds.toSet().size != submittedStampIds.size) {
            return Result.Invalid("the same stamp was submitted more than once")
        }
        val alreadySpent = submittedStampIds.filter { it in alreadyRedeemedStampIds }
        if (alreadySpent.isNotEmpty()) {
            return Result.Invalid("${alreadySpent.size} of these stamps were already redeemed")
        }
        return Result.Valid
    }
}
