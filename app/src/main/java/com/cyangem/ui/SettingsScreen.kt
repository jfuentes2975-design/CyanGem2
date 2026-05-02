package com.cyangem.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyangem.ui.theme.*
import com.cyangem.viewmodel.MainViewModel

// =============================================================================
// HC-013 — Minimal Settings: stripped of In-App Answers (the source of the
// HC-007..HC-012 churn) and AI Mode (ChatGPT Backup) — both replaced by
// Home's Open Gemini / Open Hey Cyan buttons.
//
// Kept:
//   - About card (version + pivot note)
//   - Connection Tips (BLE diagnostics still useful for support role)
//   - Protocol Notes (BLE protocol UUIDs — useful for the Hey Cyan native app
//     team or for any future reverse-engineering)
//
// Removed:
//   - InAppAnswersCard (Save Key / Clear Key / Load Test Key / Test In-App AI)
//   - ChatGptHandoffCard (Open ChatGPT button — Home's Open Gemini supersedes)
//
// MainViewModel parameter is kept for ABI stability with CyanGemApp's NavHost
// composable signature, but currently unused inside this composable.
// =============================================================================

@Composable
fun SettingsScreen(vm: MainViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        Spacer(Modifier.height(16.dp))
        Text(
            "Settings",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = OnSurface,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Text(
            "CyanGem v1.0.0 • Personal Use",
            fontSize = 12.sp,
            color = OnSurfaceMuted,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(Modifier.height(16.dp))

        SettingsSection("About") {
            AboutCard()
        }

        SettingsSection("Connection Tips") {
            ConnectionTipsCard()
        }

        SettingsSection("Protocol Notes") {
            BleProtocolCard()
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun AboutCard() {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        color = SurfaceElevated,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "CyanGem2 is a companion app to Gemini Live and the Hey Cyan glasses.",
                fontSize = 13.sp,
                color = OnSurface
            )
            Text(
                "Audio routing, microphone, and AI conversation are handled by your phone's Bluetooth and the Gemini app — not by this app.",
                fontSize = 11.sp,
                color = OnSurfaceMuted
            )
            Text(
                "Use the Home tab to open Gemini, open the Hey Cyan app, and check the daily-mode checklist.",
                fontSize = 11.sp,
                color = OnSurfaceMuted
            )
        }
    }
}

@Composable
private fun ConnectionTipsCard() {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        color = SurfaceElevated,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(
                "🔵 Bluetooth" to "Pair / unpair Hey Cyan glasses through your phone's Settings → Connections → Bluetooth.",
                "🎧 Audio routing" to "If audio plays from the phone speaker instead of the glasses, expand the OS media-output panel and select Hey Cyan.",
                "🎤 Microphone" to "Confirm Microphone permission for the Google app so Gemini Live can hear you through the glasses.",
                "🔋 Background" to "Settings → Apps → Google → Battery → Unrestricted helps Gemini Live stay responsive when the screen is off.",
            ).forEach { (title, detail) ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(title, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = CyanPrimary)
                    Text(detail, fontSize = 12.sp, color = OnSurfaceMuted)
                }
                if (title != "🔋 Background") HorizontalDivider(color = Color(0xFF30363D), thickness = 0.5.dp)
            }
        }
    }
}

@Composable
private fun BleProtocolCard() {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        color = SurfaceElevated,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "Reference for any future direct-BLE work (photo bridge, custom commands). The Hey Cyan native app handles these today.",
                fontSize = 11.sp,
                color = OnSurfaceMuted
            )
            Spacer(Modifier.height(4.dp))
            Text("Primary Service UUID", fontSize = 11.sp, color = OnSurfaceMuted)
            Text(
                "7905FFF0-B5CE-4E99-A40F-4B1E122D00D0",
                fontSize = 10.sp,
                color = CyanSecondary,
                fontFamily = FontFamily.Monospace
            )
            Spacer(Modifier.height(4.dp))
            Text("Secondary Service UUID", fontSize = 11.sp, color = OnSurfaceMuted)
            Text(
                "6e40fff0-b5a3-f393-e0a9-e50e24dcca9e",
                fontSize = 10.sp,
                color = CyanSecondary,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.padding(bottom = 4.dp)) {
        Text(
            title.uppercase(),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = OnSurfaceMuted,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            letterSpacing = 1.sp
        )
        content()
    }
}
