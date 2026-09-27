package io.fidelitycard.app.data

import io.fidelitycard.app.qr.CustomerMessage
import io.fidelitycard.core.RedemptionValidator
import io.fidelitycard.crypto.InvalidSignatureException
import io.fidelitycard.crypto.ProgramManifest
import io.fidelitycard.crypto.RedemptionCertificate
import io.fidelitycard.crypto.SigningKeyPair
import io.fidelitycard.crypto.StampToken
import io.fidelitycard.crypto.wire.MalformedMessageException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class ProgramSummary(
    val programId: String,
    val name: String,
    val threshold: Int,
    val reward: String,
    val programManifestBytes: ByteArray,
)

/** What happened after the issuer scanned whatever a customer's phone was showing. */
sealed interface IssuerScanOutcome {
    data class Stamped(val responseBytes: ByteArray) : IssuerScanOutcome
    data class Redeemed(val responseBytes: ByteArray) : IssuerScanOutcome
    data class Failed(val message: String) : IssuerScanOutcome
}

/**
 * Everything the issuer side of the app does: create programs, and respond
 * to whatever a customer's phone shows via the single "Scan a customer"
 * action (SPEC/SPECS.md §6). Each program's signing key is reconstructed
 * from its stored seed only for the moment it's needed to sign a response,
 * never held longer than that.
 *
 * There is no enrollment step (SPEC/SPECS.md §6.1): a card id is first
 * seen (and lazily recorded) the moment it shows up in a stamp request.
 * Stamps are unordered (§6.2): minting one is unconditional, gated only by
 * the cashier's own decision to tap "Scan a customer" for a real purchase
 * - the same trust model as a paper stamp card. Only redemption is
 * cryptographically gated, via [RedemptionValidator] and the spent-stamp-id
 * store in [redeemedStampDao].
 */
class IssuerRepository(
    private val programDao: IssuerProgramDao,
    private val issuedCardDao: IssuedCardDao,
    private val redeemedStampDao: RedeemedStampDao,
) {

    fun observePrograms(): Flow<List<ProgramSummary>> =
        programDao.observeAll().map { entities -> entities.map { it.toSummary() } }

    suspend fun findProgram(programId: String): ProgramSummary? =
        programDao.findById(programId)?.toSummary()

    suspend fun createProgram(name: String, threshold: Int, reward: String): ProgramSummary {
        val issuer = SigningKeyPair.generate()
        val manifest = ProgramManifest.issue(issuer, name, threshold, reward)
        val entity = IssuerProgramEntity(
            programId = manifest.programId,
            name = name,
            threshold = threshold,
            reward = reward,
            issuerSeed = issuer.seed,
            programManifestBytes = manifest.toWireBytes(),
            createdAt = System.currentTimeMillis(),
        )
        programDao.insert(entity)
        return entity.toSummary()
    }

    /** The single entry point behind "Scan a customer": figures out what the scan means and responds to it. */
    suspend fun handleCustomerMessage(programId: String, scannedBytes: ByteArray): IssuerScanOutcome {
        val program = programDao.findById(programId)
            ?: return IssuerScanOutcome.Failed("This business could not be found")
        val message = CustomerMessage.parse(scannedBytes)
            ?: return IssuerScanOutcome.Failed("Couldn't read that code - ask the customer to try again")
        if (message.programId() != program.programId) {
            return IssuerScanOutcome.Failed("That code is for a different business")
        }

        val issuer = SigningKeyPair.fromSeed(program.issuerSeed)
        return when (message) {
            is CustomerMessage.StampRequest -> handleStamp(program, issuer, message)
            is CustomerMessage.RedemptionRequest -> handleRedemption(program, issuer, message)
        }
    }

    private suspend fun handleStamp(
        program: IssuerProgramEntity,
        issuer: SigningKeyPair,
        request: CustomerMessage.StampRequest,
    ): IssuerScanOutcome {
        if (issuedCardDao.find(program.programId, request.cardId) == null) {
            issuedCardDao.insert(
                IssuedCardEntity(program.programId, request.cardId, createdAt = System.currentTimeMillis()),
            )
        }
        val stamp = StampToken.mint(issuer, program.programId, request.cardId)
        return IssuerScanOutcome.Stamped(stamp.toWireBytes())
    }

    private suspend fun handleRedemption(
        program: IssuerProgramEntity,
        issuer: SigningKeyPair,
        request: CustomerMessage.RedemptionRequest,
    ): IssuerScanOutcome {
        val card = issuedCardDao.find(program.programId, request.cardId)
            ?: return IssuerScanOutcome.Failed("This card could not be found")

        val stampIdHexes = try {
            request.stampTokenWireBytes.map { wireBytes ->
                val token = StampToken.parseAndVerify(wireBytes, issuer.publicKey)
                check(token.cardId == card.cardId) { "stamp belongs to a different card" }
                token.stampId.toHex()
            }
        } catch (e: InvalidSignatureException) {
            return IssuerScanOutcome.Failed("One of those stamps isn't valid")
        } catch (e: MalformedMessageException) {
            return IssuerScanOutcome.Failed("One of those stamps isn't valid")
        } catch (e: IllegalStateException) {
            return IssuerScanOutcome.Failed("One of those stamps isn't valid")
        }

        val alreadyRedeemed = redeemedStampDao.findAlreadyRedeemed(program.programId, card.cardId, stampIdHexes).toSet()
        return when (
            val result = RedemptionValidator.validate(
                submittedStampIds = stampIdHexes,
                alreadyRedeemedStampIds = alreadyRedeemed,
                threshold = program.threshold,
            )
        ) {
            is RedemptionValidator.Result.Invalid -> IssuerScanOutcome.Failed(result.reason)
            RedemptionValidator.Result.Valid -> {
                val redeemedAt = System.currentTimeMillis()
                redeemedStampDao.insertAll(
                    stampIdHexes.map { RedeemedStampEntity(program.programId, card.cardId, it, redeemedAt) },
                )
                val cert = RedemptionCertificate.issue(issuer, program.programId, card.cardId, stampIdHexes.size)
                IssuerScanOutcome.Redeemed(cert.toWireBytes())
            }
        }
    }

    private fun CustomerMessage.programId(): String = when (this) {
        is CustomerMessage.StampRequest -> programId
        is CustomerMessage.RedemptionRequest -> programId
    }

    private fun IssuerProgramEntity.toSummary() =
        ProgramSummary(programId, name, threshold, reward, programManifestBytes)
}
