package com.firstvoice.app.ai

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

private const val TAG = "FV.Ollama"

class OllamaClient {
    var baseUrl: String = "http://10.0.2.2:11434"

    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun isAvailable(): Boolean = withContext(Dispatchers.IO) {
        Log.d(TAG, "isAvailable() checking $baseUrl/api/tags")
        try {
            val request = Request.Builder().url("$baseUrl/api/tags").get().build()
            val result = client.newCall(request).execute().use { it.isSuccessful }
            Log.d(TAG, "isAvailable() = $result")
            result
        } catch (e: Exception) {
            Log.e(TAG, "isAvailable() FAILED: ${e.message}")
            false
        }
    }

    suspend fun chat(
        messages: List<ChatMessage>,
        model: String = "gemma4:e4b",
        temperature: Float = 0.3f,
        maxTokens: Int = 2048,
        tools: List<ToolDefinition>? = null,
        think: Boolean? = null
    ): ChatResponse = withContext(Dispatchers.IO) {
        Log.d(TAG, "chat() → $baseUrl/api/chat model=$model msgs=${messages.size} temp=$temperature tools=${tools?.size ?: 0}")
        messages.forEachIndexed { i, m -> Log.d(TAG, "  msg[$i] role=${m.role} len=${m.content.length} images=${m.images?.size ?: 0}") }
        val body = json.encodeToString(ChatRequest(model, messages, false, GenerateOptions(temperature, maxTokens), tools, think))
        val request = Request.Builder()
            .url("$baseUrl/api/chat")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
        val t0 = System.currentTimeMillis()
        val response = client.newCall(request).execute()
        val elapsed = System.currentTimeMillis() - t0
        val responseBody = response.body?.string() ?: throw OllamaException("Empty response")
        Log.d(TAG, "chat() ← HTTP ${response.code} in ${elapsed}ms bodyLen=${responseBody.length}")
        if (!response.isSuccessful) {
            Log.e(TAG, "chat() ERROR: $responseBody")
            throw OllamaException("Ollama error ${response.code}: $responseBody")
        }
        // Ollama returns NDJSON even with stream:false on some versions.
        // Concatenate all message.content fields, use metadata from the final "done":true line.
        val jsonLines = responseBody.trim().lines().filter { it.startsWith("{") }
        val fullContent = StringBuilder()
        var lastParsed: ChatResponse? = null
        var toolCalls: List<ToolCall>? = null
        for (line in jsonLines) {
            try {
                val chunk = json.decodeFromString<ChatResponse>(line)
                fullContent.append(chunk.message.content)
                if (chunk.message.tool_calls != null) toolCalls = chunk.message.tool_calls
                if (chunk.done) lastParsed = chunk
            } catch (_: Exception) {}
        }
        val parsed = (lastParsed ?: json.decodeFromString<ChatResponse>(jsonLines.last()))
            .let { it.copy(message = it.message.copy(content = fullContent.toString(), tool_calls = toolCalls ?: it.message.tool_calls)) }
        Log.d(TAG, "chat() response: ${parsed.message.content.take(200)}")
        Log.d(TAG, "chat() tool_calls: ${parsed.message.tool_calls?.size ?: 0}")
        parsed
    }

    suspend fun generate(
        prompt: String,
        model: String = "gemma4:e4b",
        temperature: Float = 0.3f,
        maxTokens: Int = 2048
    ): GenerateResponse = withContext(Dispatchers.IO) {
        Log.d(TAG, "generate() → $baseUrl/api/generate model=$model promptLen=${prompt.length}")
        val body = json.encodeToString(GenerateRequest(model, prompt, false, GenerateOptions(temperature, maxTokens)))
        val request = Request.Builder()
            .url("$baseUrl/api/generate")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
        val t0 = System.currentTimeMillis()
        val response = client.newCall(request).execute()
        val elapsed = System.currentTimeMillis() - t0
        val responseBody = response.body?.string() ?: throw OllamaException("Empty response")
        Log.d(TAG, "generate() ← HTTP ${response.code} in ${elapsed}ms")
        if (!response.isSuccessful) {
            Log.e(TAG, "generate() ERROR: $responseBody")
            throw OllamaException("Ollama error ${response.code}: $responseBody")
        }
        val jsonLines = responseBody.trim().lines().filter { it.startsWith("{") }
        val fullResponse = StringBuilder()
        var lastParsed: GenerateResponse? = null
        for (line in jsonLines) {
            try {
                val chunk = json.decodeFromString<GenerateResponse>(line)
                fullResponse.append(chunk.response)
                if (chunk.done) lastParsed = chunk
            } catch (_: Exception) {}
        }
        val parsed = (lastParsed ?: json.decodeFromString<GenerateResponse>(jsonLines.last()))
            .let { it.copy(response = fullResponse.toString()) }
        Log.d(TAG, "generate() response: ${parsed.response.take(200)}")
        Log.d(TAG, "generate() response: ${parsed.response.take(200)}")
        parsed
    }
}

@Serializable data class GenerateRequest(val model: String, val prompt: String, val stream: Boolean = false, val options: GenerateOptions? = null)
@Serializable data class GenerateOptions(val temperature: Float = 0.7f, val num_predict: Int = 2048, val top_p: Float = 0.95f, val top_k: Int = 64)
@Serializable data class GenerateResponse(val model: String = "", val response: String = "", val done: Boolean = true, val total_duration: Long = 0, val eval_count: Int = 0)
@Serializable data class ChatRequest(val model: String, val messages: List<ChatMessage>, val stream: Boolean = false, val options: GenerateOptions? = null, val tools: List<ToolDefinition>? = null, val think: Boolean? = null)
@Serializable data class ChatMessage(val role: String, val content: String = "", val images: List<String>? = null, val tool_calls: List<ToolCall>? = null, val tool_name: String? = null)
@Serializable data class ChatResponse(val model: String = "", val message: ChatMessage = ChatMessage("assistant", ""), val done: Boolean = true, val total_duration: Long = 0, val eval_count: Int = 0)
@Serializable data class ToolDefinition(val type: String = "function", val function: ToolFunction)
@Serializable data class ToolFunction(val name: String, val description: String, val parameters: ToolParameters)
@Serializable data class ToolParameters(val type: String = "object", val properties: Map<String, ToolProperty>, val required: List<String> = emptyList())
@Serializable data class ToolProperty(val type: String, val description: String = "", val enum: List<String>? = null)
@Serializable data class ToolCall(val function: ToolCallFunction)
@Serializable data class ToolCallFunction(val name: String, val arguments: Map<String, kotlinx.serialization.json.JsonElement> = emptyMap())
class OllamaException(message: String) : Exception(message)
