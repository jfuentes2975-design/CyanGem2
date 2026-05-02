package com.cyangem.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.cyangem.ui.theme.*
import com.cyangem.viewmodel.AskCyanAnswerState
import com.cyangem.viewmodel.MainViewModel

// =============================================================================
// HC-011 — Unified text + voice in-app answers inside Ask Cyan.
//
// Both typed and voice prompts land in `inputText` (Compose state), get
// reviewed via Prepare Prompt, then go through `vm.askCyanInApp(prompt)`
// which uses MainViewModel's separate Ask Cyan engine (CyanGem system prompt).
// The answer renders inside this same screen via the AnswerCard composable.
// ChatGPT app handoff stays as the backup path.
//
// HC-011 additions on top of HC-010:
//   - In-app answer mode readiness badge (Ready / Needs key) at top
//   - Action button row on the Answer card (Read Aloud, Stop, Copy Answer,
//     Clear Answer, Open in ChatGPT as Backup)
//   - rememberSaveable for inputText and preparedPrompt so they survive tab
//     switches and configuration changes (within Compose's saved-state rules)
//   - Polished NotConfigured copy with explicit Settings hint
// =============================================================================

private enum class AskCyanStatus(val display: String, val tone: AskCyanTone) {
    Ready("Ready", AskCyanTone.Neutral),
    PromptPrepared("Prompt prepared", AskCyanTone.Info),
    AskingCyan("Asking CyanGem…", AskCyanTone.Info),
    AnswerReady("Answer ready", AskCyanTone.Success),
    InAppNotConfigured("In-app AI not configured", AskCyanTone.Warn),
    OpenedChatGptAsBackup("Opened ChatGPT as backup", AskCyanTone.Success),
    PromptCopied("Prompt copied", AskCyanTone.Info),
    AnswerCopied("Answer copied", AskCyanTone.Info),
    AnswerError("Couldn't reach in-app AI", AskCyanTone.Warn),
    TranscriptCaptured("Transcript captured. Review, then tap Prepare Prompt.", AskCyanTone.Info)
}

private enum class AskCyanTone { Neutral, Info, Success, Warn }

