package com.cyangem.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.cyangem.viewmodel.MainViewModel

// =============================================================================
// HC-018 — Runtime shell. FOUR tabs only, per the technical fix-forward doc:
//
//   1. Home      → HomeScreen (5 cards: Gemini guide, HeyCyan launch,
//                  BT status, Capture Check, Truth/Limitations)
//   2. Glasses   → GlassesScreen (hardware control map: A1/A2/touchpad/lights,
//                  guided button test, troubleshooting)
//   3. Gallery   → GalleryScreen (MediaStore + Photo Picker)
//   4. Settings  → SettingsScreen (package + permission status, debug, about)
//
// HC-017's gemini_frame route is REMOVED. The in-frame Gemini Live scaffold
// is now a spike document under Design_Arch/spikes/ — not main app code.
// The HC-017 GeminiFrameScreen.kt + GeminiSession.kt + GeminiConfig.kt +
// GeminiSessionAdapter.kt + GeminiSessionController.kt files remain in the
// codebase but are unreachable at runtime (no route navigates to them).
//
// Same pattern: HC-017 HeyCyanBridge.kt + HeyCyanPackagePin.kt remain in
// the codebase but no UI references them. The "Hard Bridge" BLE controls
// have been removed from GlassesScreen — they were too aggressive for the
// stable support app per the technical doc.
//
// MainViewModel.init {} stays empty — no eager subsystem construction.
// =============================================================================

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Home     : Screen("home",     "Home",     Icons.Default.Home)
    object Glasses  : Screen("glasses",  "Glasses",  Icons.Default.BluetoothSearching)
    object Gallery  : Screen("gallery",  "Gallery",  Icons.Default.PhotoLibrary)
    object Settings : Screen("settings", "Settings", Icons.Default.Settings)
}

private val bottomNavItems = listOf(
    Screen.Home,
    Screen.Glasses,
    Screen.Gallery,
    Screen.Settings
)

@Composable
fun CyanGemApp(vm: MainViewModel = viewModel()) {
    val navController = rememberNavController()
    val uiState by vm.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let {
            snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Short)
            vm.clearSnackbar()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDest = navBackStackEntry?.destination
                bottomNavItems.forEach { screen ->
                    val selected = currentDest?.hierarchy?.any { it.route == screen.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(screen.icon, contentDescription = screen.label) },
                        label = { Text(screen.label) }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(padding)
        ) {
            composable(Screen.Home.route)     { HomeScreen() }
            composable(Screen.Glasses.route)  { GlassesScreen() }
            composable(Screen.Gallery.route)  { GalleryScreen() }
            composable(Screen.Settings.route) { SettingsScreen() }
        }
    }
}
