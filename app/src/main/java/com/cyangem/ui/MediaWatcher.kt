package com.cyangem.ui

import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

// =============================================================================
// HC-018 — MediaStore ContentObserver helper. Salvaged from HC-017.
//
// Two Compose helpers, used by HomeScreen (Capture Check) and GalleryScreen:
//
//   rememberMediaObserver(enabled, onChange):
//     Registers a ContentObserver on Images + Video EXTERNAL_CONTENT_URI for
//     the lifetime of the calling composition. onChange fires on every
//     MediaStore mutation (insert, update, delete). [enabled] lets us pause
//     (e.g., before a Capture Check baseline is set).
//
//   OnLifecycleResume(onResume):
//     Calls onResume() on every Lifecycle.Event.ON_RESUME, so screens can
//     re-query when the user returns from the native HeyCyan app or any
//     other external app.
//
// These are pure read-only Android plumbing. No BLE, no fake camera,
// no Gemini control. Salvaged in HC-018 because they make Capture Check
// feel alive (auto-detects a new file) without changing what CyanGem2
// claims to do.
// =============================================================================

private const val TAG = "MediaWatcher"

/**
 * Register a ContentObserver on the phone's image and video collections for
 * the duration of this composition. [onChange] fires on every MediaStore
 * mutation. Toggle [enabled] to pause/resume without restructuring the UI.
 */
@Composable
fun rememberMediaObserver(
    enabled: Boolean = true,
    onChange: () -> Unit
) {
    val context = LocalContext.current
    DisposableEffect(enabled) {
        if (!enabled) {
            onDispose { /* no-op */ }
            return@DisposableEffect onDispose { }
        }
        val handler = Handler(Looper.getMainLooper())
        val observer = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                Log.d(TAG, "MediaStore change: $uri")
                onChange()
            }
        }
        val resolver = context.contentResolver
        try {
            resolver.registerContentObserver(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                true,
                observer
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register Images observer: ${e.message}")
        }
        try {
            resolver.registerContentObserver(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                true,
                observer
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register Video observer: ${e.message}")
        }

        onDispose {
            try {
                resolver.unregisterContentObserver(observer)
            } catch (e: Exception) {
                Log.w(TAG, "Unregister failed: ${e.message}")
            }
        }
    }
}

/**
 * Run [onResume] every time the host activity is resumed. Useful for
 * re-querying MediaStore when the user returns from the HeyCyan app
 * (which transferred a photo while CyanGem2 was in the background).
 */
@Composable
fun OnLifecycleResume(onResume: () -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                onResume()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
}
