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
// HC-014 — Runtime shell. Four tabs only:
//
//   1. Home      → HomeScreen (5-card dashboard)
//   2. Glasses   → GlassesScreen (placeholder per spec)
//   3. Gallery   → GalleryScreen (placeholder per spec)
//   4. Settings  → SettingsScreen (Connection Tips, Protocol Notes, Diagnostics)
//
// Legacy AI/chat tabs intentionally NOT registered:
//   - Chat (ChatScreen.kt remains in codebase, dormant)
//   - Ask Cyan (AskCyanScreen.kt remains, dormant)
//   - Gems (GemsScreen.kt remains, dormant)
//   - In-App Answers (Settings card removed in HC-013)
//   - OpenRouter / Kimi / direct API: engine files remain in codebase, never invoked
//
// HC-013 safety preserved: MainViewModel still has empty init {}; no engines /
// voice / TTS / BLE / media spin up at startup.
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
            composable(Screen.Home.route) {
                HomeScreen(
                    onNavigateToGlasses = {
                        navController.navigate(Screen.Glasses.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
            composable(Screen.Glasses.route)  { GlassesScreen() }
            composable(Screen.Gallery.route)  { GalleryScreen() }
            composable(Screen.Settings.route) { SettingsScreen() }
        }
    }
}
