package com.cyangem.media

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout

/**
 * Manages smart Wi-Fi switching between home network and glasses hotspot.
 * Home network: "Kiroverto"
 * Glasses hotspot: appears as "DIRECT-xx" or "W630-xx"
 */
class WifiDirectManager(private val context: Context) {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val wifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    val HOME_WIFI = "Kiroverto"

    /**
     * Returns true if currently connected to home Wi-Fi.
     * If on home Wi-Fi → use phone camera for Gemini queries (no disconnect needed).
     * If NOT on home Wi-Fi (outdoors/mobile data) → try glasses Wi-Fi Direct.
     */
    fun isOnHomeWifi(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val caps = connectivityManager.getNetworkCapabilities(network) ?: return false
        if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return false
        val wifiInfo = wifiManager.connectionInfo ?: return false
        val ssid = wifiInfo.ssid?.replace("\"", "") ?: return false
        return ssid == HOME_WIFI
    }

    /**
     * Returns true if on mobile data (outdoors) — ideal for glasses Wi-Fi sync.
     */
    fun isOnMobileData(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val caps = connectivityManager.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
    }

    /**
     * Determine the best strategy for "What am I looking at?"
     */
    fun getQueryStrategy(): QueryStrategy {
        return when {
            isOnHomeWifi() -> QueryStrategy.USE_PHONE_CAMERA  // indoors
            isOnMobileData() -> QueryStrategy.USE_GLASSES_WIFI // outdoors
            else -> QueryStrategy.USE_PHONE_CAMERA             // fallback
        }
    }
}

enum class QueryStrategy {
    USE_PHONE_CAMERA,   // indoors on home Wi-Fi — instant, no switching
    USE_GLASSES_WIFI    // outdoors on mobile data — sync from glasses
}
