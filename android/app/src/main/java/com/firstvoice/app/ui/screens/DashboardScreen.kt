package com.firstvoice.app.ui.screens

import android.graphics.Color as AndroidColor
import android.graphics.drawable.GradientDrawable
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
import androidx.compose.ui.viewinterop.AndroidView
import com.firstvoice.app.data.model.TriageCard
import com.firstvoice.app.data.model.UrgencyLevel
import com.firstvoice.app.ui.theme.*
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

// OpenTopoMap for terrain/elevation view
private val TOPO_TILE_SOURCE = XYTileSource(
    "OpenTopoMap", 0, 17, 256, ".png",
    arrayOf("https://a.tile.opentopomap.org/", "https://b.tile.opentopomap.org/", "https://c.tile.opentopomap.org/"),
    "© OpenTopoMap contributors"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var showMap by remember { mutableStateOf(true) }
    var selectedUrgency by remember { mutableStateOf<UrgencyLevel?>(null) }
    var useTerrain by remember { mutableStateOf(false) }
    var isOnline by remember { mutableStateOf(false) }
    var selectedCardId by remember { mutableStateOf<String?>(null) }

    val container = com.firstvoice.app.FirstVoiceApp.instance.container
    val dbCards by container.database.triageCardDao().getAllFlow().collectAsState(initial = emptyList())
    val allCards = dbCards.map { it.toTriageCard() }
    val filteredCards = remember(selectedUrgency, allCards) {
        if (selectedUrgency == null) allCards else allCards.filter { it.urgencyLevel == selectedUrgency }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Situational Awareness") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    // Online/Offline toggle
                    IconButton(onClick = { isOnline = !isOnline }) {
                        Icon(
                            if (isOnline) Icons.Default.Cloud else Icons.Default.CloudOff,
                            contentDescription = if (isOnline) "Online" else "Offline",
                            tint = if (isOnline) LowGreen else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    // Terrain toggle
                    IconButton(onClick = { useTerrain = !useTerrain }) {
                        Icon(
                            Icons.Default.Terrain,
                            contentDescription = "Terrain",
                            tint = if (useTerrain) SafeBlue else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    // Map/List toggle
                    IconButton(onClick = { showMap = !showMap }) {
                        Icon(if (showMap) Icons.Default.ViewList else Icons.Default.Map, "Toggle view")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Online mode banner
            if (isOnline) {
                Surface(color = LowGreen, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "  ☁️ Online Mode — Live map tiles enabled",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(4.dp)
                    )
                }
            }

            // Filter bar
            FilterBar(selectedUrgency = selectedUrgency, onUrgencySelected = { selectedUrgency = it }, totalCount = filteredCards.size)

            if (showMap) {
                MapViewComposable(
                    cards = filteredCards,
                    useTerrain = useTerrain,
                    isOnline = isOnline,
                    selectedCardId = selectedCardId,
                    modifier = Modifier.fillMaxWidth().weight(1f)
                )
            } else {
                TimelineView(
                    cards = filteredCards,
                    selectedCardId = selectedCardId,
                    onCardTap = { card ->
                        selectedCardId = card.id
                        showMap = true // Switch to map to show highlight
                    },
                    modifier = Modifier.fillMaxWidth().weight(1f)
                )
            }
        }
    }
}

@Composable
fun FilterBar(selectedUrgency: UrgencyLevel?, onUrgencySelected: (UrgencyLevel?) -> Unit, totalCount: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilterChip(selected = selectedUrgency == null, onClick = { onUrgencySelected(null) }, label = { Text("All ($totalCount)") })
        FilterChip(selected = selectedUrgency == UrgencyLevel.CRITICAL, onClick = { onUrgencySelected(if (selectedUrgency == UrgencyLevel.CRITICAL) null else UrgencyLevel.CRITICAL) }, label = { Text("🔴") })
        FilterChip(selected = selectedUrgency == UrgencyLevel.HIGH, onClick = { onUrgencySelected(if (selectedUrgency == UrgencyLevel.HIGH) null else UrgencyLevel.HIGH) }, label = { Text("🟠") })
        FilterChip(selected = selectedUrgency == UrgencyLevel.MEDIUM, onClick = { onUrgencySelected(if (selectedUrgency == UrgencyLevel.MEDIUM) null else UrgencyLevel.MEDIUM) }, label = { Text("🟡") })
        FilterChip(selected = selectedUrgency == UrgencyLevel.LOW, onClick = { onUrgencySelected(if (selectedUrgency == UrgencyLevel.LOW) null else UrgencyLevel.LOW) }, label = { Text("🟢") })
    }
}

@Composable
fun MapViewComposable(cards: List<TriageCard>, useTerrain: Boolean, isOnline: Boolean, selectedCardId: String? = null, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var hasZoomed by remember { mutableStateOf(false) }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            Configuration.getInstance().apply {
                userAgentValue = "FirstVoice/1.0"
                osmdroidBasePath = java.io.File(ctx.filesDir, "osmdroid")
                osmdroidTileCache = java.io.File(ctx.filesDir, "osmdroid/tiles")
            }
            MapView(ctx).apply {
                setMultiTouchControls(true)
                minZoomLevel = 2.0
                maxZoomLevel = 19.0
                controller.setZoom(3.0)
                controller.setCenter(GeoPoint(20.0, 78.0)) // India center
            }
        },
        update = { mapView ->
            // Switch tile source
            val tileSource = if (useTerrain && isOnline) TOPO_TILE_SOURCE
                else if (isOnline) TileSourceFactory.MAPNIK
                else TileSourceFactory.MAPNIK // Offline uses cached MAPNIK tiles
            if (mapView.tileProvider.tileSource != tileSource) {
                mapView.setTileSource(tileSource)
            }

            // Clear and add markers
            mapView.overlays.removeAll { it is Marker }
            val geoCards = cards.filter { it.gpsCoordinates != null }

            for (card in geoCards) {
                val gps = card.gpsCoordinates!!
                val marker = Marker(mapView).apply {
                    position = GeoPoint(gps.latitude, gps.longitude)
                    title = "${card.urgencyLevel.name} — ${card.detectedLanguage}"
                    snippet = "${card.assessmentSummary.take(80)}\n👤 ${card.peopleCount ?: "?"} | ${card.needsCategories.joinToString { it.displayName() }}"
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)

                    // Color-coded marker icon — selected one is bigger
                    val isSelected = card.id == selectedCardId
                    val color = when (card.urgencyLevel) {
                        UrgencyLevel.CRITICAL -> AndroidColor.RED
                        UrgencyLevel.HIGH -> AndroidColor.rgb(255, 140, 0)
                        UrgencyLevel.MEDIUM -> AndroidColor.rgb(255, 200, 0)
                        UrgencyLevel.LOW -> AndroidColor.rgb(0, 180, 0)
                    }
                    val size = if (isSelected) 72 else 48
                    val strokeWidth = if (isSelected) 8 else 4
                    val strokeColor = if (isSelected) AndroidColor.BLACK else AndroidColor.WHITE
                    val dot = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setSize(size, size)
                        setColor(color)
                        setStroke(strokeWidth, strokeColor)
                    }
                    icon = dot
                }
                mapView.overlays.add(marker)
            }

            // Zoom to selected card
            if (selectedCardId != null) {
                val selected = geoCards.find { it.id == selectedCardId }
                if (selected?.gpsCoordinates != null) {
                    mapView.controller.animateTo(
                        GeoPoint(selected.gpsCoordinates.latitude, selected.gpsCoordinates.longitude),
                        18.0, 800
                    )
                }
            } else if (geoCards.isNotEmpty() && !hasZoomed) {
                hasZoomed = true
                val points = geoCards.map { it.gpsCoordinates!! }
                if (points.size == 1) {
                    mapView.controller.animateTo(GeoPoint(points[0].latitude, points[0].longitude), 17.0, 1500)
                } else {
                    val north = points.maxOf { it.latitude } + 0.002
                    val south = points.minOf { it.latitude } - 0.002
                    val east = points.maxOf { it.longitude } + 0.002
                    val west = points.minOf { it.longitude } - 0.002
                    val box = BoundingBox(north, east, south, west)
                    mapView.post {
                        mapView.zoomToBoundingBox(box, true, 100, 18.0, 1500)
                    }
                }
            }

            mapView.invalidate()
        }
    )
}

