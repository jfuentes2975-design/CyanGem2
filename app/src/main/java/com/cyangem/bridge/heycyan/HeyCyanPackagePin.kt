package com.cyangem.bridge.heycyan

import android.content.Context
import android.util.Log

// =============================================================================
// HC-018 — Pin the resolved HeyCyan native app package name.
// Salvaged from HC-017 unchanged.
//
// Why pin? Once Juan has confirmed which Hey Cyan candidate package is
// installed (via Open HeyCyan working successfully), pinning that name in
// SharedPreferences saves us probing the candidate list on every cold
// launch.
//
// SCOPE: this is package-detection-and-persistence, NOT a BLE bridge. No
// commands sent, no fake claims. Surfaced in HC-018 Settings under the
// HeyCyan package row. Disabled by default (no auto-pin) — the user has
// to tap Pin explicitly.
//
// File location: kept in com.cyangem.bridge.heycyan because that's where
// HC-017 placed it. The companion HeyCyanBridge.kt (which DID have BLE
// dispatch) remains dormant in the codebase but no HC-018 code calls it.
// =============================================================================

object HeyCyanPackagePin {

    private const val TAG = "HeyCyanPackagePin"
    private const val PREFS = "cyangem_bridge_pin"
    private const val KEY_PACKAGE = "hey_cyan_package"

    fun read(context: Context): String? {
        return try {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_PACKAGE, null)
                ?.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            Log.w(TAG, "read failed: ${e.message}")
            null
        }
    }

    fun write(context: Context, packageName: String) {
        try {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_PACKAGE, packageName)
                .apply()
            Log.d(TAG, "pinned package: $packageName")
        } catch (e: Exception) {
            Log.w(TAG, "write failed: ${e.message}")
        }
    }

    fun clear(context: Context) {
        try {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_PACKAGE)
                .apply()
            Log.d(TAG, "package pin cleared")
        } catch (e: Exception) {
            Log.w(TAG, "clear failed: ${e.message}")
        }
    }

    fun isPinned(context: Context): Boolean = read(context) != null
}
