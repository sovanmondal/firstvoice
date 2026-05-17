package com.firstvoice.app.data.local.dao

import androidx.room.*
import com.firstvoice.app.data.local.entity.RadioMessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RadioMessageDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(msg: RadioMessageEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(msgs: List<RadioMessageEntity>)

    @Query("SELECT * FROM radio_messages ORDER BY timestamp ASC")
    fun getAllFlow(): Flow<List<RadioMessageEntity>>

    @Query("SELECT * FROM radio_messages ORDER BY timestamp ASC")
    suspend fun getAll(): List<RadioMessageEntity>

    @Query("SELECT COUNT(*) FROM radio_messages WHERE read = 0")
    fun getUnreadCountFlow(): Flow<Int>

    @Query("UPDATE radio_messages SET read = 1 WHERE read = 0")
    suspend fun markAllRead()
}
