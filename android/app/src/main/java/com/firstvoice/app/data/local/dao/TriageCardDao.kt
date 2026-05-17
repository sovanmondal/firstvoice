package com.firstvoice.app.data.local.dao

import androidx.room.*
import com.firstvoice.app.data.local.entity.TriageCardEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TriageCardDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(card: TriageCardEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(cards: List<TriageCardEntity>)

    @Update
    suspend fun update(card: TriageCardEntity)

    @Query("SELECT * FROM triage_cards WHERE id = :id")
    suspend fun getById(id: String): TriageCardEntity?

    /** UI queries — exclude deleted cards */
    @Query("SELECT * FROM triage_cards WHERE deleted = 0 ORDER BY timestamp DESC")
    fun getAllFlow(): Flow<List<TriageCardEntity>>

    /** Sync queries — include ALL cards (deleted too) so deletes propagate */
    @Query("SELECT * FROM triage_cards ORDER BY timestamp DESC")
    suspend fun getAll(): List<TriageCardEntity>

    @Query("SELECT * FROM triage_cards WHERE deleted = 0 AND urgencyLevel = :urgency ORDER BY timestamp DESC")
    suspend fun getByUrgency(urgency: String): List<TriageCardEntity>

    @Query("SELECT COUNT(*) FROM triage_cards WHERE deleted = 0")
    suspend fun getCount(): Int

    /** Soft delete — marks as deleted with timestamp, updates updatedAt so sync picks it up */
    @Query("UPDATE triage_cards SET deleted = 1, deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long = System.currentTimeMillis())

    /** Restore from recycle bin — un-deletes, updates updatedAt so sync picks it up */
    @Query("UPDATE triage_cards SET deleted = 0, deletedAt = null, updatedAt = :now WHERE id = :id")
    suspend fun restore(id: String, now: Long = System.currentTimeMillis())

    /** Recycle bin — only deleted cards */
    @Query("SELECT * FROM triage_cards WHERE deleted = 1 ORDER BY deletedAt DESC")
    fun getDeletedFlow(): Flow<List<TriageCardEntity>>

    @Query("DELETE FROM triage_cards")
    suspend fun deleteAll()

    @Delete
    suspend fun delete(card: TriageCardEntity)
}
