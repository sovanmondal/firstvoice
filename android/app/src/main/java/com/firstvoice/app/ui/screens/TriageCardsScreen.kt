package com.firstvoice.app.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.firstvoice.app.FirstVoiceApp
import com.firstvoice.app.data.model.*
import com.firstvoice.app.ui.theme.*
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TriageCardsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val container = FirstVoiceApp.instance.container
    val scope = rememberCoroutineScope()
    val dbCards by container.database.triageCardDao().getAllFlow()
        .collectAsState(initial = emptyList())
    val cards = dbCards.map { it.toTriageCard() }
    var selectedCard by remember { mutableStateOf<TriageCard?>(null) }
    var showExportDialog by remember { mutableStateOf(false) }
    var cardToDelete by remember { mutableStateOf<TriageCard?>(null) }
    var showRecycleBin by remember { mutableStateOf(false) }
    val deletedCards by container.database.triageCardDao().getDeletedFlow()
        .collectAsState(initial = emptyList())

    // Delete confirmation dialog
    cardToDelete?.let { card ->
        AlertDialog(
            onDismissRequest = { cardToDelete = null },
            icon = { Icon(Icons.Default.DeleteForever, contentDescription = null, tint = CriticalRed) },
            title = { Text("Delete Triage Card?") },
            text = { Text("This will delete the card and sync the deletion to all connected devices.") },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            container.database.triageCardDao().softDelete(card.id)
                        }
                        cardToDelete = null
                        selectedCard = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CriticalRed)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { cardToDelete = null }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Triage Cards (${cards.size})") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (deletedCards.isNotEmpty()) {
                        BadgedBox(badge = { Badge { Text("${deletedCards.size}") } }) {
                            IconButton(onClick = { showRecycleBin = true }) {
                                Icon(Icons.Default.DeleteSweep, "Recycle Bin")
                            }
                        }
                    } else {
                        IconButton(onClick = { showRecycleBin = true }) {
                            Icon(Icons.Default.DeleteSweep, "Recycle Bin")
                        }
                    }
                    if (cards.isNotEmpty()) {
                        IconButton(onClick = { showExportDialog = true }) {
                            Icon(Icons.Default.FileDownload, "Export")
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (cards.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Assignment, contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("No triage cards yet", fontSize = 18.sp)
                    Text("Start a new encounter to create triage cards", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                items(cards.sortedByDescending { it.timestamp }) { card ->
                    TriageCardDetailItem(
                        card = card,
                        onClick = { selectedCard = card },
                        onDelete = { cardToDelete = card }
                    )
                }
            }
        }
    }

    selectedCard?.let { card ->
        TriageCardDetailSheet(
            card = card,
            onDismiss = { selectedCard = null },
            onDelete = { cardToDelete = card }
        )
    }

    if (showRecycleBin) {
        RecycleBinSheet(
            deletedCards = deletedCards.map { it.toTriageCard() },
            onDismiss = { showRecycleBin = false },
            onRestore = { card ->
                scope.launch { container.database.triageCardDao().restore(card.id) }
            }
        )
    }

    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text("Export ${cards.size} Triage Cards") },
            confirmButton = {
                Button(onClick = {
                    showExportDialog = false
                    val json = Json { prettyPrint = true }
                    val text = json.encodeToString(cards)
                    shareText(context, "triage_cards.json", text)
                }) { Text("Export JSON") }
            },
            dismissButton = {
                OutlinedButton(onClick = {
                    showExportDialog = false
                    val csv = buildCsv(cards)
                    shareText(context, "triage_cards.csv", csv)
                }) { Text("Export CSV") }
            }
        )
    }
}

fun formatCardTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm:ss z", Locale.getDefault())
    sdf.timeZone = TimeZone.getDefault()
    return sdf.format(Date(timestamp))
}

fun formatCardTimestampShort(timestamp: Long): String {
    val sdf = SimpleDateFormat("dd MMM, HH:mm z", Locale.getDefault())
    sdf.timeZone = TimeZone.getDefault()
    return sdf.format(Date(timestamp))
}

private fun shareText(context: android.content.Context, filename: String, content: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, filename)
        putExtra(Intent.EXTRA_TEXT, content)
    }
    context.startActivity(Intent.createChooser(intent, "Export $filename"))
}

private fun buildCsv(cards: List<TriageCard>): String {
    val sb = StringBuilder()
    sb.appendLine("id,urgency,people,language,lat,lon,needs,summary,timestamp")
    for (c in cards) {
        val lat = c.gpsCoordinates?.latitude ?: ""
        val lon = c.gpsCoordinates?.longitude ?: ""
        val needs = c.needsCategories.joinToString(";") { it.displayName() }
        val summary = c.assessmentSummary.replace("\"", "\"\"")
        sb.appendLine("${c.id},${c.urgencyLevel},${c.peopleCount ?: ""},${c.detectedLanguage},$lat,$lon,$needs,\"$summary\",${c.timestamp}")
    }
    return sb.toString()
}

