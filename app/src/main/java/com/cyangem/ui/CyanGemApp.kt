package com.cyangem.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
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
// HC-013 — Stabilization pivot. The bottom nav is stripped from 5–6 tabs
// (Glasses, Chat, Gems, Gallery, Settings, +AskCyan if HC-011 added it) down
// to 2 tabs (Home, Settings). NavHost routes ONLY to Home and Settings.
//
// The other screens (GlassesScreen, ChatScreen, GemsScreen, GalleryScreen,
// AskCyanScreen) are intentionally NOT removed — they remain in the codebase
// so any references in MainViewModel still compile, but they are never
// rendered. This minimizes blast radius for the stabilization patch.
//
// MainViewModel is constructed (unchanged from HC-011) but the only field
// HC-013 reads from it is uiState.snackbarMessage for Scaffold snackbars.
// =============================================================================

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Home     : Screen("home",     "Home",     Icons.Default.Home)
    object Settings : Screen("settings", "Settings", Icons.Default.Settings)
}

private val bottomNavItems = listOf(
    Screen.Home,
    Screen.Settings
)

@Composable
fun CyanGemApp(vm: MainViewModel = viewModel()) {
    val navController = rememberNavController()
    val uiState by vm.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // HC-013 — keep snackbar wiring so future code can post messages even though
    // Home doesn't currently push any. Cheap and lifecycle-safe.
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
            composable(Screen.Settings.route) { SettingsScreen(vm) }
        }
    }
}
