package com.cyangem.ble

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Receives BLE state broadcasts from the SDK.
 * Connection state changes are handled via EventBus in MainActivity.
 * This receiver just satisfies the SDK's internal broadcast registration requirement.
 */
class CyanBleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // SDK fires LocalBroadcast intents for BLE state changes.
        // MainActivity handles connection state via EventBus subscription.
    }
}
