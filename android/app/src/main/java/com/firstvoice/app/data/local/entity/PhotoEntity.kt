package com.firstvoice.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "photos")
data class PhotoEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val filePath: String,
    val thumbnailPath: String? = null,
    val assessmentText: String? = null,
    val capturedAt: Long
)
