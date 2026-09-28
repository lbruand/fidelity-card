package io.fidelitycard.backup

import io.fidelitycard.crypto.wire.WireReader
import io.fidelitycard.crypto.wire.WireWriter

private const val FORMAT_VERSION = 4
private const val KEY_LENGTH_BYTES = 32

/**
 * A full local snapshot of everything the app has stored - every program
 * an issuer runs (seed and all - see [IssuerProgramRow]), every card
 * either side is tracking, every stamp, and the issuer's minted-stamp
 * ledger ([IssuerMintedStampRow], SPEC/SPECS.md §6.3/§11 - needed for
 * large-threshold redemptions of stamps minted before this backup). This
 * is the plaintext that [BackupEncryption] wraps; nothing here is ever
 * written anywhere without that wrapping around it first (SPEC/SPECS.md
 * §11 "Backup & restore").
 *
 * Mirrors the Room entities in `:app` field-for-field by design - this is
 * a full replace-on-restore backup, not a merge, so there's no need for
 * the format to diverge from storage; see `io.fidelitycard.app.data`.
 *
 * `FORMAT_VERSION` bumped 1 -> 2 to add [issuerMintedStamps], then 2 -> 3
 * to add `color`/`icon` to [IssuerProgramRow]/[CollectorCardRow] (card
 * personalization, TODO.md "Product / UX"), then 3 -> 4 to drop `card_id`
 * from [RedeemedStampRow]/[IssuerMintedStampRow] and remove the
 * `issued_cards` table entirely (SPEC/SPECS.md §4/§5.2 - a Stamp Token
 * isn't bound to a collector identity any more, so the issuer has no
 * concept of "cards it has seen"). An older-version backup file is
 * rejected outright rather than read with defaulted/dropped fields -
 * simpler, and this early pre-release there's no real backup file anyone
 * needs read back.
 */
data class BackupSnapshot(
    val issuerPrograms: List<IssuerProgramRow>,
    val redeemedStamps: List<RedeemedStampRow>,
    val collectorCards: List<CollectorCardRow>,
    val collectorStamps: List<CollectorStampRow>,
    val issuerMintedStamps: List<IssuerMintedStampRow>,
) {
    fun encode(): ByteArray {
        val writer = WireWriter().writeByte(FORMAT_VERSION)
        writer.writeTable(issuerPrograms) { it.writeTo(writer) }
        writer.writeTable(redeemedStamps) { it.writeTo(writer) }
        writer.writeTable(collectorCards) { it.writeTo(writer) }
        writer.writeTable(collectorStamps) { it.writeTo(writer) }
        writer.writeTable(issuerMintedStamps) { it.writeTo(writer) }
        return writer.toByteArray()
    }

    companion object {
        fun decode(bytes: ByteArray): BackupSnapshot {
            val reader = WireReader(bytes)
            val version = reader.readByte()
            if (version != FORMAT_VERSION) {
                throw io.fidelitycard.crypto.wire.MalformedMessageException(
                    "Unsupported backup format version $version (expected $FORMAT_VERSION)",
                )
            }
            val snapshot = BackupSnapshot(
                issuerPrograms = reader.readTable { IssuerProgramRow.readFrom(reader) },
                redeemedStamps = reader.readTable { RedeemedStampRow.readFrom(reader) },
                collectorCards = reader.readTable { CollectorCardRow.readFrom(reader) },
                collectorStamps = reader.readTable { CollectorStampRow.readFrom(reader) },
                issuerMintedStamps = reader.readTable { IssuerMintedStampRow.readFrom(reader) },
            )
            reader.requireFullyConsumed()
            return snapshot
        }

        private fun <T> WireWriter.writeTable(rows: List<T>, writeRow: (T) -> Unit) {
            writeInt32(rows.size)
            rows.forEach { writeRow(it) }
        }

        private fun <T> WireReader.readTable(readRow: () -> T): List<T> =
            (0 until readInt32()).map { readRow() }
    }
}

/** One loyalty program this device issues. [issuerSeed] is its raw private key. */
class IssuerProgramRow(
    val programId: String,
    val name: String,
    val threshold: Int,
    val reward: String,
    val issuerSeed: ByteArray,
    val programManifestBytes: ByteArray,
    val createdAt: Long,
    val color: Int,
    val icon: String,
) {
    internal fun writeTo(writer: WireWriter) {
        writer.writeString(programId)
        writer.writeString(name)
        writer.writeInt32(threshold)
        writer.writeString(reward)
        writer.writeFixedBytes(issuerSeed, KEY_LENGTH_BYTES)
        writer.writeVarBytes(programManifestBytes)
        writer.writeInt64(createdAt)
        writer.writeInt32(color)
        writer.writeString(icon)
    }

    override fun equals(other: Any?): Boolean =
        other is IssuerProgramRow &&
            programId == other.programId &&
            name == other.name &&
            threshold == other.threshold &&
            reward == other.reward &&
            issuerSeed.contentEquals(other.issuerSeed) &&
            programManifestBytes.contentEquals(other.programManifestBytes) &&
            createdAt == other.createdAt &&
            color == other.color &&
            icon == other.icon

    override fun hashCode(): Int =
        listOf(
            programId, name, threshold, reward, issuerSeed.contentHashCode(),
            programManifestBytes.contentHashCode(), createdAt, color, icon,
        ).hashCode()

    companion object {
        internal fun readFrom(reader: WireReader): IssuerProgramRow = IssuerProgramRow(
            programId = reader.readString(),
            name = reader.readString(),
            threshold = reader.readInt32(),
            reward = reader.readString(),
            issuerSeed = reader.readFixedBytes(KEY_LENGTH_BYTES),
            programManifestBytes = reader.readVarBytes(),
            createdAt = reader.readInt64(),
            color = reader.readInt32(),
            icon = reader.readString(),
        )
    }
}

