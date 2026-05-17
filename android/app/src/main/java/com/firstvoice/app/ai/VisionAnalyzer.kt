package com.firstvoice.app.ai

import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private const val TAG = "FV.Vision"

class VisionAnalyzer(private val ollamaClient: OllamaClient) {

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable data class DamageResult(val structural_severity: String = "NONE", val structural_description: String = "", val hazards: List<HazardResult> = emptyList(), val extracted_text: String? = null, val summary: String = "")
    @Serializable data class HazardResult(val type: String = "", val severity: String = "NONE", val description: String = "")
    @Serializable data class InjuryResult(val injuries: List<InjuryDetail> = emptyList(), val summary: String = "")
    @Serializable data class InjuryDetail(val type: String = "other", val body_region: String = "", val severity: String = "MINOR", val description: String = "")

    suspend fun analyzeDamage(imageBase64: String, sessionContext: String = ""): DamageResult {
        Log.d(TAG, "analyzeDamage() imageLen=${imageBase64.length} contextLen=${sessionContext.length}")
        val response = ollamaClient.chat(
            messages = listOf(ChatMessage(role = "user", content = PromptTemplates.damageAssessmentPrompt(sessionContext), images = listOf(imageBase64))),
            maxTokens = 1024
        )
        Log.d(TAG, "analyzeDamage() raw response: ${response.message.content.take(300)}")
        return try {
            val jsonStr = extractJson(response.message.content)
            val result = json.decodeFromString<DamageResult>(jsonStr)
            Log.d(TAG, "analyzeDamage() → severity=${result.structural_severity} summary='${result.summary.take(100)}'")
            result
        } catch (e: Exception) {
            Log.e(TAG, "analyzeDamage() JSON parse failed", e)
            DamageResult(summary = "Analysis: ${response.message.content.take(200)}")
        }
    }

    suspend fun analyzeInjury(imageBase64: String): InjuryResult {
        Log.d(TAG, "analyzeInjury() imageLen=${imageBase64.length}")
        val response = ollamaClient.chat(
            messages = listOf(ChatMessage(role = "user", content = PromptTemplates.injuryAssessmentPrompt(), images = listOf(imageBase64))),
            maxTokens = 1024
        )
        Log.d(TAG, "analyzeInjury() raw response: ${response.message.content.take(300)}")
        return try {
            val jsonStr = extractJson(response.message.content)
            val result = json.decodeFromString<InjuryResult>(jsonStr)
            Log.d(TAG, "analyzeInjury() → ${result.injuries.size} injuries, summary='${result.summary.take(100)}'")
            result
        } catch (e: Exception) {
            Log.e(TAG, "analyzeInjury() JSON parse failed", e)
            InjuryResult(summary = "Analysis: ${response.message.content.take(200)}")
        }
    }

    private fun extractJson(content: String): String {
        val start = content.indexOf('{')
        val end = content.lastIndexOf('}')
        if (start == -1 || end == -1 || end <= start) throw IllegalArgumentException("No JSON found in: ${content.take(100)}")
        return content.substring(start, end + 1)
    }
}
