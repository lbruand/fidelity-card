package io.fidelitycard.app.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        IssuerProgramEntity::class,
        RedeemedStampEntity::class,
        CollectorCardEntity::class,
        CollectorStampEntity::class,
        IssuerMintedStampEntity::class,
    ],
    version = 7,
    exportSchema = true,
)
abstract class FidelityCardDatabase : RoomDatabase() {
    abstract fun issuerProgramDao(): IssuerProgramDao
    abstract fun redeemedStampDao(): RedeemedStampDao
    abstract fun collectorCardDao(): CollectorCardDao
    abstract fun collectorStampDao(): CollectorStampDao
    abstract fun issuerMintedStampDao(): IssuerMintedStampDao
}
