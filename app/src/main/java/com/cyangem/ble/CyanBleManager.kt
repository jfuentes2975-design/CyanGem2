package com.cyangem.ble

import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.oudmon.ble.base.bluetooth.BleOperateManager
import com.oudmon.ble.base.communication.ILargeDataImageResponse
import com.oudmon.ble.base.communication.LargeDataHandler
import com.oudmon.ble.base.communication.bigData.resp.GlassesDeviceNotifyListener
import com.oudmon.ble.base.communication.bigData.resp.GlassesDeviceNotifyRsp
import com.oudmon.ble.base.scan.BleScannerHelper
import com.oudmon.ble.base.scan.ScanRecord
import com.oudmon.ble.base.scan.ScanWrapperCallback
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ConnectionState { IDLE, SCANNING, CONNECTING, CONNECTED, DISCONNECTING, DISCONNECTED }

data class GlassesDevice(val address: String, val name: String, val rssi: Int = 0)

data class GlassesStatus(
    val battery: Int = -1,
    val isCharging: Boolean = false,
    val photoCount: Int = 0,
    val videoCount: Int = 0,
    val audioCount: Int = 0,
    val firmwareVersion: String = "",
    val isRecordingVideo: Boolean = false,
    val isRecordingAudio: Boolean = false
)

sealed class BleEvent {
    object PhotoTaken : BleEvent()
    object VideoStarted : BleEvent()
    object VideoStopped : BleEvent()
    object AudioStarted : BleEvent()
    object AudioStopped : BleEvent()
    object AiPhotoRequested : BleEvent()
    object AiVoiceRequested : BleEvent()
    data class BatteryUpdate(val level: Int, val charging: Boolean) : BleEvent()
    data class MediaCountUpdate(val photos: Int, val videos: Int, val audio: Int) : BleEvent()
    data class VersionInfo(val firmware: String) : BleEvent()
    data class Error(val message: String) : BleEvent()
    // FIX: New event — fired when a complete JPEG is received from the glasses via BLE.
    // This replaces the broken Wi-Fi HTTP photo download path.
    data class PhotoReceived(val jpeg: ByteArray) : BleEvent()
}

class CyanBleManager(private val context: Context) {

    private val _connectionState = MutableStateFlow(ConnectionState.IDLE)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _glassesStatus = MutableStateFlow(GlassesStatus())
    val glassesStatus: StateFlow<GlassesStatus> = _glassesStatus.asStateFlow()

    private val _events = MutableStateFlow<BleEvent?>(null)
    val events: StateFlow<BleEvent?> = _events.asStateFlow()

    private val _scannedDevices = MutableStateFlow<List<GlassesDevice>>(emptyList())
    val scannedDevices: StateFlow<List<GlassesDevice>> = _scannedDevices.asStateFlow()

    val discoveredChars: StateFlow<Map<String, List<String>>> =
        MutableStateFlow<Map<String, List<String>>>(emptyMap()).asStateFlow()

    private val mainHandler = Handler(Looper.getMainLooper())
    private var notifyRegistered = false

    // FIX: Both listeners defined BEFORE init{} so they are non-null when init runs.
    // Same root cause as VoiceEngine — Kotlin initializes properties in order of appearance.

    private val notifyListener = object : GlassesDeviceNotifyListener() {
        override fun parseData(cmdType: Int, response: GlassesDeviceNotifyRsp) {
            val load = response.loadData ?: return
            if (load.size < 7) return
            when (load[6].toInt()) {
                0x05 -> {
                    val bat = load[7].toInt() and 0xFF
                    val charging = load[8].toInt() == 1
                    _glassesStatus.value = _glassesStatus.value.copy(
                        battery = bat, isCharging = charging
                    )
                    _events.value = BleEvent.BatteryUpdate(bat, charging)
                    _connectionState.value = ConnectionState.CONNECTED
                }
                0x02 -> _events.value = BleEvent.AiPhotoRequested
                0x03 -> {
                    if (load.size > 7 && load[7].toInt() == 1)
                        _events.value = BleEvent.AiVoiceRequested
                }
                0x0f -> {
                    if (load.size >= 10) {
                        _glassesStatus.value = _glassesStatus.value.copy(
                            photoCount = load[7].toInt() and 0xFF,
                            videoCount = load[8].toInt() and 0xFF,
                            audioCount = load[9].toInt() and 0xFF
                        )
                    }
                }
            }
        }
    }

