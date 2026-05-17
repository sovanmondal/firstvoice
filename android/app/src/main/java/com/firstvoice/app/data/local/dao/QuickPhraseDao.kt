package com.firstvoice.app.data.local.dao

import androidx.room.*
import com.firstvoice.app.data.local.entity.QuickPhraseEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface QuickPhraseDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(phrases: List<QuickPhraseEntity>)

    @Query("SELECT * FROM quick_phrases WHERE category = :category")
    suspend fun getByCategory(category: String): List<QuickPhraseEntity>

    @Query("SELECT * FROM quick_phrases WHERE isFavorite = 1")
    suspend fun getFavorites(): List<QuickPhraseEntity>

    @Query("SELECT * FROM quick_phrases")
    fun getAllFlow(): Flow<List<QuickPhraseEntity>>

    @Query("UPDATE quick_phrases SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun setFavorite(id: String, isFavorite: Boolean)

    @Query("DELETE FROM quick_phrases")
    suspend fun deleteAll()
}
