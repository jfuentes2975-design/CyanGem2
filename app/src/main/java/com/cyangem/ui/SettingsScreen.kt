package com.cyangem.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.cyangem.bridge.heycyan.HeyCyanPackagePin
import com.cyangem.ui.theme.*

// =============================================================================
// HC-018 — Settings.
//
// Per the technical fix-forward document, sections are:
//   - Gemini package status
//   - HeyCyan package status
//   - Bluetooth permission status
//   - Nearby devices permission
//   - Media permission
//   - Debug mode (toggle — expands raw diagnostic info)
//   - About
//
// Explicitly NOT here:
//   - API / provider / model settings (no Gemini API key, no OpenRouter,
//     no Kimi, no in-app answer engine)
//   - BLE service/protocol UUIDs (those move to the BLE control spike doc)
//   - Connection tips (Glasses tab has troubleshooting)
//   - Advanced legacy-code expand (covered by debug mode)
// =============================================================================

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val btStatus by rememberBluetoothStatus(context)

    var permRefreshTick by remember { mutableIntStateOf(0) }
    var debugMode by remember { mutableStateOf(DebugModeStore.read(context)) }

    val mediaGranted = remember(permRefreshTick) {
        MediaBridgeRepository.hasMediaPermission(context)
    }
    val bluetoothConnectGranted = remember(permRefreshTick) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else true
    }
    val bluetoothScanGranted = remember(permRefreshTick) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED
        } else true
    }
    val notificationsGranted = remember(permRefreshTick) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else true
    }

    val geminiPkg = remember(permRefreshTick) { detectInstalledGemini(context) }
    val heyCyanPkg = remember(permRefreshTick) { detectInstalledHeyCyan(context) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundLight)
            .verticalScroll(rememberScrollState())
            .padding(top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Text(
                "Settings",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = OnSurface
            )
            Text(
                "Status, permissions, and app info",
                fontSize = 12.sp, color = OnSurfaceMuted,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        Spacer(Modifier.height(16.dp))

        SettingsSection("Apps") {
            PackageStatusCard(
                title = "Gemini",
                detected = geminiPkg,
                accent = GeminiPurple,
                description = "Gemini Live runs in the Gemini app. CyanGem2 launches it; Live mode requires a manual tap inside Gemini."
            )
            HeyCyanPackageCard(
                detected = heyCyanPkg,
                pinTick = permRefreshTick,
                onRefresh = { permRefreshTick++ }
            )
        }

        SettingsSection("Permissions") {
            PermissionCard(
                rows = listOf(
                    PermissionRowSpec(
                        icon = Icons.Default.Bluetooth,
                        label = "Bluetooth — connect",
                        granted = bluetoothConnectGranted,
                        grantedHint = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) "Granted" else "N/A on this Android version",
                        missingHint = "Missing — glasses status will read as Unknown"
                    ),
                    PermissionRowSpec(
                        icon = Icons.Default.BluetoothSearching,
                        label = "Nearby devices (Bluetooth scan)",
                        granted = bluetoothScanGranted,
                        grantedHint = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) "Granted" else "N/A on this Android version",
                        missingHint = "Missing — pairing flows that scan won't find new glasses"
                    ),
                    PermissionRowSpec(
                        icon = Icons.Default.PhotoLibrary,
                        label = "Media read (photos / videos)",
                        granted = mediaGranted,
                        grantedHint = "Granted",
                        missingHint = "Missing — Capture Check + Gallery cannot show recent files"
                    ),
                    PermissionRowSpec(
                        icon = Icons.Default.Notifications,
                        label = "Notifications",
                        granted = notificationsGranted,
                        grantedHint = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) "Granted" else "N/A on this Android version",
                        missingHint = "Missing — system notifications from CyanGem2 are silent on Android 13+"
                    )
                ),
                onRefresh = { permRefreshTick++ },
                onOpenAppSettings = { openAppSettings(context) },
                onOpenBluetoothSettings = { openBluetoothSettings(context) }
            )
        }

        SettingsSection("Debug mode") {
            DebugModeCard(
                enabled = debugMode,
                onToggle = {
                    debugMode = !debugMode
                    DebugModeStore.write(context, debugMode)
                },
                btStatusRaw = btStatus.name,
                geminiPkg = geminiPkg,
                heyCyanPkg = heyCyanPkg,
                samsungInstalled = isSamsungGalleryInstalled(context),
                photosInstalled = isGooglePhotosInstalled(context),
                mediaPermsRaw = MediaBridgeRepository.requiredMediaPermissions().joinToString(", ")
            )
        }

        SettingsSection("About") { AboutCard() }
    }
}