@Composable
fun TimelineView(cards: List<TriageCard>, selectedCardId: String? = null, onCardTap: (TriageCard) -> Unit = {}, modifier: Modifier = Modifier) {
    if (cards.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text("No triage cards to display", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    LazyColumn(modifier = modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(cards.sortedByDescending { it.timestamp }) { card ->
            TriageCardItem(card, isSelected = card.id == selectedCardId, onTap = { onCardTap(card) })
        }
    }
}

@Composable
fun TriageCardItem(card: TriageCard, isSelected: Boolean = false, onTap: () -> Unit = {}) {
    val urgencyColor = when (card.urgencyLevel) {
        UrgencyLevel.CRITICAL -> CriticalRed
        UrgencyLevel.HIGH -> HighOrange
        UrgencyLevel.MEDIUM -> MediumYellow
        UrgencyLevel.LOW -> LowGreen
    }
    Card(modifier = Modifier.fillMaxWidth(), onClick = onTap,
        border = if (isSelected) androidx.compose.foundation.BorderStroke(3.dp, urgencyColor) else null
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.width(6.dp).fillMaxHeight().background(urgencyColor))
            Column(modifier = Modifier.padding(12.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(card.urgencyLevel.name, fontWeight = FontWeight.Bold, color = urgencyColor, fontSize = 14.sp)
                    Text("👤 ${card.peopleCount ?: "?"}", fontSize = 14.sp)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(card.assessmentSummary, fontSize = 14.sp, maxLines = 2)
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    card.needsCategories.forEach { need ->
                        SuggestionChip(onClick = {}, label = { Text(need.displayName(), fontSize = 11.sp) })
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "🌐 ${card.detectedLanguage} | 📍 ${if (card.gpsCoordinates != null) "${String.format("%.4f", card.gpsCoordinates.latitude)}, ${String.format("%.4f", card.gpsCoordinates.longitude)}" else "Unknown"} | 🕐 ${formatCardTimestampShort(card.timestamp)}",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
