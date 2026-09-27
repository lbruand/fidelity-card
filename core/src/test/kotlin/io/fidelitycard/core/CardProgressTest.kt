package io.fidelitycard.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CardProgressTest {

    @Test
    fun `a brand new card has collected zero stamps`() {
        val progress = CardProgress.compute(stampCount = 0, threshold = 10)

        assertEquals(0, progress.stampCount)
        assertEquals(10, progress.remainingForNextReward)
        assertFalse(progress.isRedeemable)
    }

    @Test
    fun `one stamp short of the threshold is not yet redeemable`() {
        val progress = CardProgress.compute(stampCount = 9, threshold = 10)

        assertEquals(1, progress.remainingForNextReward)
        assertFalse(progress.isRedeemable)
    }

    @Test
    fun `reaching the threshold makes the card redeemable`() {
        val progress = CardProgress.compute(stampCount = 10, threshold = 10)

        assertEquals(0, progress.remainingForNextReward)
        assertTrue(progress.isRedeemable)
    }

    @Test
    fun `holding more stamps than the threshold is still redeemable`() {
        val progress = CardProgress.compute(stampCount = 15, threshold = 10)

        assertEquals(0, progress.remainingForNextReward)
        assertTrue(progress.isRedeemable)
    }

    @Test
    fun `threshold must be positive`() {
        assertThrows(IllegalArgumentException::class.java) {
            CardProgress.compute(stampCount = 0, threshold = 0)
        }
    }

    @Test
    fun `stampCount cannot be negative`() {
        assertThrows(IllegalArgumentException::class.java) {
            CardProgress.compute(stampCount = -1, threshold = 10)
        }
    }
}
