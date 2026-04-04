package com.cyangem.ble

import android.bluetooth.BluetoothDevice
import android.util.Log
import com.cyangem.BleStateEvent
import com.oudmon.ble.base.bluetooth.BleOperateManager
import com.oudmon.ble.base.bluetooth.DeviceManager
import com.oudmon.ble.base.bluetooth.QCBluetoothCallbackCloneReceiver
import com.oudmon.ble.base.communication.LargeDataHandler
import org.greenrobot.eventbus.EventBus

/**
 * SDK BLE callback receiver — extends QCBluetoothCallbackCloneReceiver.
 * Registered with LocalBroadcastManager using BleAction.getIntentFilter().
 * Mirrors CyanBridge's MyBluetoothReceiver exactly.
 *
 * Posts BleStateEvent (the type MainActivity @Subscribe listens for).
 */
class CyanBleReceiver : QCBluetoothCallbackCloneReceiver() {

    override fun connectStatue(device: BluetoothDevice?, connected: Boolean) {
        Log.d("CyanGem_BLE", "connectStatue: connected=$connected device=${device?.address}")
        if (device != null && connected) {
            device.name?.let { DeviceManager.getInstance().deviceName = it }
        } else {
            EventBus.getDefault().post(BleStateEvent(false))
        }
    }

    override fun onServiceDiscovered() {
        Log.d("CyanGem_BLE", "onServiceDiscovered — calling initEnable()")
        // CRITICAL: initEnable() must be called here or ALL commands fail silently
        LargeDataHandler.getInstance().initEnable()
        BleOperateManager.getInstance().isReady = true
        // Post the event type MainActivity listens for
        EventBus.getDefault().post(BleStateEvent(true))
    }

    override fun onCharacteristicChange(address: String?, uuid: String?, data: ByteArray?) {}
    override fun onCharacteristicRead(uuid: String?, data: ByteArray?) {}
}
