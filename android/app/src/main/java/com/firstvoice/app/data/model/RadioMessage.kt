package com.firstvoice.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class RadioMessage(
    val id: String,
    val deviceId: String,
    val deviceName: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val timestamp: Long,
    val message: String,
    val isQuickStatus: Boolean = false
)
