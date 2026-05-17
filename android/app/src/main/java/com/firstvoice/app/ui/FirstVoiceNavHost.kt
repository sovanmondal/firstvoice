package com.firstvoice.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.firstvoice.app.ui.screens.*

sealed class Screen(val route: String, val title: String) {
    data object Setup : Screen("setup", "Setup")
    data object Home : Screen("home", "FirstVoice")
    data object Encounter : Screen("encounter", "Encounter")
    data object TriageCards : Screen("triage_cards", "Triage Cards")
    data object Dashboard : Screen("dashboard", "Dashboard")
    data object QuickPhrases : Screen("quick_phrases", "Quick Phrases")
    data object MeshSync : Screen("mesh_sync", "Mesh Sync")
    data object FieldRadio : Screen("field_radio", "Field Radio")
    data object Settings : Screen("settings", "Settings")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FirstVoiceNavHost() {
    val navController = rememberNavController()
    val container = com.firstvoice.app.FirstVoiceApp.instance.container
    val startDest = if (container.tilePreloader.isPreloaded()) Screen.Home.route else Screen.Setup.route

    NavHost(
        navController = navController,
        startDestination = startDest
    ) {
        composable(Screen.Setup.route) {
            SetupScreen(
                onSetupComplete = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Setup.route) { inclusive = true }
                    }
                }
            )
        }
        composable(Screen.Home.route) {
            HomeScreen(
                onNewEncounter = { navController.navigate(Screen.Encounter.route) },
                onViewTriageCards = { navController.navigate(Screen.TriageCards.route) },
                onOpenDashboard = { navController.navigate(Screen.Dashboard.route) },
                onQuickPhrases = { navController.navigate(Screen.QuickPhrases.route) },
                onMeshSync = { navController.navigate(Screen.MeshSync.route) },
                onFieldRadio = { navController.navigate(Screen.FieldRadio.route) },
                onSettings = { navController.navigate(Screen.Settings.route) }
            )
        }
        composable(Screen.Encounter.route) {
            EncounterScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.TriageCards.route) {
            TriageCardsScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.Dashboard.route) {
            DashboardScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.QuickPhrases.route) {
            QuickPhrasesScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.Settings.route) {
            SettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.MeshSync.route) {
            val meshSync = container.meshSyncService
            val peers by meshSync.peers.collectAsState()
            val syncState by meshSync.syncState.collectAsState()
            val isDiscovering by meshSync.isDiscovering.collectAsState()
            MeshSyncScreen(
                onBack = { navController.popBackStack() },
                peers = peers,
                syncState = syncState,
                isDiscovering = isDiscovering,
                onStartDiscovery = { meshSync.broadcastSync() },
                onStopDiscovery = { },
                onConnectPeer = { meshSync.broadcastSync() }
            )
        }
        composable(Screen.FieldRadio.route) {
            FieldRadioScreen(onBack = { navController.popBackStack() })
        }
    }
}
