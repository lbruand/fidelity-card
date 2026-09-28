package io.fidelitycard.app.data

import io.fidelitycard.app.qr.CustomerMessage
import io.fidelitycard.app.qr.IssuerMessage
import io.fidelitycard.crypto.ProgramManifest
import io.fidelitycard.crypto.RedemptionCertificate
import io.fidelitycard.crypto.SigningKeyPair
import io.fidelitycard.crypto.StampToken
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

private const val CARD_ID = "card-1"

class CollectorRepositoryTest {

    private val issuer = SigningKeyPair.generate()
    private val manifest = ProgramManifest.issue(
        issuer, name = "Joe's Coffee", threshold = 10, reward = "Free coffee",
        color = 0xFF00897B.toInt(), icon = "☕",
    )
    private val programId = manifest.programId
    private val cardDao = FakeCollectorCardDao()
    private val stampDao = FakeCollectorStampDao()
    private val redeemedStampDao = FakeCollectorRedeemedStampDao()
    private val repository = CollectorRepository(cardDao, stampDao, redeemedStampDao)

    private suspend fun seedCard(threshold: Int) {
        cardDao.insert(
            CollectorCardEntity(
                cardId = CARD_ID,
                programId = programId,
                issuerPublicKey = issuer.publicKey.bytes,
                programName = "Joe's Coffee",
                threshold = threshold,
                reward = "Free coffee",
                createdAt = 0L,
                color = 0xFF00897B.toInt(),
                icon = "☕",
            ),
        )
    }

    private suspend fun holdStamps(count: Int) {
        (1..count).forEach {
            val token = StampToken.mint(issuer, programId)
            stampDao.insert(CollectorStampEntity(CARD_ID, token.stampId.toHex(), token.toWireBytes()))
        }
    }

    private fun stampGrantBytes(programManifest: ProgramManifest = manifest): ByteArray {
        val stamp = StampToken.mint(issuer, programManifest.programId)
        return IssuerMessage.StampGrant(programManifest.toWireBytes(), stamp.toWireBytes()).toWireBytes()
    }

    @Test
    fun `a stamp grant for a program with no existing card creates the card and credits the stamp in one scan`() = runBlocking {
        val outcome = repository.acceptIssuerMessage(stampGrantBytes())

        val stamped = assertInstanceOf(ScanBusinessOutcome.Stamped::class.java, outcome)
        assertEquals(true, stamped.justJoined)
        assertEquals(1, stamped.progress.stampCount)
        assertEquals(1, cardDao.getAll().size)
    }

    @Test
    fun `a stamp grant for an already-joined program credits the existing card, not a duplicate`() = runBlocking {
        seedCard(threshold = 5)

        val outcome = repository.acceptIssuerMessage(stampGrantBytes())

        val stamped = assertInstanceOf(ScanBusinessOutcome.Stamped::class.java, outcome)
        assertEquals(false, stamped.justJoined)
        assertEquals(CARD_ID, stamped.cardId)
        assertEquals(1, cardDao.getAll().size)
    }

    @Test
    fun `a plain program invite just joins, with no stamp`() = runBlocking {
        val outcome = repository.acceptIssuerMessage(IssuerMessage.ProgramInvite(manifest.toWireBytes()).toWireBytes())

        assertInstanceOf(ScanBusinessOutcome.Joined::class.java, outcome)
        assertEquals(0, stampDao.getAll().size)
    }

    @Test
    fun `re-scanning the exact same stamp grant twice is a harmless no-op, not a crash`() = runBlocking {
        val bytes = stampGrantBytes()

        val first = repository.acceptIssuerMessage(bytes)
        val second = repository.acceptIssuerMessage(bytes)

        val firstStamped = assertInstanceOf(ScanBusinessOutcome.Stamped::class.java, first)
        val secondStamped = assertInstanceOf(ScanBusinessOutcome.Stamped::class.java, second)
        assertEquals(firstStamped.progress.stampCount, secondStamped.progress.stampCount)
    }