// =============================================================================
// Package status card
// =============================================================================

@Composable
private fun PackageStatusCard(
    title: String,
    detected: String?,
    accent: androidx.compose.ui.graphics.Color,
    description: String
) {
    SettingsCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.Apps, null, modifier = Modifier.size(18.dp), tint = accent)
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = OnSurface, modifier = Modifier.weight(1f))
            StatusPill(if (detected != null) "Detected" else "Not detected", if (detected != null) SuccessColor else WarningAmber)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            detected ?: "(no candidate package installed)",
            fontSize = 11.sp,
            color = OnSurfaceMuted,
            fontFamily = FontFamily.Monospace
        )
        Spacer(Modifier.height(4.dp))
        Text(description, fontSize = 11.sp, color = OnSurfaceMuted)
    }
}

// =============================================================================
// HeyCyan package card — extends PackageStatusCard with Pin / Unpin actions
// (HC-018 salvage from HC-017 HeyCyanPackagePin).
// =============================================================================

@Composable
private fun HeyCyanPackageCard(
    detected: String?,
    pinTick: Int,                         // re-read pinned value when this changes
    onRefresh: () -> Unit
) {
    val context = LocalContext.current
    val pinned = remember(pinTick) { HeyCyanPackagePin.read(context) }
    val effective = pinned ?: detected
    val canPin = detected != null && pinned != detected

    SettingsCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.Apps, null, modifier = Modifier.size(18.dp), tint = CyanPrimary)
            Text(
                "HeyCyan native app",
                fontSize = 13.sp, fontWeight = FontWeight.Bold, color = OnSurface,
                modifier = Modifier.weight(1f)
            )
            val pillLabel = when {
                pinned != null -> "Pinned"
                detected != null -> "Detected"
                else -> "Not detected"
            }
            val pillColor = if (effective != null) SuccessColor else WarningAmber
            StatusPill(pillLabel, pillColor)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            effective ?: "(no candidate package installed)",
            fontSize = 11.sp,
            color = OnSurfaceMuted,
            fontFamily = FontFamily.Monospace
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "HeyCyan is the official device companion. It pairs the glasses, manages firmware, and imports captures to the phone.",
            fontSize = 11.sp, color = OnSurfaceMuted
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Pin the resolved package name once you've confirmed Open HeyCyan works — it skips the candidate probe on every cold launch.",
            fontSize = 10.sp, color = OnSurfaceMuted
        )
        Spacer(Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    detected?.let {
                        HeyCyanPackagePin.write(context, it)
                        onRefresh()
                    }
                },
                enabled = canPin,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.PushPin, null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("Pin", fontSize = 11.sp)
            }
            OutlinedButton(
                onClick = {
                    HeyCyanPackagePin.clear(context)
                    onRefresh()
                },
                enabled = pinned != null,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Clear, null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("Unpin", fontSize = 11.sp)
            }
        }
    }
}

// =============================================================================
// Permission card
// =============================================================================

private data class PermissionRowSpec(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val label: String,
    val granted: Boolean,
    val grantedHint: String,
    val missingHint: String
)

