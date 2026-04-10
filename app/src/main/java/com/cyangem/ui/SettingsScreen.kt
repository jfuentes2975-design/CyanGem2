package com.cyangem.ui

import androidx.compose.animation.*
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyangem.data.ApiKeyStore
import com.cyangem.gemini.GeminiEngine
import com.cyangem.ui.theme.*
import com.cyangem.viewmodel.MainViewModel

@Composable
fun SettingsScreen(vm: MainViewModel) {
    val uiState by vm.uiState.collectAsState()
    var apiKeyInput by remember { mutableStateOf("") }
    var apiKeyVisible by remember { mutableStateOf(false) }
    var showApiKeySection by remember { mutableStateOf(!uiState.hasApiKey) }
    var openRouterKeyInput by remember { mutableStateOf("") }
    var openRouterKeyVisible by remember { mutableStateOf(false) }
    val isOpenRouter = uiState.activeProvider == ApiKeyStore.PROVIDER_OPENROUTER

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
        Text("CyanGem v1.0.0 • Personal Use", fontSize = 12.sp, color = OnSurfaceMuted,
            modifier = Modifier.padding(horizontal = 16.dp))
        Spacer(Modifier.height(16.dp))

        // ── AI Provider ────────────────────────────────────────────────────────
        SettingsSection("AI Provider") {
            // Provider toggle
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                color = SurfaceCard,
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // OpenRouter button
                    Button(
                        onClick = { vm.setProvider(ApiKeyStore.PROVIDER_OPENROUTER) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isOpenRouter) CyanPrimary else SurfaceElevated,
                            contentColor = if (isOpenRouter) Color(0xFF003731) else OnSurfaceMuted
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("OpenRouter", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("Free ✓", fontSize = 10.sp)
                        }
                    }
                    // Gemini button
                    Button(
                        onClick = { vm.setProvider(ApiKeyStore.PROVIDER_GEMINI) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (!isOpenRouter) CyanPrimary else SurfaceElevated,
                            contentColor = if (!isOpenRouter) Color(0xFF003731) else OnSurfaceMuted
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Gemini", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("Paid", fontSize = 10.sp)
                        }
                    }
                }
            }
        }

        // ── OpenRouter Key ─────────────────────────────────────────────────────
        SettingsSection("OpenRouter (Free AI)") {
            SettingsRow(
                icon = Icons.Default.Key,
                iconTint = if (vm.apiKeyStore?.hasOpenRouterKey() == true) SuccessColor else Color(0xFFFFB300),
                title = "API Key",
                subtitle = if (vm.apiKeyStore?.hasOpenRouterKey() == true)
                    "Saved — free tier active" else "Required for OpenRouter",
            )
            Column(
                modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = openRouterKeyInput,
                    onValueChange = { openRouterKeyInput = it },
                    label = { Text("Paste OpenRouter key (sk-or-...)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = if (openRouterKeyVisible)
                        VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { openRouterKeyVisible = !openRouterKeyVisible }) {
                            Icon(
                                if (openRouterKeyVisible) Icons.Default.VisibilityOff
                                else Icons.Default.Visibility,
                                contentDescription = null, tint = OnSurfaceMuted
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
                Button(
                    onClick = {
                        if (openRouterKeyInput.isNotBlank()) {
                            vm.saveOpenRouterKey(openRouterKeyInput)
                            openRouterKeyInput = ""
                        }
                    },
                    enabled = openRouterKeyInput.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CyanPrimary,
                        contentColor = Color(0xFF003731)
                    )
                ) { Text("Save Key", fontWeight = FontWeight.Bold) }
                Surface(color = Color(0xFF0D1F1A), shape = RoundedCornerShape(8.dp)) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("Get a free key:", fontSize = 11.sp, color = CyanPrimary,
                            fontWeight = FontWeight.Medium)
                        Text("1. Go to openrouter.ai", fontSize = 11.sp, color = OnSurfaceMuted)
                        Text("2. Sign in with Google", fontSize = 11.sp, color = OnSurfaceMuted)
                        Text("3. Keys → Create Key", fontSize = 11.sp, color = OnSurfaceMuted)
                        Text("4. Paste above — no billing needed",
                            fontSize = 11.sp, color = OnSurfaceMuted)
                    }
                }
            }
        }

        // ── Gemini API Key ─────────────────────────────────────────────────────
        SettingsSection("Gemini API") {
            // Status row
            SettingsRow(
                icon = Icons.Default.Key,
                iconTint = if (uiState.hasApiKey) SuccessColor else Color(0xFFFFB300),
                title = "API Key",
                subtitle = if (uiState.hasApiKey) "Saved securely on device" else "Required to use Gemini",
                trailing = {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (uiState.hasApiKey) {
                            TextButton(
                                onClick = { showApiKeySection = !showApiKeySection },
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) { Text("Change", fontSize = 12.sp, color = OnSurfaceMuted) }
                        }
                    }
                }
            )

            // API key input
            AnimatedVisibility(visible = showApiKeySection || !uiState.hasApiKey) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = apiKeyInput,
                        onValueChange = { apiKeyInput = it },
                        label = { Text("Paste Gemini API key") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = if (apiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            IconButton(onClick = { apiKeyVisible = !apiKeyVisible }) {
                                Icon(
                                    if (apiKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = null, tint = OnSurfaceMuted
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
                                if (apiKeyInput.isNotBlank()) {
                                    vm.saveApiKey(apiKeyInput)
                                    apiKeyInput = ""
                                    showApiKeySection = false
                                }
                            },
                            enabled = apiKeyInput.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary, contentColor = Color(0xFF003731))
                        ) { Text("Save Key", fontWeight = FontWeight.Bold) }
                        if (uiState.hasApiKey) {
                            OutlinedButton(
                                onClick = { vm.apiKeyStore?.clearApiKey(); vm.showSnackbar("API key removed") }
                            ) { Text("Remove") }
                        }
                    }
                    HowToGetApiKey()
                }
            }
        }

        // ── Model info ─────────────────────────────────────────────────────────
        SettingsSection("AI Model") {
            SettingsRow(
                icon = Icons.Default.Psychology,
                title = "Model",
                subtitle = GeminiEngine.MODEL_NAME,
                trailing = {
                    Surface(color = CyanPrimary.copy(alpha = 0.15f), shape = RoundedCornerShape(6.dp)) {
                        Text("Active", fontSize = 11.sp, color = CyanPrimary, modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp))
                    }
                }
            )
        }

        // ── Connection tips ────────────────────────────────────────────────────
        SettingsSection("Connection Tips") {
            ConnectionTipsCard()
        }

        // ── BLE protocol note ──────────────────────────────────────────────────
        SettingsSection("Protocol Notes") {
            BleProtocolCard()
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun HowToGetApiKey() {
    Surface(color = Color(0xFF0D1F1A), shape = RoundedCornerShape(8.dp)) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("How to get a free API key:", fontSize = 11.sp, color = CyanPrimary, fontWeight = FontWeight.Medium)
            listOf(
                "1. Go to aistudio.google.com",
                "2. Sign in with your Google account",
                "3. Click \"Get API key\" → Create",
                "4. Copy and paste it above"
            ).forEach { step ->
                Text(step, fontSize = 11.sp, color = OnSurfaceMuted)
            }
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
            Text("7905FFF0-B5CE-4E99-A40F-4B1E122D00D0", fontSize = 10.sp, color = CyanSecondary, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
            Spacer(Modifier.height(4.dp))
            Text("Secondary Service UUID", fontSize = 11.sp, color = OnSurfaceMuted)
            Text("6e40fff0-b5a3-f393-e0a9-e50e24dcca9e", fontSize = 10.sp, color = CyanSecondary, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
            Spacer(Modifier.height(4.dp))
            Text(
                "Characteristic UUIDs are auto-detected on connect. If BLE commands don't work, use nRF Connect to sniff the exact write/notify UUIDs and file a note.",
                fontSize = 11.sp, color = OnSurfaceMuted
            )
        }
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

@Composable
private fun SettingsRow(
    icon: ImageVector,
    iconTint: Color = CyanPrimary,
    title: String,
    subtitle: String,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium, color = OnSurface, fontSize = 14.sp)
            Text(subtitle, fontSize = 12.sp, color = OnSurfaceMuted)
        }
        trailing?.invoke()
    }
}
