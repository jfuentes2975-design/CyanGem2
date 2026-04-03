package com.cyangem.ble

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ConnectionState { IDLE, SCANNING, CONNECTING, CONNECTED, DISCONNECTING, DISCONNECTED }

data class GlassesDevice(
    val device: BluetoothDevice,
    val rssi: Int,
    val name: String
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
    /** Glasses button pressed requesting AI photo analysis — route to Gemini */
    object AiPhotoRequested : BleEvent()
    data class BatteryUpdate(val level: Int, val charging: Boolean) : BleEvent()
    data class MediaCountUpdate(val photos: Int, val videos: Int, val audio: Int) : BleEvent()
    data class VersionInfo(val firmware: String) : BleEvent()
    data class RawNotification(val data: ByteArray) : BleEvent()
    data class Error(val message: String) : BleEvent()
}

@SuppressLint("MissingPermission")
class CyanBleManager(private val context: Context) {

    private val bluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter: BluetoothAdapter? get() = bluetoothManager.adapter

    private var scanner: BluetoothLeScanner? = null
    private var gatt: BluetoothGatt? = null
    private var writeChar: BluetoothGattCharacteristic? = null
    private var notifyChar: BluetoothGattCharacteristic? = null

    private val handler = Handler(Looper.getMainLooper())
    private val scanTimeout = 15_000L

    // ── Public state ──────────────────────────────────────────────────────────
    private val _connectionState = MutableStateFlow(ConnectionState.IDLE)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _scannedDevices = MutableStateFlow<List<GlassesDevice>>(emptyList())
    val scannedDevices: StateFlow<List<GlassesDevice>> = _scannedDevices.asStateFlow()

    private val _glassesStatus = MutableStateFlow(GlassesStatus())
    val glassesStatus: StateFlow<GlassesStatus> = _glassesStatus.asStateFlow()

    private val _events = MutableStateFlow<BleEvent?>(null)
    val events: StateFlow<BleEvent?> = _events.asStateFlow()

    // Discovered characteristics (for BLE inspector / debug)
    private val _discoveredChars = MutableStateFlow<Map<String, List<String>>>(emptyMap())
    val discoveredChars: StateFlow<Map<String, List<String>>> = _discoveredChars.asStateFlow()

    // ── Scanning ──────────────────────────────────────────────────────────────
    fun startScan() {
        if (adapter?.isEnabled != true) {
            emitError("Bluetooth is disabled"); return
        }
        _scannedDevices.value = emptyList()
        _connectionState.value = ConnectionState.SCANNING

        val filters = BleConstants.DEVICE_NAME_PREFIXES.map { prefix ->
            ScanFilter.Builder().setDeviceName(prefix).build()
        } + ScanFilter.Builder()
            .setServiceUuid(android.os.ParcelUuid(BleConstants.SERVICE_PRIMARY))
            .build()

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        scanner = adapter?.bluetoothLeScanner
        scanner?.startScan(filters, settings, scanCallback)

        // Auto-stop after timeout
        handler.postDelayed({ stopScan() }, scanTimeout)
    }

    fun stopScan() {
        scanner?.stopScan(scanCallback)
        scanner = null
        if (_connectionState.value == ConnectionState.SCANNING) {
            _connectionState.value = ConnectionState.IDLE
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val name = result.device.name ?: return
            val existing = _scannedDevices.value.toMutableList()
            val idx = existing.indexOfFirst { it.device.address == result.device.address }
            val device = GlassesDevice(result.device, result.rssi, name)
            if (idx >= 0) existing[idx] = device else existing.add(device)
            _scannedDevices.value = existing
        }
        override fun onScanFailed(errorCode: Int) {
            emitError("Scan failed: $errorCode")
            _connectionState.value = ConnectionState.IDLE
        }
    }

    // ── Connecting ────────────────────────────────────────────────────────────
    fun connect(device: BluetoothDevice) {
        stopScan()
        _connectionState.value = ConnectionState.CONNECTING
        gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    fun disconnect() {
        _connectionState.value = ConnectionState.DISCONNECTING
        gatt?.disconnect()
    }

    // ── GATT callbacks ────────────────────────────────────────────────────────
    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    _connectionState.value = ConnectionState.CONNECTED
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    writeChar = null
                    notifyChar = null
                    _connectionState.value = ConnectionState.DISCONNECTED
                    gatt.close()
                    this@CyanBleManager.gatt = null
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                emitError("Service discovery failed: $status"); return
            }

            // Log all discovered services + characteristics for debug
            val charMap = mutableMapOf<String, List<String>>()
            gatt.services.forEach { svc ->
                charMap[svc.uuid.toString()] = svc.characteristics.map { c ->
                    "${c.uuid} props=${c.properties}"
                }
            }
            _discoveredChars.value = charMap

            // Try primary service first
            val primarySvc = gatt.getService(BleConstants.SERVICE_PRIMARY)
                ?: gatt.getService(BleConstants.SERVICE_SECONDARY)

