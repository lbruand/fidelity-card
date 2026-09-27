package io.fidelitycard.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface IssuerProgramDao {
    @Insert
    suspend fun insert(program: IssuerProgramEntity)

    @Insert
    suspend fun insertAll(programs: List<IssuerProgramEntity>)

    @Query("SELECT * FROM issuer_programs ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<IssuerProgramEntity>>

    @Query("SELECT * FROM issuer_programs")
    suspend fun getAll(): List<IssuerProgramEntity>

    @Query("SELECT * FROM issuer_programs WHERE programId = :programId")
    suspend fun findById(programId: String): IssuerProgramEntity?

    @Query("DELETE FROM issuer_programs")
    suspend fun deleteAll()
}

@Dao
interface IssuedCardDao {
    @Insert
    suspend fun insert(card: IssuedCardEntity)

    @Insert
    suspend fun insertAll(cards: List<IssuedCardEntity>)

    @Query("SELECT * FROM issued_cards WHERE programId = :programId AND cardId = :cardId")
    suspend fun find(programId: String, cardId: String): IssuedCardEntity?

    @Query("SELECT * FROM issued_cards")
    suspend fun getAll(): List<IssuedCardEntity>

    @Query("DELETE FROM issued_cards")
    suspend fun deleteAll()
}

@Dao
interface RedeemedStampDao {
    @Insert
    suspend fun insertAll(stamps: List<RedeemedStampEntity>)

    @Query(
        "SELECT stampIdHex FROM redeemed_stamps WHERE programId = :programId AND cardId = :cardId " +
            "AND stampIdHex IN (:stampIdHexes)",
    )
    suspend fun findAlreadyRedeemed(programId: String, cardId: String, stampIdHexes: List<String>): List<String>

    @Query("SELECT * FROM redeemed_stamps")
    suspend fun getAll(): List<RedeemedStampEntity>

    @Query("DELETE FROM redeemed_stamps")
    suspend fun deleteAll()
}

/**
 * A card joined with a live count of its currently-held stamps. Room
 * tracks invalidation across both tables in the query, so this Flow
 * re-emits whenever either the card row or its stamps change - no manual
 * combining of two separate flows needed.
 */
data class CardWithStampCount(
    val cardId: String,
    val programId: String,
    val programName: String,
    val threshold: Int,
    val reward: String,
    val stampCount: Int,
)

private const val CARD_WITH_STAMP_COUNT_QUERY = """
    SELECT collector_cards.cardId AS cardId,
           collector_cards.programId AS programId,
           collector_cards.programName AS programName,
           collector_cards.threshold AS threshold,
           collector_cards.reward AS reward,
           COUNT(collector_stamps.stampIdHex) AS stampCount
    FROM collector_cards
    LEFT JOIN collector_stamps ON collector_stamps.cardId = collector_cards.cardId
"""

@Dao
interface CollectorCardDao {
    @Insert
    suspend fun insert(card: CollectorCardEntity)

    @Insert
    suspend fun insertAll(cards: List<CollectorCardEntity>)

    @Query("$CARD_WITH_STAMP_COUNT_QUERY GROUP BY collector_cards.cardId ORDER BY collector_cards.createdAt DESC")
    fun observeAllWithStampCount(): Flow<List<CardWithStampCount>>

    @Query("$CARD_WITH_STAMP_COUNT_QUERY WHERE collector_cards.cardId = :cardId GROUP BY collector_cards.cardId")
    fun observeWithStampCount(cardId: String): Flow<CardWithStampCount?>

    @Query("SELECT * FROM collector_cards WHERE cardId = :cardId")
    suspend fun findById(cardId: String): CollectorCardEntity?

    @Query("SELECT * FROM collector_cards")
    suspend fun getAll(): List<CollectorCardEntity>

    @Query("DELETE FROM collector_cards WHERE cardId = :cardId")
    suspend fun deleteById(cardId: String)

    @Query("DELETE FROM collector_cards")
    suspend fun deleteAll()
}

@Dao
interface CollectorStampDao {
    @Insert
    suspend fun insert(stamp: CollectorStampEntity)

    @Insert
    suspend fun insertAll(stamps: List<CollectorStampEntity>)

    @Query("SELECT * FROM collector_stamps WHERE cardId = :cardId")
    suspend fun findAllForCard(cardId: String): List<CollectorStampEntity>

    @Query("SELECT * FROM collector_stamps")
    suspend fun getAll(): List<CollectorStampEntity>

    @Query("DELETE FROM collector_stamps WHERE cardId = :cardId AND stampIdHex IN (:stampIdHexes)")
    suspend fun deleteByIds(cardId: String, stampIdHexes: List<String>)

    @Query("DELETE FROM collector_stamps WHERE cardId = :cardId")
    suspend fun deleteAllForCard(cardId: String)

    @Query("DELETE FROM collector_stamps")
    suspend fun deleteAll()
}
