package io.fidelitycard.app.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        IssuerProgramEntity::class,
        IssuedCardEntity::class,
        IssuedStampEntity::class,
        CollectorCardEntity::class,
        CollectorStampEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class FidelityCardDatabase : RoomDatabase() {
    abstract fun issuerProgramDao(): IssuerProgramDao
    abstract fun issuedCardDao(): IssuedCardDao
    abstract fun issuedStampDao(): IssuedStampDao
    abstract fun collectorCardDao(): CollectorCardDao
    abstract fun collectorStampDao(): CollectorStampDao
}
