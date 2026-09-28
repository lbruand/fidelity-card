package io.fidelitycard.app.data

import android.util.Log
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

private const val TAG = "IssuerRepository"

data class ProgramSummary(
    val programId: String,
    val name: String,
    val threshold: Int,
    val reward: String,
    val programManifestBytes: ByteArray,
    val color: Int,
    val icon: String,
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
    private val mintedStampDao: IssuerMintedStampDao,
) {

    fun observePrograms(): Flow<List<ProgramSummary>> =
        programDao.observeAll().map { entities -> entities.map { it.toSummary() } }

    suspend fun findProgram(programId: String): ProgramSummary? =
        programDao.findById(programId)?.toSummary()

    suspend fun createProgram(name: String, threshold: Int, reward: String, color: Int, icon: String): ProgramSummary {
        val issuer = SigningKeyPair.generate()
        val manifest = ProgramManifest.issue(issuer, name, threshold, reward, color, icon)
        val entity = IssuerProgramEntity(
            programId = manifest.programId,
            name = name,
            threshold = threshold,
            reward = reward,
            issuerSeed = issuer.seed,
            programManifestBytes = manifest.toWireBytes(),
            createdAt = System.currentTimeMillis(),
            color = color,
            icon = icon,
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
            is CustomerMessage.LargeRedemptionRequest -> handleLargeRedemption(program, message)
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
        mintedStampDao.insert(
            IssuerMintedStampEntity(program.programId, request.cardId, stamp.stampId.toHex(), mintedAt = System.currentTimeMillis()),
        )
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
            request.compactStampProofs.map { proof ->
                val token = StampToken.parseAndVerifyCompactProof(proof, program.programId, card.cardId, issuer.publicKey)
                token.stampId.toHex()
            }
        } catch (e: InvalidSignatureException) {
            // Expected occasionally: a forged/tampered stamp, or a stamp from
            // a different program's key. Not necessarily a bug, but worth a
            // real log line rather than vanishing - this is exactly the kind
            // of thing that's indistinguishable from a wire-format regression
            // without one.
            Log.w(TAG, "Redemption rejected: stamp signature did not verify (program=${program.programId}, card=${card.cardId})", e)
            return IssuerScanOutcome.Failed("One of those stamps isn't valid")
        } catch (e: MalformedMessageException) {
            Log.w(TAG, "Redemption rejected: a stamp proof was malformed (program=${program.programId}, card=${card.cardId})", e)
            return IssuerScanOutcome.Failed("One of those stamps isn't valid")
        } catch (e: IllegalArgumentException) {
            // This one *shouldn't* be reachable via the app's own encode/decode
            // path (compact proofs are always fixed-length) - if it fires, it
            // most likely means a real bug, so log it louder.
            Log.e(TAG, "Redemption rejected: a stamp proof had an unexpected length - likely a bug, not user error (program=${program.programId}, card=${card.cardId})", e)
            return IssuerScanOutcome.Failed("One of those stamps isn't valid")
        }

        return finalizeRedemption(program, issuer, card, stampIdHexes)
    }

    /**
     * The large-threshold fallback (SPEC/SPECS.md §6.3/§11): stamp ids
     * arrive bare, with no signature to check, so authenticity comes from
     * this issuer device's own [mintedStampDao] ledger instead - a raw id
     * this device never minted for this card simply won't be found.
     */
    private suspend fun handleLargeRedemption(
        program: IssuerProgramEntity,
        request: CustomerMessage.LargeRedemptionRequest,
    ): IssuerScanOutcome {
        val card = issuedCardDao.find(program.programId, request.cardId)
            ?: return IssuerScanOutcome.Failed("This card could not be found")
        val issuer = SigningKeyPair.fromSeed(program.issuerSeed)

        val stampIdHexes = request.stampIds.map { it.toHex() }
        val known = mintedStampDao.findKnown(program.programId, card.cardId, stampIdHexes).toSet()
        if (known.size != stampIdHexes.toSet().size) {
            // Expected occasionally: a stamp id this issuer never minted for
            // this card (forged, or from a different card/program) - the
            // ledger equivalent of a signature failing to verify.
            Log.w(TAG, "Large redemption rejected: a stamp id was not found in this issuer's minted ledger (program=${program.programId}, card=${card.cardId})")
            return IssuerScanOutcome.Failed("One of those stamps isn't valid")
        }

        return finalizeRedemption(program, issuer, card, stampIdHexes)
    }

    private suspend fun finalizeRedemption(
        program: IssuerProgramEntity,
        issuer: SigningKeyPair,
        card: IssuedCardEntity,
        stampIdHexes: List<String>,
    ): IssuerScanOutcome {
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
        is CustomerMessage.LargeRedemptionRequest -> programId
    }

    private fun IssuerProgramEntity.toSummary() =
        ProgramSummary(programId, name, threshold, reward, programManifestBytes, color, icon)
}
