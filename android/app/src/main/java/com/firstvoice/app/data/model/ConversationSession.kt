package com.firstvoice.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class ConversationSession(
    val id: String,
    val deviceId: String,
    val startedAt: Long,
    val endedAt: Long? = null,
    val gpsCoordinates: GPSCoordinate? = null,
    val responderLanguage: String,
    val survivorLanguage: String? = null,
    val interactions: List<Interaction> = emptyList(),
    val triageCardId: String? = null,
    val status: SessionStatus = SessionStatus.ACTIVE
)

@Serializable
enum class SessionStatus {
    ACTIVE, CLOSED, TIMED_OUT
}

@Serializable
sealed class Interaction {
    abstract val id: String
    abstract val timestamp: Long

    @Serializable
    data class SpeechTurn(
        override val id: String,
        override val timestamp: Long,
        val speaker: Speaker,
        val originalText: String,
        val originalLanguage: String,
        val translatedText: String,
        val translatedLanguage: String,
        val confidence: ConfidenceLevel
    ) : Interaction()

    @Serializable
    data class VisionAssessment(
        override val id: String,
        override val timestamp: Long,
        val photoId: String,
        val assessmentJson: String // Serialized DamageAssessment or InjuryAssessment
    ) : Interaction()

    @Serializable
    data class QuickPhraseUsage(
        override val id: String,
        override val timestamp: Long,
        val phraseId: String,
        val sourceText: String,
        val translatedText: String,
        val targetLanguage: String
    ) : Interaction()

    @Serializable
    data class Note(
        override val id: String,
        override val timestamp: Long,
        val text: String
    ) : Interaction()
}

@Serializable
enum class Speaker {
    RESPONDER, SURVIVOR
}

@Serializable
enum class ConfidenceLevel {
    HIGH, MEDIUM, LOW
}
