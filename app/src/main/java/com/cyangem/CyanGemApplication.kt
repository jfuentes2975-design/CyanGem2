package com.cyangem

import android.app.Application
import com.oudmon.ble.base.bluetooth.BleAction
import com.oudmon.ble.base.bluetooth.BleBaseControl
import com.oudmon.ble.base.bluetooth.BleOperateManager
import com.oudmon.ble.base.communication.LargeDataHandler
import androidx.localbroadcastmanager.content.LocalBroadcastManager

class CyanGemApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initBle()
    }

    private fun initBle() {
        // Initialize in correct order per CyanBridge source (MyApplication.initReceiver)
        LargeDataHandler.getInstance()
        BleOperateManager.getInstance(this)
        BleOperateManager.getInstance().setApplication(this)
        BleOperateManager.getInstance().init()
        BleBaseControl.getInstance(this).setmContext(this)

        // Register LocalBroadcast receiver for SDK internal events
        val intentFilter = BleAction.getIntentFilter()
        val receiver = com.cyangem.ble.CyanBleReceiver()
        LocalBroadcastManager.getInstance(this)
            .registerReceiver(receiver, intentFilter)
    }
}
