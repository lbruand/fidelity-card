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

/**
 * Adds [IssuerProgramEntity.color]/[icon] and [CollectorCardEntity.color]/[icon]
 * (card personalization, TODO.md "Product / UX"). Existing rows predate the
 * feature and get a fixed default (the app's own brand teal, `0xFF00897B`
 * i.e. `-16742021` as a signed 32-bit `INTEGER`, and a plain star) rather
 * than `NULL`, since both columns are `NOT NULL` in the entities.
 */
val MIGRATION_5_6: Migration = Migration(5, 6) { db ->
    db.execSQL("ALTER TABLE `issuer_programs` ADD COLUMN `color` INTEGER NOT NULL DEFAULT -16742021")
    db.execSQL("ALTER TABLE `issuer_programs` ADD COLUMN `icon` TEXT NOT NULL DEFAULT '⭐'")
    db.execSQL("ALTER TABLE `collector_cards` ADD COLUMN `color` INTEGER NOT NULL DEFAULT -16742021")
    db.execSQL("ALTER TABLE `collector_cards` ADD COLUMN `icon` TEXT NOT NULL DEFAULT '⭐'")
}
