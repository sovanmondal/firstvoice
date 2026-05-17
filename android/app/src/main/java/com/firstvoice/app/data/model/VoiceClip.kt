package com.firstvoice.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class VoiceClip(
    val id: String,
    val deviceId: String,
    val deviceName: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val timestamp: Long,
    val durationMs: Int,
    val audioBase64: String // Compressed audio as base64
)
