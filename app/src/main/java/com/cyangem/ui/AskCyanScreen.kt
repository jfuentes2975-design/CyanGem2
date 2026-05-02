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
// HC-007C — Ask Cyan in-app AI Frame.
//
// CyanGem2 acts as the prompt cockpit. The user types here, reviews a
// prompt preview, then taps Send to ChatGPT. The handoff path is
// HC-007A's ChatGptHandoff helper (handoffPromptToChatGpt). No API key,
// no engine call, no AccessibilityService.
//
// The existing Chat screen (ChatScreen.kt) is intentionally NOT modified
// by this patch and remains available as the backup handoff path.
// =============================================================================

private enum class AskCyanStatus(val display: String, val tone: AskCyanTone) {
    Ready("Ready", AskCyanTone.Neutral),
    PromptPrepared("Prompt prepared", AskCyanTone.Info),
    OpeningChatGpt("Opening ChatGPT…", AskCyanTone.Success),
    PromptCopied("Prompt copied", AskCyanTone.Info),
    ChatGptNotFound("ChatGPT not found — prompt copied", AskCyanTone.Warn)
}

private enum class AskCyanTone { Neutral, Info, Success, Warn }

@Composable
fun AskCyanScreen(vm: MainViewModel) {
    val context = LocalContext.current
    val isInstalled = remember { isChatGptInstalled(context) }

    var inputText by remember { mutableStateOf("") }
    var preparedPrompt by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf(AskCyanStatus.Ready) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp)
    ) {
        Spacer(Modifier.height(16.dp))

        Text(
            "Ask Cyan",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = OnSurface
        )
        Text(
            "Start here. CyanGem prepares your question, then hands it to ChatGPT when you're ready.",
            fontSize = 13.sp,
            color = OnSurfaceMuted
        )

        Spacer(Modifier.height(16.dp))

        // ── Privacy / how-it-works card ─────────────────────────────────────
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = SurfaceElevated,
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    "Uses installed ChatGPT app — no API key required.",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = OnSurface
                )
                Text(
                    "CyanGem2 does not store ChatGPT credentials.",
                    fontSize = 11.sp,
                    color = OnSurfaceMuted
                )
                Text(
                    "You may need to tap Send in ChatGPT.",
                    fontSize = 11.sp,
                    color = OnSurfaceMuted
                )
                Text(
                    if (isInstalled) "ChatGPT app: detected"
                    else "ChatGPT app: not detected — install from the Play Store",
                    fontSize = 11.sp,
                    color = if (isInstalled) SuccessColor else Color(0xFFFFB300),
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── Input ────────────────────────────────────────────────────────────
        OutlinedTextField(
            value = inputText,
            onValueChange = {
                inputText = it
                if (preparedPrompt != null && it != preparedPrompt) {
                    // User edited after preparing — clear the prepared state so
                    // the preview can't drift from the input.
                    preparedPrompt = null
                    status = AskCyanStatus.Ready
                }
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Ask something…", color = OnSurfaceMuted, fontSize = 14.sp) },
            minLines = 3,
            maxLines = 6,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = CyanPrimary,
                unfocusedBorderColor = Color(0xFF30363D),
                focusedTextColor = OnSurface,
                unfocusedTextColor = OnSurface,
                cursorColor = CyanPrimary
            )
        )
        Text(
            "Voice capture comes next.",
            fontSize = 11.sp,
            color = OnSurfaceMuted,
            modifier = Modifier.padding(top = 4.dp, start = 4.dp)
        )

        Spacer(Modifier.height(12.dp))

        // ── Status chip ──────────────────────────────────────────────────────
        StatusChip(status)

        // ── Prompt preview (visible only when prepared) ──────────────────────
        if (preparedPrompt != null) {
            Spacer(Modifier.height(12.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF0D1F1A),
                shape = RoundedCornerShape(10.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Prepared prompt",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = CyanPrimary
                    )
                    Text(
                        preparedPrompt!!,
                        fontSize = 13.sp,
                        color = OnSurface,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── Buttons ──────────────────────────────────────────────────────────
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Prepare Prompt — primary while not prepared
            Button(
                onClick = {
                    val trimmed = inputText.trim()
                    if (trimmed.isNotEmpty()) {
                        preparedPrompt = trimmed
                        status = AskCyanStatus.PromptPrepared
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = inputText.isNotBlank() && preparedPrompt == null,
                colors = ButtonDefaults.buttonColors(
                    containerColor = CyanPrimary,
                    contentColor = Color(0xFF003731)
                )
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Prepare Prompt", fontWeight = FontWeight.Bold)
            }

            // Send to ChatGPT — primary action when prepared
            Button(
                onClick = {
                    val prompt = preparedPrompt ?: return@Button
                    handoffPromptToChatGpt(context, prompt, vm)
                    status = if (isInstalled) AskCyanStatus.OpeningChatGpt else AskCyanStatus.ChatGptNotFound
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = preparedPrompt != null,
                colors = ButtonDefaults.buttonColors(
                    containerColor = CyanPrimary,
                    contentColor = Color(0xFF003731)
                )
            ) {
                Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Send to ChatGPT", fontWeight = FontWeight.Bold)
            }

            // Copy Prompt — secondary
            OutlinedButton(
                onClick = {
                    val prompt = preparedPrompt ?: return@OutlinedButton
                    copyToClipboard(context, prompt)
                    status = AskCyanStatus.PromptCopied
                    vm.showSnackbar("Prompt copied")
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = preparedPrompt != null
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Copy Prompt")
            }

            // Clear — tertiary
            TextButton(
                onClick = {
                    inputText = ""
                    preparedPrompt = null
                    status = AskCyanStatus.Ready
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Clear")
            }
        }
    }
}

@Composable
private fun StatusChip(status: AskCyanStatus) {
    val (bg, fg) = when (status.tone) {
        AskCyanTone.Neutral -> Color(0x1AFFFFFF) to OnSurfaceMuted
        AskCyanTone.Info -> CyanPrimary.copy(alpha = 0.18f) to CyanPrimary
        AskCyanTone.Success -> SuccessColor.copy(alpha = 0.18f) to SuccessColor
        AskCyanTone.Warn -> Color(0x33FFB300) to Color(0xFFFFB300)
    }
    Surface(
        color = bg,
        shape = RoundedCornerShape(20.dp)
    ) {
        Text(
            status.display,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = fg
        )
    }
}
