package io.fidelitycard.app

import android.app.Application
import androidx.room.Room
import io.fidelitycard.app.data.CollectorRepository
import io.fidelitycard.app.data.FidelityCardDatabase
import io.fidelitycard.app.data.IssuerRepository

/**
 * Manual dependency wiring, on purpose: this app has few enough
 * dependencies that a DI framework (Hilt/Koin) would be one more
 * non-obvious moving part for no real benefit, and SPEC/SPECS.md §8-9
 * already commits to keeping the dependency tree small and auditable.
 */
class FidelityApplication : Application() {

    private val database: FidelityCardDatabase by lazy {
        Room.databaseBuilder(this, FidelityCardDatabase::class.java, "fidelity-card.db")
            // Pre-release, no backup/restore yet (SPEC/SPECS.md §11) and no
            // real user data to protect: wiping on a schema bump is fine for
            // now. Replace with a real Migration before any actual release.
            .fallbackToDestructiveMigration(true)
            .build()
    }

    val issuerRepository: IssuerRepository by lazy {
        IssuerRepository(database.issuerProgramDao(), database.issuedCardDao(), database.issuedStampDao())
    }

    val collectorRepository: CollectorRepository by lazy {
        CollectorRepository(database.collectorCardDao(), database.collectorStampDao())
    }
}
