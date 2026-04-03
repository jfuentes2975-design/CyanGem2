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
        LargeDataHandler.getInstance()
        BleOperateManager.getInstance(this)
        BleOperateManager.getInstance().setApplication(this)
        BleOperateManager.getInstance().init()
        BleBaseControl.getInstance(this).setmContext(this)

        val intentFilter = BleAction.getIntentFilter()
        val receiver = com.cyangem.ble.CyanBleReceiver()
        LocalBroadcastManager.getInstance(this)
            .registerReceiver(receiver, intentFilter)
    }
}
