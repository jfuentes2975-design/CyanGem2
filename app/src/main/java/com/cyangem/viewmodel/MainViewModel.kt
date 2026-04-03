package com.cyangem.viewmodel

import android.app.Application
import android.bluetooth.BluetoothDevice
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cyangem.ble.BleConstants
import com.cyangem.ble.BleEvent
import com.cyangem.ble.ConnectionState
import com.cyangem.ble.CyanBleManager
import com.cyangem.ble.GlassesDevice
import com.cyangem.ble.GlassesStatus
import com.cyangem.data.ApiKeyStore
import com.cyangem.gemini.ChatMessage
import com.cyangem.gemini.Gem
import com.cyangem.gemini.GeminiEngine
import com.cyangem.gemini.GeminiResult
import com.cyangem.gemini.GemsRepository
import com.cyangem.media.MediaSyncManager
import com.cyangem.media.MediaSyncProgress
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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
    val savedMac: String = "62:2F:7C:28:7B:3B",
    val lastBleEvent: BleEvent? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val bleManager    = CyanBleManager(application)
    val mediaSyncer   = MediaSyncManager(application)
    val apiKeyStore   = ApiKeyStore(application)
    val gemsRepo      = GemsRepository(application)

    private var geminiEngine: GeminiEngine? = null

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        observeBleState()
        observeSyncProgress()
        refreshGems()
        checkApiKey()
    }

    // ── Setup / API key ───────────────────────────────────────────────────────

    private fun checkApiKey() {
        val hasKey = apiKeyStore.hasApiKey()
        _uiState.value = _uiState.value.copy(hasApiKey = hasKey)
        if (hasKey) initGemini()
    }

    fun saveApiKey(key: String) {
        apiKeyStore.setApiKey(key)
        _uiState.value = _uiState.value.copy(hasApiKey = true)
        initGemini()
        showSnackbar("API key saved")
    }

    private fun initGemini(gem: Gem? = _uiState.value.activeGem) {
        val key = apiKeyStore.getApiKey() ?: return
        geminiEngine = if (gem != null) {
            GeminiEngine.createForGem(key, gem)
        } else {
            GeminiEngine(key)
        }
    }

    // ── BLE ───────────────────────────────────────────────────────────────────

    private fun observeBleState() {
        viewModelScope.launch {
            bleManager.connectionState.collect { state ->
                _uiState.value = _uiState.value.copy(connectionState = state)
            }
        }
        viewModelScope.launch {
            bleManager.scannedDevices.collect { devices ->
                _uiState.value = _uiState.value.copy(scannedDevices = devices)
            }
        }
        viewModelScope.launch {
            bleManager.glassesStatus.collect { status ->
                _uiState.value = _uiState.value.copy(glassesStatus = status)
            }
        }
        viewModelScope.launch {
            bleManager.discoveredChars.collect { chars ->
                _uiState.value = _uiState.value.copy(discoveredChars = chars)
            }
        }
        viewModelScope.launch {
            bleManager.events.collect { event ->
                event ?: return@collect
                _uiState.value = _uiState.value.copy(lastBleEvent = event)
                handleBleEvent(event)
            }
        }
    }

    private fun handleBleEvent(event: BleEvent) {
        when (event) {
            is BleEvent.AiPhotoRequested -> {
                // Glasses AI button pressed → download latest photo → send to Gemini
                showSnackbar("📷 Analyzing with Gemini…")
                analyzeLatestGlassesPhoto()
            }
            is BleEvent.PhotoTaken -> showSnackbar("📸 Photo saved")
            is BleEvent.VideoStarted -> showSnackbar("🎥 Recording…")
            is BleEvent.VideoStopped -> showSnackbar("⏹ Recording stopped")
            is BleEvent.AudioStarted -> showSnackbar("🎙 Audio recording…")
            is BleEvent.AudioStopped -> showSnackbar("⏹ Audio stopped")
            is BleEvent.Error -> showSnackbar("⚠️ ${event.message}")
            else -> {}
        }
    }

    fun startScan() = bleManager.startScan()
    fun stopScan() = bleManager.stopScan()
    fun connect(device: BluetoothDevice) = bleManager.connect(device)
    fun disconnect() = bleManager.disconnect()

    fun connectByMac(mac: String) {
        _uiState.value = _uiState.value.copy(savedMac = mac)
        bleManager.connectByMac(mac)
    }

    fun connectSavedMac() {
        val mac = _uiState.value.savedMac
        if (mac.isNotBlank()) bleManager.connectByMac(mac)
        else showSnackbar("No saved glasses MAC address")
    }

    fun takePhoto() = bleManager.takePhoto()
    fun startVideo() = bleManager.startVideo()
    fun stopVideo() = bleManager.stopVideo()
    fun startAudio() = bleManager.startAudio()
    fun stopAudio() = bleManager.stopAudio()
    fun requestBattery() = bleManager.requestBattery()

    // ── Media sync ────────────────────────────────────────────────────────────

    private fun observeSyncProgress() {
        viewModelScope.launch {
            mediaSyncer.syncProgress.collect { progress ->
                _uiState.value = _uiState.value.copy(syncProgress = progress)
                if (!progress.isRunning && progress.downloadedFiles > 0 && progress.error == null) {
                    showSnackbar("✅ Synced ${progress.downloadedFiles} file(s) to Gallery")
                }
            }
        }
    }

    fun syncMedia() {
        viewModelScope.launch { mediaSyncer.syncAllMedia() }
    }

    // ── Gemini chat ───────────────────────────────────────────────────────────

    fun sendMessage(text: String) {
        val engine = geminiEngine ?: run {
            showSnackbar("Add your Gemini API key in Settings first")
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
            engine.sendMessageStream(text).collect { result ->
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

    // ── Vision — auto-triggered by glasses AI button ──────────────────────────

    fun analyzeLatestGlassesPhoto(customPrompt: String? = null) {
        val engine = geminiEngine ?: return
        _uiState.value = _uiState.value.copy(isGeminiThinking = true)
        viewModelScope.launch {
            val bitmap = mediaSyncer.downloadLatestPhoto()
            if (bitmap == null) {
                _uiState.value = _uiState.value.copy(
                    isGeminiThinking = false,
                    geminiError = "Could not download photo from glasses"
                )
                return@launch
            }
            val prompt = customPrompt ?: GeminiEngine.DEFAULT_IMAGE_PROMPT
            val userMsg = ChatMessage(role = "user", text = "📷 [Photo from glasses] $prompt")
            _uiState.value = _uiState.value.copy(
                chatMessages = _uiState.value.chatMessages + userMsg
            )
            when (val result = engine.analyzeImage(bitmap, prompt)) {
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
        // Re-init engine to clear chat history
        initGemini()
    }

    // ── Gems ──────────────────────────────────────────────────────────────────

    fun refreshGems() {
        val gems = gemsRepo.getGems()
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
        gemsRepo.saveGem(gem)
        refreshGems()
    }

    fun deleteGem(gemId: String) {
        gemsRepo.deleteGem(gemId)
        if (_uiState.value.activeGem?.id == gemId) {
            val fallback = _uiState.value.gems.firstOrNull { it.isDefault }
            fallback?.let { activateGem(it) }
        }
        refreshGems()
    }

    // ── UI helpers ────────────────────────────────────────────────────────────

    fun showSnackbar(message: String) {
        _uiState.value = _uiState.value.copy(snackbarMessage = message)
    }

    fun clearSnackbar() {
        _uiState.value = _uiState.value.copy(snackbarMessage = null)
    }

    override fun onCleared() {
        super.onCleared()
        bleManager.close()
    }
}
