package com.firstvoice.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.firstvoice.app.data.model.PhraseCategory
import com.firstvoice.app.data.model.QuickPhrase

@Entity(tableName = "quick_phrases")
data class QuickPhraseEntity(
    @PrimaryKey val id: String,
    val category: String,
    val sourceText: String,
    val translations: String, // JSON map
    val isFavorite: Boolean = false
)
