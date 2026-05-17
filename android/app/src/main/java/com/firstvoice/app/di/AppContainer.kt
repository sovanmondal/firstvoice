package com.firstvoice.app.di

import android.content.Context
import com.firstvoice.app.ai.*
import com.firstvoice.app.data.local.AppDatabase
import com.firstvoice.app.session.SessionManager
import com.firstvoice.app.sync.MeshSyncService
import com.firstvoice.app.util.AudioRecorder
import com.firstvoice.app.util.BatteryMonitor
import com.firstvoice.app.util.TilePreloader
import java.util.UUID

/**
 * Simple dependency injection container.
 * In a production app you'd use Hilt/Dagger, but for the hackathon
 * this keeps things simple and fast.
 */
class AppContainer(private val context: Context) {

    // Device ID — persisted across app restarts
    val deviceId: String by lazy {
        val prefs = context.getSharedPreferences("firstvoice", Context.MODE_PRIVATE)
        prefs.getString("device_id", null) ?: run {
            val id = "fv-${UUID.randomUUID().toString().take(8)}"
            prefs.edit().putString("device_id", id).apply()
            id
        }
    }

    // Database
    val database: AppDatabase by lazy { AppDatabase.getInstance(context) }

    // AI
    val ollamaClient: OllamaClient by lazy {
        OllamaClient().also {
            val prefs = context.getSharedPreferences("firstvoice", Context.MODE_PRIVATE)
            it.baseUrl = prefs.getString("ollama_url", "http://localhost:11434") ?: "http://localhost:11434"
        }
    }
    val speechEngine: SpeechEngine by lazy { SpeechEngine(ollamaClient) }
    val translationEngine: TranslationEngine by lazy { TranslationEngine(ollamaClient) }
    val visionAnalyzer: VisionAnalyzer by lazy { VisionAnalyzer(ollamaClient) }
    val triageAgent: TriageAgent by lazy { TriageAgent(ollamaClient) }

    // Session
    val sessionManager: SessionManager by lazy {
        SessionManager(
            context = context,
            sessionDao = database.sessionDao(),
            triageCardDao = database.triageCardDao(),
            speechEngine = speechEngine,
            translationEngine = translationEngine,
            visionAnalyzer = visionAnalyzer,
            triageAgent = triageAgent,
            deviceId = deviceId
        )
    }

    // Sync — WiFi-Direct
    val meshSyncService: MeshSyncService by lazy {
        MeshSyncService(context, database.triageCardDao(), database.radioMessageDao(), database.voiceClipDao(), deviceId)
    }

    // Utilities
    val audioRecorder: AudioRecorder by lazy { AudioRecorder(context) }
    val batteryMonitor: BatteryMonitor by lazy { BatteryMonitor(context) }
    val tilePreloader: TilePreloader by lazy { TilePreloader(context) }
}
