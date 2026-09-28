package io.fidelitycard.app.data

import android.util.Log
import io.fidelitycard.app.qr.CustomerMessage
import io.fidelitycard.core.CardProgress
import io.fidelitycard.crypto.InvalidSignatureException
import io.fidelitycard.crypto.ProgramManifest
import io.fidelitycard.crypto.RedemptionCertificate
import io.fidelitycard.crypto.StampToken
import io.fidelitycard.crypto.VerifyingKey
import io.fidelitycard.crypto.wire.MalformedMessageException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

private const val TAG = "CollectorRepository"

/**
 * Above this many stamps in one redemption, switch from Compact Stamp
 * Proofs (88 bytes/stamp) to bare stamp ids (16 bytes/stamp) to keep the
 * request scannable as one QR code - see [CollectorRepository.buildRedemptionRequest]
 * and CustomerMessage.LargeRedemptionRequest. ~30-40 compact proofs is the
 * documented comfortable ceiling for a single QR (SPEC/SPECS.md §6.3); 40
 * is picked to stay under that with some margin.
 */
private const val LARGE_REDEMPTION_STAMP_COUNT_THRESHOLD = 40

data class CardSummary(
    val cardId: String,
    val programId: String,
    val programName: String,
    val reward: String,
    val progress: CardProgress,
    val color: Int,
    val icon: String,
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
 * Everything the collector side of the app does: join a program, accept
 * stamps, and build/accept a redemption (SPEC/SPECS.md §6). Any message
 * this device didn't sign itself is parsed with `parseAndVerify` and
 * never trusted otherwise.
 *
 * The collector holds no cryptographic identity at all (SPEC/SPECS.md
 * §4/§6.1): a card id is just a locally-generated opaque string that never
 * leaves this device. Nothing downstream ever needs to verify who the
 * collector is, or which card a stamp belongs to (§5.2) - a Stamp Token's
 * or Redemption Certificate's issuer signature is what makes it real, and
 * possessing the actual signed stamp bytes is what makes a redemption
 * valid, neither of which requires the collector to prove anything about
 * itself.
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
        // A business's own Program Manifest failing to self-verify is
        // unusual (unlike a stamp/redemption, there's no "wrong key" case
        // here - it's self-signed) and worth a real log line.
        Log.w(TAG, "Scanned program QR did not verify", e)
        null
    } catch (e: MalformedMessageException) {
        // Routine: this is also where scanning any unrelated QR code lands.
        Log.d(TAG, "Scanned bytes are not a valid program manifest", e)
        null
    }

    /** Joining is purely local: no message to the issuer, no round trip - see SPEC/SPECS.md §6.1. */
    suspend fun joinProgram(program: ProgramManifest): CardSummary {
        val cardId = UUID.randomUUID().toString()
        val entity = CollectorCardEntity(
            cardId = cardId,
            programId = program.programId,
            issuerPublicKey = program.issuerPublicKey.bytes,
            programName = program.name,
            threshold = program.threshold,
            reward = program.reward,
            createdAt = System.currentTimeMillis(),
            color = program.color,
            icon = program.icon,
        )
        cardDao.insert(entity)
        return CardSummary(
            entity.cardId,
            entity.programId,
            entity.programName,
            entity.reward,
            CardProgress.compute(0, entity.threshold),
            entity.color,
            entity.icon,
        )
    }

    /**
     * Accepts whatever the issuer's screen is showing directly - a single
     * scan, no request sent first (SPEC/SPECS.md §6.2): a Stamp Token
     * isn't addressed to any particular card, so there's nothing for this
     * device to ask for in advance.
     */
    suspend fun acceptStampResponse(cardId: String, stampBytes: ByteArray): StampAcceptOutcome {
        val card = cardDao.findById(cardId)
            ?: return StampAcceptOutcome.Rejected("This card could not be found")
        val issuerPublicKey = VerifyingKey(card.issuerPublicKey)

        val stamp = try {
            StampToken.parseAndVerify(stampBytes, issuerPublicKey)
        } catch (e: InvalidSignatureException) {
            Log.w(TAG, "Stamp did not verify against this card's pinned issuer key (card=$cardId)", e)
            return StampAcceptOutcome.Rejected("That code isn't a valid stamp from this business")
        } catch (e: MalformedMessageException) {
            Log.w(TAG, "Stamp was malformed (card=$cardId)", e)
            return StampAcceptOutcome.Rejected("That code isn't a valid stamp from this business")
        }
        if (stamp.programId != card.programId) {
            return StampAcceptOutcome.Rejected("That stamp is for a different business")
        }

        stampDao.insert(CollectorStampEntity(cardId, stamp.stampId.toHex(), stampBytes))
        val newCount = stampDao.findAllForCard(cardId).size
        return StampAcceptOutcome.Accepted(CardProgress.compute(newCount, card.threshold))
    }

    suspend fun buildRedemptionRequest(cardId: String): PendingRedemption? {
        val card = cardDao.findById(cardId) ?: return null
        val issuerPublicKey = VerifyingKey(card.issuerPublicKey)
        val held = stampDao.findAllForCard(cardId)
        if (held.size < card.threshold) return null

        // Re-verifying here (cheap - one Ed25519 check per stamp) doubles as a
        // consistency check on our own stored bytes, and gives us each
        // stamp's raw id plus its compact proof (io.fidelitycard.crypto.
        // StampToken §5.2.1) up front, whichever mode ends up used below.
        val verified = held.mapNotNull { entity ->
            try {
                val token = StampToken.parseAndVerify(entity.stampTokenBytes, issuerPublicKey)
                Triple(entity.stampIdHex, token.stampId, token.toCompactProofBytes())
            } catch (e: InvalidSignatureException) {
                // This is our own previously-accepted stamp failing to
                // re-verify - should never happen. Silently dropping it would
                // just look like "not enough stamps yet" with no clue why, so
                // log loudly: this is a real bug (or a corrupted database),
                // not routine input.
                Log.e(TAG, "A stored stamp failed to re-verify while building a redemption request (card=$cardId, stampId=${entity.stampIdHex})", e)
                null
            } catch (e: MalformedMessageException) {
                Log.e(TAG, "A stored stamp's bytes were malformed while building a redemption request (card=$cardId, stampId=${entity.stampIdHex})", e)
                null
            }
        }
        if (verified.size < card.threshold) return null

        // Compact Stamp Proofs stay the default: they're self-contained
        // (a signature, verifiable independent of the issuer device's own
        // local state), which the raw-id fallback below deliberately isn't
        // (SPEC/SPECS.md §6.3/§11). Only switch once that many proofs would
        // push a QR past a comfortable size.
        val request = if (verified.size > LARGE_REDEMPTION_STAMP_COUNT_THRESHOLD) {
            CustomerMessage.LargeRedemptionRequest(card.programId, verified.map { it.second })
        } else {
            CustomerMessage.RedemptionRequest(card.programId, verified.map { it.third })
        }
        return PendingRedemption(verified.map { it.first }, request.toWireBytes())
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
            Log.w(TAG, "Redemption confirmation did not verify against this card's pinned issuer key (card=$cardId)", e)
            return RedemptionAcceptOutcome.Rejected("That code isn't a valid confirmation from this business")
        } catch (e: MalformedMessageException) {
            Log.w(TAG, "Redemption confirmation was malformed (card=$cardId)", e)
            return RedemptionAcceptOutcome.Rejected("That code isn't a valid confirmation from this business")
        }
        if (cert.programId != card.programId) {
            return RedemptionAcceptOutcome.Rejected("That confirmation is for a different business")
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
        CardSummary(cardId, programId, programName, reward, CardProgress.compute(stampCount, threshold), color, icon)
}
