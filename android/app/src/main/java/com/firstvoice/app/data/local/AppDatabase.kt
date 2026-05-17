package com.firstvoice.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.firstvoice.app.data.local.dao.TriageCardDao
import com.firstvoice.app.data.local.dao.SessionDao
import com.firstvoice.app.data.local.dao.QuickPhraseDao
import com.firstvoice.app.data.local.entity.TriageCardEntity
import com.firstvoice.app.data.local.entity.SessionEntity
import com.firstvoice.app.data.local.entity.QuickPhraseEntity
import com.firstvoice.app.data.local.entity.PhotoEntity
import com.firstvoice.app.data.local.converter.Converters

import com.firstvoice.app.data.local.dao.RadioMessageDao
import com.firstvoice.app.data.local.entity.RadioMessageEntity

import com.firstvoice.app.data.local.dao.VoiceClipDao
import com.firstvoice.app.data.local.entity.VoiceClipEntity

@Database(
    entities = [
        TriageCardEntity::class,
        SessionEntity::class,
        QuickPhraseEntity::class,
        PhotoEntity::class,
        RadioMessageEntity::class,
        VoiceClipEntity::class
    ],
    version = 4,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun triageCardDao(): TriageCardDao
    abstract fun sessionDao(): SessionDao
    abstract fun quickPhraseDao(): QuickPhraseDao
    abstract fun radioMessageDao(): RadioMessageDao
    abstract fun voiceClipDao(): VoiceClipDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "firstvoice.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
