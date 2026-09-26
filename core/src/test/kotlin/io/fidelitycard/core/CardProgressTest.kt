package io.fidelitycard.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CardProgressTest {

    @Test
    fun `a brand new card has collected zero stamps`() {
        val progress = CardProgress.compute(highestAcceptedSerial = 0, redeemedThroughSerial = 0, threshold = 10)

        assertEquals(0, progress.stampsSinceLastRedemption)
        assertEquals(10, progress.remainingForNextReward)
        assertFalse(progress.isRedeemable)
    }

    @Test
    fun `one stamp short of the threshold is not yet redeemable`() {
        val progress = CardProgress.compute(highestAcceptedSerial = 9, redeemedThroughSerial = 0, threshold = 10)

        assertEquals(9, progress.stampsSinceLastRedemption)
        assertEquals(1, progress.remainingForNextReward)
        assertFalse(progress.isRedeemable)
    }

    @Test
    fun `reaching the threshold makes the card redeemable`() {
        val progress = CardProgress.compute(highestAcceptedSerial = 10, redeemedThroughSerial = 0, threshold = 10)

        assertEquals(0, progress.remainingForNextReward)
        assertTrue(progress.isRedeemable)
    }

    @Test
    fun `progress resets relative to the last redemption, not to zero`() {
        // Redeemed through serial 10; two more stamps earned since.
        val progress = CardProgress.compute(highestAcceptedSerial = 12, redeemedThroughSerial = 10, threshold = 10)

        assertEquals(2, progress.stampsSinceLastRedemption)
        assertEquals(8, progress.remainingForNextReward)
        assertFalse(progress.isRedeemable)
    }

    @Test
    fun `threshold must be positive`() {
        assertThrows(IllegalArgumentException::class.java) {
            CardProgress.compute(highestAcceptedSerial = 0, redeemedThroughSerial = 0, threshold = 0)
        }
    }

    @Test
    fun `highestAcceptedSerial cannot be behind what has already been redeemed`() {
        assertThrows(IllegalArgumentException::class.java) {
            CardProgress.compute(highestAcceptedSerial = 5, redeemedThroughSerial = 10, threshold = 10)
        }
    }
}