@Composable
fun AskCyanScreen(vm: MainViewModel) {
    val context = LocalContext.current
    val isInstalled = remember { isChatGptInstalled(context) }

    // HC-011 — survive tab switches and config changes
    var inputText by rememberSaveable { mutableStateOf("") }
    var preparedPrompt by rememberSaveable { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf(AskCyanStatus.Ready) }

    // HC-010 — voice + TTS state (intentionally NOT saved across tab switch;
    // a fresh controller is created on re-entry to avoid stale resource refs)
    var voiceState by remember { mutableStateOf<VoiceCaptureState>(VoiceCaptureState.Idle) }
    var ttsState by remember { mutableStateOf<TtsState>(TtsState.Idle) }

    val controller = remember {
        AskCyanSpeechController(
            context = context,
            onState = { newState ->
                voiceState = newState
                if (newState is VoiceCaptureState.Got) {
                    inputText = newState.text
                    preparedPrompt = null
                    if (vm.askCyanAnswer.value !is AskCyanAnswerState.Idle) {
                        vm.resetAskCyanAnswer()
                    }
                    status = AskCyanStatus.TranscriptCaptured
                }
            },
            onTtsState = { ttsState = it }
        )
    }
    DisposableEffect(Unit) {
        onDispose { controller.shutdown() }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) controller.startListening()
        else voiceState = VoiceCaptureState.PermissionNeeded
    }

    val answerState by vm.askCyanAnswer.collectAsState()
    val inAppReady by vm.inAppReady.collectAsState()

    // Ensure readiness reflects current disk state when the screen enters
    // composition (e.g., user returned from Settings after saving a key).
    LaunchedEffect(Unit) { vm.refreshInAppReady() }

    // Map answer state and voice events into the local status chip.
    LaunchedEffect(answerState) {
        // Don't clobber a transient TranscriptCaptured / PromptCopied / AnswerCopied
        // unless answerState moved into a non-idle state.
        val a = answerState
        when (a) {
            is AskCyanAnswerState.NotConfigured -> status = AskCyanStatus.InAppNotConfigured
            is AskCyanAnswerState.Loading -> status = AskCyanStatus.AskingCyan
            is AskCyanAnswerState.Streaming -> status = AskCyanStatus.AskingCyan
            is AskCyanAnswerState.Answer -> status = AskCyanStatus.AnswerReady
            is AskCyanAnswerState.Error -> status = AskCyanStatus.AnswerError
            is AskCyanAnswerState.Idle -> {
                if (preparedPrompt != null) status = AskCyanStatus.PromptPrepared
                else if (inputText.isBlank()) status = AskCyanStatus.Ready
                // else leave whatever transient status is showing
            }
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
            "Voice capture stays in CyanGem until you choose where to send it.",
            fontSize = 13.sp,
            color = OnSurfaceMuted
        )

        Spacer(Modifier.height(12.dp))

        // ── HC-011 — In-app answer mode readiness badge ─────────────────────
        InAppReadinessBadge(inAppReady)

        Spacer(Modifier.height(12.dp))

        // ── How it works / privacy card ─────────────────────────────────────
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
                    "Ask in CyanGem shows answers here when configured.",
                    fontSize = 12.sp, fontWeight = FontWeight.Medium, color = OnSurface
                )
                Text(
                    "Send to ChatGPT opens the ChatGPT app as backup.",
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

        Spacer(Modifier.height(8.dp))

        VoiceMicRow(
            voiceState = voiceState,
            onMicTap = {
                val isListening = voiceState is VoiceCaptureState.Listening
                if (isListening) {
                    controller.stopListening()
                } else {
                    val granted = ContextCompat.checkSelfPermission(
                        context, Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED
                    if (granted) controller.startListening()
                    else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
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
            AnswerCard(
                state = answerState,
                ttsState = ttsState,
                onReadAloud = {
                    val text = (answerState as? AskCyanAnswerState.Answer)?.text
                    if (!text.isNullOrBlank()) controller.speak(text)
                },
                onStopReading = { controller.stopSpeaking() },
                onCopyAnswer = {
                    val text = (answerState as? AskCyanAnswerState.Answer)?.text
                    if (!text.isNullOrBlank()) {
                        copyToClipboard(context, text)
                        status = AskCyanStatus.AnswerCopied
                        vm.showSnackbar("Answer copied")
                    }
                },
                onClearAnswer = {
                    controller.stopSpeaking()
                    vm.resetAskCyanAnswer()
                    status = if (preparedPrompt != null) AskCyanStatus.PromptPrepared
                             else AskCyanStatus.Ready
                },
                onOpenInChatGptAsBackup = {
                    val prompt = preparedPrompt ?: inputText.trim().ifBlank { null }
                    if (!prompt.isNullOrBlank()) {
                        handoffPromptToChatGpt(context, prompt, vm)
                        status = AskCyanStatus.OpenedChatGptAsBackup
                    } else {
                        vm.showSnackbar("Type or speak a question first")
                    }
                }
            )
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

            // Ask in CyanGem — visually primary regardless of key (we always
            // surface a friendly NotConfigured message when key is missing).
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
                    controller.stopSpeaking()
                    status = AskCyanStatus.Ready
                    voiceState = VoiceCaptureState.Idle
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

// HC-011 — readiness badge
@Composable
private fun InAppReadinessBadge(inAppReady: Boolean) {
    val (bg, fg, label) = if (inAppReady) {
        Triple(SuccessColor.copy(alpha = 0.18f), SuccessColor, "In-app answer mode: Ready")
    } else {
        Triple(Color(0x33FFB300), Color(0xFFFFB300), "In-app answer mode: Needs key")
    }
    Surface(color = bg, shape = RoundedCornerShape(20.dp)) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                if (inAppReady) Icons.Default.CheckCircle else Icons.Default.Info,
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(14.dp)
            )
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = fg)
        }
    }
}

// HC-010 — mic row
@Composable
private fun VoiceMicRow(
    voiceState: VoiceCaptureState,
    onMicTap: () -> Unit
) {
    val isListening = voiceState is VoiceCaptureState.Listening
    val label: String = when (voiceState) {
        is VoiceCaptureState.Idle -> "Tap to speak"
        is VoiceCaptureState.Listening -> "Listening…"
        is VoiceCaptureState.Got -> "Transcript captured"
        is VoiceCaptureState.NoMatch -> "Could not hear that — tap to try again"
        is VoiceCaptureState.PermissionNeeded -> "Microphone permission needed"
        is VoiceCaptureState.Error -> "Voice error: ${voiceState.message}"
    }
    val labelColor = when (voiceState) {
        is VoiceCaptureState.Listening -> CyanPrimary
        is VoiceCaptureState.Got -> SuccessColor
        is VoiceCaptureState.NoMatch -> Color(0xFFFFB300)
        is VoiceCaptureState.PermissionNeeded -> Color(0xFFFFB300)
        is VoiceCaptureState.Error -> Color(0xFFFFB300)
        else -> OnSurfaceMuted
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FloatingActionButton(
            onClick = onMicTap,
            modifier = Modifier.size(44.dp),
            containerColor = if (isListening) ErrorColor else CyanPrimary,
            contentColor = Color(0xFF003731)
        ) {
            Icon(
                if (isListening) Icons.Default.MicOff else Icons.Default.Mic,
                contentDescription = if (isListening) "Stop listening" else "Tap to speak",
                modifier = Modifier.size(22.dp)
            )
        }
        Text(
            label,
            fontSize = 12.sp,
            color = labelColor,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
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
private fun AnswerCard(
    state: AskCyanAnswerState,
    ttsState: TtsState,
    onReadAloud: () -> Unit,
    onStopReading: () -> Unit,
    onCopyAnswer: () -> Unit,
    onClearAnswer: () -> Unit,
    onOpenInChatGptAsBackup: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = SurfaceElevated,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "CyanGem Answer",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = CyanPrimary
            )
            when (state) {
                is AskCyanAnswerState.Idle -> { /* not visible */ }

                is AskCyanAnswerState.NotConfigured -> {
                    Text(
                        "In-app answers need an OpenRouter key.",
                        fontSize = 13.sp, color = OnSurface
                    )
                    Text(
                        "Set up In-App Answers in Settings, or use ChatGPT backup.",
                        fontSize = 12.sp, color = OnSurfaceMuted
                    )
                    Spacer(Modifier.height(4.dp))
                    OutlinedButton(onClick = onOpenInChatGptAsBackup) {
                        Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Open in ChatGPT as Backup")
                    }
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
                    Spacer(Modifier.height(6.dp))
                    AnswerActionRow(
                        ttsState = ttsState,
                        onReadAloud = onReadAloud,
                        onStopReading = onStopReading,
                        onCopyAnswer = onCopyAnswer,
                        onClearAnswer = onClearAnswer,
                        onOpenInChatGptAsBackup = onOpenInChatGptAsBackup
                    )
                    if (ttsState is TtsState.Error) {
                        Text(
                            "Could not start read aloud.",
                            fontSize = 11.sp,
                            color = Color(0xFFFFB300)
                        )
                        Text(
                            ttsState.message,
                            fontSize = 11.sp,
                            color = OnSurfaceMuted
                        )
                    }
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
                    Spacer(Modifier.height(4.dp))
                    OutlinedButton(onClick = onOpenInChatGptAsBackup) {
                        Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Open in ChatGPT as Backup")
                    }
                }
            }
        }
    }
}

// HC-011 — answer card actions: Read Aloud / Stop / Copy / Clear / Backup
@Composable
private fun AnswerActionRow(
    ttsState: TtsState,
    onReadAloud: () -> Unit,
    onStopReading: () -> Unit,
    onCopyAnswer: () -> Unit,
    onClearAnswer: () -> Unit,
    onOpenInChatGptAsBackup: () -> Unit
) {
    val isSpeaking = ttsState is TtsState.Speaking
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (isSpeaking) {
                OutlinedButton(onClick = onStopReading, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.VolumeOff, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Stop Reading")
                }
            } else {
                OutlinedButton(onClick = onReadAloud, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.VolumeUp, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Read Aloud")
                }
            }
            OutlinedButton(onClick = onCopyAnswer, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Copy Answer")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(onClick = onOpenInChatGptAsBackup, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Open in ChatGPT")
            }
            TextButton(onClick = onClearAnswer, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Clear Answer")
            }
        }
    }
}
