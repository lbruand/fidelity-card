package io.fidelitycard.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A loyalty program this device issues. [issuerSeed] is the raw 32-byte
 * Ed25519 seed - SPEC/SPECS.md §7 calls for Android Keystore-backed storage
 * instead; Ed25519 support in AndroidKeyStore only arrived in API 33,
 * below this app's minSdk, so key wrapping is a known gap for a later pass
 * (not silently ignored, tracked here and in SPEC/SPECS.md §11).
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
)

/**
 * A card id this issuer has seen, from its side. There is no enrollment
 * handshake (SPEC/SPECS.md §6.1): this row is created lazily, the first
 * time a card id shows up in a stamp request, purely as a "have I seen
 * this before" sanity record - it carries no key material, since nothing
 * in the protocol ever verifies who a collector is.
 */
@Entity(tableName = "issued_cards", primaryKeys = ["programId", "cardId"])
data class IssuedCardEntity(
    val programId: String,
    val cardId: String,
    val createdAt: Long,
)

/**
 * A stamp id this issuer has already redeemed for a card - the spent-set
 * that prevents redeeming the same stamp twice (SPEC/SPECS.md §6.3/§7.1).
 * [stampIdHex] is the hex encoding of a StampToken's raw stamp id bytes.
 */
@Entity(tableName = "redeemed_stamps", primaryKeys = ["programId", "cardId", "stampIdHex"])
data class RedeemedStampEntity(
    val programId: String,
    val cardId: String,
    val stampIdHex: String,
    val redeemedAt: Long,
)

/**
 * A card this device collects stamps on. [cardId] is just a locally
 * generated opaque identifier - the collector holds no cryptographic
 * identity of its own (SPEC/SPECS.md §4/§6.1); everything that actually
 * needs protecting is covered by the issuer's own signatures on each
 * Stamp Token and Redemption Certificate.
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
)

/**
 * One accepted, verified stamp, keyed by its own unique stamp id rather
 * than a position in a sequence. Kept (wire bytes and all) until it's
 * redeemed, since a redemption request must re-present the signed tokens,
 * not just the fact that they were once accepted; deleted outright once
 * redeemed rather than tracked as spent.
 */
@Entity(tableName = "collector_stamps", primaryKeys = ["cardId", "stampIdHex"])
data class CollectorStampEntity(
    val cardId: String,
    val stampIdHex: String,
    val stampTokenBytes: ByteArray,
)
