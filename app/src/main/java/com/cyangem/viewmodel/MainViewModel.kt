package com.cyangem.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.cyangem.media.QueryStrategy
import com.cyangem.media.WifiDirectManager
import com.cyangem.ui.AskCyanPromptBuilder
import com.cyangem.ui.VoiceEngine
import com.cyangem.ui.VoiceState
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cyangem.ble.BleEvent
import com.cyangem.ble.ConnectionState
import com.cyangem.ble.CyanBleManager
import com.cyangem.ble.GlassesDevice
import com.cyangem.ble.GlassesStatus
import com.cyangem.data.ApiKeyStore
import com.cyangem.gemini.ChatMessage
import com.cyangem.gemini.Gem
import com.cyangem.gemini.GeminiEngine
import com.cyangem.gemini.OpenRouterEngine
import com.cyangem.gemini.GeminiResult
import com.cyangem.gemini.GemsRepository
import com.cyangem.media.MediaSyncManager
import com.cyangem.media.MediaSyncProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.net.Uri
import android.provider.MediaStore
import android.os.Build

// =============================================================================
// HC-013 — MainViewModel for the Home shell pivot.
//
// CRITICAL CHANGE vs HC-011: the init {} block is EMPTY. No eager construction
// of OpenRouterEngine, GeminiEngine, voice engine, BLE manager, media sync,
// Wi-Fi direct, gems repository, or in-app AI readiness signals.
//
// All public fields and methods are preserved so the dormant screens
// (GlassesScreen, ChatScreen, GemsScreen, GalleryScreen, AskCyanScreen)
// still compile — but they are unreachable from the new Home + Settings nav,
// so none of these methods are invoked at runtime.
//
// The lazy fields (bleManager, mediaSyncer, voiceEngine, wifiManager,
// apiKeyStore, gemsRepo) are NEVER accessed during normal Home/Settings use,
// so they never construct. No background BLE scanning. No SpeechRecognizer
// allocation. No EncryptedSharedPreferences open. No OpenRouterEngine init.
//
// Snackbar StateFlow remains live so CyanGemApp's Scaffold can still post
// (currently unused; preserved for future use).
// =============================================================================

data class UiState(
    val connectionState: ConnectionState = ConnectionState.IDLE,
    val scannedDevices: List<GlassesDevice> = emptyList(),
    val glassesStatus: GlassesStatus = GlassesStatus(),
    val chatMessages: List<ChatMessage> = emptyList(),
    val isGeminiThinking: Boolean = false,
    val geminiError: String? = null,
    val streamingText: String = "",
    val gems: List<Gem> = emptyList(),
    val activeGem: Gem? = null,
    val hasApiKey: Boolean = false,
    val syncProgress: MediaSyncProgress = MediaSyncProgress(),
    val snackbarMessage: String? = null,
    val discoveredChars: Map<String, List<String>> = emptyMap(),
    val savedMac: String = "62:2F:7C:28:7B:3B",
    val lastBleEvent: BleEvent? = null,
    val voiceState: VoiceState = VoiceState.Idle,
    val isListening: Boolean = false,
    val queryStrategy: QueryStrategy = QueryStrategy.USE_PHONE_CAMERA,
    val galleryPhotos: List<android.net.Uri> = emptyList(),
    val isGalleryLoading: Boolean = false,
    val activeProvider: String = com.cyangem.data.ApiKeyStore.PROVIDER_OPENROUTER
)

// HC-009 — kept for compile compat with dormant AskCyanScreen.
sealed class AskCyanAnswerState {
    object Idle : AskCyanAnswerState()
    object NotConfigured : AskCyanAnswerState()
    object Loading : AskCyanAnswerState()
    data class Streaming(val partial: String) : AskCyanAnswerState()
    data class Answer(val text: String) : AskCyanAnswerState()
    data class Error(val message: String) : AskCyanAnswerState()
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    // Lazy fields — preserved so dormant screens compile. NEVER accessed by the
    // new Home + Settings nav, so the lazy bodies never run. If you find
    // yourself adding code that touches any of these, stop and rethink — they
    // belong to the old "phone is the AI brain" architecture and should be
    // dead in the Gemini Live companion model.

    val bleManager by lazy {
        safeCreate("CyanBleManager") { CyanBleManager(application) }
    }

    val mediaSyncer by lazy {
        safeCreate("MediaSyncManager") { MediaSyncManager(application) }
    }

    val apiKeyStore by lazy {
        safeCreate("ApiKeyStore") { ApiKeyStore(application) }
    }

    val gemsRepo by lazy {
        safeCreate("GemsRepository") { GemsRepository(application) }
    }

    val voiceEngine by lazy {
        safeCreate("VoiceEngine") { VoiceEngine(application) }
    }

