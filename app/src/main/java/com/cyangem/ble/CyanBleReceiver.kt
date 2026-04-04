package com.cyangem.ble

import android.bluetooth.BluetoothDevice
import android.util.Log
import com.oudmon.ble.base.bluetooth.BleOperateManager
import com.oudmon.ble.base.bluetooth.DeviceManager
import com.oudmon.ble.base.bluetooth.QCBluetoothCallbackCloneReceiver
import com.oudmon.ble.base.communication.LargeDataHandler
import org.greenrobot.eventbus.EventBus

/**
 * SDK BLE callback receiver — must extend QCBluetoothCallbackCloneReceiver.
 * Registered with LocalBroadcastManager using BleAction.getIntentFilter().
 * Mirrors CyanBridge's MyBluetoothReceiver exactly.
 */
class CyanBleReceiver : QCBluetoothCallbackCloneReceiver() {

    override fun connectStatue(device: BluetoothDevice?, connected: Boolean) {
        Log.d("CyanGem_BLE", "connectStatue: connected=$connected device=${device?.address}")
        if (device != null && connected) {
            device.name?.let { DeviceManager.getInstance().deviceName = it }
        } else {
            EventBus.getDefault().post(BleConnectionEvent(false))
        }
    }

    override fun onServiceDiscovered() {
        Log.d("CyanGem_BLE", "onServiceDiscovered — enabling data channel")
        // CRITICAL: must call initEnable() so commands work after connection
        LargeDataHandler.getInstance().initEnable()
        BleOperateManager.getInstance().isReady = true
        EventBus.getDefault().post(BleConnectionEvent(true))
    }

    override fun onCharacteristicChange(address: String?, uuid: String?, data: ByteArray?) {
        // BLE data received — SDK handles routing internally
    }

    override fun onCharacteristicRead(uuid: String?, data: ByteArray?) {
        // Version info etc
    }
}

/** Posted via EventBus when BLE connection state changes */
data class BleConnectionEvent(val connected: Boolean)