    @Test
    fun `rejects a stamp grant whose stamp was minted for a different program than its own bundled manifest`() = runBlocking {
        // Same issuer keypair can legitimately back more than one program
        // (SPEC/SPECS.md §4) - a manifest for program A bundled with a
        // validly-signed stamp for program B must still be rejected.
        val otherManifest = ProgramManifest.issue(issuer, "Jane's Tea", threshold = 5, reward = "Free tea", color = 0xFFD84315.toInt(), icon = "🍵")
        val stampForOther = StampToken.mint(issuer, otherManifest.programId)
        val mismatchedGrant = IssuerMessage.StampGrant(manifest.toWireBytes(), stampForOther.toWireBytes()).toWireBytes()

        val outcome = repository.acceptIssuerMessage(mismatchedGrant)

        assertInstanceOf(ScanBusinessOutcome.Rejected::class.java, outcome)
        Unit
    }

    @Test
    fun `rejects a scan for a different business than the one already open`() = runBlocking {
        seedCard(threshold = 5)
        val otherManifest = ProgramManifest.issue(issuer, "Jane's Tea", threshold = 5, reward = "Free tea", color = 0xFFD84315.toInt(), icon = "🍵")

        val outcome = repository.acceptIssuerMessage(stampGrantBytes(otherManifest), expectedCardId = CARD_ID)

        assertInstanceOf(ScanBusinessOutcome.Rejected::class.java, outcome)
        assertNull(cardDao.findByProgramId(otherManifest.programId))
    }

    @Test
    fun `a stamp already redeemed by this device is rejected if scanned again, not silently re-added`() = runBlocking {
        seedCard(threshold = 1)
        val stamp = StampToken.mint(issuer, programId)
        val grantBytes = IssuerMessage.StampGrant(manifest.toWireBytes(), stamp.toWireBytes()).toWireBytes()
        assertInstanceOf(ScanBusinessOutcome.Stamped::class.java, repository.acceptIssuerMessage(grantBytes))
        val cert = RedemptionCertificate.issue(issuer, programId, redeemedCount = 1)
        val redeemed = repository.acceptRedemptionResponse(
            CARD_ID,
            PendingRedemption(listOf(stamp.stampId.toHex()), ByteArray(0)),
            cert.toWireBytes(),
        )
        assertInstanceOf(RedemptionAcceptOutcome.Accepted::class.java, redeemed)

        // A screenshot of the same stamp QR, or the issuer's screen not
        // having moved on - re-scanning it must not silently count toward
        // the *next* reward.
        val replay = repository.acceptIssuerMessage(grantBytes)

        assertInstanceOf(ScanBusinessOutcome.Rejected::class.java, replay)
        Unit
    }

    @Test
    fun `the already-redeemed memory survives leaving and rejoining the same business`() = runBlocking {
        seedCard(threshold = 1)
        val stamp = StampToken.mint(issuer, programId)
        val grantBytes = IssuerMessage.StampGrant(manifest.toWireBytes(), stamp.toWireBytes()).toWireBytes()
        repository.acceptIssuerMessage(grantBytes)
        val cert = RedemptionCertificate.issue(issuer, programId, redeemedCount = 1)
        repository.acceptRedemptionResponse(
            CARD_ID,
            PendingRedemption(listOf(stamp.stampId.toHex()), ByteArray(0)),
            cert.toWireBytes(),
        )

        repository.leaveBusiness(CARD_ID)
        // Rejoining creates a brand new local card_id - the redeemed-stamp
        // memory must not be keyed by that (§7: it would reset to empty),
        // or "leave and rejoin" would be a way to launder an
        // already-redeemed stamp back into a usable one.
        val rejoinAndReplay = repository.acceptIssuerMessage(grantBytes)

        assertInstanceOf(ScanBusinessOutcome.Rejected::class.java, rejoinAndReplay)
        Unit
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
