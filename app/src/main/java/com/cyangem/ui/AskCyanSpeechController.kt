package com.cyangem.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale

// =============================================================================
// HC-010 — Screen-local speech controller for Ask Cyan.
//
// Wraps Android SpeechRecognizer (voice → transcript) and Android
// TextToSpeech (answer → audio) with simple state callbacks. Does NOT touch
// MainViewModel, the existing app-wide VoiceEngine, or any engine provider.
// Lives inside AskCyanScreen via remember + DisposableEffect; cleaned up on
// screen leave via shutdown().
//
// Why a new controller, not VoiceEngine reuse?
//   VoiceEngine routes onResult → vm.sendMessage → engine path. Reusing it
//   for Ask Cyan would either invoke the engine flow (unwanted) or require
//   a MainViewModel rewrite. HC-010 keeps the change strictly screen-local.
// =============================================================================

internal sealed class VoiceCaptureState {
    object Idle : VoiceCaptureState()
    object Listening : VoiceCaptureState()
    /** Transcript captured. UI populates the input field with [text]. */
    data class Got(val text: String) : VoiceCaptureState()
    /** Recognizer ran but heard no speech / no match / timeout. */
    object NoMatch : VoiceCaptureState()
    /** Permission missing — UI shows a clear hint and offers a re-request. */
    object PermissionNeeded : VoiceCaptureState()
    /** Other recognizer or device error. */
    data class Error(val message: String) : VoiceCaptureState()
}

internal sealed class TtsState {
    object Idle : TtsState()
    /** TTS has started or is queued and engine is being initialized. */
    object Speaking : TtsState()
    /** Init or speak failed. */
    data class Error(val message: String) : TtsState()
}

/**
 * Screen-local SpeechRecognizer + TextToSpeech wrapper.
 *
 * **Threading:** all state callbacks fire on the Android main thread (the
 * SpeechRecognizer / TTS callbacks are main-thread by default).
 *
 * **Lifecycle:**
 *   - Construct on first compose via `remember { AskCyanSpeechController(...) }`.
 *   - Call `startListening()` / `stopListening()` from button onClicks.
 *   - Call `speak(text)` / `stopSpeaking()` for TTS.
 *   - Call `shutdown()` from `DisposableEffect.onDispose` to release both
 *     SpeechRecognizer and TextToSpeech native resources.
 *
 * **Permission:** the caller (composable) is responsible for confirming
 * RECORD_AUDIO is granted before calling startListening(). If granted=false
 * is reported by the system permission flow, surface
 * VoiceCaptureState.PermissionNeeded directly from the caller and avoid
 * calling startListening() without permission.
 */
internal class AskCyanSpeechController(
    private val context: Context,
    private val onState: (VoiceCaptureState) -> Unit,
    private val onTtsState: (TtsState) -> Unit
) {

    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var pendingSpeak: String? = null
    private var ttsRequested = false
    private var disposed = false

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            onState(VoiceCaptureState.Listening)
        }
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onError(error: Int) {
            if (disposed) return
            when (error) {
                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> onState(VoiceCaptureState.NoMatch)
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> onState(VoiceCaptureState.PermissionNeeded)
                else -> {
                    val msg = when (error) {
                        SpeechRecognizer.ERROR_NETWORK -> "network unavailable"
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "network timeout"
                        SpeechRecognizer.ERROR_AUDIO -> "audio capture error"
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "recognizer busy"
                        SpeechRecognizer.ERROR_CLIENT -> "recognizer client error"
                        SpeechRecognizer.ERROR_SERVER -> "recognizer server error"
                        else -> "recognizer error ($error)"
                    }
                    onState(VoiceCaptureState.Error(msg))
                }
            }
        }
        override fun onResults(results: Bundle?) {
            if (disposed) return
            val text = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                ?.trim()
            if (text.isNullOrBlank()) onState(VoiceCaptureState.NoMatch)
            else onState(VoiceCaptureState.Got(text))
        }
        override fun onPartialResults(partialResults: Bundle?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    /** Start listening. Caller must ensure RECORD_AUDIO is granted. */
    fun startListening() {
        if (disposed) return
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onState(VoiceCaptureState.Error("speech recognition unavailable on this device"))
            return
        }
        ensureRecognizer()
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.US)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
        }
        try {
            recognizer?.startListening(intent)
            // onReadyForSpeech will fire and we'll transition to Listening.
        } catch (e: Exception) {
            Log.e("CyanGem_AskCyanVoice", "startListening failed: ${e.message}", e)
            onState(VoiceCaptureState.Error(e.message ?: "start failed"))
        }
    }

    fun stopListening() {
        if (disposed) return
        runCatching { recognizer?.stopListening() }
        onState(VoiceCaptureState.Idle)
    }

    private fun ensureRecognizer() {
        if (recognizer == null) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(recognitionListener)
            }
        }
    }

    /** Speak [text] via the platform TextToSpeech engine. */
    fun speak(text: String) {
        if (disposed) return
        val clean = text
            .replace(Regex("[*_#`]"), "")
            .replace(Regex("\\[.*?\\]\\(.*?\\)"), "")
            .trim()
        if (clean.isBlank()) return

        ensureTts()

        if (!ttsReady) {
            // Engine still initializing — flag the request so init callback
            // can flush it. UI shows Speaking immediately for snappy feedback.
            pendingSpeak = clean
            onTtsState(TtsState.Speaking)
            return
        }
        speakNow(clean)
    }

    fun stopSpeaking() {
        if (disposed) return
        runCatching { tts?.stop() }
        onTtsState(TtsState.Idle)
    }

    private fun ensureTts() {
        if (tts != null || ttsRequested) return
        ttsRequested = true
        tts = TextToSpeech(context) { status ->
            if (disposed) return@TextToSpeech
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                tts?.setSpeechRate(0.95f)
                tts?.setPitch(1.0f)
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        if (!disposed) onTtsState(TtsState.Speaking)
                    }
                    override fun onDone(utteranceId: String?) {
                        if (!disposed) onTtsState(TtsState.Idle)
                    }
                    @Deprecated("deprecated in API 21")
                    override fun onError(utteranceId: String?) {
                        if (!disposed) onTtsState(TtsState.Error("TTS speech error"))
                    }
                    override fun onError(utteranceId: String?, errorCode: Int) {
                        if (!disposed) onTtsState(TtsState.Error("TTS error $errorCode"))
                    }
                })
                ttsReady = true
                pendingSpeak?.let { speakNow(it); pendingSpeak = null }
            } else {
                onTtsState(TtsState.Error("TTS init failed (status $status)"))
                pendingSpeak = null
            }
        }
    }

    private fun speakNow(text: String) {
        runCatching {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "ask-cyan-tts")
        }.onFailure {
            onTtsState(TtsState.Error(it.message ?: "speak failed"))
        }
    }

    /**
     * Release native resources. Idempotent. After shutdown, this controller
     * is unusable; the screen should drop the reference and create a new one
     * on next composition if needed.
     */
    fun shutdown() {
        if (disposed) return
        disposed = true
        runCatching { recognizer?.cancel() }
        runCatching { recognizer?.destroy() }
        recognizer = null
        runCatching { tts?.stop() }
        runCatching { tts?.shutdown() }
        tts = null
        ttsReady = false
        pendingSpeak = null
    }
}
