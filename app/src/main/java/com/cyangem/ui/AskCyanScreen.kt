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
// HC-010 — Ask Cyan with voice input + answer readback.
//
// Adds:
//   - Mic FAB to capture a transcript via Android SpeechRecognizer.
//     Transcript is placed in the input field — never auto-sent.
//   - Read Aloud / Stop Reading toggle on the Answer card when an in-app
//     answer is present (TextToSpeech).
//   - Runtime RECORD_AUDIO permission request via Compose ActivityResult.
//   - Updated copy clarifying the in-app vs backup paths.
//
// Both speech and TTS live in a screen-local AskCyanSpeechController. No
// MainViewModel changes. No engine changes. The legacy VoiceEngine.kt is
// untouched and still works for any other consumer.
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

    // HC-010 — voice + TTS state
    var voiceState by remember { mutableStateOf<VoiceCaptureState>(VoiceCaptureState.Idle) }
    var ttsState by remember { mutableStateOf<TtsState>(TtsState.Idle) }

    val controller = remember {
        AskCyanSpeechController(
            context = context,
            onState = { newState ->
                voiceState = newState
                if (newState is VoiceCaptureState.Got) {
                    inputText = newState.text
                    // Transcript edits clear any prior prepared prompt and answer.
                    preparedPrompt = null
                    if (vm.askCyanAnswer.value !is AskCyanAnswerState.Idle) {
                        vm.resetAskCyanAnswer()
                    }
                }
            },
            onTtsState = { ttsState = it }
        )
    }
    DisposableEffect(Unit) {
        onDispose { controller.shutdown() }
    }

    // HC-010 — permission launcher for RECORD_AUDIO
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            controller.startListening()
        } else {
            voiceState = VoiceCaptureState.PermissionNeeded
        }
    }

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
            "Voice capture stays in CyanGem until you choose where to send it.",
            fontSize = 13.sp,
            color = OnSurfaceMuted
        )

        Spacer(Modifier.height(16.dp))

        // ── How it works / privacy card (HC-010 copy) ──────────────────────
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

        // ── Input ────────────────────────────────────────────────────────────
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

        // ── Mic row (HC-010) ─────────────────────────────────────────────────
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
                        ?: (answerState as? AskCyanAnswerState.Streaming)?.partial
                    if (!text.isNullOrBlank()) controller.speak(text)
                },
                onStopReading = { controller.stopSpeaking() }
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

// HC-010 — mic row helper
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
    onStopReading: () -> Unit
) {
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
                is AskCyanAnswerState.Idle -> { /* not visible */ }
                is AskCyanAnswerState.NotConfigured -> {
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
                    // HC-010 — Read Aloud / Stop Reading
                    Spacer(Modifier.height(4.dp))
                    val isSpeaking = ttsState is TtsState.Speaking
                    val isError = ttsState is TtsState.Error
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (isSpeaking) {
                            OutlinedButton(onClick = onStopReading) {
                                Icon(Icons.Default.VolumeOff, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Stop Reading")
                            }
                        } else {
                            OutlinedButton(onClick = onReadAloud) {
                                Icon(Icons.Default.VolumeUp, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Read Aloud")
                            }
                        }
                        if (isError) {
                            Text(
                                (ttsState as TtsState.Error).message,
                                fontSize = 11.sp,
                                color = Color(0xFFFFB300)
                            )
                        }
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
                    Text(
                        "Tip: tap \"Send to ChatGPT (backup)\" to continue.",
                        fontSize = 12.sp, color = OnSurfaceMuted
                    )
                }
            }
        }
    }
}
