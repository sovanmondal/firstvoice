package com.firstvoice.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class TriageCard(
    val id: String,
    val deviceId: String,
    val sessionId: String,
    val gpsCoordinates: GPSCoordinate? = null,
    val timestamp: Long,
    val updatedAt: Long,
    val peopleCount: Int? = null,
    val urgencyLevel: UrgencyLevel,
    val needsCategories: List<NeedsCategory>,
    val detectedLanguage: String,
    val assessmentSummary: String,
    val sourceDataRefs: List<SourceDataRef> = emptyList(),
    val photos: List<PhotoAttachment> = emptyList(),
    val syncStatus: SyncStatus = SyncStatus(),
    val deleted: Boolean = false,
    val deletedAt: Long? = null
)

@Serializable
enum class UrgencyLevel {
    CRITICAL, HIGH, MEDIUM, LOW
}

@Serializable
enum class NeedsCategory {
    Medical, Extraction, Shelter, WaterFood, FamilyReunification;

    fun displayName(): String = when (this) {
        Medical -> "Medical"
        Extraction -> "Extraction"
        Shelter -> "Shelter"
        WaterFood -> "Water/Food"
        FamilyReunification -> "Family Reunification"
    }
}

@Serializable
data class GPSCoordinate(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val timestamp: Long
)

@Serializable
data class PhotoAttachment(
    val id: String,
    val filePath: String,
    val thumbnailPath: String,
    val assessmentText: String,
    val capturedAt: Long
)

@Serializable
data class SourceDataRef(
    val type: SourceDataType,
    val refId: String,
    val timestamp: Long
)

@Serializable
enum class SourceDataType {
    speech_turn, vision_assessment, quick_phrase, note
}

@Serializable
data class SyncStatus(
    val meshSynced: Boolean = false,
    val meshSyncedAt: Long? = null,
    val cloudSynced: Boolean = false,
    val cloudSyncedAt: Long? = null
)
