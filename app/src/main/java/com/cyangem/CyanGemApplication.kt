package com.cyangem

import android.app.Application
import android.os.Build
import com.oudmon.ble.base.bluetooth.BleAction
import com.oudmon.ble.base.bluetooth.BleBaseControl
import com.oudmon.ble.base.bluetooth.BleOperateManager
import com.oudmon.ble.base.communication.LargeDataHandler
import com.cyangem.ble.CyanBleReceiver

class CyanGemApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initBle()
    }

    private fun initBle() {
        LargeDataHandler.getInstance()
        BleOperateManager.getInstance(this)
        BleOperateManager.getInstance().setApplication(this)
        BleOperateManager.getInstance().init()
        BleBaseControl.getInstance(this).setmContext(this)

        // Register for SDK BLE broadcast events
        val intentFilter = BleAction.getIntentFilter()
        val receiver = CyanBleReceiver()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            registerReceiver(receiver, intentFilter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(receiver, intentFilter)
        }
    }
}
