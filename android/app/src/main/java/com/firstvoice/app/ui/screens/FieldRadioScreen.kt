package com.firstvoice.app.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.media.*
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.firstvoice.app.FirstVoiceApp
import com.firstvoice.app.data.local.entity.RadioMessageEntity
import com.firstvoice.app.data.local.entity.VoiceClipEntity
import com.firstvoice.app.data.model.RadioMessage
import com.firstvoice.app.data.model.VoiceClip
import com.firstvoice.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.*

// Unified item for the chat feed
sealed class FeedItem(val timestamp: Long) {
    class TextMsg(val msg: RadioMessageEntity) : FeedItem(msg.timestamp)
    class Voice(val clip: VoiceClipEntity) : FeedItem(clip.timestamp)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FieldRadioScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val container = FirstVoiceApp.instance.container
    val scope = rememberCoroutineScope()
    val msgDao = container.database.radioMessageDao()
    val clipDao = container.database.voiceClipDao()

    val messages by msgDao.getAllFlow().collectAsState(initial = emptyList())
    val voiceClips by clipDao.getAllFlow().collectAsState(initial = emptyList())
    val peerCount by container.meshSyncService.peers.collectAsState()
    var inputText by remember { mutableStateOf("") }
    var isRecording by remember { mutableStateOf(false) }
    var playingClipId by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()

    // Mic permission
    val micPermLauncher = rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { _ -> }

    // Merge and sort messages + voice clips by timestamp
    val feed = remember(messages, voiceClips) {
        (messages.map { FeedItem.TextMsg(it) } + voiceClips.map { FeedItem.Voice(it) })
            .sortedBy { it.timestamp }
    }

    LaunchedEffect(Unit) { msgDao.markAllRead() }
    LaunchedEffect(feed.size) {
        if (feed.isNotEmpty()) listState.animateScrollToItem(feed.size - 1)
    }

    val quickStatuses = listOf(
        "🟢 All Clear", "🔴 Need Backup", "🚑 Medical Emergency",
        "🏃 Evacuating Now", "📍 At Location", "👀 Survivor Found"
    )

    fun getGps(): Pair<Double?, Double?> {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            return null to null
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        return loc?.latitude to loc?.longitude
    }

    fun sendMessage(text: String, isQuick: Boolean = false) {
        if (text.isBlank()) return
        val (lat, lon) = getGps()
        val msg = RadioMessage(
            id = "${container.deviceId}-${System.currentTimeMillis()}",
            deviceId = container.deviceId, deviceName = android.os.Build.MODEL,
            latitude = lat, longitude = lon, timestamp = System.currentTimeMillis(),
            message = text, isQuickStatus = isQuick
        )
        scope.launch { msgDao.insert(RadioMessageEntity.from(msg, read = true)) }
        inputText = ""
    }

    // Audio recorder state
    var recorder by remember { mutableStateOf<AudioRecord?>(null) }
    var recordingStart by remember { mutableStateOf(0L) }

    fun startRecording() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return
        isRecording = true
        recordingStart = System.currentTimeMillis()
        scope.launch(Dispatchers.IO) {
            val sampleRate = 8000
            val bufSize = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val rec = AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufSize)
            recorder = rec
            val baos = ByteArrayOutputStream()
            val buf = ByteArray(bufSize)
            rec.startRecording()
            while (isRecording && (System.currentTimeMillis() - recordingStart) < 15_000) {
                val read = rec.read(buf, 0, buf.size)
                if (read > 0) baos.write(buf, 0, read)
            }
            rec.stop()
            rec.release()
            recorder = null

