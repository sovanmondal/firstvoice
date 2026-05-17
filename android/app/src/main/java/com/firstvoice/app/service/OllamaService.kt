package com.firstvoice.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat

/**
 * Foreground service that manages the Ollama inference runtime.
 * Ollama runs Gemma 4 E4B (4-bit quantized) on-device and exposes
 * a REST API at localhost:11434 for the app to call.
 */
class OllamaService : Service() {

    companion object {
        const val CHANNEL_ID = "firstvoice_ollama"
        const val NOTIFICATION_ID = 1
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification()
        startForeground(NOTIFICATION_ID, notification)
        // TODO: Start Ollama process and load Gemma 4 E4B model
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        // TODO: Stop Ollama process
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "FirstVoice AI Engine",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Keeps the Gemma 4 AI model running for offline inference"
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("FirstVoice AI Active")
            .setContentText("Gemma 4 model is ready for offline inference")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
