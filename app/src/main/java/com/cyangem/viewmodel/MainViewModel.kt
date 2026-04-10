package com.cyangem.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.cyangem.media.QueryStrategy
import com.cyangem.media.WifiDirectManager
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

class MainViewModel(application: Application) : AndroidViewModel(application) {

    // ── LAZY init — nothing heavy runs until first access ─────────────────────
    // This prevents any single subsystem crash from killing the entire launch.

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

    /** Returns whichever engine is currently active based on stored provider preference */
    private val activeEngine: Any?
        get() = when (apiKeyStore?.getProvider()) {
            com.cyangem.data.ApiKeyStore.PROVIDER_OPENROUTER -> openRouterEngine
            else -> geminiEngine
        }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        // Start subsystems only after ViewModel is constructed.
        // Each call is guarded — a failure in one does not affect others.
        runCatching { observeBleState() }.onFailure { Log.e("CyanGem", "observeBleState failed", it) }
        runCatching { observeSyncProgress() }.onFailure { Log.e("CyanGem", "observeSync failed", it) }
        runCatching { refreshGems() }.onFailure { Log.e("CyanGem", "refreshGems failed", it) }
        runCatching { checkApiKey() }.onFailure { Log.e("CyanGem", "checkApiKey failed", it) }
        runCatching { initVoice() }.onFailure { Log.e("CyanGem", "initVoice failed", it) }
        runCatching { updateQueryStrategy() }.onFailure { Log.e("CyanGem", "updateQueryStrategy failed", it) }
    }

    /** Safely create a subsystem — logs failure, returns null on error */
    private fun <T> safeCreate(name: String, block: () -> T): T? {
        return runCatching(block)
            .onFailure { Log.e("CyanGem", "Init failed: $name — ${it.message}", it) }
            .getOrNull()
    }

    // ── Setup / API key ───────────────────────────────────────────────────────

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
        showSnackbar("✅ OpenRouter key saved — using free AI")
    }

    fun setProvider(provider: String) {
        apiKeyStore?.setProvider(provider)
        _uiState.value = _uiState.value.copy(activeProvider = provider)
        initGemini()
        val name = if (provider == com.cyangem.data.ApiKeyStore.PROVIDER_OPENROUTER)
            "OpenRouter (Free)" else "Gemini"
        showSnackbar("Switched to $name")
    }

    private fun initGemini(gem: Gem? = _uiState.value.activeGem) {
        val store = apiKeyStore ?: return
        // Init OpenRouter engine if key exists
        store.getOpenRouterKey()?.let { key ->
            openRouterEngine = if (gem != null) OpenRouterEngine.createForGem(key, gem)
            else OpenRouterEngine(key)
        }
        // Init Gemini engine if key exists
        store.getApiKey()?.let { key ->
            geminiEngine = if (gem != null) GeminiEngine.createForGem(key, gem)
            else GeminiEngine(key)
        }
        val provider = store.getProvider()
        _uiState.value = _uiState.value.copy(activeProvider = provider)
    }

    // ── BLE ───────────────────────────────────────────────────────────────────

    private fun observeBleState() {
        val ble = bleManager ?: return
        viewModelScope.launch {
            ble.connectionState.collect { state ->
                _uiState.value = _uiState.value.copy(connectionState = state)
            }
        }
        viewModelScope.launch {
            ble.scannedDevices.collect { devices ->
                _uiState.value = _uiState.value.copy(scannedDevices = devices)
            }
        }
        viewModelScope.launch {
            ble.glassesStatus.collect { status ->
                _uiState.value = _uiState.value.copy(glassesStatus = status)
            }
        }
        viewModelScope.launch {
            ble.discoveredChars.collect { chars ->
                _uiState.value = _uiState.value.copy(discoveredChars = chars)
            }
        }
        viewModelScope.launch {
            ble.events.collect { event ->
                event ?: return@collect
                _uiState.value = _uiState.value.copy(lastBleEvent = event)
                handleBleEvent(event)
            }
        }
    }

    private fun handleBleEvent(event: BleEvent) {
        when (event) {
            is BleEvent.AiPhotoRequested -> {
                showSnackbar("📷 Analyzing with Gemini…")
                analyzeLatestGlassesPhoto()
            }
            // FIX: PhotoTaken now triggers an auto-save from glasses via BLE.
            // Previously this only showed a snackbar while the photo sat on the glasses.
            is BleEvent.PhotoTaken -> {
                showSnackbar("📸 Photo taken — saving from glasses…")
                saveLatestGlassesPhoto()
            }
            is BleEvent.VideoStarted -> showSnackbar("🎥 Recording…")
            is BleEvent.VideoStopped -> showSnackbar("⏹ Recording stopped")
            is BleEvent.AudioStarted -> showSnackbar("🎙 Audio recording…")
            is BleEvent.AudioStopped -> showSnackbar("⏹ Audio stopped")
            is BleEvent.Error -> showSnackbar("⚠️ ${event.message}")
            else -> {}
        }
    }

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

    // ── Media sync ────────────────────────────────────────────────────────────

    private fun observeSyncProgress() {
        val syncer = mediaSyncer ?: return
        viewModelScope.launch {
            syncer.syncProgress.collect { progress ->
                _uiState.value = _uiState.value.copy(syncProgress = progress)
                if (!progress.isRunning && progress.downloadedFiles > 0 && progress.error == null) {
                    showSnackbar("✅ Synced ${progress.downloadedFiles} file(s) to Gallery")
                    // Refresh gallery after sync completes so new photos appear immediately
                    loadGalleryPhotos()
                }
            }
        }
    }

    // ── Gallery ───────────────────────────────────────────────────────────────

    /**
     * Query MediaStore for all photos saved to DCIM/CyanGem, newest first.
     * Called on tab open and after every sync completion.
     */
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
                android.util.Log.e("CyanGem", "Gallery query failed: ${e.message}", e)
            }

            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(
                    galleryPhotos = uris,
                    isGalleryLoading = false
                )
            }
        }
    }

    /**
     * Analyze a specific gallery photo URI with Gemini.
     * Decodes URI → Bitmap then reuses the existing analyzeImageBitmap() path.
     */
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
                    showSnackbar("⚠️ Could not load photo for analysis")
                }
            }
        }
    }

    /**
     * FIX: syncMedia now uses BLE to fetch the latest photo from the glasses
     * and saves it to DCIM/CyanGem in the device gallery.
     *
     * The previous Wi-Fi HTTP sync path is not functional for the W610, which
     * is a BLE-only device. The Wi-Fi sync code is preserved in MediaSyncManager
     * for future hardware that may support it.
     */
    fun syncMedia() {
        val ble = bleManager ?: return
        if (_uiState.value.connectionState != ConnectionState.CONNECTED) {
            showSnackbar("Connect to glasses first")
            return
        }
        showSnackbar("📡 Fetching photo from glasses…")
        ble.fetchLatestPhoto { jpeg ->
            if (jpeg == null || jpeg.isEmpty()) {
                showSnackbar("No photo available on glasses")
                return@fetchLatestPhoto
            }
            viewModelScope.launch(Dispatchers.IO) {
                val uri = mediaSyncer?.saveJpegToGallery(jpeg)
                withContext(Dispatchers.Main) {
                    if (uri != null) {
                        showSnackbar("✅ Photo saved to Gallery (DCIM/CyanGem)")
                    } else {
                        showSnackbar("Failed to save photo — check storage permissions")
                    }
                }
            }
        }
    }

    /**
     * FIX: Auto-save the latest photo from glasses to gallery after PhotoTaken event.
     * Called automatically from handleBleEvent when the glasses confirm a photo was taken.
     */
    private fun saveLatestGlassesPhoto() {
        val ble = bleManager ?: return
        ble.fetchLatestPhoto { jpeg ->
            if (jpeg == null || jpeg.isEmpty()) return@fetchLatestPhoto
            viewModelScope.launch(Dispatchers.IO) {
                val uri = mediaSyncer?.saveJpegToGallery(jpeg)
                withContext(Dispatchers.Main) {
                    if (uri != null) {
                        showSnackbar("✅ Photo saved to Gallery")
                    }
                }
            }
        }
    }

    // ── Gemini chat ───────────────────────────────────────────────────────────

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

    /**
     * FIX: analyzeLatestGlassesPhoto now fetches the photo over BLE instead of Wi-Fi.
     *
     * The previous implementation called mediaSyncer.downloadLatestPhoto() which
     * tried to HTTP GET from a Wi-Fi IP — never reachable on BLE-only glasses.
     *
     * Now: bleManager.fetchLatestPhoto() → JPEG bytes arrive via BLE callback
     *   → decode to Bitmap → send to Gemini for analysis.
     */
    fun analyzeLatestGlassesPhoto(customPrompt: String? = null) {
        val engine = activeEngine ?: run {
            showSnackbar("Add an API key in Settings first")
            return
        }
        val ble = bleManager ?: return

        val prompt = customPrompt ?: GeminiEngine.DEFAULT_IMAGE_PROMPT
        val userMsg = ChatMessage(role = "user", text = "📷 [Photo from glasses] $prompt")
        _uiState.value = _uiState.value.copy(
            chatMessages = _uiState.value.chatMessages + userMsg,
            isGeminiThinking = true
        )
        showSnackbar("📡 Fetching photo from glasses via BLE…")

        ble.fetchLatestPhoto { jpeg ->
            if (jpeg == null || jpeg.isEmpty()) {
                _uiState.value = _uiState.value.copy(
                    isGeminiThinking = false,
                    geminiError = "Could not retrieve photo from glasses — make sure a photo has been taken"
                )
                return@fetchLatestPhoto
            }
            val bitmap = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)
            if (bitmap == null) {
                _uiState.value = _uiState.value.copy(
                    isGeminiThinking = false,
                    geminiError = "Photo data from glasses was invalid"
                )
                return@fetchLatestPhoto
            }
            viewModelScope.launch {
                val result = when (engine) { is OpenRouterEngine -> engine.analyzeImage(bitmap, prompt); is GeminiEngine -> engine.analyzeImage(bitmap, prompt); else -> GeminiResult.Error("No engine") }; when (result) {
                    is GeminiResult.Success -> {
                        val modelMsg = ChatMessage(role = "model", text = result.text)
                        _uiState.value = _uiState.value.copy(
                            chatMessages = _uiState.value.chatMessages + modelMsg,
                            isGeminiThinking = false
                        )
                        voiceEngine?.speak(result.text)
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
    }

    // ── Voice ─────────────────────────────────────────────────────────────────

    private fun initVoice() {
        val voice = voiceEngine ?: return
        voice.onWakeWord = { startVoiceQuery() }
        voice.onResult = { text ->
            _uiState.value = _uiState.value.copy(isListening = false)
            sendMessage(text)
            observeAndSpeakNextResponse()
        }
        viewModelScope.launch {
            voice.voiceState.collect { state ->
                _uiState.value = _uiState.value.copy(
                    voiceState = state,
                    isListening = state is VoiceState.Listening
                )
            }
        }
    }

    fun startVoiceQuery() {
        if (activeEngine == null) {
            showSnackbar("Add an API key in Settings first")
            return
        }
        voiceEngine?.startListening()
        showSnackbar("🎙 Listening…")
    }

    fun stopVoiceQuery() = voiceEngine?.stopListening()

    fun updateQueryStrategy() {
        val strategy = wifiManager?.getQueryStrategy() ?: QueryStrategy.USE_PHONE_CAMERA
        _uiState.value = _uiState.value.copy(queryStrategy = strategy)
    }

    fun whatAmILookingAt(capturePhoneCamera: (() -> Bitmap?)? = null) {
        updateQueryStrategy()
        when (_uiState.value.queryStrategy) {
            QueryStrategy.USE_PHONE_CAMERA -> {
                val bitmap = capturePhoneCamera?.invoke()
                if (bitmap != null) {
                    analyzeImageBitmap(bitmap, "What am I looking at? Describe briefly.")
                } else {
                    showSnackbar("📷 Point your phone camera and try again")
                    analyzeLatestGlassesPhoto("What am I looking at? Describe briefly.")
                }
            }
            QueryStrategy.USE_GLASSES_WIFI -> {
                showSnackbar("📡 Fetching from glasses…")
                analyzeLatestGlassesPhoto("What am I looking at? Describe briefly.")
            }
        }
    }

    fun analyzeImageBitmap(bitmap: Bitmap, prompt: String = "What am I looking at?") {
        val engine = activeEngine ?: return
        _uiState.value = _uiState.value.copy(isGeminiThinking = true)
        val userMsg = ChatMessage(role = "user", text = "📷 $prompt")
        _uiState.value = _uiState.value.copy(chatMessages = _uiState.value.chatMessages + userMsg)
        viewModelScope.launch {
            val result = when (engine) { is OpenRouterEngine -> engine.analyzeImage(bitmap, prompt); is GeminiEngine -> engine.analyzeImage(bitmap, prompt); else -> GeminiResult.Error("No engine") }; when (result) {
                is GeminiResult.Success -> {
                    val modelMsg = ChatMessage(role = "model", text = result.text)
                    _uiState.value = _uiState.value.copy(
                        chatMessages = _uiState.value.chatMessages + modelMsg,
                        isGeminiThinking = false
                    )
                    voiceEngine?.speak(result.text)
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

    private fun observeAndSpeakNextResponse() {
        viewModelScope.launch {
            val before = _uiState.value.chatMessages.size
            var waited = 0
            while (waited < 30) {
                kotlinx.coroutines.delay(500)
                waited++
                val msgs = _uiState.value.chatMessages
                if (msgs.size > before && msgs.last().role == "model") {
                    voiceEngine?.speak(msgs.last().text)
                    break
                }
            }
        }
    }

    fun clearChat() {
        _uiState.value = _uiState.value.copy(
            chatMessages = emptyList(),
            geminiError = null,
            streamingText = ""
        )
        initGemini()
    }

    // ── Gems ──────────────────────────────────────────────────────────────────

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

    // ── UI helpers ────────────────────────────────────────────────────────────

    fun showSnackbar(message: String) {
        _uiState.value = _uiState.value.copy(snackbarMessage = message)
    }

    fun clearSnackbar() {
        _uiState.value = _uiState.value.copy(snackbarMessage = null)
    }

    override fun onCleared() {
        runCatching { voiceEngine?.destroy() }
        runCatching { bleManager?.close() }
        super.onCleared()
    }
}
