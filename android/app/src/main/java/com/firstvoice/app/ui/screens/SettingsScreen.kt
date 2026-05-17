package com.firstvoice.app.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.firstvoice.app.FirstVoiceApp
import com.firstvoice.app.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val container = FirstVoiceApp.instance.container
    val scope = rememberCoroutineScope()
    val prefs = context.getSharedPreferences("firstvoice", Context.MODE_PRIVATE)

    var serverUrl by remember { mutableStateOf(container.ollamaClient.baseUrl) }
    var ollamaStatus by remember { mutableStateOf("Not tested") }
    var isTesting by remember { mutableStateOf(false) }
    var cardCount by remember { mutableStateOf(0) }
    var showClearDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        cardCount = container.database.triageCardDao().getCount()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Server URL config
            Text("Gemma 4 Server (Ollama)", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(
                "Enter your laptop/server IP running Ollama.\nPhone and server must be on the same WiFi.",
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = serverUrl,
                onValueChange = { serverUrl = it },
                label = { Text("Server URL") },
                placeholder = { Text("http://192.168.1.100:11434") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        // Save and apply
                        container.ollamaClient.baseUrl = serverUrl
                        prefs.edit().putString("ollama_url", serverUrl).apply()
                        Toast.makeText(context, "Server URL saved", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("Save") }
                OutlinedButton(
                    onClick = {
                        isTesting = true
                        ollamaStatus = "Testing..."
                        container.ollamaClient.baseUrl = serverUrl
                        prefs.edit().putString("ollama_url", serverUrl).apply()
                        scope.launch {
                            ollamaStatus = if (container.ollamaClient.isAvailable()) "✅ Connected!" else "❌ Cannot reach server"
                            isTesting = false
                        }
                    },
                    enabled = !isTesting,
                    modifier = Modifier.weight(1f)
                ) { Text("Test Connection") }
            }
            if (isTesting) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text(ollamaStatus, fontSize = 14.sp, color = if (ollamaStatus.startsWith("✅")) LowGreen else if (ollamaStatus.startsWith("❌")) CriticalRed else MaterialTheme.colorScheme.onSurfaceVariant)

            HorizontalDivider()

            // Quick setup help
            Text("Quick Setup", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(
                "1. Install Ollama on your laptop: ollama.com\n" +
                "2. Run: OLLAMA_HOST=0.0.0.0 ollama serve\n" +
                "3. Pull model: ollama pull gemma3:4b\n" +
                "4. Find laptop IP: ifconfig | grep inet\n" +
                "5. Enter http://<laptop-ip>:11434 above",
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 20.sp
            )

            HorizontalDivider()

            DetailRow("Device ID", container.deviceId)
            DetailRow("Triage Cards", "$cardCount")

            Spacer(modifier = Modifier.weight(1f))

            OutlinedButton(
                onClick = { showClearDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) { Text("Clear All Data ($cardCount cards)") }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear All Data?") },
            text = { Text("This will delete all $cardCount triage cards. Cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        showClearDialog = false
                        scope.launch {
                            container.database.triageCardDao().deleteAll()
                            cardCount = 0
                            Toast.makeText(context, "All data cleared", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete All") }
            },
            dismissButton = { OutlinedButton(onClick = { showClearDialog = false }) { Text("Cancel") } }
        )
    }
}
