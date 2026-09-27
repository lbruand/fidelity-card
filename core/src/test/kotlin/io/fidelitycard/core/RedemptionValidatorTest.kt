package io.fidelitycard.core

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RedemptionValidatorTest {

    @Test
    fun `accepts exactly enough distinct, never-redeemed stamps`() {
        val result = RedemptionValidator.validate(
            submittedStampIds = listOf("a", "b", "c"),
            alreadyRedeemedStampIds = emptySet(),
            threshold = 3,
        )

        assertTrue(result is RedemptionValidator.Result.Valid)
    }

    @Test
    fun `accepts more stamps than the bare threshold, if the collector has them`() {
        val result = RedemptionValidator.validate(
            submittedStampIds = listOf("a", "b", "c", "d", "e"),
            alreadyRedeemedStampIds = emptySet(),
            threshold = 3,
        )

        assertTrue(result is RedemptionValidator.Result.Valid)
    }

    @Test
    fun `rejects fewer stamps than the threshold`() {
        val result = RedemptionValidator.validate(
            submittedStampIds = listOf("a", "b"),
            alreadyRedeemedStampIds = emptySet(),
            threshold = 3,
        )

        assertTrue(result is RedemptionValidator.Result.Invalid)
    }

    @Test
    fun `rejects the same stamp id presented twice in one submission`() {
        val result = RedemptionValidator.validate(
            submittedStampIds = listOf("a", "a", "b"),
            alreadyRedeemedStampIds = emptySet(),
            threshold = 3,
        )

        assertTrue(result is RedemptionValidator.Result.Invalid)
    }

    @Test
    fun `rejects a submission containing a stamp already redeemed before`() {
        val result = RedemptionValidator.validate(
            submittedStampIds = listOf("a", "b", "c"),
            alreadyRedeemedStampIds = setOf("b"),
            threshold = 3,
        )

        assertTrue(result is RedemptionValidator.Result.Invalid)
    }
}
