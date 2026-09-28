package io.fidelitycard.app.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Minimal in-memory fakes of the Room DAOs, used to unit test
 * [IssuerRepository]/[CollectorRepository]'s actual logic without an
 * Android SDK or a real database - these DAOs are plain suspend-fun
 * interfaces, so a fake is just a small in-memory map. `observe*` methods
 * are unused by the behavior under test here, so they return a one-shot
 * snapshot rather than a live-updating flow.
 */
class FakeIssuerProgramDao : IssuerProgramDao {
    private val rows = mutableMapOf<String, IssuerProgramEntity>()

    override suspend fun insert(program: IssuerProgramEntity) {
        rows[program.programId] = program
    }

    override suspend fun insertAll(programs: List<IssuerProgramEntity>) {
        programs.forEach { rows[it.programId] = it }
    }

    override fun observeAll(): Flow<List<IssuerProgramEntity>> = flowOf(rows.values.toList())

    override suspend fun getAll(): List<IssuerProgramEntity> = rows.values.toList()

    override suspend fun findById(programId: String): IssuerProgramEntity? = rows[programId]

    override suspend fun deleteAll() = rows.clear()
}

class FakeRedeemedStampDao : RedeemedStampDao {
    private val rows = mutableListOf<RedeemedStampEntity>()

    override suspend fun insertAll(stamps: List<RedeemedStampEntity>) {
        rows += stamps
    }

    override suspend fun findAlreadyRedeemed(programId: String, stampIdHexes: List<String>): List<String> =
        rows.filter { it.programId == programId && it.stampIdHex in stampIdHexes }
            .map { it.stampIdHex }

    override suspend fun getAll(): List<RedeemedStampEntity> = rows.toList()

    override suspend fun deleteAll() = rows.clear()
}

class FakeIssuerMintedStampDao : IssuerMintedStampDao {
    private val rows = mutableListOf<IssuerMintedStampEntity>()

    override suspend fun insert(stamp: IssuerMintedStampEntity) {
        rows += stamp
    }

    override suspend fun insertAll(stamps: List<IssuerMintedStampEntity>) {
        rows += stamps
    }

    override suspend fun findKnown(programId: String, stampIdHexes: List<String>): List<String> =
        rows.filter { it.programId == programId && it.stampIdHex in stampIdHexes }
            .map { it.stampIdHex }

    override suspend fun getAll(): List<IssuerMintedStampEntity> = rows.toList()

    override suspend fun deleteAll() = rows.clear()
}

class FakeCollectorRedeemedStampDao : CollectorRedeemedStampDao {
    private val rows = mutableListOf<CollectorRedeemedStampEntity>()

    override suspend fun insertAll(stamps: List<CollectorRedeemedStampEntity>) {
        rows += stamps
    }

    override suspend fun findRedeemed(programId: String, stampIdHexes: List<String>): List<String> =
        rows.filter { it.programId == programId && it.stampIdHex in stampIdHexes }
            .map { it.stampIdHex }

    override suspend fun getAll(): List<CollectorRedeemedStampEntity> = rows.toList()

    override suspend fun deleteAll() = rows.clear()
}

class FakeCollectorCardDao : CollectorCardDao {
    private val rows = mutableMapOf<String, CollectorCardEntity>()

    override suspend fun insert(card: CollectorCardEntity) {
        rows[card.cardId] = card
    }

    override suspend fun insertAll(cards: List<CollectorCardEntity>) {
        cards.forEach { rows[it.cardId] = it }
    }

    override fun observeAllWithStampCount(): Flow<List<CardWithStampCount>> = flowOf(emptyList())

    override fun observeWithStampCount(cardId: String): Flow<CardWithStampCount?> = flowOf(null)

    override suspend fun findById(cardId: String): CollectorCardEntity? = rows[cardId]

    override suspend fun findByProgramId(programId: String): CollectorCardEntity? =
        rows.values.firstOrNull { it.programId == programId }

    override suspend fun getAll(): List<CollectorCardEntity> = rows.values.toList()

    override suspend fun deleteById(cardId: String) {
        rows.remove(cardId)
    }

    override suspend fun deleteAll() = rows.clear()
}

class FakeCollectorStampDao : CollectorStampDao {
    private val rows = mutableMapOf<Pair<String, String>, CollectorStampEntity>()

    /** Mirrors the real DAO's `onConflict = IGNORE`: a repeat insert of an existing (cardId, stampIdHex) is a no-op, not an overwrite. */
    override suspend fun insert(stamp: CollectorStampEntity) {
        rows.putIfAbsent(stamp.cardId to stamp.stampIdHex, stamp)
    }

    override suspend fun insertAll(stamps: List<CollectorStampEntity>) {
        stamps.forEach { rows[it.cardId to it.stampIdHex] = it }
    }

    override suspend fun findAllForCard(cardId: String): List<CollectorStampEntity> =
        rows.values.filter { it.cardId == cardId }

    override suspend fun getAll(): List<CollectorStampEntity> = rows.values.toList()

    override suspend fun deleteByIds(cardId: String, stampIdHexes: List<String>) {
        stampIdHexes.forEach { rows.remove(cardId to it) }
    }

    override suspend fun deleteAllForCard(cardId: String) {
        rows.keys.filter { it.first == cardId }.forEach { rows.remove(it) }
    }

    override suspend fun deleteAll() = rows.clear()
}
