package io.fidelitycard.app.data

import io.fidelitycard.app.qr.CustomerMessage
import io.fidelitycard.core.CardProgress
import io.fidelitycard.crypto.CardCertificate
import io.fidelitycard.crypto.InvalidSignatureException
import io.fidelitycard.crypto.ProgramManifest
import io.fidelitycard.crypto.RedemptionCertificate
import io.fidelitycard.crypto.SigningKeyPair
import io.fidelitycard.crypto.StampToken
import io.fidelitycard.crypto.VerifyingKey
import io.fidelitycard.crypto.wire.MalformedMessageException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.util.UUID

data class CardSummary(
    val cardId: String,
    val programId: String,
    val programName: String,
    val reward: String,
    val progress: CardProgress,
)

/** Held by the UI between "scanned the business's QR" and "scanned their reply" - not persisted until it succeeds. */
data class PendingJoin(
    val program: ProgramManifest,
    val collectorKeyPair: SigningKeyPair,
    val cardId: String,
)

sealed interface StampAcceptOutcome {
    data class Accepted(val progress: CardProgress) : StampAcceptOutcome
    data class Rejected(val reason: String) : StampAcceptOutcome
}

/** The specific stamp ids included in an outgoing redemption request, so the response can be applied precisely. */
data class PendingRedemption(val stampIdHexes: List<String>, val requestBytes: ByteArray)

sealed interface RedemptionAcceptOutcome {
    data object Accepted : RedemptionAcceptOutcome
    data class Rejected(val reason: String) : RedemptionAcceptOutcome
}

/**
 * Everything the collector side of the app does: join a program, request
 * and accept stamps, and build/accept a redemption (SPEC/SPECS.md §6). Any
 * message this device didn't sign itself is parsed with `parseAndVerify`
 * and never trusted otherwise.
 */
class CollectorRepository(
    private val cardDao: CollectorCardDao,
    private val stampDao: CollectorStampDao,
) {

    fun observeCards(): Flow<List<CardSummary>> =
        cardDao.observeAllWithStampCount().map { rows -> rows.map { it.toSummary() } }

    fun observeCard(cardId: String): Flow<CardSummary?> =
        cardDao.observeWithStampCount(cardId).map { it?.toSummary() }

    fun parseProgramQr(bytes: ByteArray): ProgramManifest? = try {
        ProgramManifest.parseAndVerify(bytes)
    } catch (e: InvalidSignatureException) {
        null
    } catch (e: MalformedMessageException) {
        null
    }

    fun buildJoinRequest(program: ProgramManifest): Pair<PendingJoin, ByteArray> {
        val collectorKeyPair = SigningKeyPair.generate()
        val cardId = UUID.randomUUID().toString()
        val pending = PendingJoin(program, collectorKeyPair, cardId)
        val request = CustomerMessage.JoinRequest(
            programId = program.programId,
            cardId = cardId,
            collectorPublicKey = collectorKeyPair.publicKey,
            requestedAt = Instant.now(),
        )
        return pending to request.toWireBytes()
    }

    suspend fun completeJoin(pending: PendingJoin, cardCertBytes: ByteArray): CardSummary? {
        val cert = try {
            CardCertificate.parseAndVerify(cardCertBytes, pending.program.issuerPublicKey)
        } catch (e: InvalidSignatureException) {
            return null
        } catch (e: MalformedMessageException) {
            return null
        }
        if (cert.programId != pending.program.programId ||
            cert.cardId != pending.cardId ||
            cert.collectorPublicKey != pending.collectorKeyPair.publicKey
        ) {
            return null
        }

        val entity = CollectorCardEntity(
            cardId = pending.cardId,
            programId = pending.program.programId,
            issuerPublicKey = pending.program.issuerPublicKey.bytes,
            programName = pending.program.name,
            threshold = pending.program.threshold,
            reward = pending.program.reward,
            collectorSeed = pending.collectorKeyPair.seed,
            createdAt = System.currentTimeMillis(),
        )
        cardDao.insert(entity)
        return CardSummary(entity.cardId, entity.programId, entity.programName, entity.reward, CardProgress.compute(0, entity.threshold))
    }

    suspend fun buildStampRequest(cardId: String): ByteArray? {
        val card = cardDao.findById(cardId) ?: return null
        return CustomerMessage.StampRequest(card.programId, cardId).toWireBytes()
    }

    suspend fun acceptStampResponse(cardId: String, stampBytes: ByteArray): StampAcceptOutcome {
        val card = cardDao.findById(cardId)
            ?: return StampAcceptOutcome.Rejected("This card could not be found")
        val issuerPublicKey = VerifyingKey(card.issuerPublicKey)

        val stamp = try {
            StampToken.parseAndVerify(stampBytes, issuerPublicKey)
        } catch (e: InvalidSignatureException) {
            return StampAcceptOutcome.Rejected("That code isn't a valid stamp from this business")
        } catch (e: MalformedMessageException) {
            return StampAcceptOutcome.Rejected("That code isn't a valid stamp from this business")
        }
        if (stamp.programId != card.programId || stamp.cardId != card.cardId) {
            return StampAcceptOutcome.Rejected("That stamp is for a different card")
        }

        stampDao.insert(CollectorStampEntity(cardId, stamp.stampId.toHex(), stampBytes))
        val newCount = stampDao.findAllForCard(cardId).size
        return StampAcceptOutcome.Accepted(CardProgress.compute(newCount, card.threshold))
    }

    suspend fun buildRedemptionRequest(cardId: String): PendingRedemption? {
        val card = cardDao.findById(cardId) ?: return null
        val held = stampDao.findAllForCard(cardId)
        if (held.size < card.threshold) return null

        val request = CustomerMessage.RedemptionRequest(card.programId, cardId, held.map { it.stampTokenBytes })
        return PendingRedemption(held.map { it.stampIdHex }, request.toWireBytes())
    }

    suspend fun acceptRedemptionResponse(
        cardId: String,
        pending: PendingRedemption,
        certBytes: ByteArray,
    ): RedemptionAcceptOutcome {
        val card = cardDao.findById(cardId)
            ?: return RedemptionAcceptOutcome.Rejected("This card could not be found")
        val issuerPublicKey = VerifyingKey(card.issuerPublicKey)

        val cert = try {
            RedemptionCertificate.parseAndVerify(certBytes, issuerPublicKey)
        } catch (e: InvalidSignatureException) {
            return RedemptionAcceptOutcome.Rejected("That code isn't a valid confirmation from this business")
        } catch (e: MalformedMessageException) {
            return RedemptionAcceptOutcome.Rejected("That code isn't a valid confirmation from this business")
        }
        if (cert.programId != card.programId || cert.cardId != card.cardId) {
            return RedemptionAcceptOutcome.Rejected("That confirmation is for a different card")
        }

        stampDao.deleteByIds(cardId, pending.stampIdHexes)
        return RedemptionAcceptOutcome.Accepted
    }

    /**
     * Deletes a card and its stamps entirely, so the person can rejoin from
     * scratch. The escape hatch for state that can't otherwise be repaired
     * (a corrupted local database, a card mixed up between programs, etc):
     * irreversible, loses whatever progress this card had.
     */
    suspend fun leaveBusiness(cardId: String) {
        stampDao.deleteAllForCard(cardId)
        cardDao.deleteById(cardId)
    }

    private fun CardWithStampCount.toSummary() =
        CardSummary(cardId, programId, programName, reward, CardProgress.compute(stampCount, threshold))
}
