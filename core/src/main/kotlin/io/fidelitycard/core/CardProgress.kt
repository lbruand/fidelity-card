package io.fidelitycard.core

/**
 * How far a card is toward its next reward. Progress is measured relative
 * to the last redemption, not to zero: a card that redeemed at serial 10
 * and has since earned two more stamps needs `threshold - 2` more, not
 * `threshold`.
 */
@ConsistentCopyVisibility
data class CardProgress private constructor(
    val stampsSinceLastRedemption: Int,
    val threshold: Int,
) {
    val remainingForNextReward: Int get() = (threshold - stampsSinceLastRedemption).coerceAtLeast(0)
    val isRedeemable: Boolean get() = stampsSinceLastRedemption >= threshold

    companion object {
        fun compute(highestAcceptedSerial: Int, redeemedThroughSerial: Int, threshold: Int): CardProgress {
            require(threshold > 0) { "threshold must be positive, was $threshold" }
            require(highestAcceptedSerial >= redeemedThroughSerial) {
                "highestAcceptedSerial ($highestAcceptedSerial) cannot be behind " +
                    "redeemedThroughSerial ($redeemedThroughSerial)"
            }
            return CardProgress(highestAcceptedSerial - redeemedThroughSerial, threshold)
        }
    }
}