    private val scanCallback = object : ScanWrapperCallback {
        override fun onStart() {}
        override fun onStop() {}
        override fun onScanFailed(errorCode: Int) {
            _events.value = BleEvent.Error("Scan failed: $errorCode")
            _connectionState.value = ConnectionState.IDLE
        }
        override fun onLeScan(device: BluetoothDevice, rssi: Int, scanRecord: ByteArray?) {
            val name = try { device.name ?: "Unknown" } catch (_: SecurityException) { "Unknown" }
            upsertDevice(device.address, name, rssi)
        }
        override fun onParsedData(device: BluetoothDevice?, scanRecord: ScanRecord?) {
            val addr = device?.address ?: return
            val name = try {
                scanRecord?.deviceName ?: device.name ?: "Unknown"
            } catch (_: SecurityException) { scanRecord?.deviceName ?: "Unknown" }
            val rssi = _scannedDevices.value.firstOrNull {
                it.address.equals(addr, true)
            }?.rssi ?: 0
            upsertDevice(addr, name, rssi)
        }
        override fun onBatchScanResults(results: MutableList<ScanResult>?) {}
    }

    // init runs AFTER all properties above are initialized
    init {
        registerNotifyListener()
    }

    private fun registerNotifyListener() {
        if (notifyRegistered) return
        LargeDataHandler.getInstance().addOutDeviceListener(100, notifyListener)
        notifyRegistered = true
    }

    // ── Scan ─────────────────────────────────────────────────────────────────

    fun startScan() {
        _scannedDevices.value = emptyList()
        _connectionState.value = ConnectionState.SCANNING
        BleScannerHelper.getInstance().reSetCallback()
        BleScannerHelper.getInstance().scanDevice(context, null, scanCallback)
    }

    fun stopScan() {
        BleScannerHelper.getInstance().stopScan(context)
        if (_connectionState.value == ConnectionState.SCANNING)
            _connectionState.value = ConnectionState.IDLE
    }

    private fun upsertDevice(addr: String, name: String, rssi: Int) {
        val list = _scannedDevices.value.toMutableList()
        val idx = list.indexOfFirst { it.address.equals(addr, true) }
        val d = GlassesDevice(addr, name, rssi)
        if (idx >= 0) list[idx] = d else list.add(d)
        _scannedDevices.value = list
    }

    // ── Connect ───────────────────────────────────────────────────────────────

    fun connectByMac(mac: String) {
        stopScan()
        _connectionState.value = ConnectionState.CONNECTING
        BleOperateManager.getInstance().connectDirectly(mac)
    }

    fun disconnect() {
        _connectionState.value = ConnectionState.DISCONNECTING
        BleOperateManager.getInstance().unBindDevice()
    }

    // ── Commands ──────────────────────────────────────────────────────────────

    fun takePhoto() {
        LargeDataHandler.getInstance().glassesControl(byteArrayOf(0x02, 0x01, 0x01)) { _, rsp ->
            if (rsp.dataType == 1 && rsp.errorCode == 0) {
                _events.value = BleEvent.PhotoTaken
                _glassesStatus.value = _glassesStatus.value.copy(
                    photoCount = _glassesStatus.value.photoCount + 1
                )
            }
        }
    }

    fun startVideo() {
        LargeDataHandler.getInstance().glassesControl(byteArrayOf(0x02, 0x01, 0x02)) { _, rsp ->
            if (rsp.dataType == 1 && rsp.errorCode == 0) {
                _glassesStatus.value = _glassesStatus.value.copy(isRecordingVideo = true)
                _events.value = BleEvent.VideoStarted
            }
        }
    }

    fun stopVideo() {
        LargeDataHandler.getInstance().glassesControl(byteArrayOf(0x02, 0x01, 0x03)) { _, _ ->
            _glassesStatus.value = _glassesStatus.value.copy(isRecordingVideo = false)
            _events.value = BleEvent.VideoStopped
        }
    }

    fun startAudio() {
        LargeDataHandler.getInstance().glassesControl(byteArrayOf(0x02, 0x01, 0x08)) { _, rsp ->
            if (rsp.dataType == 1 && rsp.errorCode == 0) {
                _glassesStatus.value = _glassesStatus.value.copy(isRecordingAudio = true)
                _events.value = BleEvent.AudioStarted
            }
        }
    }

