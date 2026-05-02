package com.cyangem.ui

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyangem.ui.theme.*
import com.cyangem.viewmodel.MainViewModel

// =============================================================================
// HC-007A — Settings: AI Mode is the only AI section in the visible UX.
// Gemini / OpenRouter / AI Provider / AI Model sections removed. Engine
// classes still exist on disk and can be re-enabled later from a future
// patch — this is a UI change only.
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

        SettingsSection("AI Mode") {
            ChatGptHandoffCard(vm)
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
private fun ChatGptHandoffCard(vm: MainViewModel) {
    val context = LocalContext.current
    val isInstalled = remember { isChatGptInstalled(context) }

    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        color = SurfaceElevated,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "Uses installed ChatGPT app — no API key required",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = OnSurface
            )
            Text(
                if (isInstalled) "ChatGPT app: detected"
                else "ChatGPT app: not detected — install it from the Play Store",
                fontSize = 11.sp,
                color = if (isInstalled) SuccessColor else Color(0xFFFFB300),
                fontWeight = FontWeight.Medium
            )
            Button(
                onClick = { handoffTestPromptToChatGpt(context, vm) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = CyanPrimary,
                    contentColor = Color(0xFF003731)
                )
            ) {
                Icon(
                    Icons.Default.Send,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text("Test ChatGPT Handoff", fontWeight = FontWeight.Bold)
            }
            Text(
                "No ChatGPT credentials stored.\n" +
                "No paid API call from CyanGem2.\n" +
                "Tapping the button sends a test prompt to ChatGPT; ChatGPT may " +
                "require you to tap Send to deliver the message.",
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
                "🔵 BLE" to "Keep glasses within 10m during connection. Glasses must be powered on.",
                "📶 Media Sync" to "After connecting BLE, enable Wi-Fi. The glasses broadcast a Wi-Fi Direct network. Your phone joins it automatically during sync.",
                "🔋 Background" to "Go to Settings → Apps → CyanGem → Battery → Unrestricted to keep BLE alive.",
                "🔧 Commands fail?" to "Open BLE Inspector (Glasses tab) and verify write/notify characteristic UUIDs. Use nRF Connect to cross-reference.",
            ).forEach { (title, detail) ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(title, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = CyanPrimary)
                    Text(detail, fontSize = 12.sp, color = OnSurfaceMuted)
                }
                if (title != "🔧 Commands fail?") HorizontalDivider(color = Color(0xFF30363D), thickness = 0.5.dp)
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
            Spacer(Modifier.height(4.dp))
            Text(
                "Characteristic UUIDs are auto-detected on connect. If BLE commands don't work, use nRF Connect to sniff the exact write/notify UUIDs and file a note.",
                fontSize = 11.sp,
                color = OnSurfaceMuted
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