    val wifiManager by lazy {
        safeCreate("WifiDirectManager") { WifiDirectManager(application) }
    }

    private var geminiEngine: GeminiEngine? = null
    private var openRouterEngine: OpenRouterEngine? = null

    private val activeEngine: Any?
        get() = when (apiKeyStore?.getProvider()) {
            com.cyangem.data.ApiKeyStore.PROVIDER_OPENROUTER -> openRouterEngine
            else -> geminiEngine
        }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    // HC-009 / HC-011 fields kept for compile compat with dormant screens.
    private val _askCyanAnswer = MutableStateFlow<AskCyanAnswerState>(AskCyanAnswerState.Idle)
    val askCyanAnswer: StateFlow<AskCyanAnswerState> = _askCyanAnswer.asStateFlow()

    private val _inAppReady = MutableStateFlow(false)
    val inAppReady: StateFlow<Boolean> = _inAppReady.asStateFlow()

    private var askCyanEngine: OpenRouterEngine? = null
    private var askCyanEngineKey: String? = null

    init {
        // HC-013 — INTENTIONALLY EMPTY. No eager subsystem construction.
        // Per Ops review: MainViewModel must not initialize OpenRouter, Kimi,
        // Gemini API, Ask Cyan, voice engine, BLE experiments, or media sync
        // in the background. The Home shell does not need any of these.
        Log.d("CyanGem", "HC-013 MainViewModel constructed — init block intentionally empty.")
    }

    private fun <T> safeCreate(name: String, block: () -> T): T? {
        return runCatching(block)
            .onFailure { Log.e("CyanGem", "Init failed: $name — ${it.message}", it) }
            .getOrNull()
    }

    // ── UI helpers ────────────────────────────────────────────────────────────

    fun showSnackbar(message: String) {
        _uiState.value = _uiState.value.copy(snackbarMessage = message)
    }

    fun clearSnackbar() {
        _uiState.value = _uiState.value.copy(snackbarMessage = null)
    }

    // =========================================================================
    // Below: methods retained for compile compat with dormant screens. They
    // are NEVER invoked from the Home + Settings nav. If a future screen needs
    // any of these, evaluate whether it actually belongs in the Gemini Live
    // companion model before re-wiring.
    // =========================================================================

    private fun checkApiKey() {
        val store = apiKeyStore ?: return
        val provider = store.getProvider()
        val hasKey = when (provider) {
            com.cyangem.data.ApiKeyStore.PROVIDER_OPENROUTER -> store.hasOpenRouterKey()
            else -> store.hasApiKey()
        }
        _uiState.value = _uiState.value.copy(hasApiKey = hasKey, activeProvider = provider)
        if (hasKey) initGemini()
    }

    fun saveApiKey(key: String) {
        apiKeyStore?.setApiKey(key)
        _uiState.value = _uiState.value.copy(hasApiKey = true)
        initGemini()
        showSnackbar("Gemini API key saved")
    }

    fun saveOpenRouterKey(key: String) {
        apiKeyStore?.setOpenRouterKey(key)
        apiKeyStore?.setProvider(com.cyangem.data.ApiKeyStore.PROVIDER_OPENROUTER)
        _uiState.value = _uiState.value.copy(
            hasApiKey = true,
            activeProvider = com.cyangem.data.ApiKeyStore.PROVIDER_OPENROUTER
        )
        initGemini()
        refreshInAppReady()
        showSnackbar("OpenRouter key saved")
    }

    fun setProvider(provider: String) {
        apiKeyStore?.setProvider(provider)
        _uiState.value = _uiState.value.copy(activeProvider = provider)
        initGemini()
        showSnackbar("Provider set: $provider")
    }

    private fun initGemini(gem: Gem? = _uiState.value.activeGem) {
        val store = apiKeyStore ?: return
        store.getOpenRouterKey()?.let { key ->
            openRouterEngine = if (gem != null) OpenRouterEngine.createForGem(key, gem)
            else OpenRouterEngine(key)
        }
        store.getApiKey()?.let { key ->
            geminiEngine = if (gem != null) GeminiEngine.createForGem(key, gem)
            else GeminiEngine(key)
        }
        val provider = store.getProvider()
        _uiState.value = _uiState.value.copy(activeProvider = provider)
    }

    fun refreshInAppReady() {
        _inAppReady.value = apiKeyStore?.hasOpenRouterKey() == true
    }

