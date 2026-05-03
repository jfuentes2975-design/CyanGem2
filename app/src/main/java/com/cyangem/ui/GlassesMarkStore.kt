package com.cyangem.ui

import android.content.Context
import android.util.Log

// =============================================================================
// HC-019 — Persistent set of MediaStore URIs that the user manually flagged
// as "this is a smart-glasses capture".
//
// Distinct from ReviewStore (Mark for Review): ReviewStore is a generic
// "look at this later" mark; GlassesMarkStore is a typed assertion that an
// item came from the glasses. The Gallery Glasses filter consults this
// store as one of four signals in GlassesMediaIdentifier.
//
// Backed by SharedPreferences as a Set<String>. No new dependencies.
// Survives app restart.
// =============================================================================

object GlassesMarkStore {

    private const val TAG = "GlassesMarkStore"
    private const val PREFS = "cyangem_glasses_marks"
    private const val KEY = "glasses_uris"

    /** Read the current set. Empty on any failure. */
    fun read(context: Context): Set<String> {
        return try {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getStringSet(KEY, emptySet())?.toSet() ?: emptySet()
        } catch (e: Exception) {
            Log.w(TAG, "read failed: ${e.message}")
            emptySet()
        }
    }

    /** Replace the entire set. */
    fun write(context: Context, marks: Set<String>) {
        try {
            // Defensive: write a fresh copy so SharedPreferences picks up the change.
            val copy = HashSet(marks)
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putStringSet(KEY, copy).apply()
        } catch (e: Exception) {
            Log.w(TAG, "write failed: ${e.message}")
        }
    }

    /** Toggle a single URI. Returns the new set. */
    fun toggle(context: Context, uri: String): Set<String> {
        val current = read(context)
        val next = if (current.contains(uri)) current - uri else current + uri
        write(context, next)
        return next
    }

    fun isMarked(context: Context, uri: String): Boolean {
        if (uri.isEmpty()) return false
        return read(context).contains(uri)
    }

    /** Add (idempotent). */
    fun add(context: Context, uri: String) {
        if (uri.isEmpty()) return
        val current = read(context)
        if (current.contains(uri)) return
        write(context, current + uri)
    }

    @Suppress("unused")
    fun clear(context: Context) {
        try {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().remove(KEY).apply()
        } catch (e: Exception) {
            Log.w(TAG, "clear failed: ${e.message}")
        }
    }
}
