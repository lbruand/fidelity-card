package io.fidelitycard.app.data

import android.util.Log
import androidx.room.withTransaction
import io.fidelitycard.backup.BackupDecryptionException
import io.fidelitycard.backup.BackupEncryption
import io.fidelitycard.backup.BackupSnapshot
import io.fidelitycard.backup.CollectorCardRow
import io.fidelitycard.backup.CollectorStampRow
import io.fidelitycard.backup.IssuedCardRow
import io.fidelitycard.backup.IssuerMintedStampRow
import io.fidelitycard.backup.IssuerProgramRow
import io.fidelitycard.backup.RedeemedStampRow
import io.fidelitycard.crypto.wire.MalformedMessageException

private const val TAG = "BackupRepository"

sealed interface BackupImportOutcome {
    data object Success : BackupImportOutcome
    data class Failed(val reason: String) : BackupImportOutcome
}

/**
 * Export/import of the entire local database as one encrypted file
 * (SPEC/SPECS.md §11 "Backup & restore"). A restore is a full replace, not
 * a merge - simplest safe behavior, and the only one that makes sense
 * given nothing here has a notion of conflict resolution between two
 * independently-evolved states.
 */
class BackupRepository(private val database: FidelityCardDatabase) {

    suspend fun exportEncrypted(passphrase: CharArray): ByteArray {
        val snapshot = BackupSnapshot(
            issuerPrograms = database.issuerProgramDao().getAll().map { it.toRow() },
            issuedCards = database.issuedCardDao().getAll().map { it.toRow() },
            redeemedStamps = database.redeemedStampDao().getAll().map { it.toRow() },
            collectorCards = database.collectorCardDao().getAll().map { it.toRow() },
            collectorStamps = database.collectorStampDao().getAll().map { it.toRow() },
            issuerMintedStamps = database.issuerMintedStampDao().getAll().map { it.toRow() },
        )
        return BackupEncryption.encrypt(snapshot.encode(), passphrase)
    }

    suspend fun importEncrypted(envelope: ByteArray, passphrase: CharArray): BackupImportOutcome {
        val snapshot = try {
            BackupSnapshot.decode(BackupEncryption.decrypt(envelope, passphrase))
        } catch (e: BackupDecryptionException) {
            Log.w(TAG, "Backup import rejected: decryption failed", e)
            return BackupImportOutcome.Failed("Wrong passphrase, or this backup file is corrupted")
        } catch (e: MalformedMessageException) {
            Log.w(TAG, "Backup import rejected: decrypted, but not a valid backup snapshot", e)
            return BackupImportOutcome.Failed("This doesn't look like a valid backup file")
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Backup import rejected: not a valid backup file (too short)", e)
            return BackupImportOutcome.Failed("This doesn't look like a valid backup file")
        }

        database.withTransaction {
            database.issuerProgramDao().deleteAll()
            database.issuedCardDao().deleteAll()
            database.redeemedStampDao().deleteAll()
            database.collectorCardDao().deleteAll()
            database.collectorStampDao().deleteAll()
            database.issuerMintedStampDao().deleteAll()

            database.issuerProgramDao().insertAll(snapshot.issuerPrograms.map { it.toEntity() })
            database.issuedCardDao().insertAll(snapshot.issuedCards.map { it.toEntity() })
            database.redeemedStampDao().insertAll(snapshot.redeemedStamps.map { it.toEntity() })
            database.collectorCardDao().insertAll(snapshot.collectorCards.map { it.toEntity() })
            database.collectorStampDao().insertAll(snapshot.collectorStamps.map { it.toEntity() })
            database.issuerMintedStampDao().insertAll(snapshot.issuerMintedStamps.map { it.toEntity() })
        }
        return BackupImportOutcome.Success
    }

    private fun IssuerProgramEntity.toRow() =
        IssuerProgramRow(programId, name, threshold, reward, issuerSeed, programManifestBytes, createdAt, color, icon)

    private fun IssuerProgramRow.toEntity() =
        IssuerProgramEntity(programId, name, threshold, reward, issuerSeed, programManifestBytes, createdAt, color, icon)

    private fun IssuedCardEntity.toRow() = IssuedCardRow(programId, cardId, createdAt)

    private fun IssuedCardRow.toEntity() = IssuedCardEntity(programId, cardId, createdAt)

    private fun RedeemedStampEntity.toRow() = RedeemedStampRow(programId, cardId, stampIdHex, redeemedAt)

    private fun RedeemedStampRow.toEntity() = RedeemedStampEntity(programId, cardId, stampIdHex, redeemedAt)

    private fun CollectorCardEntity.toRow() =
        CollectorCardRow(cardId, programId, issuerPublicKey, programName, threshold, reward, createdAt, color, icon)

    private fun CollectorCardRow.toEntity() =
        CollectorCardEntity(cardId, programId, issuerPublicKey, programName, threshold, reward, createdAt, color, icon)

    private fun CollectorStampEntity.toRow() = CollectorStampRow(cardId, stampIdHex, stampTokenBytes)

    private fun CollectorStampRow.toEntity() = CollectorStampEntity(cardId, stampIdHex, stampTokenBytes)

    private fun IssuerMintedStampEntity.toRow() = IssuerMintedStampRow(programId, cardId, stampIdHex, mintedAt)

    private fun IssuerMintedStampRow.toEntity() = IssuerMintedStampEntity(programId, cardId, stampIdHex, mintedAt)
}
