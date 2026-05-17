package com.firstvoice.app.ai

import android.util.Log

private const val TAG = "FV.Translate"

class TranslationEngine(private val ollamaClient: OllamaClient) {

    data class TranslationResult(
        val translatedText: String,
        val sourceLang: String,
        val targetLang: String
    )

    /** Fast: detect language + translate in ONE Ollama call */
    suspend fun detectAndTranslate(text: String, targetLang: String): TranslationResult {
        Log.d(TAG, "detectAndTranslate() → '$targetLang' text='${text.take(80)}'")
        val prompt = PromptTemplates.detectAndTranslatePrompt(text, targetLang)
        val response = ollamaClient.chat(
            messages = listOf(ChatMessage(role = "user", content = prompt)),
            temperature = 0.2f,
            maxTokens = 512
        )
        val content = response.message.content.trim()
        var lang = "Unknown"
        var translated = content
        for (line in content.lines()) {
            when {
                line.startsWith("LANG:") -> lang = line.removePrefix("LANG:").trim()
                line.startsWith("TEXT:") -> translated = line.removePrefix("TEXT:").trim()
            }
        }
        // Fallback: if no LANG/TEXT format, treat whole response as translation
        if (lang == "Unknown" && !content.contains("LANG:")) translated = content.removeSurrounding("\"")
        Log.d(TAG, "detectAndTranslate() → lang=$lang '${translated.take(120)}'")
        return TranslationResult(translated, lang, targetLang)
    }

    suspend fun translate(text: String, sourceLang: String, targetLang: String): TranslationResult {
        Log.d(TAG, "translate() '$sourceLang' → '$targetLang' text='${text.take(80)}'")
        val prompt = PromptTemplates.translationPrompt(sourceLang, targetLang, text)
        val response = ollamaClient.chat(
            messages = listOf(ChatMessage(role = "user", content = prompt)),
            temperature = 0.2f,
            maxTokens = 512
        )
        val translated = response.message.content.trim().removeSurrounding("\"")
        Log.d(TAG, "translate() → '${translated.take(120)}'")
        return TranslationResult(translated, sourceLang, targetLang)
    }
}
