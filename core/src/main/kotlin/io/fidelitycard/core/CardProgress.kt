package io.fidelitycard.core

/**
 * How far a card is toward its next reward. Stamps are unordered (no
 * serial/sequence), so progress is simply how many currently-held,
 * not-yet-redeemed stamps a card has - redeemed stamps are removed
 * outright rather than tracked as "already counted".
 */
@ConsistentCopyVisibility
data class CardProgress private constructor(
    val stampCount: Int,
    val threshold: Int,
) {
    val remainingForNextReward: Int get() = (threshold - stampCount).coerceAtLeast(0)
    val isRedeemable: Boolean get() = stampCount >= threshold

    companion object {
        fun compute(stampCount: Int, threshold: Int): CardProgress {
            require(threshold > 0) { "threshold must be positive, was $threshold" }
            require(stampCount >= 0) { "stampCount cannot be negative, was $stampCount" }
            return CardProgress(stampCount, threshold)
        }
    }
}
