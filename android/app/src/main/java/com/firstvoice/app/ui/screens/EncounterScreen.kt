package com.firstvoice.app.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.location.LocationManager
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.firstvoice.app.FirstVoiceApp
import com.firstvoice.app.ui.theme.*
import com.firstvoice.app.util.CameraHelper
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import kotlinx.coroutines.launch
import java.io.File

enum class InteractionType { SPEECH, VISION, QUICK_PHRASE, NOTE }

data class InteractionDisplay(
    val speaker: String,
    val originalText: String,
    val translatedText: String,
    val language: String,
    val confidence: String,
    val type: InteractionType
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun EncounterScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val container = FirstVoiceApp.instance.container
    val coroutineScope = rememberCoroutineScope()

    var isProcessing by remember { mutableStateOf(false) }
    var processingLabel by remember { mutableStateOf("") }
    var showSurvivorView by remember { mutableStateOf(false) }
    var survivorLanguage by remember { mutableStateOf("Detecting...") }
    var responderLanguage by remember { mutableStateOf("English") }
    var lastTranslation by remember { mutableStateOf("") }
    val interactions = remember { mutableStateListOf<InteractionDisplay>() }
    val listState = rememberLazyListState()
    var showEndDialog by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("") }
    var photoFile by remember { mutableStateOf<File?>(null) }

    val permissionsState = rememberMultiplePermissionsState(
        listOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA, Manifest.permission.ACCESS_FINE_LOCATION)
    )

    LaunchedEffect(Unit) {
        if (!permissionsState.allPermissionsGranted) permissionsState.launchMultiplePermissionRequest()
    }

    // Common handler for speech result from either mic
    fun processSpeech(speaker: String, spokenText: String) {
        isProcessing = true
        processingLabel = "Translating..."
        coroutineScope.launch {
            try {
                Log.d("FirstVoice", "$speaker speech: '$spokenText'")
                // Single Ollama call: detect language + translate together
                val targetLang = if (speaker == "Survivor") responderLanguage
                    else if (survivorLanguage != "Detecting...") survivorLanguage else "English"
                val result = container.translationEngine.detectAndTranslate(spokenText, targetLang)
                val detectedLang = result.sourceLang
                Log.d("FirstVoice", "$speaker detected: $detectedLang → ${result.translatedText.take(80)}")
                if (speaker == "Survivor" && detectedLang != "Unknown") survivorLanguage = detectedLang

                interactions.add(InteractionDisplay(speaker, spokenText, result.translatedText, detectedLang, "HIGH", InteractionType.SPEECH))
                lastTranslation = result.translatedText
                statusMessage = ""
                listState.animateScrollToItem(interactions.size - 1)
            } catch (e: Exception) {
                Log.e("FirstVoice", "$speaker failed", e)
                interactions.add(InteractionDisplay(speaker, spokenText, "[Failed: ${e.message?.take(60)}]", "Unknown", "LOW", InteractionType.SPEECH))
                statusMessage = "⚠️ AI error"
            } finally {
                isProcessing = false
                processingLabel = ""
            }
        }
    }

    // Push-to-talk state: tap to start recording, tap again to stop
    var isRecording by remember { mutableStateOf(false) }
    var recordingForSurvivor by remember { mutableStateOf(true) }

    fun launchMic(isForSurvivor: Boolean) {
        if (isRecording) {
            // Stop recording
            container.audioRecorder.stopRecording()
            return
        }
        // Start recording
        isRecording = true
        recordingForSurvivor = isForSurvivor
        isProcessing = true
        processingLabel = if (isForSurvivor) "🎤 Recording... tap again to stop" else "🎤 Recording... tap again to stop"
        coroutineScope.launch {
            try {
                val result = container.audioRecorder.record(15) // max 15s, stops early on tap
                isRecording = false
                when (result) {
                    is com.firstvoice.app.util.AudioRecorder.RecordingResult.Success -> {
                        processingLabel = "Transcribing with Gemma 4..."
                        val transcription = container.speechEngine.transcribe(result.audioBase64)
                        val spokenText = transcription.text
                        if (spokenText.isNotBlank() && !spokenText.startsWith("[")) {
                            processSpeech(if (isForSurvivor) "Survivor" else "Responder", spokenText)
                        } else {
                            statusMessage = "⚠️ Could not transcribe audio. Try again."
                            isProcessing = false; processingLabel = ""
                        }
                    }
                    is com.firstvoice.app.util.AudioRecorder.RecordingResult.Error -> {
                        statusMessage = "⚠️ ${result.message}"
                        isProcessing = false; processingLabel = ""
                    }
                }
            } catch (e: Exception) {
                Log.e("FirstVoice", "Gemma audio failed", e)
                statusMessage = "⚠️ Audio error: ${e.message?.take(60)}"
                isRecording = false; isProcessing = false; processingLabel = ""
            }
        }
    }

    // Camera launcher
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && photoFile != null) {
            isProcessing = true
            processingLabel = "Analyzing photo with AI..."
            coroutineScope.launch {
                try {
                    Log.d("FirstVoice", "Photo captured")
                    val base64 = CameraHelper.uriToBase64(context, FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", photoFile!!), 1024)
                    if (base64 != null) {
                        val ctx = interactions.joinToString("\n") { "[${it.speaker}] ${it.originalText}" }
                        val result = container.visionAnalyzer.analyzeDamage(base64, ctx)
                        interactions.add(InteractionDisplay("System", "📸 Photo analyzed",
                            result.summary.ifBlank { "Structural: ${result.structural_severity} — ${result.structural_description}" },
                            "", "", InteractionType.VISION))
                        listState.animateScrollToItem(interactions.size - 1)
                    } else statusMessage = "⚠️ Failed to read photo"
                } catch (e: Exception) {
                    Log.e("FirstVoice", "Photo failed", e)
                    interactions.add(InteractionDisplay("System", "📸 Photo captured", "[Analysis failed: ${e.message?.take(80)}]", "", "", InteractionType.VISION))
                } finally { isProcessing = false; processingLabel = "" }
            }
        }
    }

    if (showSurvivorView) {
        SurvivorViewScreen(text = lastTranslation, language = survivorLanguage, onExit = { showSurvivorView = false })
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Column { Text("Active Encounter", fontSize = 18.sp); Text("Survivor: $survivorLanguage", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) } },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = { IconButton(onClick = { showSurvivorView = true }) { Icon(Icons.Default.Visibility, "Show to Survivor") } }
            )
        },
        bottomBar = {
            BottomAppBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 8.dp) {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(onClick = { launchMic(true) }, enabled = !isProcessing || (isRecording && recordingForSurvivor), modifier = Modifier.size(56.dp)) {
                            Icon(Icons.Default.Mic, "Record Survivor", modifier = Modifier.size(32.dp), tint = if (isRecording && recordingForSurvivor) CriticalRed else SafeBlue)
                        }
                        Text(if (isRecording && recordingForSurvivor) "⏹ Stop" else "Survivor", fontSize = 11.sp)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(onClick = { launchMic(false) }, enabled = !isProcessing || (isRecording && !recordingForSurvivor), modifier = Modifier.size(56.dp)) {
                            Icon(Icons.Default.RecordVoiceOver, "Record Responder", modifier = Modifier.size(32.dp), tint = if (isRecording && !recordingForSurvivor) CriticalRed else LowGreen)
                        }
                        Text(if (isRecording && !recordingForSurvivor) "⏹ Stop" else "Responder", fontSize = 11.sp)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(onClick = { val f = CameraHelper.createTempImageFile(context); photoFile = f; cameraLauncher.launch(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f)) }, enabled = !isProcessing, modifier = Modifier.size(56.dp)) {
                            Icon(Icons.Default.CameraAlt, "Take Photo", modifier = Modifier.size(32.dp))
                        }
                        Text("Photo", fontSize = 11.sp)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(onClick = { showEndDialog = true }, enabled = !isProcessing, modifier = Modifier.size(56.dp)) {
                            Icon(Icons.Default.Done, "End Encounter", modifier = Modifier.size(32.dp), tint = CriticalRed)
                        }
                        Text("End", fontSize = 11.sp, color = CriticalRed)
                    }
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (isProcessing) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(processingLabel, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)
                }
            }
            if (statusMessage.isNotEmpty()) Text(statusMessage, fontSize = 12.sp, color = HighOrange, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            if (interactions.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.RecordVoiceOver, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Tap a button below to start", fontSize = 18.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Record survivor or responder speech,\nor take a photo of the scene", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    }
                }
            } else {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
                    items(interactions) { InteractionBubble(it) }
                }
            }
        }
    }

    if (showEndDialog) {
        AlertDialog(onDismissRequest = { showEndDialog = false }, title = { Text("End Encounter?") },
            text = { Text("This will generate a triage card from the conversation using AI and save it.") },
            confirmButton = {
                Button(onClick = {
                    showEndDialog = false; isProcessing = true; processingLabel = "Generating triage card..."
                    coroutineScope.launch {
                        try {
                            val sc = interactions.joinToString("\n") { i -> when (i.type) {
                                InteractionType.SPEECH -> "[${i.speaker}] (${i.language}): ${i.originalText}\n  → Translated: ${i.translatedText}"
                                InteractionType.VISION -> "[Photo Assessment]: ${i.translatedText}"
                                else -> "[${i.speaker}]: ${i.originalText}"
                            }}
                            val gps = try { val lm = context.getSystemService(android.content.Context.LOCATION_SERVICE) as LocationManager; val loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER) ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER); loc?.let { com.firstvoice.app.data.model.GPSCoordinate(it.latitude, it.longitude, it.accuracy, it.time) } } catch (_: SecurityException) { null }
                            val card = container.triageAgent.generateTriageCard("session-${System.currentTimeMillis()}", container.deviceId, sc, gps)
                            container.database.triageCardDao().insert(com.firstvoice.app.data.local.entity.TriageCardEntity.fromTriageCard(card))
                            Toast.makeText(context, "Triage card: ${card.urgencyLevel}", Toast.LENGTH_LONG).show()
                        } catch (e: Exception) { Toast.makeText(context, "Failed: ${e.message?.take(60)}", Toast.LENGTH_LONG).show()
                        } finally { isProcessing = false; processingLabel = ""; onBack() }
                    }
                }, colors = ButtonDefaults.buttonColors(containerColor = CriticalRed)) { Text("End & Generate Triage Card") }
            },
            dismissButton = { OutlinedButton(onClick = { showEndDialog = false }) { Text("Continue Encounter") } }
        )
    }
}

