package com.firstvoice.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.firstvoice.app.data.model.*

@Entity(tableName = "conversation_sessions")
data class SessionEntity(
    @PrimaryKey val id: String,
    val deviceId: String,
    val startedAt: Long,
    val endedAt: Long? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val responderLanguage: String,
    val survivorLanguage: String? = null,
    val interactions: List<Interaction>,
    val triageCardId: String? = null,
    val status: SessionStatus
) {
    fun toConversationSession(): ConversationSession = ConversationSession(
        id = id,
        deviceId = deviceId,
        startedAt = startedAt,
        endedAt = endedAt,
        gpsCoordinates = if (latitude != null && longitude != null) {
            GPSCoordinate(latitude, longitude, 0f, startedAt)
        } else null,
        responderLanguage = responderLanguage,
        survivorLanguage = survivorLanguage,
        interactions = interactions,
        triageCardId = triageCardId,
        status = status
    )

    companion object {
        fun fromConversationSession(session: ConversationSession): SessionEntity = SessionEntity(
            id = session.id,
            deviceId = session.deviceId,
            startedAt = session.startedAt,
            endedAt = session.endedAt,
            latitude = session.gpsCoordinates?.latitude,
            longitude = session.gpsCoordinates?.longitude,
            responderLanguage = session.responderLanguage,
            survivorLanguage = session.survivorLanguage,
            interactions = session.interactions,
            triageCardId = session.triageCardId,
            status = session.status
        )
    }
}
