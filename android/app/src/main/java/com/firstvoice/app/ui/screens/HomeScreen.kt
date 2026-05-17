package com.firstvoice.app.ui.screens

import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.firstvoice.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNewEncounter: () -> Unit,
    onViewTriageCards: () -> Unit,
    onOpenDashboard: () -> Unit,
    onQuickPhrases: () -> Unit,
    onMeshSync: () -> Unit = {},
    onFieldRadio: () -> Unit = {},
    onSettings: () -> Unit
) {
    val context = LocalContext.current
    val container = com.firstvoice.app.FirstVoiceApp.instance.container
    val syncState by container.meshSyncService.syncState.collectAsState()
    val unreadRadio by container.database.radioMessageDao().getUnreadCountFlow().collectAsState(initial = 0)

    // Check Bluetooth status
    val btAdapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
    var btEnabled by remember { mutableStateOf(btAdapter?.isEnabled == true) }
    var showBtDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        btEnabled = btAdapter?.isEnabled == true
        if (!btEnabled) showBtDialog = true
    }

    if (showBtDialog && !btEnabled) {
        AlertDialog(
            onDismissRequest = { showBtDialog = false },
            icon = { Icon(Icons.Default.Bluetooth, contentDescription = null, modifier = Modifier.size(32.dp)) },
            title = { Text("Bluetooth Required for Mesh Sync") },
            text = { Text("FirstVoice needs Bluetooth to sync triage cards with nearby devices. Please enable Bluetooth.") },
            confirmButton = {
                Button(onClick = {
                    showBtDialog = false
                    context.startActivity(Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                        flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                    })
                }) { Text("Enable Bluetooth") }
            },
            dismissButton = {
                TextButton(onClick = { showBtDialog = false }) { Text("Later") }
            }
        )
    }

    val syncPermissions = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_SCAN)
            add(Manifest.permission.BLUETOOTH_ADVERTISE)
            add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }
    }.toTypedArray()

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) container.meshSyncService.broadcastSync()
    }

    // Auto-request permissions on first launch
    val permRequested = remember { mutableStateOf(false) }
    val autoPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }
    LaunchedEffect(Unit) {
        if (!permRequested.value) {
            permRequested.value = true
            val needed = syncPermissions.any {
                ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
            }
            if (needed) autoPermLauncher.launch(syncPermissions)
        }
    }

    fun syncWithPermissions() {
        val granted = syncPermissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        if (granted) container.meshSyncService.broadcastSync() else permLauncher.launch(syncPermissions)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "FirstVoice",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Status bar
            val batteryText = remember { mutableStateOf("--") }
            val peersText = remember { mutableStateOf("0") }
            val modelStatus = remember { mutableStateOf("⏳ Checking...") }
            LaunchedEffect(Unit) {
                val c = com.firstvoice.app.FirstVoiceApp.instance.container
                modelStatus.value = if (c.ollamaClient.isAvailable()) "🟢 AI: Ready" else "🔴 AI: Offline"
            }
            LaunchedEffect(Unit) {
                try {
                    val c = com.firstvoice.app.FirstVoiceApp.instance.container
                    c.batteryMonitor.batteryLevel.collect { batteryText.value = "$it" }
                } catch (_: Exception) {}
            }
            LaunchedEffect(Unit) {
                try {
                    val c = com.firstvoice.app.FirstVoiceApp.instance.container
                    c.meshSyncService.peers.collect { peersText.value = "${it.size}" }
                } catch (_: Exception) {}
            }

            Card(
                modifier = Modifier.fillMaxWidth().clickable { onMeshSync() },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(modelStatus.value, fontSize = 14.sp)
                    Text("📡 Peers: ${peersText.value}", fontSize = 14.sp)
                    Text("🔋 ${batteryText.value}%", fontSize = 14.sp)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Main 4-button grid (crisis-optimized, large touch targets)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                CrisisButton(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.RecordVoiceOver,
                    label = "New\nEncounter",
                    containerColor = SafeBlue,
                    onClick = onNewEncounter
                )
                CrisisButton(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.ChatBubble,
                    label = "Quick\nPhrases",
                    containerColor = LowGreen,
                    onClick = onQuickPhrases
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                CrisisButton(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Assignment,
                    label = "Triage\nCards",
                    containerColor = HighOrange,
                    onClick = onViewTriageCards
                )
                CrisisButton(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Map,
                    label = "Dashboard\n& Map",
                    containerColor = MaterialTheme.colorScheme.tertiary,
                    onClick = onOpenDashboard
                )
            }

            // Field Radio button with unread badge
            Button(
                onClick = onFieldRadio,
                modifier = Modifier.fillMaxWidth().height(64.dp),
                colors = ButtonDefaults.buttonColors(containerColor = HighOrange)
            ) {
                Icon(Icons.Default.Radio, contentDescription = null, modifier = Modifier.size(28.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text("📻 Field Radio", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                if (unreadRadio > 0) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Badge { Text("$unreadRadio") }
                }
            }

            // One-tap Sync button
            Button(
                onClick = { syncWithPermissions() },
                modifier = Modifier.fillMaxWidth().height(64.dp),
                enabled = !syncState.isSyncing,
                colors = ButtonDefaults.buttonColors(containerColor = SafeBlue)
            ) {
                if (syncState.isSyncing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Syncing...", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                } else {
                    Icon(Icons.Default.SyncAlt, contentDescription = null, modifier = Modifier.size(28.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Sync to All Devices", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
            if (syncState.lastSyncTimestamp != null) {
                Text(
                    "Last sync: ${syncState.cardsSent} sent, ${syncState.cardsReceived} received",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            syncState.error?.let {
                Text("⚠️ $it", fontSize = 12.sp, color = CriticalRed)
            }

            Spacer(modifier = Modifier.weight(1f))

            // Footer info
            Text(
                text = "Powered by Gemma 4 · Fully Offline · 140+ Languages",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun CrisisButton(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    containerColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .height(140.dp),
        colors = ButtonDefaults.buttonColors(containerColor = containerColor),
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                icon,
                contentDescription = label,
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = label,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )
        }
    }
}
