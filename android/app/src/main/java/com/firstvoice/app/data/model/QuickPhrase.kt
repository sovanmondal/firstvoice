package com.firstvoice.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class QuickPhrase(
    val id: String,
    val category: PhraseCategory,
    val sourceText: String,
    val translations: Map<String, String>,
    val isFavorite: Boolean = false
)

@Serializable
enum class PhraseCategory {
    medical, safety, logistics, identification;

    fun displayName(): String = when (this) {
        medical -> "Medical"
        safety -> "Safety"
        logistics -> "Logistics"
        identification -> "Identification"
    }
}
