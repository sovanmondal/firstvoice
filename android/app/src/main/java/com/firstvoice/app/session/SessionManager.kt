package com.firstvoice.app.session

import android.content.Context
import android.location.Location
import android.location.LocationManager
import com.firstvoice.app.ai.SpeechEngine
import com.firstvoice.app.ai.TranslationEngine
import com.firstvoice.app.ai.TriageAgent
import com.firstvoice.app.ai.VisionAnalyzer
import com.firstvoice.app.data.local.dao.SessionDao
import com.firstvoice.app.data.local.dao.TriageCardDao
import com.firstvoice.app.data.local.entity.SessionEntity
import com.firstvoice.app.data.local.entity.TriageCardEntity
import com.firstvoice.app.data.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

/**
 * Central orchestrator for conversation sessions.
 * Manages the session lifecycle, maintains the ordered interaction log,
 * and feeds accumulated context to downstream AI engines.
 */
class SessionManager(
    private val context: Context,
    private val sessionDao: SessionDao,
    private val triageCardDao: TriageCardDao,
    private val speechEngine: SpeechEngine,
    private val translationEngine: TranslationEngine,
    private val visionAnalyzer: VisionAnalyzer,
    private val triageAgent: TriageAgent,
    private val deviceId: String
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _activeSession = MutableStateFlow<ConversationSession?>(null)
    val activeSession: StateFlow<ConversationSession?> = _activeSession

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing

    private val _lastResult = MutableStateFlow<ProcessingResult?>(null)
    val lastResult: StateFlow<ProcessingResult?> = _lastResult

    private var inactivityJob: Job? = null
    private val inactivityTimeoutMs = 30 * 60 * 1000L // 30 minutes

    sealed class ProcessingResult {
        data class Transcription(
            val text: String,
            val language: String,
            val confidence: ConfidenceLevel,
            val translatedText: String,
            val translatedLanguage: String
        ) : ProcessingResult()

        data class DamageAnalysis(
            val result: VisionAnalyzer.DamageResult
        ) : ProcessingResult()

        data class InjuryAnalysis(
            val result: VisionAnalyzer.InjuryResult
        ) : ProcessingResult()

        data class TriageGenerated(
            val card: TriageCard
        ) : ProcessingResult()

        data class Error(val message: String) : ProcessingResult()
    }

    /**
     * Start a new encounter session.
     */
    suspend fun startSession(responderLanguage: String = "English"): ConversationSession {
        val gps = getCurrentLocation()
        val session = ConversationSession(
            id = UUID.randomUUID().toString(),
            deviceId = deviceId,
            startedAt = System.currentTimeMillis(),
            gpsCoordinates = gps,
            responderLanguage = responderLanguage,
            status = SessionStatus.ACTIVE
        )
        _activeSession.value = session
        sessionDao.insert(SessionEntity.fromConversationSession(session))
        resetInactivityTimer()
        return session
    }

    /**
     * Process a speech recording from the survivor.
     * Transcribes, detects language, translates to responder's language.
     */
    suspend fun processSurvivorSpeech(audioBase64: String) {
        val session = _activeSession.value ?: return
        _isProcessing.value = true
        resetInactivityTimer()

        try {
            // Transcribe
            val transcription = speechEngine.transcribe(audioBase64)

            // Update survivor language if detected with high confidence
            if (transcription.confidence != ConfidenceLevel.LOW && session.survivorLanguage == null) {
                val updated = session.copy(survivorLanguage = transcription.detectedLanguage)
                _activeSession.value = updated
            }

            // Translate to responder's language
            val translation = translationEngine.translate(
                text = transcription.text,
                sourceLang = transcription.detectedLanguage,
                targetLang = session.responderLanguage
            )

            // Add to session log
            val turn = Interaction.SpeechTurn(
                id = UUID.randomUUID().toString(),
                timestamp = System.currentTimeMillis(),
                speaker = Speaker.SURVIVOR,
                originalText = transcription.text,
                originalLanguage = transcription.detectedLanguage,
                translatedText = translation.translatedText,
                translatedLanguage = session.responderLanguage,
                confidence = transcription.confidence
            )
            appendInteraction(turn)

            _lastResult.value = ProcessingResult.Transcription(
                text = transcription.text,
                language = transcription.detectedLanguage,
                confidence = transcription.confidence,
                translatedText = translation.translatedText,
                translatedLanguage = session.responderLanguage
            )
        } catch (e: Exception) {
            _lastResult.value = ProcessingResult.Error("Speech processing failed: ${e.message}")
        } finally {
            _isProcessing.value = false
        }
    }

    /**
     * Process a speech recording from the responder.
     * Transcribes and translates to survivor's language.
     */
    suspend fun processResponderSpeech(audioBase64: String) {
        val session = _activeSession.value ?: return
        val survivorLang = session.survivorLanguage ?: "Unknown"
        _isProcessing.value = true
        resetInactivityTimer()

        try {
            val transcription = speechEngine.transcribe(audioBase64)

            val translation = translationEngine.translate(
                text = transcription.text,
                sourceLang = session.responderLanguage,
                targetLang = survivorLang
            )

            val turn = Interaction.SpeechTurn(
                id = UUID.randomUUID().toString(),
                timestamp = System.currentTimeMillis(),
                speaker = Speaker.RESPONDER,
                originalText = transcription.text,
                originalLanguage = session.responderLanguage,
                translatedText = translation.translatedText,
                translatedLanguage = survivorLang,
                confidence = transcription.confidence
            )
            appendInteraction(turn)

            _lastResult.value = ProcessingResult.Transcription(
                text = transcription.text,
                language = session.responderLanguage,
                confidence = transcription.confidence,
                translatedText = translation.translatedText,
                translatedLanguage = survivorLang
            )
        } catch (e: Exception) {
            _lastResult.value = ProcessingResult.Error("Speech processing failed: ${e.message}")
        } finally {
            _isProcessing.value = false
        }
    }

    /**
     * Process a photo for damage assessment.
     */
    suspend fun processPhoto(imageBase64: String, assessmentType: String = "damage") {
        _isProcessing.value = true
        resetInactivityTimer()

        try {
            val sessionContext = buildSessionContext()

            when (assessmentType) {
                "damage" -> {
                    val result = visionAnalyzer.analyzeDamage(imageBase64, sessionContext)
                    val interaction = Interaction.VisionAssessment(
                        id = UUID.randomUUID().toString(),
                        timestamp = System.currentTimeMillis(),
                        photoId = UUID.randomUUID().toString(),
                        assessmentJson = result.summary
                    )
                    appendInteraction(interaction)
                    _lastResult.value = ProcessingResult.DamageAnalysis(result)
                }
                "injury" -> {
                    val result = visionAnalyzer.analyzeInjury(imageBase64)
                    val interaction = Interaction.VisionAssessment(
                        id = UUID.randomUUID().toString(),
                        timestamp = System.currentTimeMillis(),
                        photoId = UUID.randomUUID().toString(),
                        assessmentJson = result.summary
                    )
                    appendInteraction(interaction)
                    _lastResult.value = ProcessingResult.InjuryAnalysis(result)
                }
            }
        } catch (e: Exception) {
            _lastResult.value = ProcessingResult.Error("Photo analysis failed: ${e.message}")
        } finally {
            _isProcessing.value = false
        }
    }

    /**
     * Log a quick phrase usage in the session.
     */
    suspend fun logQuickPhrase(phraseId: String, sourceText: String, translatedText: String, targetLang: String) {
        val interaction = Interaction.QuickPhraseUsage(
            id = UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis(),
            phraseId = phraseId,
            sourceText = sourceText,
            translatedText = translatedText,
            targetLanguage = targetLang
        )
        appendInteraction(interaction)
        resetInactivityTimer()
    }

    /**
     * Add a manual note to the session.
     */
    suspend fun addNote(text: String) {
        val interaction = Interaction.Note(
            id = UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis(),
            text = text
        )
        appendInteraction(interaction)
        resetInactivityTimer()
    }

    /**
     * End the encounter and generate a triage card.
     */
    suspend fun endSession(): TriageCard? {
        val session = _activeSession.value ?: return null
        _isProcessing.value = true
        inactivityJob?.cancel()

        try {
            val context = buildSessionContext()
            val card = triageAgent.generateTriageCard(
                sessionId = session.id,
                deviceId = deviceId,
                sessionContext = context,
                gps = session.gpsCoordinates
            )

            // Save triage card
            triageCardDao.insert(TriageCardEntity.fromTriageCard(card))

            // Close session
            val closedSession = session.copy(
                endedAt = System.currentTimeMillis(),
                triageCardId = card.id,
                status = SessionStatus.CLOSED
            )
            _activeSession.value = null
            sessionDao.update(SessionEntity.fromConversationSession(closedSession))

            _lastResult.value = ProcessingResult.TriageGenerated(card)
            return card
        } catch (e: Exception) {
            _lastResult.value = ProcessingResult.Error("Triage generation failed: ${e.message}")
            return null
        } finally {
            _isProcessing.value = false
        }
    }

    /**
     * Build a text context string from all session interactions
     * for feeding into Gemma 4's context window.
     */
    fun buildSessionContext(): String {
        val session = _activeSession.value ?: return ""
        val sb = StringBuilder()

        for (interaction in session.interactions) {
            when (interaction) {
                is Interaction.SpeechTurn -> {
                    val speaker = if (interaction.speaker == Speaker.RESPONDER) "Responder" else "Survivor"
                    sb.appendLine("[$speaker] (${interaction.originalLanguage}): ${interaction.originalText}")
                    sb.appendLine("  → Translated (${interaction.translatedLanguage}): ${interaction.translatedText}")
                }
                is Interaction.VisionAssessment -> {
                    sb.appendLine("[Photo Assessment]: ${interaction.assessmentJson}")
                }
                is Interaction.QuickPhraseUsage -> {
                    sb.appendLine("[Quick Phrase]: ${interaction.sourceText} → ${interaction.translatedText} (${interaction.targetLanguage})")
                }
                is Interaction.Note -> {
                    sb.appendLine("[Note]: ${interaction.text}")
                }
            }
        }

        return sb.toString()
    }

    private suspend fun appendInteraction(interaction: Interaction) {
        val session = _activeSession.value ?: return
        val updated = session.copy(interactions = session.interactions + interaction)
        _activeSession.value = updated
        sessionDao.update(SessionEntity.fromConversationSession(updated))
    }

    private fun resetInactivityTimer() {
        inactivityJob?.cancel()
        inactivityJob = scope.launch {
            delay(inactivityTimeoutMs)
            // Auto-close session after 30 minutes of inactivity
            val session = _activeSession.value ?: return@launch
            val timedOut = session.copy(
                endedAt = System.currentTimeMillis(),
                status = SessionStatus.TIMED_OUT
            )
            _activeSession.value = null
            sessionDao.update(SessionEntity.fromConversationSession(timedOut))
        }
    }

    private fun getCurrentLocation(): GPSCoordinate? {
        return try {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val location: Location? = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            location?.let {
                GPSCoordinate(
                    latitude = it.latitude,
                    longitude = it.longitude,
                    accuracy = it.accuracy,
                    timestamp = it.time
                )
            }
        } catch (e: SecurityException) {
            null // GPS permission not granted
        }
    }

    fun destroy() {
        inactivityJob?.cancel()
        scope.cancel()
    }
}
