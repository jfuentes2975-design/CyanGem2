package com.cyangem.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyangem.gemini.ChatMessage
import com.cyangem.ui.theme.*
import com.cyangem.viewmodel.MainViewModel

// =============================================================================
// HC-007B — Ask ChatGPT (was: Chat). Typed Send → ChatGPT app handoff.
//
// The Send button bypasses the old engine path and calls
// handoffPromptToChatGpt(context, prompt, vm) directly. No API key required.
// No engine call. The helper is in HC-007A's ChatGptHandoff.kt.
//
// Voice (mic FAB) still routes through MainViewModel.startVoiceQuery →
// initVoice's onResult → vm.sendMessage and remains tied to the old engine
// path. Voice handoff is HC-007D scope.
// =============================================================================

@Composable
fun ChatScreen(vm: MainViewModel) {
    val uiState by vm.uiState.collectAsState()
    val listState = rememberLazyListState()
    var inputText by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        ChatTopBar(
            activeGemName = uiState.activeGem?.let { "${it.emoji} ${it.name}" } ?: "CyanGem",
            onClear = vm::clearChat
        )

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (uiState.chatMessages.isEmpty()) {
                item { EmptyChat(activeGemName = uiState.activeGem?.name ?: "CyanGem") }
            }
            items(uiState.chatMessages) { msg -> ChatBubble(message = msg) }
        }

        // Voice mic bar (always available — no API-key gate)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val isListening = uiState.isListening
            FloatingActionButton(
                onClick = {
                    if (isListening) vm.stopVoiceQuery()
                    else vm.startVoiceQuery()
                },
                modifier = Modifier.size(44.dp),
                containerColor = if (isListening) ErrorColor else CyanPrimary,
                contentColor = Color(0xFF003731)
            ) {
                Icon(
                    if (isListening) Icons.Default.MicOff else Icons.Default.Mic,
                    contentDescription = "Voice",
                    modifier = Modifier.size(20.dp)
                )
            }
            Text(
                "Tap mic to speak (voice handoff lands later), or type below.",
                fontSize = 12.sp,
                color = OnSurfaceMuted,
                modifier = Modifier.weight(1f)
            )
        }

        ChatInputBar(
            text = inputText,
            onTextChange = { inputText = it },
            onSend = {
                val txt = inputText.trim()
                if (txt.isNotEmpty()) {
                    handoffPromptToChatGpt(context, txt, vm)
                    inputText = ""
                }
            },
            enabled = true,
            modifier = Modifier.focusRequester(focusRequester)
        )
    }
}

@Composable
private fun ChatTopBar(activeGemName: String, onClear: () -> Unit) {
    Surface(color = SurfaceCard) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Ask ChatGPT", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = OnSurface)
                Text(activeGemName, fontSize = 12.sp, color = CyanPrimary)
            }
            IconButton(onClick = onClear) {
                Icon(Icons.Default.DeleteSweep, contentDescription = "Clear chat", tint = OnSurfaceMuted)
            }
        }
    }
}

@Composable
private fun EmptyChat(activeGemName: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("💎", fontSize = 40.sp)
        Text(activeGemName, fontWeight = FontWeight.Medium, color = OnSurface, fontSize = 16.sp)
        Text(
            "Type a question and tap send.\n" +
            "Your question will open in the ChatGPT app.\n" +
            "No API key required.\n" +
            "You may need to tap Send in ChatGPT.",
            fontSize = 13.sp,
            color = OnSurfaceMuted,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
private fun ChatBubble(message: ChatMessage) {
    val isUser = message.role == "user"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(
                        Brush.radialGradient(listOf(CyanPrimary, CyanSecondary)),
                        RoundedCornerShape(8.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text("G", fontWeight = FontWeight.Bold, color = Color(0xFF003731), fontSize = 12.sp)
            }
            Spacer(Modifier.width(6.dp))
        }
        Surface(
            color = if (isUser) CyanPrimary.copy(alpha = 0.15f) else SurfaceElevated,
            shape = RoundedCornerShape(
                topStart = if (isUser) 16.dp else 4.dp,
                topEnd = if (isUser) 4.dp else 16.dp,
                bottomStart = 16.dp, bottomEnd = 16.dp
            ),
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Text(
                text = message.text,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                color = OnSurface,
                fontSize = 14.sp
            )
        }
    }
}

@Composable
private fun ChatInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(color = SurfaceCard) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .navigationBarsPadding(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier
                    .weight(1f)
                    .then(modifier),
                placeholder = { Text("Ask anything…", color = OnSurfaceMuted, fontSize = 14.sp) },
                enabled = enabled,
                maxLines = 4,
                shape = RoundedCornerShape(20.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CyanPrimary,
                    unfocusedBorderColor = Color(0xFF30363D),
                    focusedTextColor = OnSurface,
                    unfocusedTextColor = OnSurface,
                    cursorColor = CyanPrimary
                )
            )
            FloatingActionButton(
                onClick = onSend,
                modifier = Modifier.size(48.dp),
                containerColor = if (text.isNotBlank() && enabled) CyanPrimary else SurfaceElevated,
                contentColor = if (text.isNotBlank() && enabled) Color(0xFF003731) else OnSurfaceMuted
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", modifier = Modifier.size(20.dp))
            }
        }
    }
}
