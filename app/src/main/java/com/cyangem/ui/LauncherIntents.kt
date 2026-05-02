package com.cyangem.ui

import android.content.Context
import android.content.Intent
import android.util.Log

// =============================================================================
// HC-013 — Helpers for launching external apps from the Home screen buttons.
//
// Each helper tries a list of candidate package names via
// PackageManager.getLaunchIntentForPackage. Returns true on first successful
// launch, false if no candidate resolves (UI shows a snackbar in that case).
//
// Package visibility on Android 11+ requires a <queries> declaration in
// AndroidManifest.xml — added by HC-013.
// =============================================================================

private const val TAG = "CyanGem_Launcher"

private val GEMINI_PACKAGE_CANDIDATES = listOf(
    // Gemini standalone (formerly Bard). Check the most likely first.
    "com.google.android.apps.bard",
    // Hypothetical future Gemini-branded package — try anyway.
    "com.google.android.apps.gemini",
    // Google app — hosts Assistant / Gemini integration on most phones.
    "com.google.android.googlequicksearchbox"
)

private val HEY_CYAN_PACKAGE_CANDIDATES = listOf(
    // Best guesses for the Hey Cyan / Oudmon companion app. Juan can find
    // the exact package name via:
    //   adb shell pm list packages | grep -i cyan
    // and we hard-code the result as the first entry.
    "com.heycyan.app",
    "com.heyx.heycyan",
    "io.heycyan.app",
    "com.oudmon.heycyan",
    "com.cyan.glasses"
)

/**
 * Try to open the Gemini app. First tries known standalone packages, then
 * falls back to the public ACTION_VOICE_COMMAND intent which the OS routes
 * to the user's default voice assistant.
 *
 * @return true if any launch succeeded; false if everything failed.
 */
fun openGeminiApp(context: Context): Boolean {
    val pm = context.packageManager

    for (pkg in GEMINI_PACKAGE_CANDIDATES) {
        try {
            val intent = pm.getLaunchIntentForPackage(pkg)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                Log.d(TAG, "Opened Gemini via package: $pkg")
                return true
            }
        } catch (e: Exception) {
            Log.w(TAG, "Gemini candidate $pkg failed: ${e.message}")
            // continue to next
        }
    }

    // Fallback — public action, no <queries> needed.
    return try {
        val voiceIntent = Intent(Intent.ACTION_VOICE_COMMAND).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(voiceIntent)
        Log.d(TAG, "Opened voice assistant via ACTION_VOICE_COMMAND")
        true
    } catch (e: Exception) {
        Log.w(TAG, "ACTION_VOICE_COMMAND fallback failed: ${e.message}")
        false
    }
}

/**
 * Try to open the Hey Cyan companion app. Iterates the candidate list until
 * one resolves.
 *
 * @return true if a candidate launched; false if none resolved.
 */
fun openHeyCyanApp(context: Context): Boolean {
    val pm = context.packageManager
    for (pkg in HEY_CYAN_PACKAGE_CANDIDATES) {
        try {
            val intent = pm.getLaunchIntentForPackage(pkg)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                Log.d(TAG, "Opened Hey Cyan via package: $pkg")
                return true
            }
        } catch (e: Exception) {
            Log.w(TAG, "Hey Cyan candidate $pkg failed: ${e.message}")
        }
    }
    Log.w(TAG, "Hey Cyan app not found among candidates: $HEY_CYAN_PACKAGE_CANDIDATES")
    return false
}

/**
 * Open the OS Bluetooth settings page. Public action — no <queries> needed.
 *
 * @return true on success, false if the action is somehow not handled.
 */
fun openBluetoothSettings(context: Context): Boolean {
    return try {
        val intent = Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
        Log.d(TAG, "Opened OS Bluetooth settings")
        true
    } catch (e: Exception) {
        Log.w(TAG, "Open Bluetooth settings failed: ${e.message}")
        false
    }
}

/**
 * Diagnostic count — for the test plan, Juan can read the candidate list
 * length to know how many package names HC-013 attempted before giving up.
 */
val HEY_CYAN_CANDIDATE_COUNT: Int = HEY_CYAN_PACKAGE_CANDIDATES.size
val GEMINI_CANDIDATE_COUNT: Int = GEMINI_PACKAGE_CANDIDATES.size
