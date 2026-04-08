package com.cyangem.ble

import android.bluetooth.BluetoothDevice
import android.util.Log
import com.cyangem.BleStateEvent
import com.oudmon.ble.base.bluetooth.BleOperateManager
import com.oudmon.ble.base.bluetooth.DeviceManager
import com.oudmon.ble.base.bluetooth.LargeDataParser
import com.oudmon.ble.base.bluetooth.QCBluetoothCallbackCloneReceiver
import com.oudmon.ble.base.communication.LargeDataHandler
import org.greenrobot.eventbus.EventBus

/**
 * SDK BLE callback receiver — extends QCBluetoothCallbackCloneReceiver.
 * Registered with LocalBroadcastManager using BleAction.getIntentFilter().
 *
 * FIX: onCharacteristicChange now routes bytes through LargeDataParser.
 * This is the critical missing link — without this, every photo/video/audio
 * byte the glasses send over BLE is silently discarded.
 *
 * The flow after this fix:
 *   glasses sends BLE notify → onCharacteristicChange fires
 *     → LargeDataParser.parseBigLargeData() assembles multi-packet data
 *       → ILargeDataImageResponse.parseData() fires when photo is complete
 *         → CyanBleManager.fetchLatestPhoto() callback delivers the JPEG
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
        EventBus.getDefault().post(BleStateEvent(true))
    }

    /**
     * FIX: Route incoming BLE characteristic bytes through LargeDataParser.
     *
     * Previously this was empty — every byte the glasses sent was thrown away.
     * LargeDataParser.parseBigLargeData() handles:
     *   - packet reassembly across multiple BLE notify packets
     *   - routing by UUID to the correct handler (photos, battery, device info, etc.)
     *   - triggering ILargeDataImageResponse when a full photo is assembled
     */
    override fun onCharacteristicChange(address: String?, uuid: String?, data: ByteArray?) {
        if (uuid != null && data != null) {
            LargeDataParser.getInstance().parseBigLargeData(uuid, data)
        }
    }

    override fun onCharacteristicRead(uuid: String?, data: ByteArray?) {}
}
