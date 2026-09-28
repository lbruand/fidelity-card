package io.fidelitycard.backup

import io.fidelitycard.crypto.wire.MalformedMessageException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class BackupSnapshotTest {

    private val sample = BackupSnapshot(
        issuerPrograms = listOf(
            IssuerProgramRow(
                programId = "PROGRAM1",
                name = "Joe's Coffee",
                threshold = 10,
                reward = "Free coffee",
                issuerSeed = ByteArray(32) { it.toByte() },
                programManifestBytes = ByteArray(157) { it.toByte() },
                createdAt = 1_700_000_000_000L,
                color = 0xFF00897B.toInt(),
                icon = "☕",
            ),
        ),
        issuedCards = listOf(
            IssuedCardRow(programId = "PROGRAM1", cardId = "card-1", createdAt = 1_700_000_001_000L),
        ),
        redeemedStamps = listOf(
            RedeemedStampRow(
                programId = "PROGRAM1",
                cardId = "card-1",
                stampIdHex = "00112233445566778899aabbccddeeff",
                redeemedAt = 1_700_000_002_000L,
            ),
        ),
        collectorCards = listOf(
            CollectorCardRow(
                cardId = "card-2",
                programId = "PROGRAM2",
                issuerPublicKey = ByteArray(32) { (it + 1).toByte() },
                programName = "Jane's Tea",
                threshold = 5,
                reward = "Free tea",
                createdAt = 1_700_000_003_000L,
                color = 0xFFD84315.toInt(),
                icon = "🍵",
            ),
        ),
        collectorStamps = listOf(
            CollectorStampRow(
                cardId = "card-2",
                stampIdHex = "ffeeddccbbaa99887766554433221100",
                stampTokenBytes = ByteArray(156) { it.toByte() },
            ),
        ),
        issuerMintedStamps = listOf(
            IssuerMintedStampRow(
                programId = "PROGRAM1",
                cardId = "card-1",
                stampIdHex = "00112233445566778899aabbccddeeff",
                mintedAt = 1_700_000_004_000L,
            ),
        ),
    )

    @Test
    fun `encoding and decoding round trips every row and field`() {
        val decoded = BackupSnapshot.decode(sample.encode())

        assertEquals(sample, decoded)
    }

    @Test
    fun `round trips when every table is empty`() {
        val empty = BackupSnapshot(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList())

        assertEquals(empty, BackupSnapshot.decode(empty.encode()))
    }

    @Test
    fun `rejects bytes from an unsupported format version`() {
        val bytes = sample.encode()
        val tampered = bytes.copyOf()
        tampered[0] = (tampered[0].toInt() + 1).toByte()

        assertThrows(MalformedMessageException::class.java) {
            BackupSnapshot.decode(tampered)
        }
    }

    @Test
    fun `rejects truncated bytes rather than crashing unrecognizably`() {
        val bytes = sample.encode()

        assertThrows(MalformedMessageException::class.java) {
            BackupSnapshot.decode(bytes.copyOf(bytes.size / 2))
        }
    }
}
