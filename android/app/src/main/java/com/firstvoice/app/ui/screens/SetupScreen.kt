package com.firstvoice.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.firstvoice.app.FirstVoiceApp
import com.firstvoice.app.util.TilePreloader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * First-launch setup screen that preloads offline map tiles.
 * Shows progress while downloading zoom 0-3 tiles (~85 tiles, ~2 MB).
 */
@Composable
fun SetupScreen(onSetupComplete: () -> Unit) {
    val container = FirstVoiceApp.instance.container
    val scope = rememberCoroutineScope()

    var progress by remember { mutableStateOf<TilePreloader.PreloadProgress?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // Check if already preloaded
    LaunchedEffect(Unit) {
        if (container.tilePreloader.isPreloaded()) {
            onSetupComplete()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "🌍 FirstVoice",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Crisis Communication Agent",
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(48.dp))

        if (isLoading) {
            val p = progress
            if (p != null) {
                Text(
                    "Downloading offline map tiles...",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                LinearProgressIndicator(
                    progress = { p.downloadedTiles.toFloat() / p.totalTiles.coerceAtLeast(1) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "${p.downloadedTiles} / ${p.totalTiles} tiles (zoom ${p.currentZoom})",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (p.failedTiles > 0) {
                    Text(
                        "${p.failedTiles} failed — will retry later",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        } else {
            Text(
                "FirstVoice needs to download a small set of map tiles (~2 MB) for offline use.",
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    isLoading = true
                    error = null
                    scope.launch {
                        try {
                            container.tilePreloader.preloadTiles(maxZoom = 3) { p ->
                                progress = p
                            }
                            withContext(Dispatchers.Main) {
                                onSetupComplete()
                            }
                        } catch (e: Exception) {
                            error = e.message
                            isLoading = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Download Map Tiles & Continue")
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = { onSetupComplete() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Skip — Use Online Maps")
            }

            error?.let {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "Error: $it",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
