package com.firstvoice.app.data.local.dao

import androidx.room.*
import com.firstvoice.app.data.local.entity.VoiceClipEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VoiceClipDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(clip: VoiceClipEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(clips: List<VoiceClipEntity>)

    @Query("SELECT * FROM voice_clips ORDER BY timestamp ASC")
    fun getAllFlow(): Flow<List<VoiceClipEntity>>

    @Query("SELECT * FROM voice_clips ORDER BY timestamp ASC")
    suspend fun getAll(): List<VoiceClipEntity>

    @Query("UPDATE voice_clips SET played = 1 WHERE id = :id")
    suspend fun markPlayed(id: String)
}
