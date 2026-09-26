package io.fidelitycard.core

/** What the issuer should do in response to a stamp request. */
sealed interface StampRequestDecision {
    /** The collector is exactly caught up: mint a genuinely new stamp. */
    data class MintNew(val nextSerial: Int) : StampRequestDecision

    /**
     * The collector is behind what this issuer already minted for this card
     * - almost always because a previous stamp's QR round trip was
     * interrupted after minting but before the collector accepted it (the
     * scan failed, the app closed, etc). Resending the already-issued token
     * for [serial] lets the collector catch up without granting anything
     * unearned and without the issuer minting a duplicate.
     */
    data class ResendPrevious(val serial: Int) : StampRequestDecision

    /**
     * The collector claims more stamps than this issuer ever issued for the
     * card - not recoverable by resending anything; this indicates real
     * inconsistency (corrupted local state, or a card mixed up with another
     * program) rather than a lost round trip.
     */
    data class Rejected(val reason: String) : StampRequestDecision
}

/**
 * Decides how an issuer should respond to a stamp request, given its own
 * count of stamps issued so far for the card (SPEC/SPECS.md §6.2, and the
 * self-healing behavior this adds on top of it). Resending catches the
 * collector up one stamp at a time, the same pace as normal use.
 */
object StampIssuance {
    fun decide(requestedLastAcceptedSerial: Int, issuedSerialCount: Int): StampRequestDecision = when {
        requestedLastAcceptedSerial == issuedSerialCount -> StampRequestDecision.MintNew(issuedSerialCount + 1)
        requestedLastAcceptedSerial < issuedSerialCount -> StampRequestDecision.ResendPrevious(requestedLastAcceptedSerial + 1)
        else -> StampRequestDecision.Rejected(
            "This card claims $requestedLastAcceptedSerial stamps, but only $issuedSerialCount have been issued",
        )
    }
}
