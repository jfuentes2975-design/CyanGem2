package com.cyangem.ui

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
import com.cyangem.ui.theme.*

// =============================================================================
// HC-015 — Settings, restyled to match approved design.
//
// Sections: About, Diagnostics, Connection Tips, Protocol Notes, Advanced.
// "Advanced" is collapsed-minimal per spec — currently shows just a note that
// legacy AI/chat code is dormant. No AI/OpenRouter/Kimi keys or controls.
// =============================================================================

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val btStatus by rememberBluetoothStatus(context)
    var advancedExpanded by remember { mutableStateOf(false) }

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
                "Companion controls for Gemini Live + Hey Cyan glasses",
                fontSize = 12.sp, color = OnSurfaceMuted,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        Spacer(Modifier.height(16.dp))

        SettingsSection("About") { AboutCard() }
        SettingsSection("Diagnostics") { DiagnosticsCard(btStatusLabel = btStatus.displayLabel()) }
        SettingsSection("Connection Tips") { ConnectionTipsCard() }
        SettingsSection("Protocol Notes") { BleProtocolCard() }
        SettingsSection("Advanced") {
            AdvancedCard(expanded = advancedExpanded, onToggle = { advancedExpanded = !advancedExpanded })
        }
    }
}

@Composable
private fun AboutCard() {
    SettingsCard {
        Text(
            "CyanGem v1.0.0 • Personal Use",
            fontSize = 13.sp, fontWeight = FontWeight.Bold, color = OnSurface
        )
        Text(
            "CyanGem2 is a companion app to Gemini Live and the Hey Cyan glasses. Audio routing, microphone, and AI conversation are handled by your phone's Bluetooth stack and the Gemini app — not by this app.",
            fontSize = 12.sp, color = OnSurfaceMuted
        )
    }
}

@Composable
private fun DiagnosticsCard(btStatusLabel: String) {
    val context = LocalContext.current
    var feedback by remember { mutableStateOf<String?>(null) }

    SettingsCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.Bluetooth, null, modifier = Modifier.size(18.dp), tint = CyanPrimary)
            Text(btStatusLabel, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = OnSurface)
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = {
            val ok = openBluetoothSettings(context)
            feedback = if (ok) null else "Could not open Bluetooth settings."
        }) {
            Icon(Icons.Default.OpenInNew, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Open Bluetooth Settings")
        }
        feedback?.let { Text(it, fontSize = 11.sp, color = ErrorColor) }
        Text(
            "For glasses pair / unpair / connect, use the OS Bluetooth settings — CyanGem2 does not handle audio routing.",
            fontSize = 11.sp, color = OnSurfaceMuted
        )
    }
}

@Composable
private fun ConnectionTipsCard() {
    SettingsCard {
        listOf(
            "🔵 Bluetooth" to "Pair / unpair Hey Cyan glasses through Settings → Connections → Bluetooth.",
            "🎧 Audio routing" to "If audio plays from the phone speaker instead of glasses, expand the OS media-output panel and select Hey Cyan.",
            "🎤 Microphone" to "Confirm Microphone permission for the Google app so Gemini Live can hear you through the glasses.",
            "🔋 Background" to "Settings → Apps → Google → Battery → Unrestricted helps Gemini Live stay responsive when the screen is off."
        ).forEach { (title, detail) ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = CyanPrimary)
                Text(detail, fontSize = 12.sp, color = OnSurfaceMuted)
            }
            if (title != "🔋 Background") HorizontalDivider(color = BorderSubtle, thickness = 0.5.dp)
        }
    }
}

@Composable
private fun BleProtocolCard() {
    SettingsCard {
        Text(
            "Reference for any future direct-BLE work (photo bridge, custom commands). The native Hey Cyan app handles these today.",
            fontSize = 11.sp, color = OnSurfaceMuted
        )
        Spacer(Modifier.height(4.dp))
        Text("Primary Service UUID", fontSize = 11.sp, color = OnSurfaceMuted)
        Text(
            "7905FFF0-B5CE-4E99-A40F-4B1E122D00D0",
            fontSize = 10.sp, color = CyanSecondary, fontFamily = FontFamily.Monospace
        )
        Spacer(Modifier.height(4.dp))
        Text("Secondary Service UUID", fontSize = 11.sp, color = OnSurfaceMuted)
        Text(
            "6e40fff0-b5a3-f393-e0a9-e50e24dcca9e",
            fontSize = 10.sp, color = CyanSecondary, fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun AdvancedCard(expanded: Boolean, onToggle: () -> Unit) {
    SettingsCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Legacy code status", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = OnSurface, modifier = Modifier.weight(1f))
            TextButton(onClick = onToggle) {
                Text(if (expanded) "Hide" else "Show")
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    null, modifier = Modifier.size(16.dp)
                )
            }
        }
        if (expanded) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Legacy AI/chat features (Chat, Ask Cyan, Gems, OpenRouter, Kimi, in-app answer engine) are present in the codebase but disabled in runtime. They are not visible in navigation and are never invoked at startup.",
                fontSize = 11.sp, color = OnSurfaceMuted
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "No API keys are stored or requested by this app. Settings here does not include AI/OpenRouter/Kimi controls.",
                fontSize = 11.sp, color = OnSurfaceMuted
            )
        }
    }
}

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
