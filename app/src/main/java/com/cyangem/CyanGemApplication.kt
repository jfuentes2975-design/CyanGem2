package com.cyangem

import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.oudmon.ble.base.bluetooth.BleAction
import com.oudmon.ble.base.bluetooth.BleBaseControl
import com.oudmon.ble.base.bluetooth.BleOperateManager
import com.oudmon.ble.base.communication.LargeDataHandler
import org.greenrobot.eventbus.EventBus

class CyanGemApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        try {
            initBle()
        } catch (e: Exception) {
            Log.e("CyanGem", "BLE init error: ${e.message}", e)
        }
    }

    private fun initBle() {
        // Step 1: Init SDK singletons — exact order from CyanBridge MyApplication.initReceiver()
        LargeDataHandler.getInstance()
        BleOperateManager.getInstance(this)
        BleOperateManager.getInstance().setApplication(this)
        BleOperateManager.getInstance().init()

        // Step 2: Register system Bluetooth receiver (bond state, ACL connect/disconnect)
        // Must use RECEIVER_EXPORTED — these are system Bluetooth broadcasts
        val deviceFilter = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }
        val bluetoothReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val connectState = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1)
                if (connectState == BluetoothAdapter.STATE_OFF) {
                    BleOperateManager.getInstance().disconnect()
                    EventBus.getDefault().post(BleStateEvent(false))
                } else if (connectState == BluetoothAdapter.STATE_ON) {
                    EventBus.getDefault().post(BleStateEvent(true))
                }
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            registerReceiver(bluetoothReceiver, deviceFilter, RECEIVER_EXPORTED)
        } else {
            registerReceiver(bluetoothReceiver, deviceFilter)
        }

        // Step 3: Register SDK internal receiver via LocalBroadcastManager
        // BleAction.getIntentFilter() is for SDK-internal LocalBroadcasts — NOT system broadcasts
        val sdkFilter = BleAction.getIntentFilter()
        val sdkReceiver = com.cyangem.ble.CyanBleReceiver()
        LocalBroadcastManager.getInstance(this).registerReceiver(sdkReceiver, sdkFilter)

        // Step 4: Set BLE base context
        BleBaseControl.getInstance(this).setmContext(this)
    }
}

/** Simple event posted to EventBus when Bluetooth state changes */
data class BleStateEvent(val enabled: Boolean)
