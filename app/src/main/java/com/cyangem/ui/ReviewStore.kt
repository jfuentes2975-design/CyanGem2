package com.cyangem.ui

import android.content.Context
import android.util.Log

// =============================================================================
// HC-018 — Persistent "Mark for Review" set. Salvaged from HC-017.
//
// Stored as a Set<String> of MediaStore content URIs in a small
// SharedPreferences entry. No new dependencies, no encryption (URIs are
// not secrets). Survives app restart.
//
// GalleryScreen's Mark-for-Review action reads / toggles via this object.
// =============================================================================

object ReviewStore {

    private const val TAG = "ReviewStore"
    private const val PREFS = "cyangem_review_marks"
    private const val KEY = "marked_uris"

    /** Read the current set. Returns an empty set on any failure. */
    fun read(context: Context): Set<String> {
        return try {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            prefs.getStringSet(KEY, emptySet())?.toSet() ?: emptySet()
        } catch (e: Exception) {
            Log.w(TAG, "read failed: ${e.message}")
            emptySet()
        }
    }

    /** Replace the entire set. */
    fun write(context: Context, marks: Set<String>) {
        try {
            // Defensive: write a fresh copy. SharedPreferences caches the Set
            // reference and ignores subsequent edits if the contents are the
            // same instance.
            val copy = HashSet(marks)
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putStringSet(KEY, copy)
                .apply()
        } catch (e: Exception) {
            Log.w(TAG, "write failed: ${e.message}")
        }
    }

    /** Toggle a single URI; returns the new set. */
    fun toggle(context: Context, uri: String): Set<String> {
        val current = read(context)
        val next: Set<String> = if (current.contains(uri)) {
            current - uri
        } else {
            current + uri
        }
        write(context, next)
        return next
    }

    /** Clear everything. */
    @Suppress("unused")
    fun clear(context: Context) {
        try {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY)
                .apply()
        } catch (e: Exception) {
            Log.w(TAG, "clear failed: ${e.message}")
        }
    }
}
