package com.firstvoice.app.ai

import android.util.Log
import com.firstvoice.app.data.model.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.intOrNull
import java.util.UUID

private const val TAG = "FV.Triage"

class TriageAgent(private val ollamaClient: OllamaClient) {

    private val json = Json { ignoreUnknownKeys = true }

    private val triageToolDef = ToolDefinition(
        function = ToolFunction(
            name = "generate_triage_card",
            description = "Generate a structured triage card from a disaster encounter conversation. Call this after analyzing the conversation to produce a structured assessment.",
            parameters = ToolParameters(
                properties = mapOf(
                    "people_count" to ToolProperty("integer", "Number of people affected, or 0 if unknown"),
                    "urgency_level" to ToolProperty("string", "Urgency level of the situation", enum = listOf("CRITICAL", "HIGH", "MEDIUM", "LOW")),
                    "needs_categories" to ToolProperty("string", "Comma-separated list of needs: Medical, Extraction, Shelter, WaterFood, FamilyReunification"),
                    "detected_language" to ToolProperty("string", "Language the survivor was speaking"),
                    "assessment_summary" to ToolProperty("string", "Concise summary of the situation in one or two sentences")
                ),
                required = listOf("urgency_level", "needs_categories", "detected_language", "assessment_summary")
            )
        )
    )

    suspend fun generateTriageCard(sessionId: String, deviceId: String, sessionContext: String, gps: GPSCoordinate? = null): TriageCard {
        Log.d(TAG, "generateTriageCard() sessionId=$sessionId contextLen=${sessionContext.length} gps=${gps != null}")

        val response = ollamaClient.chat(
            messages = listOf(
                ChatMessage(role = "system", content = "You are a disaster triage agent. Analyze the encounter transcript and call generate_triage_card with structured assessment data."),
                ChatMessage(role = "user", content = "Analyze this encounter and generate a triage card:\n\n$sessionContext")
            ),
            temperature = 0.2f,
            maxTokens = 512,
            tools = listOf(triageToolDef),
            think = false
        )

        Log.d(TAG, "generateTriageCard() tool_calls=${response.message.tool_calls?.size} content='${response.message.content.take(200)}'")

        val toolCall = response.message.tool_calls?.firstOrNull { it.function.name == "generate_triage_card" }

        val now = System.currentTimeMillis()

        return if (toolCall != null) {
            val args = toolCall.function.arguments
            Log.d(TAG, "generateTriageCard() native tool call args: $args")

            val peopleCount = args["people_count"]?.jsonPrimitive?.intOrNull
            val urgency = args["urgency_level"]?.jsonPrimitive?.content ?: "MEDIUM"
            val needsStr = args["needs_categories"]?.jsonPrimitive?.content ?: ""
            val language = args["detected_language"]?.jsonPrimitive?.content ?: "Unknown"
            val summary = args["assessment_summary"]?.jsonPrimitive?.content ?: ""

            TriageCard(
                id = UUID.randomUUID().toString(), deviceId = deviceId, sessionId = sessionId,
                gpsCoordinates = gps, timestamp = now, updatedAt = now,
                peopleCount = peopleCount,
                urgencyLevel = parseUrgencyLevel(urgency),
                needsCategories = parseNeedsCategories(needsStr),
                detectedLanguage = language,
                assessmentSummary = summary,
                sourceDataRefs = emptyList(), photos = emptyList(), syncStatus = SyncStatus()
            )
        } else {
            // Fallback: parse from content if model didn't use tool calling
            Log.w(TAG, "generateTriageCard() model did not use tool call, falling back to JSON parse")
            parseFromContent(response.message.content, sessionId, deviceId, gps, now)
        }
    }

    private fun parseFromContent(content: String, sessionId: String, deviceId: String, gps: GPSCoordinate?, now: Long): TriageCard {
        return try {
            val jsonStr = extractJson(content)
            val draft = json.decodeFromString<TriageCardDraft>(jsonStr)
            TriageCard(
                id = UUID.randomUUID().toString(), deviceId = deviceId, sessionId = sessionId,
                gpsCoordinates = gps, timestamp = now, updatedAt = now,
                peopleCount = draft.people_count,
                urgencyLevel = parseUrgencyLevel(draft.urgency_level),
                needsCategories = draft.needs_categories.mapNotNull { parseNeedsCategory(it) },
                detectedLanguage = draft.detected_language,
                assessmentSummary = draft.assessment_summary,
                sourceDataRefs = emptyList(), photos = emptyList(), syncStatus = SyncStatus()
            )
        } catch (e: Exception) {
            Log.e(TAG, "generateTriageCard() fallback parse failed", e)
            TriageCard(
                id = UUID.randomUUID().toString(), deviceId = deviceId, sessionId = sessionId,
                gpsCoordinates = gps, timestamp = now, updatedAt = now,
                peopleCount = null, urgencyLevel = UrgencyLevel.MEDIUM,
                needsCategories = emptyList(), detectedLanguage = "Unknown",
                assessmentSummary = "Auto-generated. Parse failed: ${e.message}",
                sourceDataRefs = emptyList(), photos = emptyList(), syncStatus = SyncStatus()
            )
        }
    }

    @kotlinx.serialization.Serializable
    data class TriageCardDraft(
        val people_count: Int? = null,
        val urgency_level: String = "MEDIUM",
        val needs_categories: List<String> = emptyList(),
        val detected_language: String = "Unknown",
        val assessment_summary: String = ""
    )

    suspend fun generateIncidentReport(cards: List<TriageCard>): String {
        Log.d(TAG, "generateIncidentReport() cards=${cards.size}")
        val cardsJson = json.encodeToString(kotlinx.serialization.builtins.ListSerializer(TriageCard.serializer()), cards)
        val response = ollamaClient.chat(
            messages = listOf(ChatMessage(role = "user", content = PromptTemplates.incidentReportPrompt(cardsJson))),
            maxTokens = 2048
        )
        return response.message.content
    }

    private fun parseUrgencyLevel(value: String): UrgencyLevel {
        return try { UrgencyLevel.valueOf(value.uppercase().trim()) } catch (_: Exception) { UrgencyLevel.MEDIUM }
    }

    private fun parseNeedsCategories(commaStr: String): List<NeedsCategory> {
        return commaStr.split(",").mapNotNull { parseNeedsCategory(it.trim()) }
    }

    private fun parseNeedsCategory(value: String): NeedsCategory? {
        return when (value.lowercase().trim()) {
            "medical" -> NeedsCategory.Medical
            "extraction" -> NeedsCategory.Extraction
            "shelter" -> NeedsCategory.Shelter
            "water/food", "waterfood", "water_food" -> NeedsCategory.WaterFood
            "family reunification", "familyreunification", "family_reunification" -> NeedsCategory.FamilyReunification
            else -> { Log.w(TAG, "Unknown needs category: '$value'"); null }
        }
    }

    private fun extractJson(content: String): String {
        val start = content.indexOf('{')
        val end = content.lastIndexOf('}')
        if (start == -1 || end == -1 || end <= start) throw IllegalArgumentException("No JSON found")
        return content.substring(start, end + 1)
    }
}
