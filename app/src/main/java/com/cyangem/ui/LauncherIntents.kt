package com.cyangem.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings

internal fun openGeminiApp(context: Context): Boolean {
    return launchFirstAvailablePackage(
        context = context,
        packages = listOf(
            "com.google.android.apps.bard",
            "com.google.android.googlequicksearchbox"
        )
    )
}

internal fun openHeyCyanApp(context: Context): Boolean {
    return launchFirstAvailablePackage(
        context = context,
        packages = listOf(
            "com.heycyan.android",
            "com.heycyan.app",
            "com.heycyan.glasses",
            "com.cyan.heycyan"
        )
    )
}

internal fun openBluetoothSettings(context: Context): Boolean {
    return try {
        val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        true
    } catch (_: Exception) {
        false
    }
}

internal fun openSamsungGallery(context: Context): Boolean {
    return launchFirstAvailablePackage(
        context = context,
        packages = listOf("com.sec.android.gallery3d")
    )
}

internal fun openGooglePhotos(context: Context): Boolean {
    return launchFirstAvailablePackage(
        context = context,
        packages = listOf("com.google.android.apps.photos")
    )
}

internal fun openSystemGallery(context: Context): Boolean {
    return try {
        if (openSamsungGallery(context)) return true
        if (openGooglePhotos(context)) return true

        val fallbackIntent = Intent(Intent.ACTION_VIEW).apply {
            type = "image/*"
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        context.startActivity(fallbackIntent)
        true
    } catch (_: Exception) {
        false
    }
}

private fun launchFirstAvailablePackage(
    context: Context,
    packages: List<String>
): Boolean {
    return try {
        val packageManager = context.packageManager

        for (packageName in packages) {
            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                return true
            }
        }

        false
    } catch (_: Exception) {
        false
    }
}

internal fun isGooglePhotosInstalled(context: android.content.Context): Boolean {
    return isPackageInstalled(context, "com.google.android.apps.photos")
}

internal fun isSamsungGalleryInstalled(context: android.content.Context): Boolean {
    return isPackageInstalled(context, "com.sec.android.gallery3d")
}

internal fun isPackageInstalled(
    context: android.content.Context,
    packageName: String
): Boolean {
    return try {
        context.packageManager.getLaunchIntentForPackage(packageName) != null
    } catch (_: Exception) {
        false
    }
}