    fun askCyanInApp(prompt: String) {
        // Dormant — Home shell does not call this. Kept for compile compat.
        val trimmed = AskCyanPromptBuilder.wrapUserPrompt(prompt)
        if (trimmed.isEmpty()) return
        val store = apiKeyStore ?: run {
            _askCyanAnswer.value = AskCyanAnswerState.Error("Storage not available")
            return
        }
        val key = store.getOpenRouterKey()
        if (key.isNullOrBlank()) {
            _askCyanAnswer.value = AskCyanAnswerState.NotConfigured
            return
        }
        val engine = if (askCyanEngine != null && askCyanEngineKey == key) {
            askCyanEngine!!
        } else {
            OpenRouterEngine(key, AskCyanPromptBuilder.ASK_CYAN_SYSTEM_PROMPT).also {
                askCyanEngine = it
                askCyanEngineKey = key
            }
        }
        _askCyanAnswer.value = AskCyanAnswerState.Loading
        viewModelScope.launch {
            var fullText = ""
            var hadError = false
            try {
                engine.sendMessageStream(trimmed).collect { result ->
                    when (result) {
                        is GeminiResult.Streaming -> {
                            fullText += result.chunk
                            _askCyanAnswer.value = AskCyanAnswerState.Streaming(fullText)
                        }
                        is GeminiResult.Error -> {
                            hadError = true
                            _askCyanAnswer.value = AskCyanAnswerState.Error(result.message)
                        }
                        else -> {}
                    }
                }
            } catch (e: Exception) {
                hadError = true
                _askCyanAnswer.value = AskCyanAnswerState.Error(e.message ?: "In-app AI failed")
            }
            if (!hadError) {
                if (fullText.isNotEmpty()) {
                    _askCyanAnswer.value = AskCyanAnswerState.Answer(fullText)
                } else {
                    _askCyanAnswer.value = AskCyanAnswerState.Error("No response received")
                }
            }
        }
    }

    fun resetAskCyanAnswer() {
        _askCyanAnswer.value = AskCyanAnswerState.Idle
    }

    // ── BLE / media / voice / chat / gallery / gems methods kept for compile
    // compat with dormant screens. Bodies preserved from HC-011 unchanged.
    // None are invoked from Home + Settings.
    // ─────────────────────────────────────────────────────────────────────────

    fun startScan() = bleManager?.startScan()
    fun stopScan() = bleManager?.stopScan()
    fun connectByMac(mac: String) {
        _uiState.value = _uiState.value.copy(savedMac = mac)
        bleManager?.connectByMac(mac)
    }
    fun disconnect() = bleManager?.disconnect()

    fun connectSavedMac() {
        val mac = _uiState.value.savedMac
        if (mac.isNotBlank()) bleManager?.connectByMac(mac)
        else showSnackbar("No saved glasses MAC address")
    }

    fun takePhoto() = bleManager?.takePhoto()
    fun startVideo() = bleManager?.startVideo()
    fun stopVideo() = bleManager?.stopVideo()
    fun startAudio() = bleManager?.startAudio()
    fun stopAudio() = bleManager?.stopAudio()
    fun requestBattery() = bleManager?.requestBattery()

    fun updateConnectionState(connected: Boolean) {
        bleManager?.updateConnectionState(connected)
    }