@Composable
private fun PermissionCard(
    rows: List<PermissionRowSpec>,
    onRefresh: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenBluetoothSettings: () -> Unit
) {
    SettingsCard {
        rows.forEachIndexed { index, row ->
            PermissionRow(row)
            if (index < rows.size - 1) HorizontalDivider(color = BorderSubtle, thickness = 0.5.dp)
        }
        Spacer(Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onRefresh, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Refresh, null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("Refresh", fontSize = 11.sp)
            }
            OutlinedButton(onClick = onOpenAppSettings, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Settings, null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("App Settings", fontSize = 11.sp)
            }
            OutlinedButton(onClick = onOpenBluetoothSettings, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Bluetooth, null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("BT", fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun PermissionRow(row: PermissionRowSpec) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(row.icon, null, modifier = Modifier.size(18.dp), tint = if (row.granted) SuccessColor else WarningAmber)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(row.label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = OnSurface)
            Text(
                if (row.granted) row.grantedHint else row.missingHint,
                fontSize = 11.sp,
                color = if (row.granted) OnSurfaceMuted else WarningAmberSoft
            )
        }
        StatusPill(if (row.granted) "Granted" else "Missing", if (row.granted) SuccessColor else WarningAmber)
    }
}

// =============================================================================
// Debug mode
// =============================================================================

@Composable
private fun DebugModeCard(
    enabled: Boolean,
    onToggle: () -> Unit,
    btStatusRaw: String,
    geminiPkg: String?,
    heyCyanPkg: String?,
    samsungInstalled: Boolean,
    photosInstalled: Boolean,
    mediaPermsRaw: String
) {
    SettingsCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Show debug diagnostics", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = OnSurface, modifier = Modifier.weight(1f))
            Switch(
                checked = enabled,
                onCheckedChange = { onToggle() },
                colors = SwitchDefaults.colors(checkedThumbColor = CyanPrimary, checkedTrackColor = CyanPrimary.copy(alpha = 0.30f))
            )
        }
        Text(
            "Off by default. When enabled, this card shows raw diagnostic strings for log/issue reports.",
            fontSize = 11.sp, color = OnSurfaceMuted
        )
        if (enabled) {
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = BorderSubtle, thickness = 0.5.dp)
            Spacer(Modifier.height(8.dp))
            DebugRow("Bluetooth adapter", btStatusRaw)
            DebugRow("Gemini package", geminiPkg ?: "(none)")
            DebugRow("HeyCyan package", heyCyanPkg ?: "(none)")
            DebugRow("Samsung Gallery installed", samsungInstalled.toString())
            DebugRow("Google Photos installed", photosInstalled.toString())
            DebugRow("Media permissions requested", mediaPermsRaw)
            DebugRow("Build SDK_INT", Build.VERSION.SDK_INT.toString())
            DebugRow("Build manufacturer / model", "${Build.MANUFACTURER} / ${Build.MODEL}")
        }
    }
}

@Composable
private fun DebugRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(label, fontSize = 11.sp, color = OnSurfaceMuted, modifier = Modifier.width(140.dp))
        Text(value, fontSize = 11.sp, color = OnSurface, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
    }
}

// =============================================================================
// About
// =============================================================================

@Composable
private fun AboutCard() {
    SettingsCard {
        Text(
            "CyanGem v1.0.0  •  Personal Use",
            fontSize = 13.sp, fontWeight = FontWeight.Bold, color = OnSurface
        )
        Text(
            "CyanGem2 is a support / diagnostics / workflow / launch / control-guide / Gallery-MediaStore-bridge app for the Hey Cyan glasses + Gemini Live + Samsung Galaxy phone.",
            fontSize = 12.sp, color = OnSurfaceMuted
        )
        Text(
            "It does NOT run AI. It does NOT capture from the glasses camera. It does NOT pull media off the glasses over BLE/Wi-Fi. Those concerns belong to Gemini Live (AI), HeyCyan native app (capture / import), and the spike documents under Design_Arch/spikes/ (research-only, not production).",
            fontSize = 11.sp, color = OnSurfaceMuted
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "No API keys are stored or requested. No analytics, no telemetry, no background internet calls.",
            fontSize = 11.sp, color = OnSurfaceMuted
        )
    }
}

// =============================================================================
// Layout helpers
// =============================================================================

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        color = SurfaceCard,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, BorderSubtle)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp), content = content)
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.padding(bottom = 4.dp)) {
        Text(
            title.uppercase(),
            fontSize = 11.sp, fontWeight = FontWeight.Bold,
            color = OnSurfaceMuted,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            letterSpacing = 1.sp
        )
        content()
    }
}

// =============================================================================
// Debug mode persistence
// =============================================================================

private object DebugModeStore {
    private const val PREFS = "cyangem_debug"
    private const val KEY = "debug_mode"

    fun read(context: Context): Boolean = try {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, false)
    } catch (e: Exception) { false }

    fun write(context: Context, enabled: Boolean) {
        try {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY, enabled).apply()
        } catch (e: Exception) { /* ignore */ }
    }
}
