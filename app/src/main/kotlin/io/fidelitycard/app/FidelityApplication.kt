package io.fidelitycard.app

import android.app.Application
import androidx.room.Room
import io.fidelitycard.app.data.BackupRepository
import io.fidelitycard.app.data.CollectorRepository
import io.fidelitycard.app.data.FidelityCardDatabase
import io.fidelitycard.app.data.IssuerRepository
import io.fidelitycard.app.data.MIGRATION_4_5
import io.fidelitycard.app.data.MIGRATION_5_6
import io.fidelitycard.app.data.ModePreference

/**
 * Manual dependency wiring, on purpose: this app has few enough
 * dependencies that a DI framework (Hilt/Koin) would be one more
 * non-obvious moving part for no real benefit, and SPEC/SPECS.md §8-9
 * already commits to keeping the dependency tree small and auditable.
 */
class FidelityApplication : Application() {

    private val database: FidelityCardDatabase by lazy {
        Room.databaseBuilder(this, FidelityCardDatabase::class.java, "fidelity-card.db")
            // Versions 1-3 predate schema export (no baseline schema JSON
            // exists to write a real Migration against) and were only ever
            // installed pre-release, so destructively wiping from any of
            // them is still fine. From version 4 onward, schemas are
            // exported (see app/schemas/) and any future version bump
            // requires a real Migration added via .addMigrations(...) -
            // Room throws if one is missing instead of silently wiping
            // (TODO.md, SPEC/SPECS.md §8).
            .fallbackToDestructiveMigrationFrom(true, 1, 2, 3)
            .addMigrations(MIGRATION_4_5, MIGRATION_5_6)
            .build()
    }

    val issuerRepository: IssuerRepository by lazy {
        IssuerRepository(
            database.issuerProgramDao(),
            database.issuedCardDao(),
            database.redeemedStampDao(),
            database.issuerMintedStampDao(),
        )
    }

    val collectorRepository: CollectorRepository by lazy {
        CollectorRepository(database.collectorCardDao(), database.collectorStampDao())
    }

    val backupRepository: BackupRepository by lazy {
        BackupRepository(database)
    }

    val modePreference: ModePreference by lazy { ModePreference(this) }
}
