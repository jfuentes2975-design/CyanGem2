package com.cyangem.ui

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay

// =============================================================================
// HC-013 — Bluetooth status helper for the Home screen's Bluetooth/audio
// checklist. Reads OS-level adapter state with a 5-second refresh tick.
//
// Intentionally simple: returns Off / On / Unsupported / Permission missing.
// Does NOT enumerate connected devices — that requires a BluetoothProfile
// proxy which is async and adds lifecycle complexity. For pairing/connection
// status of a specific glasses device, the user is directed to OS Settings.
// =============================================================================

enum class BtAdapterStatus {
    On,
    Off,
    Unsupported,
    PermissionMissing
}

/**
 * Composable that returns a polling [State] of [BtAdapterStatus]. Refreshes
 * every 5 seconds while the composable is in composition. No leaks: the
 * LaunchedEffect cancels on dispose.
 */
@Composable
fun rememberBluetoothStatus(context: Context): State<BtAdapterStatus> {
    val state = remember { mutableStateOf(readBluetoothStatus(context)) }
    LaunchedEffect(Unit) {
        while (true) {
            state.value = readBluetoothStatus(context)
            delay(5_000)
        }
    }
    return state
}

private fun readBluetoothStatus(context: Context): BtAdapterStatus {
    // On Android 12+ we need BLUETOOTH_CONNECT to query adapter state details.
    // For a simple isEnabled read, BLUETOOTH_CONNECT is recommended but a
    // missing permission shouldn't crash — surface as PermissionMissing.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) return BtAdapterStatus.PermissionMissing
    }

    val mgr = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        ?: return BtAdapterStatus.Unsupported
    val adapter: BluetoothAdapter = mgr.adapter ?: return BtAdapterStatus.Unsupported

    return try {
        if (adapter.isEnabled) BtAdapterStatus.On else BtAdapterStatus.Off
    } catch (e: SecurityException) {
        BtAdapterStatus.PermissionMissing
    } catch (e: Exception) {
        BtAdapterStatus.Unsupported
    }
}

/** Human-readable label for a [BtAdapterStatus]. Neutral phrasing for the
 *  permission-missing and unsupported cases — never alarms the user. */
fun BtAdapterStatus.displayLabel(): String = when (this) {
    BtAdapterStatus.On -> "Bluetooth: On"
    BtAdapterStatus.Off -> "Bluetooth: Off"
    BtAdapterStatus.Unsupported -> "Bluetooth status unavailable"
    BtAdapterStatus.PermissionMissing -> "Bluetooth status unavailable — check phone settings"
}
