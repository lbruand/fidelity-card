package io.fidelitycard.app.data

import io.fidelitycard.app.qr.CustomerMessage
import io.fidelitycard.app.qr.IssuerMessage
import io.fidelitycard.crypto.ProgramManifest
import io.fidelitycard.crypto.SigningKeyPair
import io.fidelitycard.crypto.StampToken
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test

private const val PROGRAM_ID = "PROGRAM1"

class IssuerRepositoryTest {

    private val issuer = SigningKeyPair.generate()
    private val programDao = FakeIssuerProgramDao()
    private val redeemedStampDao = FakeRedeemedStampDao()
    private val mintedStampDao = FakeIssuerMintedStampDao()
    private val repository = IssuerRepository(programDao, redeemedStampDao, mintedStampDao)

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
                color = 0xFF00897B.toInt(),
                icon = "☕",
            ),
        )
    }

    private suspend fun mintStamps(count: Int): List<ByteArray> =
        (1..count).map {
            val bundleBytes = repository.mintStamp(PROGRAM_ID)!!
            val grant = IssuerMessage.parse(bundleBytes) as IssuerMessage.StampGrant
            StampToken.parseAndVerify(grant.stampBytes, issuer.publicKey).stampId
        }

    @Test
    fun `minting a stamp bundles the program manifest alongside it, so a single scan can join and stamp`() = runBlocking {
        val manifest = ProgramManifest.issue(issuer, "Joe's Coffee", threshold = 1, reward = "Free coffee", color = 0xFF00897B.toInt(), icon = "☕")
        programDao.insert(
            IssuerProgramEntity(
                programId = manifest.programId,
                name = "Joe's Coffee",
                threshold = 1,
                reward = "Free coffee",
                issuerSeed = issuer.seed,
                programManifestBytes = manifest.toWireBytes(),
                createdAt = 0L,
                color = 0xFF00897B.toInt(),
                icon = "☕",
            ),
        )

        val message = IssuerMessage.parse(repository.mintStamp(manifest.programId)!!)

        val grant = assertInstanceOf(IssuerMessage.StampGrant::class.java, message)
        assertEquals(manifest.programId, ProgramManifest.parseAndVerify(grant.manifestBytes).programId)
    }

    @Test
    fun `minting a stamp records it in the issuer's minted ledger`() = runBlocking {
        seedProgram(threshold = 1)

        val stampIds = mintStamps(1)

        val known = mintedStampDao.findKnown(PROGRAM_ID, stampIds.map { it.toHex() })
        assertEquals(1, known.size)
    }

    @Test
    fun `large redemption succeeds for stamp ids the issuer actually minted`() = runBlocking {
        seedProgram(threshold = 2)
        val stampIds = mintStamps(2)

        val outcome = repository.handleCustomerMessage(
            PROGRAM_ID,
            CustomerMessage.LargeRedemptionRequest(PROGRAM_ID, stampIds).toWireBytes(),
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
            CustomerMessage.LargeRedemptionRequest(PROGRAM_ID, listOf(forgedStampId)).toWireBytes(),
        )

        assertInstanceOf(IssuerScanOutcome.Failed::class.java, outcome)
        assertEquals(0, redeemedStampDao.getAll().size)
    }

    @Test
    fun `large redemption rejects a stamp id already redeemed`() = runBlocking {
        seedProgram(threshold = 1)
        val stampIds = mintStamps(1)
        val request = CustomerMessage.LargeRedemptionRequest(PROGRAM_ID, stampIds).toWireBytes()
        assertInstanceOf(IssuerScanOutcome.Redeemed::class.java, repository.handleCustomerMessage(PROGRAM_ID, request))

        val secondAttempt = repository.handleCustomerMessage(PROGRAM_ID, request)

        assertInstanceOf(IssuerScanOutcome.Failed::class.java, secondAttempt)
        Unit
    }
}
