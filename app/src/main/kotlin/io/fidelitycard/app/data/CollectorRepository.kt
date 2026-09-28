package io.fidelitycard.app.data

import android.util.Log
import io.fidelitycard.app.qr.CustomerMessage
import io.fidelitycard.app.qr.IssuerMessage
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

/**
 * What happened after scanning whatever an issuer's screen was showing
 * (SPEC/SPECS.md §6.1/§6.2): a plain invite (just joins), a stamp grant
 * for a program this device has no card for yet (creates the card *and*
 * credits the stamp, in one scan - [justJoined] is `true`), or a stamp
 * grant for a program already joined (just credits the stamp).
 */
sealed interface ScanBusinessOutcome {
    data class Joined(val cardId: String, val programName: String) : ScanBusinessOutcome
    data class Stamped(val cardId: String, val progress: CardProgress, val justJoined: Boolean) : ScanBusinessOutcome
    data class Rejected(val reason: String) : ScanBusinessOutcome
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
    private val redeemedStampDao: CollectorRedeemedStampDao,
) {

    fun observeCards(): Flow<List<CardSummary>> =
        cardDao.observeAllWithStampCount().map { rows -> rows.map { it.toSummary() } }

    fun observeCard(cardId: String): Flow<CardSummary?> =
        cardDao.observeWithStampCount(cardId).map { it?.toSummary() }

    /**
     * The single entry point for whatever an issuer's screen is showing
     * (SPEC/SPECS.md §6.1/§6.2) - a plain invite or a stamp grant, and
     * whether the grant is this device's very first stamp for that
     * program or its hundredth: a stamp grant always carries the Program
     * Manifest, so there's no separate "join first" case to handle
     * specially. One card per program per device (a repeat invite or
     * grant for a program already joined reuses the existing card rather
     * than creating a duplicate).
     *
     * [expectedCardId], if given, rejects a scan for any other business -
     * used when this is reached from an already-open card's own "Get a
     * stamp" button, so scanning a stray, unrelated business's QR doesn't
     * silently create or credit the wrong card.
     */
    suspend fun acceptIssuerMessage(scannedBytes: ByteArray, expectedCardId: String? = null): ScanBusinessOutcome {
        val message = IssuerMessage.parse(scannedBytes)
            ?: return ScanBusinessOutcome.Rejected("Couldn't read that code - ask the business to try again")

        val manifest = try {
            ProgramManifest.parseAndVerify(message.manifestBytes)
        } catch (e: InvalidSignatureException) {
            // A business's own Program Manifest failing to self-verify is
            // unusual (unlike a stamp/redemption, there's no "wrong key"
            // case here - it's self-signed) and worth a real log line.
            Log.w(TAG, "Scanned program manifest did not verify", e)
            return ScanBusinessOutcome.Rejected("That business's code doesn't look right")
        } catch (e: MalformedMessageException) {
            // Routine: this is also where scanning any unrelated QR code lands.
            Log.d(TAG, "Scanned bytes did not contain a valid program manifest", e)
            return ScanBusinessOutcome.Rejected("Couldn't read that code - ask the business to try again")
        }

        val expectedCard = expectedCardId?.let { cardDao.findById(it) }
        if (expectedCard != null && manifest.programId != expectedCard.programId) {
            return ScanBusinessOutcome.Rejected("That code is for a different business")
        }

        val existing = expectedCard ?: cardDao.findByProgramId(manifest.programId)
        val card = existing ?: run {
            val entity = CollectorCardEntity(
                cardId = UUID.randomUUID().toString(),
                programId = manifest.programId,
                issuerPublicKey = manifest.issuerPublicKey.bytes,
                programName = manifest.name,
                threshold = manifest.threshold,
                reward = manifest.reward,
                createdAt = System.currentTimeMillis(),
                color = manifest.color,
                icon = manifest.icon,
            )
            cardDao.insert(entity)
            entity
        }

        return when (message) {
            is IssuerMessage.ProgramInvite -> ScanBusinessOutcome.Joined(card.cardId, card.programName)
            is IssuerMessage.StampGrant -> acceptStampGrant(card, message.stampBytes, justJoined = existing == null)
        }
    }

    private suspend fun acceptStampGrant(card: CollectorCardEntity, stampBytes: ByteArray, justJoined: Boolean): ScanBusinessOutcome {
        val issuerPublicKey = VerifyingKey(card.issuerPublicKey)

        val stamp = try {
            StampToken.parseAndVerify(stampBytes, issuerPublicKey)
        } catch (e: InvalidSignatureException) {
            Log.w(TAG, "Stamp did not verify against this card's pinned issuer key (card=${card.cardId})", e)
            return ScanBusinessOutcome.Rejected("That code isn't a valid stamp from this business")
        } catch (e: MalformedMessageException) {
            Log.w(TAG, "Stamp was malformed (card=${card.cardId})", e)
            return ScanBusinessOutcome.Rejected("That code isn't a valid stamp from this business")
        }
        // The manifest and the stamp are separately signed, and an issuer
        // *can* share one keypair across several of its own programs
        // (SPEC/SPECS.md §4) - so a validly-signed stamp for a *different*
        // program than the manifest it arrived bundled with is possible
        // and must still be rejected here, not just trusted because the
        // signature checked out.
        if (stamp.programId != card.programId) {
            return ScanBusinessOutcome.Rejected("That stamp is for a different business")
        }

        val stampIdHex = stamp.stampId.toHex()
        if (redeemedStampDao.findRedeemed(card.programId, listOf(stampIdHex)).isNotEmpty()) {
            // Expected occasionally: a re-scanned screenshot, or the
            // issuer's screen not having moved on from the last stamp it
            // showed. Caught here, immediately, rather than letting it
            // silently re-join the held pool and only surface later as a
            // whole redemption batch failing for an unrelated-looking
            // reason (see CollectorRedeemedStampEntity's doc).
            Log.w(TAG, "Stamp rejected: already redeemed by this device (program=${card.programId})")
            return ScanBusinessOutcome.Rejected("You've already redeemed this stamp")
        }

        stampDao.insert(CollectorStampEntity(card.cardId, stampIdHex, stampBytes))
        val newCount = stampDao.findAllForCard(card.cardId).size
        return ScanBusinessOutcome.Stamped(card.cardId, CardProgress.compute(newCount, card.threshold), justJoined)
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

        val redeemedAt = System.currentTimeMillis()
        redeemedStampDao.insertAll(pending.stampIdHexes.map { CollectorRedeemedStampEntity(card.programId, it, redeemedAt) })
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
