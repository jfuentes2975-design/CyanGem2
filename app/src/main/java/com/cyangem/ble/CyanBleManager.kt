package com.cyangem.ble

import android.content.Context
import com.oudmon.ble.base.bluetooth.BleOperateManager
import com.oudmon.ble.base.bluetooth.DeviceManager
import com.oudmon.ble.base.communication.LargeDataHandler
import com.oudmon.ble.base.communication.bigData.resp.GlassesDeviceNotifyListener
import com.oudmon.ble.base.communication.bigData.resp.GlassesDeviceNotifyRsp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ConnectionState { IDLE, SCANNING, CONNECTING, CONNECTED, DISCONNECTING, DISCONNECTED }

data class GlassesDevice(
    val address: String,
    val name: String,
    val rssi: Int = 0
)

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
    /** Glasses AI photo button pressed — Button 1 short */
    object AiPhotoRequested : BleEvent()
    /** Glasses AI voice button pressed — Button 1 long / Hey Cyan */
    object AiVoiceRequested : BleEvent()
    data class BatteryUpdate(val level: Int, val charging: Boolean) : BleEvent()
    data class MediaCountUpdate(val photos: Int, val videos: Int, val audio: Int) : BleEvent()
    data class VersionInfo(val firmware: String) : BleEvent()
    data class Error(val message: String) : BleEvent()
}

/**
 * CyanBleManager wraps the real glasses SDK (glasses_sdk_20250723_v01.aar).
 * All commands go through LargeDataHandler — no raw BLE characteristic writes.
 *
 * Confirmed command protocol from CyanBridge source:
 * - Camera:       glassesControl([0x02, 0x01, 0x01])
 * - Video start:  glassesControl([0x02, 0x01, 0x02])
 * - Video stop:   glassesControl([0x02, 0x01, 0x03])
 * - Audio start:  glassesControl([0x02, 0x01, 0x08])
 * - Audio stop:   glassesControl([0x02, 0x01, 0x0c])
 * - Stop AI mic:  glassesControl([0x02, 0x01, 0x0b])
 * - Media count:  glassesControl([0x02, 0x04])
 * - Download:     glassesControl([0x02, 0x01, 0x04])
 *
 * Notification bytes (loadData[6]):
 * - 0x02 = AI Photo button pressed
 * - 0x03 (+loadData[7]==1) = AI Voice button pressed
 * - 0x05 = Battery report
 */
class CyanBleManager(private val context: Context) {

    private val _connectionState = MutableStateFlow(ConnectionState.IDLE)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _glassesStatus = MutableStateFlow(GlassesStatus())
    val glassesStatus: StateFlow<GlassesStatus> = _glassesStatus.asStateFlow()

    private val _events = MutableStateFlow<BleEvent?>(null)
    val events: StateFlow<BleEvent?> = _events.asStateFlow()

    // Expose discovered chars map (empty — SDK handles this internally)
    val discoveredChars: StateFlow<Map<String, List<String>>> =
        MutableStateFlow<Map<String, List<String>>>(emptyMap()).asStateFlow()

    // Expose scanned devices (SDK handles scanning internally)
    val scannedDevices: StateFlow<List<GlassesDevice>> =
        MutableStateFlow<List<GlassesDevice>>(emptyList()).asStateFlow()

    private var notifyListenerRegistered = false

    init {
        registerNotifyListener()
    }

    private fun registerNotifyListener() {
        if (notifyListenerRegistered) return
        LargeDataHandler.getInstance().addOutDeviceListener(100, deviceNotifyListener)
        notifyListenerRegistered = true
    }

    // ── Connection ────────────────────────────────────────────────────────────

    fun startScan() {
        _connectionState.value = ConnectionState.SCANNING
        BleOperateManager.getInstance().startScan()
    }

    fun stopScan() {
        BleOperateManager.getInstance().stopScan()
        if (_connectionState.value == ConnectionState.SCANNING) {
            _connectionState.value = ConnectionState.IDLE
        }
    }

    fun connectByMac(macAddress: String) {
        _connectionState.value = ConnectionState.CONNECTING
        BleOperateManager.getInstance().connectDirectly(macAddress)
    }

    fun disconnect() {
        _connectionState.value = ConnectionState.DISCONNECTING
        BleOperateManager.getInstance().unBindDevice()
    }

    val isConnected: Boolean
        get() = BleOperateManager.getInstance().isConnected

    // ── Commands ──────────────────────────────────────────────────────────────

    fun takePhoto() {
        LargeDataHandler.getInstance().glassesControl(
            byteArrayOf(0x02, 0x01, 0x01)
        ) { _, rsp ->
            if (rsp.dataType == 1 && rsp.errorCode == 0) {
                _events.value = BleEvent.PhotoTaken
                _glassesStatus.value = _glassesStatus.value.copy(
                    photoCount = _glassesStatus.value.photoCount + 1
                )
            }
        }
    }

