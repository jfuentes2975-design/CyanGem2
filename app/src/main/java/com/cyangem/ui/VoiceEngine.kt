package com.cyangem.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
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

    private val _voiceState = MutableStateFlow<VoiceState>(VoiceState.Idle)
    val voiceState: StateFlow<VoiceState> = _voiceState.asStateFlow()

    var onResult: ((String) -> Unit)? = null
    var onWakeWord: (() -> Unit)? = null

    private val wakeWords = listOf("hey cyan", "hey gem", "hey gemini", "cyan", "ok cyan")

    // FIX: recognitionListener defined BEFORE init block so it exists when initRecognizer() runs
    private val recognitionListener = object : RecognitionListener {
        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val text = matches?.firstOrNull() ?: return
            val lower = text.lowercase()
            if (wakeWords.any { lower.contains(it) }) {
                onWakeWord?.invoke()
                _voiceState.value = VoiceState.Idle
                return
            }
            _voiceState.value = VoiceState.Result(text)
            onResult?.invoke(text)
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val partial = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull() ?: return
            if (wakeWords.any { partial.lowercase().contains(it) }) {
                onWakeWord?.invoke()
            }
        }

        override fun onError(error: Int) {
            val msg = when (error) {
                SpeechRecognizer.ERROR_NO_MATCH -> "No speech detected"
                SpeechRecognizer.ERROR_NETWORK -> "Network error"
                SpeechRecognizer.ERROR_AUDIO -> "Audio error"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Mic permission denied"
                else -> "Speech error $error"
            }
            _voiceState.value = VoiceState.Error(msg)
        }

        override fun onReadyForSpeech(params: Bundle?) { _voiceState.value = VoiceState.Listening }
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() { _voiceState.value = VoiceState.Processing }
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    init {
        // Now safe — recognitionListener is already initialized above
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
            }
        }
    }

    fun speak(text: String) {
        if (!ttsReady) return
        val clean = text
            .replace(Regex("[*_#`]"), "")
            .replace(Regex("\\[.*?\\]\\(.*?\\)"), "")
            .trim()
        tts?.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "cyangem_tts")
    }

    fun stopSpeaking() = tts?.stop()

    // ── Recognition ───────────────────────────────────────────────────────────

    private fun initRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) return
        recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer?.setRecognitionListener(recognitionListener)
    }

    fun startListening() {
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
        } catch (e: Exception) {
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
    }
}
