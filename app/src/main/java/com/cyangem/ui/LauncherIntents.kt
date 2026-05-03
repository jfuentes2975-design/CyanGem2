package com.cyangem.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log

// =============================================================================
// HC-019 — Launch helpers. Slimmed and honest (carried from HC-018) plus
// the new com.glasssutdio.wear HeyCyan candidate.
// =============================================================================

private const val TAG = "CyanGem_Launcher"

private val GEMINI_PACKAGE_CANDIDATES = listOf(
    "com.google.android.apps.bard",
    "com.google.android.apps.gemini",
    "com.google.android.googlequicksearchbox"
)

private val HEY_CYAN_PACKAGE_CANDIDATES = listOf(
    // HC-019 NEW — from Juan's technical fix-forward document
    "com.glasssutdio.wear",
    // Carried unchanged
    "com.heycyan.app",
    "com.heyx.heycyan",
    "io.heycyan.app",
    "com.oudmon.heycyan",
    "com.cyan.glasses"
)

// =============================================================================
// Gemini — honest launch
// =============================================================================

fun openGeminiApp(context: Context): Boolean {
    val pm = context.packageManager
    for (pkg in GEMINI_PACKAGE_CANDIDATES) {
        try {
            val intent = pm.getLaunchIntentForPackage(pkg)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                Log.d(TAG, "Opened Gemini app via $pkg")
                return true
            }
        } catch (e: Exception) {
            Log.w(TAG, "Gemini candidate $pkg threw: ${e.message}")
        }
    }
    return try {
        val voiceIntent = Intent(Intent.ACTION_VOICE_COMMAND).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(voiceIntent)
        Log.d(TAG, "Fell back to ACTION_VOICE_COMMAND")
        true
    } catch (e: Exception) {
        Log.w(TAG, "ACTION_VOICE_COMMAND failed: ${e.message}")
        false
    }
}

// =============================================================================
// Hey Cyan native app
// =============================================================================

fun openHeyCyanApp(context: Context): Boolean {
    val pm = context.packageManager
    for (pkg in HEY_CYAN_PACKAGE_CANDIDATES) {
        try {
            val intent = pm.getLaunchIntentForPackage(pkg)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                Log.d(TAG, "Opened Hey Cyan via $pkg")
                return true
            }
        } catch (e: Exception) {
            Log.w(TAG, "Hey Cyan candidate $pkg threw: ${e.message}")
        }
    }
    return false
}

// =============================================================================
// Bluetooth + system settings
// =============================================================================

fun openBluetoothSettings(context: Context): Boolean {
    return try {
        val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
        true
    } catch (e: Exception) {
        Log.w(TAG, "Open Bluetooth settings failed: ${e.message}")
        false
    }
}

fun openAppSettings(context: Context): Boolean {
    return try {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
        true
    } catch (e: Exception) {
        Log.w(TAG, "Open app settings failed: ${e.message}")
        false
    }
}

// =============================================================================
// Gallery apps
// =============================================================================

fun openSamsungGallery(context: Context): Boolean {
    return try {
        val pm = context.packageManager
        val intent = pm.getLaunchIntentForPackage(PackageDetector.PKG_SAMSUNG_GALLERY) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    } catch (e: Exception) {
        Log.w(TAG, "Open Samsung Gallery failed: ${e.message}")
        false
    }
}

fun openGooglePhotos(context: Context): Boolean {
    return try {
        val pm = context.packageManager
        val intent = pm.getLaunchIntentForPackage(PackageDetector.PKG_GOOGLE_PHOTOS) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    } catch (e: Exception) {
        Log.w(TAG, "Open Google Photos failed: ${e.message}")
        false
    }
}

fun openSystemGallery(context: Context): Boolean {
    return try {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
        true
    } catch (e: Exception) {
        Log.w(TAG, "Open system gallery failed: ${e.message}")
        false
    }
}

fun openMediaItem(context: Context, uri: Uri, mimeType: String?): Boolean {
    return try {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType ?: "image/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        true
    } catch (e: Exception) {
        Log.w(TAG, "Open media item failed: ${e.message}")
        false
    }
}

fun shareMediaItem(context: Context, uri: Uri, mimeType: String?): Boolean {
    return try {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType ?: "image/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "Share"))
        true
    } catch (e: Exception) {
        Log.w(TAG, "Share media item failed: ${e.message}")
        false
    }
}

// =============================================================================
// Convenience wrappers
// =============================================================================

fun isSamsungGalleryInstalled(context: Context): Boolean =
    PackageDetector.isSamsungGalleryInstalled(context)

fun isGooglePhotosInstalled(context: Context): Boolean =
    PackageDetector.isGooglePhotosInstalled(context)

fun isHeyCyanInstalled(context: Context): Boolean =
    PackageDetector.isHeyCyanInstalled(context)

fun isGeminiInstalled(context: Context): Boolean =
    PackageDetector.isGeminiInstalled(context)

fun detectInstalledHeyCyan(context: Context): String? =
    PackageDetector.detectHeyCyan(context)

fun detectInstalledGemini(context: Context): String? =
    PackageDetector.detectGemini(context)
