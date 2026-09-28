package io.fidelitycard.app.data

import android.util.Log
import io.fidelitycard.app.qr.CustomerMessage
import io.fidelitycard.app.qr.IssuerMessage
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

/** What happened after the issuer scanned a customer's redemption request. */
sealed interface IssuerScanOutcome {
    data class Redeemed(val responseBytes: ByteArray) : IssuerScanOutcome
    data class Failed(val message: String) : IssuerScanOutcome
}

/**
 * Everything the issuer side of the app does: create programs, mint
 * stamps, and respond to a customer's redemption request via "Scan a
 * customer" (SPEC/SPECS.md §6). Each program's signing key is
 * reconstructed from its stored seed only for the moment it's needed to
 * sign a response, never held longer than that.
 *
 * There is no enrollment step (SPEC/SPECS.md §6.1), and a Stamp Token
 * isn't bound to any collector/card id either (§5.2/§6.2): minting is a
 * single one-way QR, unconditional, gated only by the cashier's own
 * decision to tap "Give a stamp" for a real purchase - the same trust
 * model as a paper stamp card. Only redemption is cryptographically
 * gated, via [RedemptionValidator] and the spent-stamp-id store in
 * [redeemedStampDao] (scoped per program, not per card - whoever holds
 * enough valid, unspent stamps can redeem them).
 */
class IssuerRepository(
    private val programDao: IssuerProgramDao,
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

    /**
     * "Give a stamp": mints unconditionally, no scan or customer input
     * needed first (SPEC/SPECS.md §6.2) - a stamp isn't addressed to
     * anyone, so there's nothing to learn from the customer before
     * minting one. Always bundles the Program Manifest alongside the
     * stamp (`IssuerMessage.StampGrant`), so a single scan works whether
     * the collector already has a card for this program or this is their
     * very first stamp - no separate "join" scan is ever required.
     * Returns `null` only if [programId] itself doesn't exist (e.g. stale
     * UI state).
     */
    suspend fun mintStamp(programId: String): ByteArray? {
        val program = programDao.findById(programId) ?: return null
        val issuer = SigningKeyPair.fromSeed(program.issuerSeed)
        val stamp = StampToken.mint(issuer, program.programId)
        mintedStampDao.insert(IssuerMintedStampEntity(program.programId, stamp.stampId.toHex(), mintedAt = System.currentTimeMillis()))
        return IssuerMessage.StampGrant(program.programManifestBytes, stamp.toWireBytes()).toWireBytes()
    }

    /** The single entry point behind "Scan a customer": a redemption request, small or large. */
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
            is CustomerMessage.RedemptionRequest -> handleRedemption(program, issuer, message)
            is CustomerMessage.LargeRedemptionRequest -> handleLargeRedemption(program, issuer, message)
        }
    }

    private suspend fun handleRedemption(
        program: IssuerProgramEntity,
        issuer: SigningKeyPair,
        request: CustomerMessage.RedemptionRequest,
    ): IssuerScanOutcome {
        val stampIdHexes = try {
            request.compactStampProofs.map { proof ->
                val token = StampToken.parseAndVerifyCompactProof(proof, program.programId, issuer.publicKey)
                token.stampId.toHex()
            }
        } catch (e: InvalidSignatureException) {
            // Expected occasionally: a forged/tampered stamp, or a stamp from
            // a different program's key. Not necessarily a bug, but worth a
            // real log line rather than vanishing - this is exactly the kind
            // of thing that's indistinguishable from a wire-format regression
            // without one.
            Log.w(TAG, "Redemption rejected: stamp signature did not verify (program=${program.programId})", e)
            return IssuerScanOutcome.Failed("One of those stamps isn't valid")
        } catch (e: MalformedMessageException) {
            Log.w(TAG, "Redemption rejected: a stamp proof was malformed (program=${program.programId})", e)
            return IssuerScanOutcome.Failed("One of those stamps isn't valid")
        } catch (e: IllegalArgumentException) {
            // This one *shouldn't* be reachable via the app's own encode/decode
            // path (compact proofs are always fixed-length) - if it fires, it
            // most likely means a real bug, so log it louder.
            Log.e(TAG, "Redemption rejected: a stamp proof had an unexpected length - likely a bug, not user error (program=${program.programId})", e)
            return IssuerScanOutcome.Failed("One of those stamps isn't valid")
        }

        return finalizeRedemption(program, issuer, stampIdHexes)
    }

    /**
     * The large-threshold fallback (SPEC/SPECS.md §6.3/§11): stamp ids
     * arrive bare, with no signature to check, so authenticity comes from
     * this issuer device's own [mintedStampDao] ledger instead - a raw id
     * this device never minted simply won't be found.
     */
    private suspend fun handleLargeRedemption(
        program: IssuerProgramEntity,
        issuer: SigningKeyPair,
        request: CustomerMessage.LargeRedemptionRequest,
    ): IssuerScanOutcome {
        val stampIdHexes = request.stampIds.map { it.toHex() }
        val known = mintedStampDao.findKnown(program.programId, stampIdHexes).toSet()
        if (known.size != stampIdHexes.toSet().size) {
            // Expected occasionally: a stamp id this issuer never minted
            // (forged, or from a different program) - the ledger equivalent
            // of a signature failing to verify.
            Log.w(TAG, "Large redemption rejected: a stamp id was not found in this issuer's minted ledger (program=${program.programId})")
            return IssuerScanOutcome.Failed("One of those stamps isn't valid")
        }

        return finalizeRedemption(program, issuer, stampIdHexes)
    }

    private suspend fun finalizeRedemption(
        program: IssuerProgramEntity,
        issuer: SigningKeyPair,
        stampIdHexes: List<String>,
    ): IssuerScanOutcome {
        val alreadyRedeemed = redeemedStampDao.findAlreadyRedeemed(program.programId, stampIdHexes).toSet()
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
                    stampIdHexes.map { RedeemedStampEntity(program.programId, it, redeemedAt) },
                )
                val cert = RedemptionCertificate.issue(issuer, program.programId, stampIdHexes.size)
                IssuerScanOutcome.Redeemed(cert.toWireBytes())
            }
        }
    }

    private fun CustomerMessage.programId(): String = when (this) {
        is CustomerMessage.RedemptionRequest -> programId
        is CustomerMessage.LargeRedemptionRequest -> programId
    }

    private fun IssuerProgramEntity.toSummary() =
        ProgramSummary(programId, name, threshold, reward, programManifestBytes, color, icon)
}
