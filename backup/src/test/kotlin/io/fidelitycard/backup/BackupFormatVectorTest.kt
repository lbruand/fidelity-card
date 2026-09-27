package io.fidelitycard.backup

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Byte-exact test vector published in SPEC/BACKUP_FORMAT.md. Only the
 * plaintext [BackupSnapshot] encoding can be pinned this way -
 * [BackupEncryption]'s output is deliberately non-deterministic (fresh
 * random salt/iv every call), so there is no fixed ciphertext to publish
 * for that half of the format; see BACKUP_FORMAT.md for why that's fine.
 */
class BackupFormatVectorTest {

    @Test
    fun `snapshot encoding matches the published vector`() {
        val snapshot = BackupSnapshot(
            issuerPrograms = listOf(
                IssuerProgramRow(
                    programId = "OUBSI7FFNROBZITIHPKRLX7HFL",
                    name = "Joe's Coffee",
                    threshold = 10,
                    reward = "Free coffee",
                    issuerSeed = ByteArray(32) { it.toByte() },
                    programManifestBytes = ByteArray(20) { (it + 1).toByte() },
                    createdAt = 1_700_000_000_000L,
                ),
            ),
            issuedCards = listOf(
                IssuedCardRow("OUBSI7FFNROBZITIHPKRLX7HFL", "11111111-1111-1111-1111-111111111111", 1_700_000_001_000L),
            ),
            redeemedStamps = emptyList(),
            collectorCards = emptyList(),
            collectorStamps = emptyList(),
            issuerMintedStamps = emptyList(),
        )

        assertEquals(
            "0200000001001a4f554253493746464e524f425a49544948504b524c583748464c000c4a6f65277320436f666665650000000a000b4672656520636f66666565000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f00140102030405060708090a0b0c0d0e0f10111213140000018bcfe5680000000001001a4f554253493746464e524f425a49544948504b524c583748464c002431313131313131312d313131312d313131312d313131312d3131313131313131313131310000018bcfe56be800000000000000000000000000000000",
            snapshot.encode().joinToString("") { "%02x".format(it) },
        )
    }
}
