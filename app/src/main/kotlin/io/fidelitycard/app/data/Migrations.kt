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

/**
 * Drops `card_id` from a Stamp Token/Redemption Certificate no longer
 * binding one (SPEC/SPECS.md §4/§5.2 - "why does stamping need a round
 * trip?"): `issued_cards` is gone entirely (the issuer has no concept of
 * "cards it has seen" any more), and `redeemed_stamps`/`issuer_minted_stamps`
 * drop their `cardId` column and are re-keyed per program instead of per
 * (program, card). SQLite on this app's minSdk can't drop a column or
 * change a primary key in place, so each table is rebuilt: create the new
 * shape, copy surviving columns across (`INSERT OR IGNORE` in case two old
 * rows for different cards happened to share a `(programId, stampIdHex)` -
 * astronomically unlikely given `stamp_id` is a random 16-byte value, but
 * cheap to guard against outright rather than have the migration itself
 * throw on somebody's real device), drop the old table, rename the new one
 * into place. `CREATE TABLE` statements copied verbatim from the
 * Room-generated `app/schemas/io.fidelitycard.app.data.FidelityCardDatabase/7.json`.
 */
val MIGRATION_6_7: Migration = Migration(6, 7) { db ->
    db.execSQL("DROP TABLE IF EXISTS `issued_cards`")

    db.execSQL(
        "CREATE TABLE `redeemed_stamps_new` (`programId` TEXT NOT NULL, `stampIdHex` TEXT NOT NULL, " +
            "`redeemedAt` INTEGER NOT NULL, PRIMARY KEY(`programId`, `stampIdHex`))",
    )
    db.execSQL(
        "INSERT OR IGNORE INTO `redeemed_stamps_new` (programId, stampIdHex, redeemedAt) " +
            "SELECT programId, stampIdHex, redeemedAt FROM `redeemed_stamps`",
    )
    db.execSQL("DROP TABLE `redeemed_stamps`")
    db.execSQL("ALTER TABLE `redeemed_stamps_new` RENAME TO `redeemed_stamps`")

    db.execSQL(
        "CREATE TABLE `issuer_minted_stamps_new` (`programId` TEXT NOT NULL, `stampIdHex` TEXT NOT NULL, " +
            "`mintedAt` INTEGER NOT NULL, PRIMARY KEY(`programId`, `stampIdHex`))",
    )
    db.execSQL(
        "INSERT OR IGNORE INTO `issuer_minted_stamps_new` (programId, stampIdHex, mintedAt) " +
            "SELECT programId, stampIdHex, mintedAt FROM `issuer_minted_stamps`",
    )
    db.execSQL("DROP TABLE `issuer_minted_stamps`")
    db.execSQL("ALTER TABLE `issuer_minted_stamps_new` RENAME TO `issuer_minted_stamps`")
}

/**
 * Adds [CollectorRedeemedStampEntity] - the collector's own memory of
 * which stamp ids it has already redeemed, so re-scanning an
 * already-redeemed stamp's QR (a screenshot, a stale display) is rejected
 * immediately instead of being silently re-accepted as a fresh stamp
 * (see that entity's doc for the full reasoning). A brand new table, no
 * data to migrate.
 */
val MIGRATION_7_8: Migration = Migration(7, 8) { db ->
    db.execSQL(
        "CREATE TABLE IF NOT EXISTS `collector_redeemed_stamps` (`programId` TEXT NOT NULL, " +
            "`stampIdHex` TEXT NOT NULL, `redeemedAt` INTEGER NOT NULL, PRIMARY KEY(`programId`, `stampIdHex`))",
    )
}
