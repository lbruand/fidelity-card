package io.fidelitycard.app.data

import io.fidelitycard.app.qr.CustomerMessage
import io.fidelitycard.crypto.SigningKeyPair
import io.fidelitycard.crypto.StampToken
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test

private const val PROGRAM_ID = "PROGRAM1"
private const val CARD_ID = "card-1"

class CollectorRepositoryTest {

    private val issuer = SigningKeyPair.generate()
    private val cardDao = FakeCollectorCardDao()
    private val stampDao = FakeCollectorStampDao()
    private val repository = CollectorRepository(cardDao, stampDao)

    private suspend fun seedCard(threshold: Int) {
        cardDao.insert(
            CollectorCardEntity(
                cardId = CARD_ID,
                programId = PROGRAM_ID,
                issuerPublicKey = issuer.publicKey.bytes,
                programName = "Joe's Coffee",
                threshold = threshold,
                reward = "Free coffee",
                createdAt = 0L,
            ),
        )
    }

    private suspend fun holdStamps(count: Int) {
        (1..count).forEach {
            val token = StampToken.mint(issuer, PROGRAM_ID, CARD_ID)
            stampDao.insert(CollectorStampEntity(CARD_ID, token.stampId.toHex(), token.toWireBytes()))
        }
    }

    @Test
    fun `uses Compact Stamp Proofs for a normal-sized redemption`() = runBlocking {
        seedCard(threshold = 40)
        holdStamps(40)

        val pending = repository.buildRedemptionRequest(CARD_ID)!!

        val message = CustomerMessage.parse(pending.requestBytes)
        assertInstanceOf(CustomerMessage.RedemptionRequest::class.java, message)
        Unit
    }

    @Test
    fun `switches to bare stamp ids once a redemption is large enough`() = runBlocking {
        seedCard(threshold = 41)
        holdStamps(41)

        val pending = repository.buildRedemptionRequest(CARD_ID)!!

        val message = CustomerMessage.parse(pending.requestBytes)
        assertInstanceOf(CustomerMessage.LargeRedemptionRequest::class.java, message)
        Unit
    }
}
