package com.firstvoice.app.data.local.dao

import androidx.room.*
import com.firstvoice.app.data.local.entity.SessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: SessionEntity)

    @Update
    suspend fun update(session: SessionEntity)

    @Query("SELECT * FROM conversation_sessions WHERE id = :id")
    suspend fun getById(id: String): SessionEntity?

    @Query("SELECT * FROM conversation_sessions ORDER BY startedAt DESC")
    fun getAllFlow(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM conversation_sessions WHERE status = :status ORDER BY startedAt DESC")
    suspend fun getByStatus(status: String): List<SessionEntity>

    @Query("SELECT * FROM conversation_sessions WHERE status = 'ACTIVE' LIMIT 1")
    suspend fun getActiveSession(): SessionEntity?

    @Query("DELETE FROM conversation_sessions")
    suspend fun deleteAll()
}
