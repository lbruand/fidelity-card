package io.fidelitycard.core

/** Whether an incoming stamp extends a card's ledger, or must be rejected. */
sealed interface StampAcceptance {
    data class Accepted(val newHighestAcceptedSerial: Int) : StampAcceptance
    data class Rejected(val reason: String) : StampAcceptance
}

/**
 * Enforces the rule from SPEC/SPECS.md §5.3: a stamp is only accepted if
 * its serial is exactly one more than the last one this card accepted. This
 * is what makes replaying an old stamp QR, or grafting one meant for
 * another card, detectable on the collector's own device - a single
 * [io.fidelitycard.crypto.StampToken] passing signature verification only
 * proves the issuer signed it, not that it belongs next in this card's
 * sequence.
 */
object StampLedger {
    fun evaluate(currentHighestAcceptedSerial: Int, incomingSerial: Int): StampAcceptance {
        val expected = currentHighestAcceptedSerial + 1
        return if (incomingSerial == expected) {
            StampAcceptance.Accepted(incomingSerial)
        } else {
            StampAcceptance.Rejected("expected serial $expected next, got $incomingSerial")
        }
    }
}
