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
 * One customer's enrollment in one of this device's programs, from the
 * issuer's side. Stamps are unordered and unconditionally minted (issuance
 * is a trust matter for the issuer, like a paper card - SPEC/SPECS.md §6.2),
 * so this only needs to record that the card exists, not any stamp count.
 */
@Entity(tableName = "issued_cards", primaryKeys = ["programId", "cardId"])
data class IssuedCardEntity(
    val programId: String,
    val cardId: String,
    val collectorPublicKey: ByteArray,
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

/** A card this device collects stamps on. See [IssuerProgramEntity] for the key-storage caveat. */
@Entity(tableName = "collector_cards")
data class CollectorCardEntity(
    @PrimaryKey val cardId: String,
    val programId: String,
    val issuerPublicKey: ByteArray,
    val programName: String,
    val threshold: Int,
    val reward: String,
    val collectorSeed: ByteArray,
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