    fun loadGalleryPhotos() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isGalleryLoading = true)
            val uris = mutableListOf<Uri>()
            val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DATE_ADDED
            )
            val selection = "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
            val selectionArgs = arrayOf("DCIM/CyanGem%")
            val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

            try {
                getApplication<android.app.Application>().contentResolver.query(
                    collection, projection, selection, selectionArgs, sortOrder
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idCol)
                        uris.add(Uri.withAppendedPath(collection, id.toString()))
                    }
                }
            } catch (e: Exception) {
                Log.e("CyanGem", "Gallery query failed: ${e.message}", e)
            }

            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(
                    galleryPhotos = uris,
                    isGalleryLoading = false
                )
            }
        }
    }

    fun analyzeGalleryPhoto(uri: Uri, customPrompt: String? = null) {
        val prompt = customPrompt ?: "Describe what you see in this photo in detail."
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    android.graphics.ImageDecoder.decodeBitmap(
                        android.graphics.ImageDecoder.createSource(
                            getApplication<android.app.Application>().contentResolver, uri
                        )
                    ) { decoder, _, _ -> decoder.isMutableRequired = true }
                } else {
                    @Suppress("DEPRECATION")
                    android.provider.MediaStore.Images.Media.getBitmap(
                        getApplication<android.app.Application>().contentResolver, uri
                    )
                }
                withContext(Dispatchers.Main) {
                    analyzeImageBitmap(bitmap, prompt)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showSnackbar("Could not load photo for analysis")
                }
            }
        }
    }

    fun syncMedia() { /* dormant */ }

    fun sendMessage(text: String) {
        val engine = activeEngine ?: run {
            showSnackbar("Add an API key in Settings first")
            return
        }
        val userMsg = ChatMessage(role = "user", text = text)
        _uiState.value = _uiState.value.copy(
            chatMessages = _uiState.value.chatMessages + userMsg,
            isGeminiThinking = true,
            streamingText = "",
            geminiError = null
        )
        viewModelScope.launch {
            var fullText = ""
            val stream = when (engine) {
                is OpenRouterEngine -> engine.sendMessageStream(text)
                is GeminiEngine -> engine.sendMessageStream(text)
                else -> return@launch
            }
            stream.collect { result ->
                when (result) {
                    is GeminiResult.Streaming -> {
                        fullText += result.chunk
                        _uiState.value = _uiState.value.copy(streamingText = fullText)
                    }
                    is GeminiResult.Error -> {
                        _uiState.value = _uiState.value.copy(
                            isGeminiThinking = false,
                            streamingText = "",
                            geminiError = result.message
                        )
                        return@collect
                    }
                    else -> {}
                }
            }
            if (fullText.isNotEmpty()) {
                val modelMsg = ChatMessage(role = "model", text = fullText)
                _uiState.value = _uiState.value.copy(
                    chatMessages = _uiState.value.chatMessages + modelMsg,
                    isGeminiThinking = false,
                    streamingText = ""
                )
            }
        }
    }

    fun analyzeLatestGlassesPhoto(customPrompt: String? = null) {
        // Dormant — preserved for compile compat with dormant ChatScreen.
        showSnackbar("Photo bridge not available yet")
    }

    fun startVoiceQuery() { /* dormant */ }
    fun stopVoiceQuery() { /* dormant */ }

    fun updateQueryStrategy() {
        val strategy = wifiManager?.getQueryStrategy() ?: QueryStrategy.USE_PHONE_CAMERA
        _uiState.value = _uiState.value.copy(queryStrategy = strategy)
    }

    fun whatAmILookingAt(capturePhoneCamera: (() -> Bitmap?)? = null) {
        // Dormant.
        showSnackbar("Photo bridge not available yet")
    }

    fun analyzeImageBitmap(bitmap: Bitmap, prompt: String = "What am I looking at?") {
        val engine = activeEngine ?: return
        _uiState.value = _uiState.value.copy(isGeminiThinking = true)
        val userMsg = ChatMessage(role = "user", text = "📷 $prompt")
        _uiState.value = _uiState.value.copy(chatMessages = _uiState.value.chatMessages + userMsg)
        viewModelScope.launch {
            val result = when (engine) {
                is OpenRouterEngine -> engine.analyzeImage(bitmap, prompt)
                is GeminiEngine -> engine.analyzeImage(bitmap, prompt)
                else -> GeminiResult.Error("No engine")
            }
            when (result) {
                is GeminiResult.Success -> {
                    val modelMsg = ChatMessage(role = "model", text = result.text)
                    _uiState.value = _uiState.value.copy(
                        chatMessages = _uiState.value.chatMessages + modelMsg,
                        isGeminiThinking = false
                    )
                }
                is GeminiResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isGeminiThinking = false,
                        geminiError = result.message
                    )
                }
                else -> {}
            }
        }
    }

    fun clearChat() {
        _uiState.value = _uiState.value.copy(
            chatMessages = emptyList(),
            geminiError = null,
            streamingText = ""
        )
    }

    fun refreshGems() {
        val repo = gemsRepo ?: return
        val gems = repo.getGems()
        val active = _uiState.value.activeGem ?: gems.firstOrNull { it.isDefault }
        _uiState.value = _uiState.value.copy(gems = gems, activeGem = active)
    }

    fun activateGem(gem: Gem) {
        _uiState.value = _uiState.value.copy(activeGem = gem)
        initGemini(gem)
        clearChat()
        showSnackbar("${gem.emoji} ${gem.name} activated")
    }

    fun saveGem(gem: Gem) {
        gemsRepo?.saveGem(gem)
        refreshGems()
    }

    fun deleteGem(gemId: String) {
        gemsRepo?.deleteGem(gemId)
        if (_uiState.value.activeGem?.id == gemId) {
            val fallback = _uiState.value.gems.firstOrNull { it.isDefault }
            fallback?.let { activateGem(it) }
        }
        refreshGems()
    }

    override fun onCleared() {
        // HC-013 — INTENTIONALLY DOES NOT access voiceEngine or bleManager.
        // Both are `by lazy`; touching them here would TRIGGER construction
        // at shutdown just to destroy them, which defeats the no-eager-init
        // goal of HC-013. If they were never accessed during the session
        // (the expected path for the Home shell), there's nothing to clean
        // up. If a future code path re-introduces direct access, cleanup
        // wiring should move next to the access site, not here.
        super.onCleared()
    }
}