@Composable
fun TriageCardDetailItem(card: TriageCard, onClick: () -> Unit, onDelete: () -> Unit) {
    val urgencyColor = when (card.urgencyLevel) {
        UrgencyLevel.CRITICAL -> CriticalRed
        UrgencyLevel.HIGH -> HighOrange
        UrgencyLevel.MEDIUM -> MediumYellow
        UrgencyLevel.LOW -> LowGreen
    }

    Card(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .width(8.dp)
                    .height(IntrinsicSize.Max)
                    .background(urgencyColor)
            )
            Column(modifier = Modifier.weight(1f).padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(card.urgencyLevel.name, fontWeight = FontWeight.Bold, color = urgencyColor, fontSize = 16.sp)
                        if (card.syncStatus.meshSynced) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(Icons.Default.SyncAlt, "Synced", modifier = Modifier.size(16.dp), tint = LowGreen)
                        }
                    }
                    Text("👤 ${card.peopleCount ?: "?"}  🌐 ${card.detectedLanguage}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(card.assessmentSummary, fontSize = 14.sp, maxLines = 3)
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    card.needsCategories.forEach { need ->
                        SuggestionChip(onClick = {}, label = { Text(need.displayName(), fontSize = 11.sp) })
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "📍 ${if (card.gpsCoordinates != null)
                                "${String.format("%.4f", card.gpsCoordinates.latitude)}, ${String.format("%.4f", card.gpsCoordinates.longitude)}"
                            else "Location unknown"}",
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "🕐 ${formatCardTimestamp(card.timestamp)}",
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Delete, "Delete", tint = CriticalRed, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TriageCardDetailSheet(card: TriageCard, onDismiss: () -> Unit, onDelete: () -> Unit) {
    val urgencyColor = when (card.urgencyLevel) {
        UrgencyLevel.CRITICAL -> CriticalRed
        UrgencyLevel.HIGH -> HighOrange
        UrgencyLevel.MEDIUM -> MediumYellow
        UrgencyLevel.LOW -> LowGreen
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Triage Card", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(card.urgencyLevel.name, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = urgencyColor)
            }
            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))
            DetailRow("ID", card.id.take(8) + "...")
            DetailRow("Device", card.deviceId)
            DetailRow("Registered", formatCardTimestamp(card.timestamp))
            DetailRow("Timezone", TimeZone.getDefault().getDisplayName(false, TimeZone.SHORT))
            DetailRow("People", "${card.peopleCount ?: "Unknown"}")
            DetailRow("Language", card.detectedLanguage)
            DetailRow("Location",
                if (card.gpsCoordinates != null)
                    "${String.format("%.4f", card.gpsCoordinates.latitude)}, ${String.format("%.4f", card.gpsCoordinates.longitude)}"
                else "Unknown"
            )
            DetailRow("Needs", card.needsCategories.joinToString { it.displayName() })
            DetailRow("Mesh Synced", if (card.syncStatus.meshSynced) "✅ Yes" else "❌ No")
            Spacer(modifier = Modifier.height(12.dp))
            Text("Assessment", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(card.assessmentSummary, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(24.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Close") }
                Button(
                    onClick = onDelete,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = CriticalRed)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Delete")
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecycleBinSheet(
    deletedCards: List<TriageCard>,
    onDismiss: () -> Unit,
    onRestore: (TriageCard) -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("🗑️ Recycle Bin", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("${deletedCards.size} items", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("Deleted cards sync across all devices. Restore to bring them back.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(16.dp))

            if (deletedCards.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                    Text("Recycle bin is empty", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(deletedCards) { card ->
                        val urgencyColor = when (card.urgencyLevel) {
                            UrgencyLevel.CRITICAL -> CriticalRed
                            UrgencyLevel.HIGH -> HighOrange
                            UrgencyLevel.MEDIUM -> MediumYellow
                            UrgencyLevel.LOW -> LowGreen
                        }
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Box(modifier = Modifier.width(6.dp).height(IntrinsicSize.Max).background(urgencyColor))
                                Column(modifier = Modifier.weight(1f).padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(card.urgencyLevel.name, fontWeight = FontWeight.Bold, color = urgencyColor, fontSize = 14.sp)
                                        Text("👤 ${card.peopleCount ?: "?"} · ${card.detectedLanguage}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(card.assessmentSummary, fontSize = 13.sp, maxLines = 2)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    card.deletedAt?.let {
                                        Text("Deleted: ${formatCardTimestampShort(it)}", fontSize = 11.sp, color = CriticalRed)
                                    }
                                }
                                IconButton(
                                    onClick = { onRestore(card) },
                                    modifier = Modifier.align(Alignment.CenterVertically)
                                ) {
                                    Icon(Icons.Default.RestoreFromTrash, "Restore", tint = LowGreen, modifier = Modifier.size(24.dp))
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Close") }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
