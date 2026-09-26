package io.fidelitycard.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface IssuerProgramDao {
    @Insert
    suspend fun insert(program: IssuerProgramEntity)

    @Query("SELECT * FROM issuer_programs ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<IssuerProgramEntity>>

    @Query("SELECT * FROM issuer_programs WHERE programId = :programId")
    suspend fun findById(programId: String): IssuerProgramEntity?
}

@Dao
interface IssuedCardDao {
    @Insert
    suspend fun insert(card: IssuedCardEntity)

    @Query("SELECT * FROM issued_cards WHERE programId = :programId AND cardId = :cardId")
    suspend fun find(programId: String, cardId: String): IssuedCardEntity?

    @Update
    suspend fun update(card: IssuedCardEntity)
}

@Dao
interface IssuedStampDao {
    @Insert
    suspend fun insert(stamp: IssuedStampEntity)

    @Query("SELECT * FROM issued_stamps WHERE programId = :programId AND cardId = :cardId AND serial = :serial")
    suspend fun find(programId: String, cardId: String, serial: Int): IssuedStampEntity?
}

@Dao
interface CollectorCardDao {
    @Insert
    suspend fun insert(card: CollectorCardEntity)

    @Query("SELECT * FROM collector_cards ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<CollectorCardEntity>>

    @Query("SELECT * FROM collector_cards WHERE cardId = :cardId")
    fun observeById(cardId: String): Flow<CollectorCardEntity?>

    @Query("SELECT * FROM collector_cards WHERE cardId = :cardId")
    suspend fun findById(cardId: String): CollectorCardEntity?

    @Update
    suspend fun update(card: CollectorCardEntity)

    @Query("DELETE FROM collector_cards WHERE cardId = :cardId")
    suspend fun deleteById(cardId: String)
}

@Dao
interface CollectorStampDao {
    @Insert
    suspend fun insert(stamp: CollectorStampEntity)

    @Query("SELECT * FROM collector_stamps WHERE cardId = :cardId ORDER BY serial ASC")
    suspend fun findAllForCard(cardId: String): List<CollectorStampEntity>

    @Query("DELETE FROM collector_stamps WHERE cardId = :cardId AND serial <= :throughSerial")
    suspend fun deleteRedeemed(cardId: String, throughSerial: Int)

    @Query("DELETE FROM collector_stamps WHERE cardId = :cardId")
    suspend fun deleteAllForCard(cardId: String)
}
