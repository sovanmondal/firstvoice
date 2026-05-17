package com.firstvoice.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.firstvoice.app.data.model.VoiceClip

@Entity(tableName = "voice_clips")
data class VoiceClipEntity(
    @PrimaryKey val id: String,
    val deviceId: String,
    val deviceName: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val timestamp: Long,
    val durationMs: Int,
    val audioBase64: String,
    val played: Boolean = false
) {
    fun toVoiceClip() = VoiceClip(id, deviceId, deviceName, latitude, longitude, timestamp, durationMs, audioBase64)

    companion object {
        fun from(clip: VoiceClip, played: Boolean = false) =
            VoiceClipEntity(clip.id, clip.deviceId, clip.deviceName, clip.latitude, clip.longitude, clip.timestamp, clip.durationMs, clip.audioBase64, played)
    }
}