            val durationMs = (System.currentTimeMillis() - recordingStart).toInt()
            val audioBytes = baos.toByteArray()
            if (audioBytes.size > 100) { // Skip if too short
                val (lat, lon) = getGps()
                val clip = VoiceClip(
                    id = "${container.deviceId}-vc-${System.currentTimeMillis()}",
                    deviceId = container.deviceId, deviceName = android.os.Build.MODEL,
                    latitude = lat, longitude = lon, timestamp = System.currentTimeMillis(),
                    durationMs = durationMs,
                    audioBase64 = Base64.encodeToString(audioBytes, Base64.NO_WRAP)
                )
                clipDao.insert(VoiceClipEntity.from(clip, played = true))
            }
            withContext(Dispatchers.Main) { isRecording = false }
        }
    }

    fun stopRecording() { isRecording = false }

    fun playClip(clip: VoiceClipEntity) {
        playingClipId = clip.id
        scope.launch(Dispatchers.IO) {
            try {
                val audioBytes = Base64.decode(clip.audioBase64, Base64.NO_WRAP)
                val sampleRate = 8000
                val bufSize = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
                val track = AudioTrack.Builder()
                    .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                    .setAudioFormat(AudioFormat.Builder().setSampleRate(sampleRate).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                    .setBufferSizeInBytes(bufSize)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
                track.play()
                track.write(audioBytes, 0, audioBytes.size)
                track.stop()
                track.release()
                clipDao.markPlayed(clip.id)
            } catch (e: Exception) {
                android.util.Log.w("FieldRadio", "Playback error: ${e.message}")
            }
            withContext(Dispatchers.Main) { playingClipId = null }
        }
    }

    // Auto-play incoming voice clips
    val autoPlayedIds = remember { mutableSetOf<String>() }
    LaunchedEffect(voiceClips.size) {
        val newIncoming = voiceClips.filter {
            it.deviceId != container.deviceId && !it.played && it.id !in autoPlayedIds
        }
        for (clip in newIncoming) {
            autoPlayedIds.add(clip.id)
            playClip(clip)
            delay(clip.durationMs.toLong() + 500)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("📻 Field Radio", fontWeight = FontWeight.Bold)
                        Text("${peerCount.size} peers nearby", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Quick status chips
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                quickStatuses.take(3).forEach { s ->
                    SuggestionChip(onClick = { sendMessage(s, true) }, label = { Text(s, fontSize = 11.sp) }, modifier = Modifier.weight(1f))
                }
            }
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                quickStatuses.drop(3).forEach { s ->
                    SuggestionChip(onClick = { sendMessage(s, true) }, label = { Text(s, fontSize = 11.sp) }, modifier = Modifier.weight(1f))
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // Feed: messages + voice clips interleaved
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp),
                state = listState, verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(feed) { item ->
                    when (item) {
                        is FeedItem.TextMsg -> RadioBubble(msg = item.msg, isMe = item.msg.deviceId == container.deviceId)
                        is FeedItem.Voice -> VoiceClipBubble(
                            clip = item.clip, isMe = item.clip.deviceId == container.deviceId,
                            isPlaying = playingClipId == item.clip.id, onPlay = { playClip(item.clip) }
                        )
                    }
                }
            }

            // Input bar + PTT button
            HorizontalDivider()
            Row(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                // Push-to-talk button
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(if (isRecording) CriticalRed else SafeBlue)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = {
                                    if (isRecording) {
                                        stopRecording()
                                    } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                        startRecording()
                                    } else {
                                        micPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                        contentDescription = "Push to talk",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }
                if (isRecording) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("🔴 Recording...", color = CriticalRed, fontWeight = FontWeight.Bold)
                } else {
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedTextField(
                        value = inputText, onValueChange = { inputText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Message...") }, maxLines = 2,
                        shape = RoundedCornerShape(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    FilledIconButton(onClick = { sendMessage(inputText) }, enabled = inputText.isNotBlank()) {
                        Icon(Icons.AutoMirrored.Filled.Send, "Send")
                    }
                }
            }
        }
    }
}

@Composable
fun VoiceClipBubble(clip: VoiceClipEntity, isMe: Boolean, isPlaying: Boolean, onPlay: () -> Unit) {
    val bgColor = if (isMe) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val align = if (isMe) Arrangement.End else Arrangement.Start

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = align) {
        Row(
            modifier = Modifier
                .widthIn(max = 260.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(bgColor)
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onPlay, enabled = !isPlaying) {
                Icon(
                    if (isPlaying) Icons.Default.GraphicEq else Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = if (isPlaying) CriticalRed else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                if (!isMe) {
                    Text(clip.deviceName, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
                Text("🎙 Voice ${clip.durationMs / 1000}s", fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    if (clip.latitude != null) {
                        Text("📍${String.format("%.3f", clip.latitude)}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(formatRadioTime(clip.timestamp), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun RadioBubble(msg: RadioMessageEntity, isMe: Boolean) {
    val bgColor = if (isMe) MaterialTheme.colorScheme.primaryContainer
    else if (msg.isQuickStatus) MaterialTheme.colorScheme.tertiaryContainer
    else MaterialTheme.colorScheme.surfaceVariant
    val align = if (isMe) Arrangement.End else Arrangement.Start

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = align) {
        Column(
            modifier = Modifier.widthIn(max = 300.dp).clip(RoundedCornerShape(12.dp)).background(bgColor).padding(10.dp)
        ) {
            if (!isMe) {
                Text(msg.deviceName, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
            Text(msg.message, fontSize = if (msg.isQuickStatus) 16.sp else 14.sp, fontWeight = if (msg.isQuickStatus) FontWeight.Bold else FontWeight.Normal)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                if (msg.latitude != null && msg.longitude != null) {
                    Text("📍${String.format("%.3f", msg.latitude)},${String.format("%.3f", msg.longitude)}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(formatRadioTime(msg.timestamp), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun formatRadioTime(ts: Long): String {
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    return sdf.format(Date(ts))
}
