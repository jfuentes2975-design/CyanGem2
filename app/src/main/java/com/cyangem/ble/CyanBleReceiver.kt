package com.cyangem.ble

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.oudmon.ble.base.bluetooth.BleAction
import com.oudmon.ble.base.bluetooth.BleOperateManager

class CyanBleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // SDK fires these actions on connect/disconnect
        // MainActivity listens via EventBus — this receiver just keeps SDK internals happy
        when (intent.action) {
            BleAction.ACTION_GATT_CONNECTED -> { /* handled by EventBus */ }
            BleAction.ACTION_GATT_DISCONNECTED -> { /* handled by EventBus */ }
        }
    }
}
