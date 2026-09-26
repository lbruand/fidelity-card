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

/** One customer's enrollment in one of this device's programs, from the issuer's side. */
@Entity(tableName = "issued_cards", primaryKeys = ["programId", "cardId"])
data class IssuedCardEntity(
    val programId: String,
    val cardId: String,
    val collectorPublicKey: ByteArray,
    val issuedSerialCount: Int,
    val redeemedThroughSerial: Int,
    val createdAt: Long,
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
    val lastAcceptedSerial: Int,
    val redeemedThroughSerial: Int,
    val createdAt: Long,
)

/**
 * One accepted, verified stamp. Kept around (wire bytes and all) until it's
 * redeemed, since a redemption request must re-present the signed tokens,
 * not just the fact that they were once accepted.
 */
@Entity(tableName = "collector_stamps", primaryKeys = ["cardId", "serial"])
data class CollectorStampEntity(
    val cardId: String,
    val serial: Int,
    val stampTokenBytes: ByteArray,
)
