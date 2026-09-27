package io.fidelitycard.app.data

import io.fidelitycard.app.qr.CustomerMessage
import io.fidelitycard.crypto.SigningKeyPair
import io.fidelitycard.crypto.StampToken
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test

private const val PROGRAM_ID = "PROGRAM1"
private const val CARD_ID = "card-1"

class IssuerRepositoryTest {

    private val issuer = SigningKeyPair.generate()
    private val programDao = FakeIssuerProgramDao()
    private val issuedCardDao = FakeIssuedCardDao()
    private val redeemedStampDao = FakeRedeemedStampDao()
    private val mintedStampDao = FakeIssuerMintedStampDao()
    private val repository = IssuerRepository(programDao, issuedCardDao, redeemedStampDao, mintedStampDao)

    private suspend fun seedProgram(threshold: Int) {
        programDao.insert(
            IssuerProgramEntity(
                programId = PROGRAM_ID,
                name = "Joe's Coffee",
                threshold = threshold,
                reward = "Free coffee",
                issuerSeed = issuer.seed,
                programManifestBytes = ByteArray(0),
                createdAt = 0L,
            ),
        )
    }

    private suspend fun mintStamps(count: Int): List<ByteArray> =
        (1..count).map {
            val outcome = repository.handleCustomerMessage(
                PROGRAM_ID,
                CustomerMessage.StampRequest(PROGRAM_ID, CARD_ID).toWireBytes(),
            )
            val stamped = assertInstanceOf(IssuerScanOutcome.Stamped::class.java, outcome)
            StampToken.parseAndVerify(stamped.responseBytes, issuer.publicKey).stampId
        }

    @Test
    fun `minting a stamp records it in the issuer's minted ledger`() = runBlocking {
        seedProgram(threshold = 1)

        val stampIds = mintStamps(1)

        val known = mintedStampDao.findKnown(PROGRAM_ID, CARD_ID, stampIds.map { it.toHex() })
        assertEquals(1, known.size)
    }

    @Test
    fun `large redemption succeeds for stamp ids the issuer actually minted`() = runBlocking {
        seedProgram(threshold = 2)
        val stampIds = mintStamps(2)

        val outcome = repository.handleCustomerMessage(
            PROGRAM_ID,
            CustomerMessage.LargeRedemptionRequest(PROGRAM_ID, CARD_ID, stampIds).toWireBytes(),
        )

        assertInstanceOf(IssuerScanOutcome.Redeemed::class.java, outcome)
        assertEquals(2, redeemedStampDao.getAll().size)
    }

    @Test
    fun `large redemption rejects a stamp id the issuer never minted`() = runBlocking {
        seedProgram(threshold = 1)
        mintStamps(1)
        val forgedStampId = ByteArray(StampToken.STAMP_ID_LENGTH_BYTES) { 0x42 }

        val outcome = repository.handleCustomerMessage(
            PROGRAM_ID,
            CustomerMessage.LargeRedemptionRequest(PROGRAM_ID, CARD_ID, listOf(forgedStampId)).toWireBytes(),
        )

        assertInstanceOf(IssuerScanOutcome.Failed::class.java, outcome)
        assertEquals(0, redeemedStampDao.getAll().size)
    }

    @Test
    fun `large redemption rejects a stamp id already redeemed`() = runBlocking {
        seedProgram(threshold = 1)
        val stampIds = mintStamps(1)
        val request = CustomerMessage.LargeRedemptionRequest(PROGRAM_ID, CARD_ID, stampIds).toWireBytes()
        assertInstanceOf(IssuerScanOutcome.Redeemed::class.java, repository.handleCustomerMessage(PROGRAM_ID, request))

        val secondAttempt = repository.handleCustomerMessage(PROGRAM_ID, request)

        assertInstanceOf(IssuerScanOutcome.Failed::class.java, secondAttempt)
        Unit
    }
}
