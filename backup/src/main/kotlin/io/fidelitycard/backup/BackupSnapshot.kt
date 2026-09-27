package io.fidelitycard.backup

import io.fidelitycard.crypto.wire.WireReader
import io.fidelitycard.crypto.wire.WireWriter

private const val FORMAT_VERSION = 1
private const val KEY_LENGTH_BYTES = 32

/**
 * A full local snapshot of everything the app has stored - every program
 * an issuer runs (seed and all - see [IssuerProgramRow]), every card
 * either side is tracking, and every stamp. This is the plaintext that
 * [BackupEncryption] wraps; nothing here is ever written anywhere without
 * that wrapping around it first (SPEC/SPECS.md §11 "Backup & restore").
 *
 * Mirrors the Room entities in `:app` field-for-field by design - this is
 * a full replace-on-restore backup, not a merge, so there's no need for
 * the format to diverge from storage; see `io.fidelitycard.app.data`.
 */
data class BackupSnapshot(
    val issuerPrograms: List<IssuerProgramRow>,
    val issuedCards: List<IssuedCardRow>,
    val redeemedStamps: List<RedeemedStampRow>,
    val collectorCards: List<CollectorCardRow>,
    val collectorStamps: List<CollectorStampRow>,
) {
    fun encode(): ByteArray {
        val writer = WireWriter().writeByte(FORMAT_VERSION)
        writer.writeTable(issuerPrograms) { it.writeTo(writer) }
        writer.writeTable(issuedCards) { it.writeTo(writer) }
        writer.writeTable(redeemedStamps) { it.writeTo(writer) }
        writer.writeTable(collectorCards) { it.writeTo(writer) }
        writer.writeTable(collectorStamps) { it.writeTo(writer) }
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
                issuedCards = reader.readTable { IssuedCardRow.readFrom(reader) },
                redeemedStamps = reader.readTable { RedeemedStampRow.readFrom(reader) },
                collectorCards = reader.readTable { CollectorCardRow.readFrom(reader) },
                collectorStamps = reader.readTable { CollectorStampRow.readFrom(reader) },
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
) {
    internal fun writeTo(writer: WireWriter) {
        writer.writeString(programId)
        writer.writeString(name)
        writer.writeInt32(threshold)
        writer.writeString(reward)
        writer.writeFixedBytes(issuerSeed, KEY_LENGTH_BYTES)
        writer.writeVarBytes(programManifestBytes)
        writer.writeInt64(createdAt)
    }

    override fun equals(other: Any?): Boolean =
        other is IssuerProgramRow &&
            programId == other.programId &&
            name == other.name &&
            threshold == other.threshold &&
            reward == other.reward &&
            issuerSeed.contentEquals(other.issuerSeed) &&
            programManifestBytes.contentEquals(other.programManifestBytes) &&
            createdAt == other.createdAt

    override fun hashCode(): Int =
        listOf(programId, name, threshold, reward, issuerSeed.contentHashCode(), programManifestBytes.contentHashCode(), createdAt).hashCode()

    companion object {
        internal fun readFrom(reader: WireReader): IssuerProgramRow = IssuerProgramRow(
            programId = reader.readString(),
            name = reader.readString(),
            threshold = reader.readInt32(),
            reward = reader.readString(),
            issuerSeed = reader.readFixedBytes(KEY_LENGTH_BYTES),
            programManifestBytes = reader.readVarBytes(),
            createdAt = reader.readInt64(),
        )
    }
}

/** A card id this issuer has seen (SPEC/SPECS.md §6.1 - lazily created, no key material). */
data class IssuedCardRow(
    val programId: String,
    val cardId: String,
    val createdAt: Long,
) {
    internal fun writeTo(writer: WireWriter) {
        writer.writeString(programId)
        writer.writeString(cardId)
        writer.writeInt64(createdAt)
    }

    companion object {
        internal fun readFrom(reader: WireReader): IssuedCardRow =
            IssuedCardRow(reader.readString(), reader.readString(), reader.readInt64())
    }
}

/** One stamp id this issuer has already redeemed for a card - the double-redemption spent-set. */
data class RedeemedStampRow(
    val programId: String,
    val cardId: String,
    val stampIdHex: String,
    val redeemedAt: Long,
) {
    internal fun writeTo(writer: WireWriter) {
        writer.writeString(programId)
        writer.writeString(cardId)
        writer.writeString(stampIdHex)
        writer.writeInt64(redeemedAt)
    }

    companion object {
        internal fun readFrom(reader: WireReader): RedeemedStampRow =
            RedeemedStampRow(reader.readString(), reader.readString(), reader.readString(), reader.readInt64())
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
) {
    internal fun writeTo(writer: WireWriter) {
        writer.writeString(cardId)
        writer.writeString(programId)
        writer.writeFixedBytes(issuerPublicKey, KEY_LENGTH_BYTES)
        writer.writeString(programName)
        writer.writeInt32(threshold)
        writer.writeString(reward)
        writer.writeInt64(createdAt)
    }

    override fun equals(other: Any?): Boolean =
        other is CollectorCardRow &&
            cardId == other.cardId &&
            programId == other.programId &&
            issuerPublicKey.contentEquals(other.issuerPublicKey) &&
            programName == other.programName &&
            threshold == other.threshold &&
            reward == other.reward &&
            createdAt == other.createdAt

    override fun hashCode(): Int =
        listOf(cardId, programId, issuerPublicKey.contentHashCode(), programName, threshold, reward, createdAt).hashCode()

    companion object {
        internal fun readFrom(reader: WireReader): CollectorCardRow = CollectorCardRow(
            cardId = reader.readString(),
            programId = reader.readString(),
            issuerPublicKey = reader.readFixedBytes(KEY_LENGTH_BYTES),
            programName = reader.readString(),
            threshold = reader.readInt32(),
            reward = reader.readString(),
            createdAt = reader.readInt64(),
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
