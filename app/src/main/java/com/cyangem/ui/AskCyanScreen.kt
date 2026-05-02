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
import com.cyangem.viewmodel.AskCyanAnswerState
import com.cyangem.viewmodel.MainViewModel

// =============================================================================
// HC-009A — Ask Cyan with clearer no-key wording.
//
// Only change vs HC-009: the NotConfigured branch in AnswerCard now reads:
//   "In-app answers need an OpenRouter key. Go to Settings → In-App Answers,
//   or use Send to ChatGPT as backup."
//
// All other behavior identical to HC-009.
// =============================================================================

private enum class AskCyanStatus(val display: String, val tone: AskCyanTone) {
    Ready("Ready", AskCyanTone.Neutral),
    PromptPrepared("Prompt prepared", AskCyanTone.Info),
    AskingCyan("Asking CyanGem…", AskCyanTone.Info),
    AnswerReady("Answer ready", AskCyanTone.Success),
    InAppNotConfigured("In-app AI not configured", AskCyanTone.Warn),
    OpenedChatGptAsBackup("Opened ChatGPT as backup", AskCyanTone.Success),
    PromptCopied("Prompt copied", AskCyanTone.Info),
    AnswerError("Couldn't reach in-app AI", AskCyanTone.Warn)
}

private enum class AskCyanTone { Neutral, Info, Success, Warn }

@Composable
fun AskCyanScreen(vm: MainViewModel) {
    val context = LocalContext.current
    val isInstalled = remember { isChatGptInstalled(context) }

    var inputText by remember { mutableStateOf("") }
    var preparedPrompt by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf(AskCyanStatus.Ready) }

    val answerState by vm.askCyanAnswer.collectAsState()

    LaunchedEffect(answerState) {
        status = when (val a = answerState) {
            is AskCyanAnswerState.Idle -> {
                if (preparedPrompt != null) AskCyanStatus.PromptPrepared
                else if (inputText.isBlank()) AskCyanStatus.Ready
                else status
            }
            is AskCyanAnswerState.NotConfigured -> AskCyanStatus.InAppNotConfigured
            is AskCyanAnswerState.Loading -> AskCyanStatus.AskingCyan
            is AskCyanAnswerState.Streaming -> AskCyanStatus.AskingCyan
            is AskCyanAnswerState.Answer -> AskCyanStatus.AnswerReady
            is AskCyanAnswerState.Error -> AskCyanStatus.AnswerError
        }
    }

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
            "Ask in CyanGem for an in-app answer. ChatGPT app is always available as a backup.",
            fontSize = 13.sp,
            color = OnSurfaceMuted
        )

        Spacer(Modifier.height(16.dp))

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
                    "Primary: Ask in CyanGem (in-app, free with an OpenRouter key).",
                    fontSize = 12.sp, fontWeight = FontWeight.Medium, color = OnSurface
                )
                Text(
                    "Backup: Send to the installed ChatGPT app — no API key required.",
                    fontSize = 11.sp, color = OnSurfaceMuted
                )
                Text(
                    "CyanGem2 does not store ChatGPT credentials.",
                    fontSize = 11.sp, color = OnSurfaceMuted
                )
                Text(
                    if (isInstalled) "ChatGPT app: detected (backup ready)"
                    else "ChatGPT app: not detected — backup will fall back to share sheet",
                    fontSize = 11.sp,
                    color = if (isInstalled) SuccessColor else Color(0xFFFFB300),
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = inputText,
            onValueChange = {
                inputText = it
                if (preparedPrompt != null && it != preparedPrompt) {
                    preparedPrompt = null
                    if (answerState !is AskCyanAnswerState.Idle) vm.resetAskCyanAnswer()
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

        StatusChip(status)

        if (preparedPrompt != null) {
            Spacer(Modifier.height(12.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF0D1F1A),
                shape = RoundedCornerShape(10.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Prepared prompt", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CyanPrimary)
                    Text(
                        preparedPrompt!!,
                        fontSize = 13.sp,
                        color = OnSurface,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        if (answerState !is AskCyanAnswerState.Idle) {
            Spacer(Modifier.height(12.dp))
            AnswerCard(answerState)
        }

        Spacer(Modifier.height(16.dp))

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    val trimmed = inputText.trim()
                    if (trimmed.isNotEmpty()) {
                        preparedPrompt = trimmed
                        if (answerState !is AskCyanAnswerState.Idle) vm.resetAskCyanAnswer()
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

            Button(
                onClick = {
                    val prompt = preparedPrompt ?: return@Button
                    vm.askCyanInApp(prompt)
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = preparedPrompt != null
                    && answerState !is AskCyanAnswerState.Loading
                    && answerState !is AskCyanAnswerState.Streaming,
                colors = ButtonDefaults.buttonColors(
                    containerColor = CyanPrimary,
                    contentColor = Color(0xFF003731)
                )
            ) {
                Icon(Icons.Default.Stars, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Ask in CyanGem", fontWeight = FontWeight.Bold)
            }

            OutlinedButton(
                onClick = {
                    val prompt = preparedPrompt ?: return@OutlinedButton
                    handoffPromptToChatGpt(context, prompt, vm)
                    status = AskCyanStatus.OpenedChatGptAsBackup
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = preparedPrompt != null
            ) {
                Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Send to ChatGPT (backup)")
            }

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

            TextButton(
                onClick = {
                    inputText = ""
                    preparedPrompt = null
                    vm.resetAskCyanAnswer()
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
    Surface(color = bg, shape = RoundedCornerShape(20.dp)) {
        Text(
            status.display,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = fg
        )
    }
}

@Composable
private fun AnswerCard(state: AskCyanAnswerState) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = SurfaceElevated,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "CyanGem answer",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = CyanPrimary
            )
            when (state) {
                is AskCyanAnswerState.Idle -> {
                    // Not visible — caller guards.
                }
                is AskCyanAnswerState.NotConfigured -> {
                    // HC-009A — exact wording per spec
                    Text(
                        "In-app answers need an OpenRouter key. Go to Settings → In-App Answers, or use Send to ChatGPT as backup.",
                        fontSize = 13.sp, color = OnSurface
                    )
                }
                is AskCyanAnswerState.Loading -> {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            color = CyanPrimary,
                            strokeWidth = 2.dp
                        )
                        Text("Asking CyanGem…", fontSize = 13.sp, color = OnSurfaceMuted)
                    }
                }
                is AskCyanAnswerState.Streaming -> {
                    Text(state.partial, fontSize = 14.sp, color = OnSurface)
                    Text("…", fontSize = 14.sp, color = CyanPrimary)
                }
                is AskCyanAnswerState.Answer -> {
                    Text(state.text, fontSize = 14.sp, color = OnSurface)
                }
                is AskCyanAnswerState.Error -> {
                    Text(
                        "Couldn't reach in-app AI.",
                        fontSize = 13.sp, color = Color(0xFFFFB300)
                    )
                    Text(
                        state.message,
                        fontSize = 12.sp, color = OnSurfaceMuted
                    )
                    Text(
                        "Tip: tap \"Send to ChatGPT (backup)\" to continue.",
                        fontSize = 12.sp, color = OnSurfaceMuted
                    )
                }
            }
        }
    }
}