/**
 * One stamp id this issuer has already redeemed - the double-redemption
 * spent-set, scoped per program, not per card (SPEC/SPECS.md §5.2/§7.1: a
 * Stamp Token isn't bound to any collector identity).
 */
data class RedeemedStampRow(
    val programId: String,
    val stampIdHex: String,
    val redeemedAt: Long,
) {
    internal fun writeTo(writer: WireWriter) {
        writer.writeString(programId)
        writer.writeString(stampIdHex)
        writer.writeInt64(redeemedAt)
    }

    companion object {
        internal fun readFrom(reader: WireReader): RedeemedStampRow =
            RedeemedStampRow(reader.readString(), reader.readString(), reader.readInt64())
    }
}

/**
 * A stamp id this issuer has ever minted - the ledger the large-threshold
 * redemption fallback verifies against instead of a signature
 * (SPEC/SPECS.md §6.3/§11).
 */
data class IssuerMintedStampRow(
    val programId: String,
    val stampIdHex: String,
    val mintedAt: Long,
) {
    internal fun writeTo(writer: WireWriter) {
        writer.writeString(programId)
        writer.writeString(stampIdHex)
        writer.writeInt64(mintedAt)
    }

    companion object {
        internal fun readFrom(reader: WireReader): IssuerMintedStampRow =
            IssuerMintedStampRow(reader.readString(), reader.readString(), reader.readInt64())
    }
}

/** A card this device collects stamps on. */
class CollectorCardRow(
    val cardId: String,
    val programId: String,
    val issuerPublicKey: ByteArray,
    val programName: String,
    val threshold: Int,
    val reward: String,
    val createdAt: Long,
    val color: Int,
    val icon: String,
) {
    internal fun writeTo(writer: WireWriter) {
        writer.writeString(cardId)
        writer.writeString(programId)
        writer.writeFixedBytes(issuerPublicKey, KEY_LENGTH_BYTES)
        writer.writeString(programName)
        writer.writeInt32(threshold)
        writer.writeString(reward)
        writer.writeInt64(createdAt)
        writer.writeInt32(color)
        writer.writeString(icon)
    }

    override fun equals(other: Any?): Boolean =
        other is CollectorCardRow &&
            cardId == other.cardId &&
            programId == other.programId &&
            issuerPublicKey.contentEquals(other.issuerPublicKey) &&
            programName == other.programName &&
            threshold == other.threshold &&
            reward == other.reward &&
            createdAt == other.createdAt &&
            color == other.color &&
            icon == other.icon

    override fun hashCode(): Int =
        listOf(cardId, programId, issuerPublicKey.contentHashCode(), programName, threshold, reward, createdAt, color, icon).hashCode()

    companion object {
        internal fun readFrom(reader: WireReader): CollectorCardRow = CollectorCardRow(
            cardId = reader.readString(),
            programId = reader.readString(),
            issuerPublicKey = reader.readFixedBytes(KEY_LENGTH_BYTES),
            programName = reader.readString(),
            threshold = reader.readInt32(),
            reward = reader.readString(),
            createdAt = reader.readInt64(),
            color = reader.readInt32(),
            icon = reader.readString(),
        )
    }
}

/** One accepted stamp, kept until redeemed. */
class CollectorStampRow(
    val cardId: String,
    val stampIdHex: String,
    val stampTokenBytes: ByteArray,
) {
    internal fun writeTo(writer: WireWriter) {
        writer.writeString(cardId)
        writer.writeString(stampIdHex)
        writer.writeVarBytes(stampTokenBytes)
    }

    override fun equals(other: Any?): Boolean =
        other is CollectorStampRow &&
            cardId == other.cardId &&
            stampIdHex == other.stampIdHex &&
            stampTokenBytes.contentEquals(other.stampTokenBytes)

    override fun hashCode(): Int =
        listOf(cardId, stampIdHex, stampTokenBytes.contentHashCode()).hashCode()

    companion object {
        internal fun readFrom(reader: WireReader): CollectorStampRow =
            CollectorStampRow(reader.readString(), reader.readString(), reader.readVarBytes())
    }
}
