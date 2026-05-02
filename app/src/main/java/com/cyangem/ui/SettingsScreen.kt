package com.cyangem.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyangem.ui.theme.*
import com.cyangem.viewmodel.MainViewModel

// =============================================================================
// HC-009A — Settings polish: "In-App Answers" promoted to the FIRST section
// with visual emphasis (cyan-bordered SurfaceCard) so it cannot be missed.
// AI Mode (ChatGPT handoff backup) drops to second. Connection Tips and
// Protocol Notes order unchanged.
//
// No engine code touched. Engine files (Gemini/OpenRouter/ApiKeyStore) still
// exist on disk and can be re-enabled in a future patch.
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

        // ── HC-009A — In-App Answers FIRST, visually emphasized ─────────────
        SettingsSection("In-App Answers") {
            InAppAnswersCard(vm)
        }

        // ── AI Mode (ChatGPT handoff backup) ─────────────────────────────────
        SettingsSection("AI Mode (ChatGPT Backup)") {
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
private fun InAppAnswersCard(vm: MainViewModel) {
    val store = vm.apiKeyStore
    var keyInput by remember { mutableStateOf("") }
    var keyVisible by remember { mutableStateOf(false) }
    var refreshTrigger by remember { mutableIntStateOf(0) }
    val hasKey = remember(refreshTrigger) { store?.hasOpenRouterKey() == true }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .border(
                width = 1.dp,
                color = CyanPrimary.copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp)
            ),
        color = SurfaceCard,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Status badge — bold, with color tint
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    color = if (hasKey) SuccessColor.copy(alpha = 0.18f) else Color(0x33FFB300),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text(
                        if (hasKey) "Ready" else "Not configured",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (hasKey) SuccessColor else Color(0xFFFFB300)
                    )
                }
            }

            Text(
                "Ask Cyan can answer inside CyanGem when an OpenRouter key is saved.",
                fontSize = 13.sp,
                color = OnSurface
            )

            OutlinedTextField(
                value = keyInput,
                onValueChange = { keyInput = it },
                label = { Text("OpenRouter API key") },
                placeholder = { Text("sk-or-...", color = OnSurfaceMuted) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { keyVisible = !keyVisible }) {
                        Icon(
                            if (keyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = null,
                            tint = OnSurfaceMuted
                        )
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CyanPrimary,
                    unfocusedBorderColor = Color(0xFF30363D),
                    focusedTextColor = OnSurface,
                    unfocusedTextColor = OnSurface,
                    cursorColor = CyanPrimary
                )
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        val trimmed = keyInput.trim()
                        if (trimmed.isNotEmpty()) {
                            store?.setOpenRouterKey(trimmed)
                            keyInput = ""
                            refreshTrigger++
                            vm.showSnackbar("In-app answers ready")
                        }
                    },
                    enabled = keyInput.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CyanPrimary,
                        contentColor = Color(0xFF003731)
                    )
                ) { Text("Save Key", fontWeight = FontWeight.Bold) }

                if (hasKey) {
                    OutlinedButton(
                        onClick = {
                            store?.clearOpenRouterKey()
                            refreshTrigger++
                            vm.showSnackbar("Key cleared")
                        }
                    ) { Text("Clear Key") }
                }
            }

            // Notes — exact wording per HC-009A spec
            Spacer(Modifier.height(2.dp))
            Text(
                "ChatGPT handoff still works without this key.",
                fontSize = 11.sp,
                color = OnSurfaceMuted
            )
            Text(
                "This does not store ChatGPT credentials.",
                fontSize = 11.sp,
                color = OnSurfaceMuted
            )
            Text(
                "Use this only if you want answers to appear inside CyanGem.",
                fontSize = 11.sp,
                color = OnSurfaceMuted
            )

            // Hint to get a key
            Text(
                "Get a free key at openrouter.ai → sign in with Google → Keys → Create Key.",
                fontSize = 11.sp,
                color = CyanPrimary,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
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
