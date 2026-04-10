package com.cyangem.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

sealed class VoiceState {
    object Idle : VoiceState()
    object Listening : VoiceState()
    object Processing : VoiceState()
    data class Result(val text: String) : VoiceState()
    data class Error(val message: String) : VoiceState()
}

class VoiceEngine(private val context: Context) {

    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    // FIX 3: Queue speak requests that arrive before TTS is ready.
    // TTS init is async — without this, speak() on cold start silently drops.
    private val pendingSpeakQueue = mutableListOf<String>()

    private val _voiceState = MutableStateFlow<VoiceState>(VoiceState.Idle)
    val voiceState: StateFlow<VoiceState> = _voiceState.asStateFlow()

    var onResult: ((String) -> Unit)? = null
    var onWakeWord: (() -> Unit)? = null

    private val wakeWords = listOf("hey cyan", "hey gem", "hey gemini", "cyan", "ok cyan")

    // FIX 1: Guard flag prevents both onPartialResults AND onResults firing onWakeWord.
    private var wakeWordHandled = false

    // recognitionListener defined BEFORE init — Kotlin initializes top-down.
    private val recognitionListener = object : RecognitionListener {

        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val text = matches?.firstOrNull() ?: return
            val lower = text.lowercase()

            if (wakeWords.any { lower.contains(it) }) {
                if (!wakeWordHandled) {
                    Log.d("CyanGem_Voice", "Wake word in onResults: $text")
                    handleWakeWord()
                } else {
                    wakeWordHandled = false
                    _voiceState.value = VoiceState.Idle
                }
                return
            }

            // Actual user query
            Log.d("CyanGem_Voice", "Query received: $text")
            wakeWordHandled = false
            _voiceState.value = VoiceState.Result(text)
            onResult?.invoke(text)
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val partial = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull() ?: return

            if (!wakeWordHandled && wakeWords.any { partial.lowercase().contains(it) }) {
                Log.d("CyanGem_Voice", "Wake word in onPartialResults: $partial")
                // FIX 1: Stop BEFORE invoking onWakeWord.
                // Previously called startListening() while recognizer was still active
                // → ERROR_RECOGNIZER_BUSY → silent failure → pipeline dead.
                wakeWordHandled = true
                recognizer?.stopListening()
                handleWakeWord()
            }
        }

        override fun onError(error: Int) {
            val msg = when (error) {
                SpeechRecognizer.ERROR_NO_MATCH -> "No speech detected"
                SpeechRecognizer.ERROR_NETWORK -> "Network error"
                SpeechRecognizer.ERROR_AUDIO -> "Audio error"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Mic permission denied"
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
                else -> "Speech error $error"
            }
            Log.e("CyanGem_Voice", "Recognition error: $msg (code $error)")
            wakeWordHandled = false
            _voiceState.value = VoiceState.Error(msg)
        }

        override fun onReadyForSpeech(params: Bundle?) {
            Log.d("CyanGem_Voice", "Ready for speech")
            _voiceState.value = VoiceState.Listening
        }
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() { _voiceState.value = VoiceState.Processing }
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    init {
        initTts()
        initRecognizer()
    }

    // ── TTS ───────────────────────────────────────────────────────────────────

    private fun initTts() {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                tts?.setSpeechRate(0.95f)
                tts?.setPitch(1.0f)
                ttsReady = true
                Log.d("CyanGem_Voice", "TTS ready — flushing ${pendingSpeakQueue.size} queued item(s)")
                // FIX 3: Flush queued speak calls
                pendingSpeakQueue.forEach { speakInternal(it) }
                pendingSpeakQueue.clear()
            } else {
                Log.e("CyanGem_Voice", "TTS init failed: status $status")
            }
        }
    }

    fun speak(text: String) {
        if (text.isBlank()) return
        val clean = text
            .replace(Regex("[*_#`]"), "")
            .replace(Regex("\\[.*?\\]\\(.*?\\)"), "")
            .trim()
        if (clean.isBlank()) return
        if (!ttsReady) {
            Log.d("CyanGem_Voice", "TTS not ready — queuing: ${clean.take(40)}")
            pendingSpeakQueue.add(clean)
            return
        }
        speakInternal(clean)
    }

    private fun speakInternal(text: String) {
        Log.d("CyanGem_Voice", "TTS speaking: ${text.take(60)}")
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "cyangem_tts")
    }

    fun stopSpeaking() = tts?.stop()

    // ── Recognition ───────────────────────────────────────────────────────────

    private fun initRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.e("CyanGem_Voice", "Speech recognition not available")
            return
        }
        recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer?.setRecognitionListener(recognitionListener)
        Log.d("CyanGem_Voice", "Recognizer initialized")
    }

    /**
     * FIX 2: Speak audio confirmation before starting query session.
     * Previously the app silently started listening after wake — user had no
     * idea the mic was open. Now speaks "Yes?" then starts listening after
     * a 700ms delay (enough for TTS to finish the single word).
     */
    private fun handleWakeWord() {
        _voiceState.value = VoiceState.Idle
        speak("Yes?")
        Handler(Looper.getMainLooper()).postDelayed({
            onWakeWord?.invoke()
        }, 700L)
    }

    fun startListening() {
        wakeWordHandled = false
        _voiceState.value = VoiceState.Listening
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.US)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
        }
        try {
            recognizer?.startListening(intent)
            Log.d("CyanGem_Voice", "startListening called")
        } catch (e: Exception) {
            Log.e("CyanGem_Voice", "startListening failed: ${e.message}")
            _voiceState.value = VoiceState.Error("Mic error: ${e.message}")
        }
    }

    fun stopListening() {
        recognizer?.stopListening()
        _voiceState.value = VoiceState.Idle
    }

    fun destroy() {
        runCatching { recognizer?.destroy() }
        runCatching { tts?.shutdown() }
        pendingSpeakQueue.clear()
    }
}
