package com.firstvoice.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.firstvoice.app.data.model.*

@Entity(tableName = "triage_cards")
data class TriageCardEntity(
    @PrimaryKey val id: String,
    val deviceId: String,
    val sessionId: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val gpsAccuracy: Float? = null,
    val gpsTimestamp: Long? = null,
    val timestamp: Long,
    val updatedAt: Long,
    val peopleCount: Int? = null,
    val urgencyLevel: UrgencyLevel,
    val needsCategories: List<NeedsCategory>,
    val detectedLanguage: String,
    val assessmentSummary: String,
    val sourceDataRefs: List<SourceDataRef>,
    val photos: List<PhotoAttachment>,
    val syncStatus: SyncStatus,
    val deleted: Boolean = false,
    val deletedAt: Long? = null
) {
    fun toTriageCard(): TriageCard = TriageCard(
        id = id,
        deviceId = deviceId,
        sessionId = sessionId,
        gpsCoordinates = if (latitude != null && longitude != null) {
            GPSCoordinate(latitude, longitude, gpsAccuracy ?: 0f, gpsTimestamp ?: 0L)
        } else null,
        timestamp = timestamp,
        updatedAt = updatedAt,
        peopleCount = peopleCount,
        urgencyLevel = urgencyLevel,
        needsCategories = needsCategories,
        detectedLanguage = detectedLanguage,
        assessmentSummary = assessmentSummary,
        sourceDataRefs = sourceDataRefs,
        photos = photos,
        syncStatus = syncStatus,
        deleted = deleted,
        deletedAt = deletedAt
    )

    companion object {
        fun fromTriageCard(card: TriageCard): TriageCardEntity = TriageCardEntity(
            id = card.id,
            deviceId = card.deviceId,
            sessionId = card.sessionId,
            latitude = card.gpsCoordinates?.latitude,
            longitude = card.gpsCoordinates?.longitude,
            gpsAccuracy = card.gpsCoordinates?.accuracy,
            gpsTimestamp = card.gpsCoordinates?.timestamp,
            timestamp = card.timestamp,
            updatedAt = card.updatedAt,
            peopleCount = card.peopleCount,
            urgencyLevel = card.urgencyLevel,
            needsCategories = card.needsCategories,
            detectedLanguage = card.detectedLanguage,
            assessmentSummary = card.assessmentSummary,
            sourceDataRefs = card.sourceDataRefs,
            photos = card.photos,
            syncStatus = card.syncStatus,
            deleted = card.deleted,
            deletedAt = card.deletedAt
        )
    }
}
