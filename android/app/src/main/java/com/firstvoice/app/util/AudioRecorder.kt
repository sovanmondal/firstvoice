package com.firstvoice.app.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Base64
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

private const val TAG = "FV.Audio"

/**
 * Records audio from the device microphone in PCM 16-bit 16kHz mono format,
 * which is the input format expected by Gemma 4's audio encoder.
 */
class AudioRecorder(private val context: Context) {

    companion object {
        const val SAMPLE_RATE = 16000
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        const val MAX_DURATION_SECONDS = 30
    }

    private var audioRecord: AudioRecord? = null
    private var isRecording = false

    /**
     * Check if microphone permission is granted.
     */
    fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Record audio for the specified duration and return as base64 string.
     * Max duration is 30 seconds (Gemma 4 E4B audio input limit).
     */
    suspend fun record(durationSeconds: Int = MAX_DURATION_SECONDS): RecordingResult =
        withContext(Dispatchers.IO) {
            Log.d(TAG, "record() duration=${durationSeconds}s permission=${hasPermission()}")
            if (!hasPermission()) {
                return@withContext RecordingResult.Error("Microphone permission not granted")
            }

            val actualDuration = durationSeconds.coerceAtMost(MAX_DURATION_SECONDS)
            val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)

            try {
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    bufferSize
                )

                if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                    return@withContext RecordingResult.Error("Failed to initialize audio recorder")
                }

                val outputStream = ByteArrayOutputStream()
                val buffer = ByteArray(bufferSize)
                val totalBytes = SAMPLE_RATE * 2 * actualDuration // 16-bit = 2 bytes per sample

                audioRecord?.startRecording()
                isRecording = true

                var bytesRead = 0
                while (isRecording && bytesRead < totalBytes) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: break
                    if (read > 0) {
                        outputStream.write(buffer, 0, read)
                        bytesRead += read
                    }
                }

                audioRecord?.stop()
                isRecording = false

                val audioBytes = outputStream.toByteArray()
                // Wrap raw PCM in WAV header so Ollama recognizes the audio format
                val wavBytes = pcmToWav(audioBytes, SAMPLE_RATE, 1, 16)
                val base64 = Base64.encodeToString(wavBytes, Base64.NO_WRAP)

                RecordingResult.Success(
                    audioBase64 = base64,
                    durationSeconds = bytesRead.toFloat() / (SAMPLE_RATE * 2),
                    sampleRate = SAMPLE_RATE
                ).also { Log.d(TAG, "record() SUCCESS ${it.durationSeconds}s base64Len=${base64.length}") }
            } catch (e: Exception) {
                isRecording = false
                Log.e(TAG, "record() FAILED", e)
                RecordingResult.Error("Recording failed: ${e.message}")
            } finally {
                audioRecord?.release()
                audioRecord = null
            }
        }

    /**
     * Stop an ongoing recording early.
     */
    fun stopRecording() {
        isRecording = false
    }

    sealed class RecordingResult {
        data class Success(
            val audioBase64: String,
            val durationSeconds: Float,
            val sampleRate: Int
        ) : RecordingResult()

        data class Error(val message: String) : RecordingResult()
    }

    private fun pcmToWav(pcmData: ByteArray, sampleRate: Int, channels: Int, bitsPerSample: Int): ByteArray {
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8
        val dataSize = pcmData.size
        val totalSize = 36 + dataSize
        val header = ByteArray(44)
        // RIFF header
        header[0] = 'R'.code.toByte(); header[1] = 'I'.code.toByte(); header[2] = 'F'.code.toByte(); header[3] = 'F'.code.toByte()
        writeInt(header, 4, totalSize)
        header[8] = 'W'.code.toByte(); header[9] = 'A'.code.toByte(); header[10] = 'V'.code.toByte(); header[11] = 'E'.code.toByte()
        // fmt chunk
        header[12] = 'f'.code.toByte(); header[13] = 'm'.code.toByte(); header[14] = 't'.code.toByte(); header[15] = ' '.code.toByte()
        writeInt(header, 16, 16) // chunk size
        writeShort(header, 20, 1) // PCM format
        writeShort(header, 22, channels)
        writeInt(header, 24, sampleRate)
        writeInt(header, 28, byteRate)
        writeShort(header, 32, blockAlign)
        writeShort(header, 34, bitsPerSample)
        // data chunk
        header[36] = 'd'.code.toByte(); header[37] = 'a'.code.toByte(); header[38] = 't'.code.toByte(); header[39] = 'a'.code.toByte()
        writeInt(header, 40, dataSize)
        return header + pcmData
    }

    private fun writeInt(buf: ByteArray, offset: Int, value: Int) {
        buf[offset] = (value and 0xFF).toByte()
        buf[offset + 1] = (value shr 8 and 0xFF).toByte()
        buf[offset + 2] = (value shr 16 and 0xFF).toByte()
        buf[offset + 3] = (value shr 24 and 0xFF).toByte()
    }

    private fun writeShort(buf: ByteArray, offset: Int, value: Int) {
        buf[offset] = (value and 0xFF).toByte()
        buf[offset + 1] = (value shr 8 and 0xFF).toByte()
    }
}
