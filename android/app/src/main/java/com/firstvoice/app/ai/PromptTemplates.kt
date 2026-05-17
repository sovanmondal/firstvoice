package com.firstvoice.app.ai

/**
 * Gemma 4 prompt templates for all AI capabilities.
 * These templates leverage Gemma 4's native features:
 * - Audio encoder for ASR
 * - Vision encoder for image analysis
 * - Function calling (6 special tokens) for structured output
 * - Thinking mode for multi-step reasoning
 */
object PromptTemplates {

    /**
     * Speech transcription with automatic language detection.
     * Used with audio input via Gemma 4 E4B's native audio encoder.
     */
    fun transcriptionPrompt(): String = """
You are a multilingual speech transcription assistant deployed in a disaster zone.
Transcribe the audio accurately. Detect the spoken language.
Respond in this exact format:
LANGUAGE: <detected language name>
CONFIDENCE: <HIGH|MEDIUM|LOW>
TRANSCRIPTION: <transcribed text>
    """.trimIndent()

    /**
     * Bidirectional text translation for crisis communication.
     */
    fun translationPrompt(sourceLang: String, targetLang: String, text: String): String = """
Translate from $sourceLang to $targetLang. Respond with ONLY the translated text.
"$text"
    """.trimIndent()

    /**
     * Combined language detection + translation in one call.
     * Saves one round-trip to Ollama.
     */
    fun detectAndTranslatePrompt(text: String, targetLang: String): String = """
Detect the language of this text and translate it to $targetLang.
Respond in this exact format (2 lines only):
LANG: <detected language>
TEXT: <translated text>

"$text"
    """.trimIndent()

    /**
     * Vision-based damage assessment with structured output.
     */
    fun damageAssessmentPrompt(sessionContext: String = ""): String = """
You are a disaster damage assessment AI. Analyze this image for:
1. Structural damage (NONE, MINOR, MODERATE, SEVERE, CATASTROPHIC)
2. Environmental hazards (flooding, fire, gas leaks, chemical spills) with severity (NONE, LOW, MODERATE, HIGH, EXTREME)
3. Any handwritten signs or text visible

${if (sessionContext.isNotEmpty()) "Context from conversation:\n$sessionContext\n" else ""}

Respond in this exact JSON format:
{
  "structural_severity": "<severity>",
  "structural_description": "<description>",
  "hazards": [{"type": "<type>", "severity": "<severity>", "description": "<description>"}],
  "extracted_text": "<text or null>",
  "summary": "<one sentence summary>"
}
    """.trimIndent()

    /**
     * Vision-based injury assessment with structured output.
     */
    fun injuryAssessmentPrompt(): String = """
You are a medical triage AI assistant. Analyze this image for visible injuries.
For each injury found, identify:
1. Type: wound, burn, bleeding, fracture_indication, or other
2. Body region affected
3. Severity: MINOR, MODERATE, SEVERE, or LIFE_THREATENING

IMPORTANT: This is an AI-generated assessment and does NOT replace professional medical evaluation.

Respond in this exact JSON format:
{
  "injuries": [
    {"type": "<type>", "body_region": "<region>", "severity": "<severity>", "description": "<description>"}
  ],
  "summary": "<one sentence summary>"
}
    """.trimIndent()

    /**
     * Triage card generation using thinking mode + function calling.
     * This is the core agentic capability.
     */
    fun triageCardPrompt(sessionContext: String): String = """
You are a disaster triage agent. Based on the encounter transcript below, generate a structured triage card.

Think step-by-step:
1. How many people are affected?
2. What is the urgency level? (CRITICAL = immediate life threat, HIGH = serious but stable, MEDIUM = needs attention soon, LOW = minor/can wait)
3. What categories of needs are present? (Medical, Extraction, Shelter, Water/Food, Family Reunification)
4. What language was the survivor speaking?
5. Summarize the situation concisely.

ENCOUNTER TRANSCRIPT:
$sessionContext

Respond in this exact JSON format:
{
  "people_count": <number or null if unknown>,
  "urgency_level": "<CRITICAL|HIGH|MEDIUM|LOW>",
  "needs_categories": ["<category1>", "<category2>"],
  "detected_language": "<language>",
  "assessment_summary": "<concise summary of the situation>"
}
    """.trimIndent()

    /**
     * Incident report generation from multiple triage cards.
     */
    fun incidentReportPrompt(cardsJson: String): String = """
You are a disaster coordination report writer. Generate a standardized incident report from the following triage cards.

Include:
1. Incident summary
2. Location details
3. Affected population count
4. Urgency breakdown
5. Needs breakdown
6. Timeline of events
7. Detailed assessment

TRIAGE CARDS:
$cardsJson

Write a clear, structured report suitable for sharing with coordination centers.
    """.trimIndent()
}
