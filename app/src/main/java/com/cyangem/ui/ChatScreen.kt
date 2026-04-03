package com.cyangem.ui

import androidx.compose.animation.*
import androidx.compose.foundation.*
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyangem.gemini.ChatMessage
import com.cyangem.ui.theme.*
import com.cyangem.viewmodel.MainViewModel
import kotlinx.coroutines.launch

@Composable
fun ChatScreen(vm: MainViewModel) {
    val uiState by vm.uiState.collectAsState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var inputText by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    // Auto-scroll to bottom on new messages
    LaunchedEffect(uiState.chatMessages.size, uiState.streamingText) {
        if (uiState.chatMessages.isNotEmpty() || uiState.streamingText.isNotEmpty()) {
            scope.launch {
                listState.animateScrollToItem(
                    maxOf(0, uiState.chatMessages.size + (if (uiState.streamingText.isNotEmpty()) 1 else 0) - 1)
                )
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top bar
        ChatTopBar(
            activeGemName = uiState.activeGem?.let { "${it.emoji} ${it.name}" } ?: "CyanGem",
            hasApiKey = uiState.hasApiKey,
            onClear = vm::clearChat
        )

        // No API key warning
        if (!uiState.hasApiKey) {
            NoApiKeyBanner()
        }

        // Message list
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (uiState.chatMessages.isEmpty() && !uiState.isGeminiThinking) {
                item { EmptyChat(activeGemName = uiState.activeGem?.name ?: "CyanGem") }
            }

            items(uiState.chatMessages) { msg ->
                ChatBubble(message = msg)
            }

            // Streaming bubble
            if (uiState.streamingText.isNotEmpty()) {
                item {
                    StreamingBubble(text = uiState.streamingText)
                }
            }

            // Thinking indicator
            if (uiState.isGeminiThinking && uiState.streamingText.isEmpty()) {
                item { ThinkingIndicator() }
            }

            // Error
            uiState.geminiError?.let { err ->
                item {
                    ErrorBubble(error = err)
                }
            }
        }

        // Photo analyze shortcut
        AnimatedVisibility(
            visible = uiState.hasApiKey,
            enter = slideInVertically { it } + fadeIn()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QuickActionChip(
                    label = "📷 Analyze glasses photo",
                    onClick = { vm.analyzeLatestGlassesPhoto() },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Input bar
        ChatInputBar(
            text = inputText,
            onTextChange = { inputText = it },
            onSend = {
                val txt = inputText.trim()
                if (txt.isNotEmpty()) {
                    vm.sendMessage(txt)
                    inputText = ""
                }
            },
            enabled = uiState.hasApiKey && !uiState.isGeminiThinking,
            modifier = Modifier.focusRequester(focusRequester)
        )
    }
}

@Composable
private fun ChatTopBar(activeGemName: String, hasApiKey: Boolean, onClear: () -> Unit) {
    Surface(color = SurfaceCard) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Chat", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = OnSurface)
                Text(activeGemName, fontSize = 12.sp, color = CyanPrimary)
            }
            IconButton(onClick = onClear) {
                Icon(Icons.Default.DeleteSweep, contentDescription = "Clear chat", tint = OnSurfaceMuted)
            }
        }
    }
}

@Composable
private fun NoApiKeyBanner() {
    Surface(color = Color(0xFF1A1000)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Key, contentDescription = null, tint = Color(0xFFFFB300), modifier = Modifier.size(16.dp))
            Text(
                "Add your Gemini API key in Settings to start chatting",
                fontSize = 12.sp, color = Color(0xFFFFB300)
            )
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
        Text("Ask anything or tap 📷 to analyze\nwhat your glasses see",
            fontSize = 13.sp, color = OnSurfaceMuted, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
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
                color = if (isUser) OnSurface else OnSurface,
                fontSize = 14.sp
            )
        }
    }
}

@Composable
private fun StreamingBubble(text: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
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
        Surface(
            color = SurfaceElevated,
            shape = RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp),
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(text = text, color = OnSurface, fontSize = 14.sp)
                Text("▌", color = CyanPrimary, fontSize = 14.sp) // cursor
            }
        }
    }
}

@Composable
private fun ThinkingIndicator() {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
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
        Surface(color = SurfaceElevated, shape = RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp)) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(3) {
                    CircularProgressIndicator(modifier = Modifier.size(6.dp), color = CyanPrimary, strokeWidth = 1.5.dp)
                }
            }
        }
    }
}

@Composable
private fun ErrorBubble(error: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = ErrorColor.copy(alpha = 0.1f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = ErrorColor, modifier = Modifier.size(16.dp))
            Text(error, fontSize = 12.sp, color = ErrorColor)
        }
    }
}

@Composable
private fun QuickActionChip(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        color = SurfaceElevated,
        shape = RoundedCornerShape(20.dp)
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            fontSize = 12.sp, color = CyanPrimary
        )
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
