package com.firstvoice.app.ai

import android.util.Log
import com.firstvoice.app.data.model.ConfidenceLevel

private const val TAG = "FV.Speech"

class SpeechEngine(private val ollamaClient: OllamaClient) {

    data class TranscriptionResult(
        val text: String,
        val detectedLanguage: String,
        val confidence: ConfidenceLevel,
        val segmentCount: Int = 1
    )

    suspend fun transcribe(audioBase64: String): TranscriptionResult {
        Log.d(TAG, "transcribe() audioLen=${audioBase64.length}")
        return try {
            val response = ollamaClient.chat(
                messages = listOf(
                    ChatMessage(role = "system", content = PromptTemplates.transcriptionPrompt()),
                    ChatMessage(role = "user", content = "Transcribe the following speech audio.", images = listOf(audioBase64))
                )
            )
            val result = parseTranscriptionResponse(response.message.content)
            Log.d(TAG, "transcribe() → lang=${result.detectedLanguage} conf=${result.confidence} text='${result.text.take(80)}'")
            result
        } catch (e: Exception) {
            Log.e(TAG, "transcribe() FAILED", e)
            TranscriptionResult("[Audio transcription unavailable: ${e.message?.take(80)}]", "Unknown", ConfidenceLevel.LOW)
        }
    }

    suspend fun transcribeText(text: String, hintLanguage: String? = null): TranscriptionResult {
        Log.d(TAG, "transcribeText() text='${text.take(80)}' hint=$hintLanguage")
        val prompt = if (hintLanguage != null) {
            "The following text is spoken in a disaster/emergency context. The language might be $hintLanguage. Detect the exact language and return the transcription.\n\"$text\""
        } else {
            "The following text is spoken in a disaster/emergency context. Detect the language and return the transcription.\n\"$text\""
        }
        return try {
            val response = ollamaClient.chat(
                messages = listOf(
                    ChatMessage(role = "system", content = PromptTemplates.transcriptionPrompt()),
                    ChatMessage(role = "user", content = prompt)
                )
            )
            val result = parseTranscriptionResponse(response.message.content)
            Log.d(TAG, "transcribeText() → lang=${result.detectedLanguage} conf=${result.confidence} text='${result.text.take(80)}'")
            result
        } catch (e: Exception) {
            Log.e(TAG, "transcribeText() FAILED", e)
            TranscriptionResult(text, hintLanguage ?: "Unknown", ConfidenceLevel.LOW)
        }
    }

    private fun parseTranscriptionResponse(content: String): TranscriptionResult {
        Log.d(TAG, "parseResponse() raw='${content.take(200)}'")
        val lines = content.lines()
        var language = "Unknown"
        var confidence = ConfidenceLevel.MEDIUM
        var transcription = content
        for (line in lines) {
            when {
                line.startsWith("LANGUAGE:") -> language = line.removePrefix("LANGUAGE:").trim()
                line.startsWith("CONFIDENCE:") -> {
                    confidence = try { ConfidenceLevel.valueOf(line.removePrefix("CONFIDENCE:").trim().uppercase()) } catch (_: Exception) { ConfidenceLevel.MEDIUM }
                }
                line.startsWith("TRANSCRIPTION:") -> transcription = line.removePrefix("TRANSCRIPTION:").trim()
            }
        }
        return TranscriptionResult(transcription, language, confidence)
    }
}
