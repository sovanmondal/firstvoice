package com.firstvoice.app.data.local.converter

import androidx.room.TypeConverter
import com.firstvoice.app.data.model.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class Converters {

    private val json = Json { ignoreUnknownKeys = true }

    // UrgencyLevel
    @TypeConverter
    fun fromUrgencyLevel(value: UrgencyLevel): String = value.name

    @TypeConverter
    fun toUrgencyLevel(value: String): UrgencyLevel = UrgencyLevel.valueOf(value)

    // NeedsCategory list
    @TypeConverter
    fun fromNeedsCategoryList(value: List<NeedsCategory>): String =
        json.encodeToString(value)

    @TypeConverter
    fun toNeedsCategoryList(value: String): List<NeedsCategory> =
        json.decodeFromString(value)

    // SourceDataRef list
    @TypeConverter
    fun fromSourceDataRefList(value: List<SourceDataRef>): String =
        json.encodeToString(value)

    @TypeConverter
    fun toSourceDataRefList(value: String): List<SourceDataRef> =
        json.decodeFromString(value)

    // PhotoAttachment list
    @TypeConverter
    fun fromPhotoAttachmentList(value: List<PhotoAttachment>): String =
        json.encodeToString(value)

    @TypeConverter
    fun toPhotoAttachmentList(value: String): List<PhotoAttachment> =
        json.decodeFromString(value)

    // SyncStatus
    @TypeConverter
    fun fromSyncStatus(value: SyncStatus): String =
        json.encodeToString(value)

    @TypeConverter
    fun toSyncStatus(value: String): SyncStatus =
        json.decodeFromString(value)

    // SessionStatus
    @TypeConverter
    fun fromSessionStatus(value: SessionStatus): String = value.name

    @TypeConverter
    fun toSessionStatus(value: String): SessionStatus = SessionStatus.valueOf(value)

    // Interaction list (for sessions)
    @TypeConverter
    fun fromInteractionList(value: List<Interaction>): String =
        json.encodeToString(value)

    @TypeConverter
    fun toInteractionList(value: String): List<Interaction> =
        json.decodeFromString(value)
}