@Composable
fun InteractionBubble(interaction: InteractionDisplay) {
    val context = LocalContext.current
    val isResponder = interaction.speaker == "Responder"
    val isSystem = interaction.speaker == "System"
    val bubbleColor = when { isSystem -> MaterialTheme.colorScheme.tertiaryContainer; isResponder -> SafeBlue.copy(alpha = 0.1f); else -> MaterialTheme.colorScheme.surfaceVariant }
    val alignment = when { isSystem -> Alignment.CenterHorizontally; isResponder -> Alignment.End; else -> Alignment.Start }
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = alignment) {
        Text(when { isSystem -> "📸 Vision Assessment"; isResponder -> "🗣️ Responder (${interaction.language})"; else -> "👤 Survivor (${interaction.language})" },
            fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
        Card(modifier = Modifier.widthIn(max = 320.dp), colors = CardDefaults.cardColors(containerColor = bubbleColor)) {
            Column(modifier = Modifier.padding(12.dp)) {
                // Original text with ear icon (hear what was said)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(interaction.originalText, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    if (interaction.type == InteractionType.SPEECH) {
                        IconButton(onClick = { speakText(context, interaction.originalText, interaction.language) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Hearing, "Hear original", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                        }
                    }
                }
                // Translated text with speaker icon (play for other party)
                if (interaction.translatedText.isNotEmpty() && interaction.type == InteractionType.SPEECH) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (interaction.translatedText != interaction.originalText) interaction.translatedText else interaction.originalText,
                            fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = {
                            val textToSpeak = if (interaction.translatedText != interaction.originalText) interaction.translatedText else interaction.originalText
                            val lang = if (isResponder) "survivor" else "English"
                            speakText(context, textToSpeak, lang)
                        }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.VolumeUp, "Speak for other", modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                } else if (interaction.translatedText.isNotEmpty() && interaction.translatedText != interaction.originalText) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                    Text(interaction.translatedText, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                }
                if (interaction.confidence.isNotEmpty()) { Spacer(modifier = Modifier.height(4.dp)); Text("Confidence: ${interaction.confidence}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}

private var ttsInstance: android.speech.tts.TextToSpeech? = null

private fun speakText(context: android.content.Context, text: String, language: String) {
    val locale = when (language.lowercase()) {
        "hindi" -> java.util.Locale("hi", "IN")
        "bengali", "bangla" -> java.util.Locale("bn", "IN")
        "tamil" -> java.util.Locale("ta", "IN")
        "telugu" -> java.util.Locale("te", "IN")
        "marathi" -> java.util.Locale("mr", "IN")
        "gujarati" -> java.util.Locale("gu", "IN")
        "kannada" -> java.util.Locale("kn", "IN")
        "malayalam" -> java.util.Locale("ml", "IN")
        "punjabi" -> java.util.Locale("pa", "IN")
        "urdu" -> java.util.Locale("ur", "IN")
        "spanish" -> java.util.Locale("es", "ES")
        "french" -> java.util.Locale("fr", "FR")
        "arabic" -> java.util.Locale("ar", "SA")
        "german" -> java.util.Locale("de", "DE")
        "japanese" -> java.util.Locale("ja", "JP")
        "korean" -> java.util.Locale("ko", "KR")
        "chinese", "mandarin" -> java.util.Locale("zh", "CN")
        "portuguese" -> java.util.Locale("pt", "BR")
        "russian" -> java.util.Locale("ru", "RU")
        else -> java.util.Locale.ENGLISH
    }
    // Shutdown previous instance
    ttsInstance?.stop()
    ttsInstance?.shutdown()
    ttsInstance = android.speech.tts.TextToSpeech(context) { status ->
        if (status == android.speech.tts.TextToSpeech.SUCCESS) {
            ttsInstance?.language = locale
            ttsInstance?.speak(text, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "fv_${System.currentTimeMillis()}")
        }
    }
}
