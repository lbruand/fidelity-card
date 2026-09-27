package io.fidelitycard.app.data

import androidx.room.migration.Migration

/**
 * `CREATE TABLE` copied verbatim from the Room-generated
 * `app/schemas/io.fidelitycard.app.data.FidelityCardDatabase/5.json`, so it
 * matches [IssuerMintedStampEntity]'s schema exactly rather than being
 * hand-typed against it. The first real [Migration] this app ships - see
 * `FidelityApplication`'s `fallbackToDestructiveMigrationFrom` for why
 * version 5 is where that guardrail starts requiring one.
 */
val MIGRATION_4_5: Migration = Migration(4, 5) { db ->
    db.execSQL(
        "CREATE TABLE IF NOT EXISTS `issuer_minted_stamps` (`programId` TEXT NOT NULL, " +
            "`cardId` TEXT NOT NULL, `stampIdHex` TEXT NOT NULL, `mintedAt` INTEGER NOT NULL, " +
            "PRIMARY KEY(`programId`, `cardId`, `stampIdHex`))",
    )
}
