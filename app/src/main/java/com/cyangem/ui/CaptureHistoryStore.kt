package com.cyangem.ui

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

// =============================================================================
// HC-019 — Persistent capture history.
//
// Records every detected capture session so:
//   - the Gallery Glasses filter knows which URIs came from CaptureSession
//     workflows (strongest signal in GlassesMediaIdentifier).
//   - Juan can review past detections without redoing the test.
//   - history survives app restart.
//
// Storage: JSON array stringified into a single SharedPreferences entry.
// org.json is built into Android — no new dependency.
//
// Cap: 100 most recent entries. Older entries get trimmed on add().
// =============================================================================

data class CaptureHistoryEntry(
    val sessionId: String,
    val capturedAtSec: Long,
    val mediaUri: String,
    val mediaName: String?,
    val mediaType: String,        // "Image" or "Video"
    val source: String            // CaptureSource.name
)

object CaptureHistoryStore {

    private const val TAG = "CaptureHistory"
    private const val PREFS = "cyangem_capture_history"
    private const val KEY = "history_json"
    private const val MAX_ENTRIES = 100

    /** Read the full history (newest first). Returns empty list on failure. */
    fun read(context: Context): List<CaptureHistoryEntry> {
        return try {
            val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY, null) ?: return emptyList()
            val arr = JSONArray(raw)
            val out = mutableListOf<CaptureHistoryEntry>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                out.add(
                    CaptureHistoryEntry(
                        sessionId = obj.optString("sessionId", ""),
                        capturedAtSec = obj.optLong("capturedAtSec", 0L),
                        mediaUri = obj.optString("mediaUri", ""),
                        mediaName = obj.optString("mediaName", "").takeIf { it.isNotEmpty() },
                        mediaType = obj.optString("mediaType", "Image"),
                        source = obj.optString("source", "Manual")
                    )
                )
            }
            out
        } catch (e: Exception) {
            Log.w(TAG, "read failed: ${e.message}")
            emptyList()
        }
    }

    /** Add a new entry. Most recent first. Trims to MAX_ENTRIES. */
    fun add(context: Context, entry: CaptureHistoryEntry) {
        try {
            val current = read(context)
            // De-dup by URI: if this URI was already captured, drop the old
            // entry and re-add at the top with the latest metadata.
            val deduped = current.filterNot { it.mediaUri == entry.mediaUri }
            val next = (listOf(entry) + deduped).take(MAX_ENTRIES)
            write(context, next)
        } catch (e: Exception) {
            Log.w(TAG, "add failed: ${e.message}")
        }
    }

    /** Convenience: was this URI ever recorded by a successful CaptureSession? */
    fun containsUri(context: Context, uri: String): Boolean {
        if (uri.isEmpty()) return false
        return try {
            // Cheap path — read once and check. If the cap of 100 ever proves
            // expensive, swap to an in-memory cache populated on first read.
            read(context).any { it.mediaUri == uri }
        } catch (e: Exception) {
            false
        }
    }

    /** Clear everything. */
    @Suppress("unused")
    fun clear(context: Context) {
        try {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().remove(KEY).apply()
        } catch (e: Exception) {
            Log.w(TAG, "clear failed: ${e.message}")
        }
    }

    // ---- internal -----------------------------------------------------------

    private fun write(context: Context, entries: List<CaptureHistoryEntry>) {
        try {
            val arr = JSONArray()
            entries.forEach { e ->
                arr.put(
                    JSONObject().apply {
                        put("sessionId", e.sessionId)
                        put("capturedAtSec", e.capturedAtSec)
                        put("mediaUri", e.mediaUri)
                        put("mediaName", e.mediaName ?: "")
                        put("mediaType", e.mediaType)
                        put("source", e.source)
                    }
                )
            }
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY, arr.toString()).apply()
        } catch (e: Exception) {
            Log.w(TAG, "write failed: ${e.message}")
        }
    }
}
