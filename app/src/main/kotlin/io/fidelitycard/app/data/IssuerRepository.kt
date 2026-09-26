package io.fidelitycard.app.data

import io.fidelitycard.app.qr.CustomerMessage
import io.fidelitycard.core.RedemptionValidator
import io.fidelitycard.core.StampIssuance
import io.fidelitycard.core.StampRequestDecision
import io.fidelitycard.crypto.CardCertificate
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
    data class Enrolled(val responseBytes: ByteArray) : IssuerScanOutcome

    /** [wasResent] distinguishes a genuinely new stamp from catching a card up on one it already earned. */
    data class Stamped(val responseBytes: ByteArray, val newStampCount: Int, val wasResent: Boolean) : IssuerScanOutcome
    data class Redeemed(val responseBytes: ByteArray) : IssuerScanOutcome
    data class Failed(val message: String) : IssuerScanOutcome
}

/**
 * Everything the issuer side of the app does: create programs, and respond
 * to whatever a customer's phone shows via the single "Scan a customer"
 * action (SPEC/SPECS.md §6). Each program's signing key is reconstructed
 * from its stored seed only for the moment it's needed to sign a response,
 * never held longer than that.
 */
class IssuerRepository(
    private val programDao: IssuerProgramDao,
    private val issuedCardDao: IssuedCardDao,
    private val issuedStampDao: IssuedStampDao,
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
            is CustomerMessage.JoinRequest -> handleJoin(program, issuer, message)
            is CustomerMessage.StampRequest -> handleStamp(program, issuer, message)
            is CustomerMessage.RedemptionRequest -> handleRedemption(program, issuer, message)
        }
    }

    private suspend fun handleJoin(
        program: IssuerProgramEntity,
        issuer: SigningKeyPair,
        request: CustomerMessage.JoinRequest,
    ): IssuerScanOutcome {
        if (issuedCardDao.find(program.programId, request.cardId) != null) {
            return IssuerScanOutcome.Failed("This card was already created")
        }
        val cert = CardCertificate.issue(issuer, program.programId, request.cardId, request.collectorPublicKey)
        issuedCardDao.insert(
            IssuedCardEntity(
                programId = program.programId,
                cardId = request.cardId,
                collectorPublicKey = request.collectorPublicKey.bytes,
                issuedSerialCount = 0,
                redeemedThroughSerial = 0,
                createdAt = System.currentTimeMillis(),
            ),
        )
        return IssuerScanOutcome.Enrolled(cert.toWireBytes())
    }

    private suspend fun handleStamp(
        program: IssuerProgramEntity,
        issuer: SigningKeyPair,
        request: CustomerMessage.StampRequest,
    ): IssuerScanOutcome {
        val card = issuedCardDao.find(program.programId, request.cardId)
            ?: return IssuerScanOutcome.Failed("This card could not be found")

        return when (
            val decision = StampIssuance.decide(
                requestedLastAcceptedSerial = request.lastAcceptedSerial,
                issuedSerialCount = card.issuedSerialCount,
            )
        ) {
            is StampRequestDecision.MintNew -> {
                val stamp = StampToken.mint(issuer, program.programId, card.cardId, decision.nextSerial)
                val wireBytes = stamp.toWireBytes()
                issuedStampDao.insert(IssuedStampEntity(program.programId, card.cardId, decision.nextSerial, wireBytes))
                issuedCardDao.update(card.copy(issuedSerialCount = decision.nextSerial))
                IssuerScanOutcome.Stamped(wireBytes, decision.nextSerial, wasResent = false)
            }
            is StampRequestDecision.ResendPrevious -> {
                val previous = issuedStampDao.find(program.programId, card.cardId, decision.serial)
                    ?: return IssuerScanOutcome.Failed(
                        "This card's records are inconsistent and can't be repaired automatically - " +
                            "ask the customer to leave and rejoin this business",
                    )
                IssuerScanOutcome.Stamped(previous.stampTokenBytes, decision.serial, wasResent = true)
            }
            is StampRequestDecision.Rejected -> IssuerScanOutcome.Failed(
                "This card's records are inconsistent and can't be repaired automatically - " +
                    "ask the customer to leave and rejoin this business",
            )
        }
    }

    private suspend fun handleRedemption(
        program: IssuerProgramEntity,
        issuer: SigningKeyPair,
        request: CustomerMessage.RedemptionRequest,
    ): IssuerScanOutcome {
        val card = issuedCardDao.find(program.programId, request.cardId)
            ?: return IssuerScanOutcome.Failed("This card could not be found")

        val serials = try {
            request.stampTokenWireBytes.map { wireBytes ->
                val token = StampToken.parseAndVerify(wireBytes, issuer.publicKey)
                check(token.cardId == card.cardId) { "stamp belongs to a different card" }
                token.serial
            }.sorted()
        } catch (e: InvalidSignatureException) {
            return IssuerScanOutcome.Failed("One of those stamps isn't valid")
        } catch (e: MalformedMessageException) {
            return IssuerScanOutcome.Failed("One of those stamps isn't valid")
        } catch (e: IllegalStateException) {
            return IssuerScanOutcome.Failed("One of those stamps isn't valid")
        }

        return when (
            val result = RedemptionValidator.validate(
                submittedSerialsSortedAscending = serials,
                alreadyRedeemedThroughSerial = card.redeemedThroughSerial,
                issuedSerialCount = card.issuedSerialCount,
                threshold = program.threshold,
            )
        ) {
            is RedemptionValidator.Result.Invalid -> IssuerScanOutcome.Failed(result.reason)
            is RedemptionValidator.Result.Valid -> {
                val cert = RedemptionCertificate.issue(
                    issuer, program.programId, card.cardId, result.redeemedThroughSerial,
                )
                issuedCardDao.update(card.copy(redeemedThroughSerial = result.redeemedThroughSerial))
                IssuerScanOutcome.Redeemed(cert.toWireBytes())
            }
        }
    }

    private fun CustomerMessage.programId(): String = when (this) {
        is CustomerMessage.JoinRequest -> programId
        is CustomerMessage.StampRequest -> programId
        is CustomerMessage.RedemptionRequest -> programId
    }

    private fun IssuerProgramEntity.toSummary() =
        ProgramSummary(programId, name, threshold, reward, programManifestBytes)
}