    fun stopAudio() {
        LargeDataHandler.getInstance().glassesControl(byteArrayOf(0x02, 0x01, 0x0c)) { _, _ ->
            _glassesStatus.value = _glassesStatus.value.copy(isRecordingAudio = false)
            _events.value = BleEvent.AudioStopped
        }
    }

    fun requestBattery() = LargeDataHandler.getInstance().syncBattery()

    fun requestMediaCount() {
        LargeDataHandler.getInstance().glassesControl(byteArrayOf(0x02, 0x04)) { _, rsp ->
            if (rsp.dataType == 4) {
                _glassesStatus.value = _glassesStatus.value.copy(
                    photoCount = rsp.imageCount,
                    videoCount = rsp.videoCount,
                    audioCount = rsp.recordCount
                )
                _events.value = BleEvent.MediaCountUpdate(
                    rsp.imageCount, rsp.videoCount, rsp.recordCount
                )
            }
        }
    }

    fun syncTime() = LargeDataHandler.getInstance().syncTime { _, _ -> }

    fun updateConnectionState(connected: Boolean) {
        _connectionState.value = if (connected) {
            syncTime()
            mainHandler.postDelayed({ requestBattery(); requestMediaCount() }, 1000)
            ConnectionState.CONNECTED
        } else ConnectionState.DISCONNECTED
    }

    /**
     * FIX: Fetch the latest photo from the glasses over BLE.
     *
     * Uses LargeDataHandler.getPictureThumbnails() which sends a sync request to the glasses.
     * The glasses respond with the latest photo in chunks via BLE notify.
     * Each chunk arrives via ILargeDataImageResponse.parseData(cmdType, isLast, data).
     * When isLast=true, all chunks are assembled into a complete JPEG and delivered via [onResult].
     *
     * [onResult] is always called on the main thread.
     * [onResult] receives null if no photo is available or the transfer fails.
     *
     * This replaces the broken Wi-Fi HTTP download path entirely.
     */
    fun fetchLatestPhoto(onResult: (ByteArray?) -> Unit) {
        val chunks = mutableListOf<ByteArray>()
        LargeDataHandler.getInstance().getPictureThumbnails(
            object : ILargeDataImageResponse {
                override fun parseData(cmdType: Int, isLast: Boolean, data: ByteArray?) {
                    if (data != null && data.isNotEmpty()) {
                        chunks.add(data)
                    }
                    if (isLast) {
                        val jpeg = chunks.fold(byteArrayOf()) { acc, b -> acc + b }
                        mainHandler.post {
                            onResult(if (jpeg.isEmpty()) null else jpeg)
                        }
                    }
                }
            }
        )
    }

    /**
     * Sync ALL photos from the glasses over BLE.
     *
     * The SDK handles iteration internally: getPictureThumbnails() requests index 0,
     * fires the callback once per photo, then automatically requests the next index
     * until isLast=true. No external loop needed.
     *
     * [onPhoto] called on main thread for each photo:
     *   current  — 1-based count of photos received so far
     *   total    — expected total from glassesStatus.photoCount (0 if unknown)
     *   isLast   — true when this is the final photo
     *   jpeg     — raw JPEG bytes
     *
     * NOTE: Video transfer is not supported by this SDK version.
     */
    fun syncAllMedia(
        onPhoto: (current: Int, total: Int, isLast: Boolean, jpeg: ByteArray) -> Unit
    ) {
        val total = _glassesStatus.value.photoCount.takeIf { it > 0 } ?: 0
        var received = 0
        LargeDataHandler.getInstance().getPictureThumbnails(
            object : ILargeDataImageResponse {
                override fun parseData(cmdType: Int, isLast: Boolean, data: ByteArray?) {
                    val bytes = data ?: return
                    if (bytes.isEmpty()) return
                    received++
                    val snapshot = received
                    mainHandler.post { onPhoto(snapshot, total, isLast, bytes) }
                }
            }
        )
    }

    fun close() {
        if (notifyRegistered) {
            try { LargeDataHandler.getInstance().removeOutDeviceListener(100) } catch (_: Exception) {}
            notifyRegistered = false
        }
    }
}