    fun startVideo() {
        LargeDataHandler.getInstance().glassesControl(
            byteArrayOf(0x02, 0x01, 0x02)
        ) { _, rsp ->
            if (rsp.dataType == 1 && rsp.errorCode == 0 && rsp.workTypeIng == 2) {
                _glassesStatus.value = _glassesStatus.value.copy(isRecordingVideo = true)
                _events.value = BleEvent.VideoStarted
            }
        }
    }

    fun stopVideo() {
        LargeDataHandler.getInstance().glassesControl(
            byteArrayOf(0x02, 0x01, 0x03)
        ) { _, rsp ->
            if (rsp.dataType == 1) {
                _glassesStatus.value = _glassesStatus.value.copy(isRecordingVideo = false)
                _events.value = BleEvent.VideoStopped
            }
        }
    }

    fun startAudio() {
        LargeDataHandler.getInstance().glassesControl(
            byteArrayOf(0x02, 0x01, 0x08)
        ) { _, rsp ->
            if (rsp.dataType == 1 && rsp.errorCode == 0) {
                _glassesStatus.value = _glassesStatus.value.copy(isRecordingAudio = true)
                _events.value = BleEvent.AudioStarted
            }
        }
    }

    fun stopAudio() {
        LargeDataHandler.getInstance().glassesControl(
            byteArrayOf(0x02, 0x01, 0x0c)
        ) { _, rsp ->
            if (rsp.dataType == 1) {
                _glassesStatus.value = _glassesStatus.value.copy(isRecordingAudio = false)
                _events.value = BleEvent.AudioStopped
            }
        }
    }

    fun stopAiMic() {
        LargeDataHandler.getInstance().glassesControl(
            byteArrayOf(0x02, 0x01, 0x0b)
        ) { _, _ -> }
    }

    fun requestBattery() {
        LargeDataHandler.getInstance().syncBattery()
    }

    fun requestMediaCount() {
        LargeDataHandler.getInstance().glassesControl(
            byteArrayOf(0x02, 0x04)
        ) { _, rsp ->
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

    fun syncTime() {
        LargeDataHandler.getInstance().syncTime { _, _ -> }
    }

    fun requestVersion() {
        LargeDataHandler.getInstance().syncDeviceInfo { _, response ->
            if (response != null) {
                val version = "FW: ${response.firmwareVersion} WiFi: ${response.wifiFirmwareVersion}"
                _glassesStatus.value = _glassesStatus.value.copy(firmwareVersion = version)
                _events.value = BleEvent.VersionInfo(version)
            }
        }
    }

    fun enterDownloadMode() {
        LargeDataHandler.getInstance().glassesControl(
            byteArrayOf(0x02, 0x01, 0x04)
        ) { _, _ -> }
    }

    fun exitDownloadMode() {
        LargeDataHandler.getInstance().glassesControl(
            byteArrayOf(0x02, 0x01, 0x09)
        ) { _, _ -> }
    }

    // ── Notification listener ─────────────────────────────────────────────────

    private val deviceNotifyListener = object : GlassesDeviceNotifyListener() {
        override fun parseData(cmdType: Int, response: GlassesDeviceNotifyRsp) {
            val load = response.loadData
            if (load.size < 7) return

            when (load[6].toInt()) {
                // Battery report
                0x05 -> {
                    val battery = load[7].toInt() and 0xFF
                    val charging = load[8].toInt() == 1
                    _glassesStatus.value = _glassesStatus.value.copy(
                        battery = battery, isCharging = charging
                    )
                    _events.value = BleEvent.BatteryUpdate(battery, charging)
                    _connectionState.value = ConnectionState.CONNECTED
                }
                // AI Photo button — Button 1 short press
                0x02 -> {
                    _events.value = BleEvent.AiPhotoRequested
                    _connectionState.value = ConnectionState.CONNECTED
                }
                // AI Voice button — Button 1 long press / Hey Cyan
                0x03 -> {
                    if (load.size > 7 && load[7].toInt() == 1) {
                        _events.value = BleEvent.AiVoiceRequested
                        _connectionState.value = ConnectionState.CONNECTED
                    }
                }
                // Media count update
                0x0f -> {
                    if (load.size >= 10) {
                        val photos = load[7].toInt() and 0xFF
                        val videos = load[8].toInt() and 0xFF
                        val audio  = load[9].toInt() and 0xFF
                        _glassesStatus.value = _glassesStatus.value.copy(
                            photoCount = photos, videoCount = videos, audioCount = audio
                        )
                    }
                }
            }
        }
    }

    fun updateConnectionState(connected: Boolean) {
        _connectionState.value = if (connected) {
            // Initial sync on connect
            syncTime()
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                requestBattery()
                requestMediaCount()
            }, 1000)
            ConnectionState.CONNECTED
        } else {
            ConnectionState.DISCONNECTED
        }
    }

    fun close() {
        if (notifyListenerRegistered) {
            try {
                LargeDataHandler.getInstance().removeOutDeviceListener(100)
            } catch (_: Exception) {}
            notifyListenerRegistered = false
        }
    }
}
