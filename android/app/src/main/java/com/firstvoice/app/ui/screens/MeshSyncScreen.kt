package com.firstvoice.app.ui.screens

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
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.firstvoice.app.sync.MeshSyncService
import com.firstvoice.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeshSyncScreen(
    onBack: () -> Unit,
    peers: List<MeshSyncService.PeerDevice> = emptyList(),
    syncState: MeshSyncService.SyncState = MeshSyncService.SyncState(),
    isDiscovering: Boolean = false,
    onStartDiscovery: () -> Unit = {},
    onStopDiscovery: () -> Unit = {},
    onConnectPeer: (MeshSyncService.PeerDevice) -> Unit = {}
) {
    val context = LocalContext.current

    val requiredPermissions = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }
    }.toTypedArray()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) {
            onStartDiscovery()
        }
    }

    fun startWithPermissions() {
        val allGranted = requiredPermissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        if (allGranted) onStartDiscovery() else permissionLauncher.launch(requiredPermissions)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mesh Sync") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Sync status card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (syncState.isSyncing) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Sync Status", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        Column {
                            Text("Peers", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${syncState.connectedPeerCount}", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        }
                        Column {
                            Text("Sent", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${syncState.cardsSent}", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        }
                        Column {
                            Text("Received", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${syncState.cardsReceived}", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    if (syncState.lastSyncTimestamp != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Last sync: ${formatTimestamp(syncState.lastSyncTimestamp)}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (syncState.error != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "⚠️ ${syncState.error}",
                            fontSize = 12.sp,
                            color = CriticalRed
                        )
                    }
                    if (syncState.isSyncing) {
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            }

            // Discovery controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { if (isDiscovering) onStopDiscovery() else startWithPermissions() },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isDiscovering) CriticalRed else SafeBlue
                    )
                ) {
                    Icon(
                        if (isDiscovering) Icons.Default.Sync else Icons.Default.SyncAlt,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isDiscovering) "Syncing..." else "Sync to All Devices")
                }
            }

            if (isDiscovering) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            // Discovered peers list
            Text("Nearby Devices", fontWeight = FontWeight.Bold, fontSize = 16.sp)

            if (peers.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.WifiFind,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            if (isDiscovering) "Scanning for nearby FirstVoice devices..."
                            else "Tap 'Scan for Devices' to find nearby responders",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(peers) { peer ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { onConnectPeer(peer) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(peer.deviceName, fontWeight = FontWeight.Medium)
                                    Text(
                                        peer.deviceAddress,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Button(onClick = { onConnectPeer(peer) }) {
                                    Text("Sync")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    return when {
        diff < 60_000 -> "Just now"
        diff < 3600_000 -> "${diff / 60_000}m ago"
        else -> "${diff / 3600_000}h ago"
    }
}