            if (primarySvc != null) {
                writeChar = primarySvc.getCharacteristic(BleConstants.CHAR_WRITE)
                    ?: primarySvc.getCharacteristic(BleConstants.NUS_RX)
                    ?: primarySvc.characteristics.firstOrNull { c ->
                        c.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0 ||
                        c.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0
                    }

                notifyChar = primarySvc.getCharacteristic(BleConstants.CHAR_NOTIFY)
                    ?: primarySvc.getCharacteristic(BleConstants.NUS_TX)
                    ?: primarySvc.characteristics.firstOrNull { c ->
                        c.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0
                    }
            }

            notifyChar?.let { enableNotifications(gatt, it) }

            // Initial sync: datetime + battery request
            handler.postDelayed({
                sendCommand(BleConstants.CMD_DATETIME_SYNC,
                    BleConstants.buildDatetimePayload())
                handler.postDelayed({ sendCommand(BleConstants.CMD_BATTERY_REQ) }, 300)
            }, 500)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            parseNotification(value)
        }

        // Deprecated but needed for API < 33
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            parseNotification(characteristic.value ?: return)
        }
    }

    // ── Notification parsing ──────────────────────────────────────────────────
    private fun parseNotification(data: ByteArray) {
        if (data.isEmpty()) return

        // Emit raw event for debug overlay
        _events.value = BleEvent.RawNotification(data)

        when (data[0]) {
            BleConstants.CMD_BATTERY_REQ -> {
                if (data.size >= 3) {
                    val level = data[1].toInt() and 0xFF
                    val charging = data[2] != 0.toByte()
                    _glassesStatus.value = _glassesStatus.value.copy(
                        battery = level, isCharging = charging
                    )
                    _events.value = BleEvent.BatteryUpdate(level, charging)
                }
            }
            BleConstants.CMD_MEDIA_COUNT -> {
                if (data.size >= 4) {
                    val photos = data[1].toInt() and 0xFF
                    val videos = data[2].toInt() and 0xFF
                    val audio  = data[3].toInt() and 0xFF
                    _glassesStatus.value = _glassesStatus.value.copy(
                        photoCount = photos, videoCount = videos, audioCount = audio
                    )
                    _events.value = BleEvent.MediaCountUpdate(photos, videos, audio)
                }
            }
            BleConstants.CMD_PHOTO -> {
                _events.value = BleEvent.PhotoTaken
                _glassesStatus.value = _glassesStatus.value.copy(
                    photoCount = _glassesStatus.value.photoCount + 1
                )
            }
            BleConstants.CMD_VIDEO_START -> {
                _glassesStatus.value = _glassesStatus.value.copy(isRecordingVideo = true)
                _events.value = BleEvent.VideoStarted
            }
            BleConstants.CMD_VIDEO_STOP -> {
                _glassesStatus.value = _glassesStatus.value.copy(isRecordingVideo = false)
                _events.value = BleEvent.VideoStopped
            }
            BleConstants.CMD_AUDIO_START -> {
                _glassesStatus.value = _glassesStatus.value.copy(isRecordingAudio = true)
                _events.value = BleEvent.AudioStarted
            }
            BleConstants.CMD_AUDIO_STOP -> {
                _glassesStatus.value = _glassesStatus.value.copy(isRecordingAudio = false)
                _events.value = BleEvent.AudioStopped
            }
            BleConstants.CMD_AI_PHOTO -> {
                // Glasses requested AI photo — route to Gemini instead of stock AI
                _events.value = BleEvent.AiPhotoRequested
            }
            BleConstants.CMD_VERSION_REQ -> {
                val version = String(data.drop(1).toByteArray()).trim()
                _glassesStatus.value = _glassesStatus.value.copy(firmwareVersion = version)
                _events.value = BleEvent.VersionInfo(version)
            }
        }
    }

    // ── Command sending ───────────────────────────────────────────────────────
    fun sendCommand(cmd: Byte, payload: ByteArray = ByteArray(0)): Boolean {
        val char = writeChar ?: run { emitError("Not connected or write char not found"); return false }
        val frame = BleConstants.buildCommand(cmd, payload)
        char.value = frame
        @Suppress("DEPRECATION")
        return gatt?.writeCharacteristic(char) == true
    }

    fun takePhoto()        = sendCommand(BleConstants.CMD_PHOTO)
    fun startVideo()       = sendCommand(BleConstants.CMD_VIDEO_START)
    fun stopVideo()        = sendCommand(BleConstants.CMD_VIDEO_STOP)
    fun startAudio()       = sendCommand(BleConstants.CMD_AUDIO_START)
    fun stopAudio()        = sendCommand(BleConstants.CMD_AUDIO_STOP)
    fun requestBattery()   = sendCommand(BleConstants.CMD_BATTERY_REQ)
    fun requestMediaCount()= sendCommand(BleConstants.CMD_MEDIA_COUNT)

    // ── Helpers ───────────────────────────────────────────────────────────────
    private fun enableNotifications(gatt: BluetoothGatt, char: BluetoothGattCharacteristic) {
        gatt.setCharacteristicNotification(char, true)
        val descriptor = char.getDescriptor(BleConstants.CCCD) ?: return
        descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        @Suppress("DEPRECATION")
        gatt.writeDescriptor(descriptor)
    }

    private fun emitError(msg: String) {
        _events.value = BleEvent.Error(msg)
    }

    fun close() {
        stopScan()
        gatt?.close()
        gatt = null
    }
}
