package io.fidelitycard.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A loyalty program this device issues. [issuerSeed] is the raw 32-byte
 * Ed25519 seed, stored as plain bytes rather than Android Keystore-backed -
 * a deliberate scope decision, not an oversight (SPEC/SPECS.md §2/§7).
 * [color]/[icon] are the card's visual personality, also signed inside
 * [programManifestBytes] (`io.fidelitycard.crypto.ProgramManifest`) -
 * duplicated here as plain columns purely so the issuer's own list/detail
 * screens can render them without re-parsing the manifest every time.
 */
@Entity(tableName = "issuer_programs")
data class IssuerProgramEntity(
    @PrimaryKey val programId: String,
    val name: String,
    val threshold: Int,
    val reward: String,
    val issuerSeed: ByteArray,
    val programManifestBytes: ByteArray,
    val createdAt: Long,
    val color: Int,
    val icon: String,
)

/**
 * A stamp id this issuer has already redeemed - the spent-set that
 * prevents redeeming the same stamp twice (SPEC/SPECS.md §6.3/§7.1),
 * scoped per program, not per card: a Stamp Token isn't bound to any
 * collector identity (§5.2), so there is no card to scope it to - whoever
 * holds `threshold`-many unspent stamps for this program can redeem them.
 * [stampIdHex] is the hex encoding of a StampToken's raw stamp id bytes.
 */
@Entity(tableName = "redeemed_stamps", primaryKeys = ["programId", "stampIdHex"])
data class RedeemedStampEntity(
    val programId: String,
    val stampIdHex: String,
    val redeemedAt: Long,
)

/**
 * A stamp id this issuer has ever minted - a local ledger, not a wire
 * message. Populated the moment a stamp is minted, and used only by the
 * large-threshold redemption path (SPEC/SPECS.md §6.3/§11, `TODO.md`):
 * once a redemption's stamp count outgrows what fits as full Compact Stamp
 * Proofs in one QR, the collector can instead submit bare 16-byte stamp
 * ids, verified against this ledger instead of re-checking a signature per
 * stamp - ~5x smaller again than a compact proof. This trades the
 * signature's self-contained proof for a dependency on this issuer
 * device's own local state surviving, the same shape as the existing
 * §7.4 multi-till limitation - which is why full-signature redemption
 * stays the default for normal thresholds and this is only a fallback for
 * when the QR would otherwise not fit.
 */
@Entity(tableName = "issuer_minted_stamps", primaryKeys = ["programId", "stampIdHex"])
data class IssuerMintedStampEntity(
    val programId: String,
    val stampIdHex: String,
    val mintedAt: Long,
)

/**
 * A card this device collects stamps on. [cardId] is just a locally
 * generated opaque identifier - the collector holds no cryptographic
 * identity of its own (SPEC/SPECS.md §4/§6.1); everything that actually
 * needs protecting is covered by the issuer's own signatures on each
 * Stamp Token and Redemption Certificate. [color]/[icon] are copied from
 * the Program Manifest at join time (SPEC/SPECS.md §5.1) - the card's
 * visual personality, chosen by the issuer.
 */
@Entity(tableName = "collector_cards")
data class CollectorCardEntity(
    @PrimaryKey val cardId: String,
    val programId: String,
    val issuerPublicKey: ByteArray,
    val programName: String,
    val threshold: Int,
    val reward: String,
    val createdAt: Long,
    val color: Int,
    val icon: String,
)

/**
 * One accepted, verified stamp, keyed by its own unique stamp id rather
 * than a position in a sequence. [cardId] is purely local grouping (which
 * of this device's cards the stamp counts toward) - a Stamp Token itself
 * carries no card id at all (SPEC/SPECS.md §5.2), so this association
 * exists only here, never on the wire. Kept (wire bytes and all) until
 * it's redeemed, since a redemption request must re-present the signed
 * tokens, not just the fact that they were once accepted; deleted outright
 * once redeemed rather than tracked as spent.
 */
@Entity(tableName = "collector_stamps", primaryKeys = ["cardId", "stampIdHex"])
data class CollectorStampEntity(
    val cardId: String,
    val stampIdHex: String,
    val stampTokenBytes: ByteArray,
)
