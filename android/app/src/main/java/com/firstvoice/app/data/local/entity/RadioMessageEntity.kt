package com.firstvoice.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.firstvoice.app.data.model.RadioMessage

@Entity(tableName = "radio_messages")
data class RadioMessageEntity(
    @PrimaryKey val id: String,
    val deviceId: String,
    val deviceName: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val timestamp: Long,
    val message: String,
    val isQuickStatus: Boolean = false,
    val read: Boolean = false
) {
    fun toRadioMessage() = RadioMessage(id, deviceId, deviceName, latitude, longitude, timestamp, message, isQuickStatus)

    companion object {
        fun from(msg: RadioMessage, read: Boolean = false) = RadioMessageEntity(
            msg.id, msg.deviceId, msg.deviceName, msg.latitude, msg.longitude, msg.timestamp, msg.message, msg.isQuickStatus, read
        )
    }
}
