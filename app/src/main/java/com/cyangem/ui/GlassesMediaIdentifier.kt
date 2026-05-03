package com.cyangem.ui

import android.content.Context

// =============================================================================
// HC-019 — Decide whether a CyanMediaItem is a smart-glasses (Hey Cyan)
// capture, using four sources of evidence:
//
//   1. RELATIVE_PATH match: item.relativePath contains a known glasses
//      folder fragment. Folder candidates were collected from Juan's
//      technical fix-forward document and prior session logs.
//
//   2. DISPLAY_NAME match: item.displayName contains a known glasses
//      file-name fragment. Less reliable than path; used as a fallback.
//
//   3. CaptureHistoryStore: the URI was previously detected by a
//      CaptureSession running on the Glasses tab or by the Capture Check
//      card on Home. This is the strongest signal — it means CyanGem2
//      WATCHED the file land via the Capture Session baseline + delta.
//
//   4. GlassesMarkStore: the user manually flagged the URI as a glasses
//      capture from the Gallery action row.
//
// Path / name matching are case-insensitive. Empty / null inputs return false.
//
// What this is NOT:
//   - A guarantee. A user could have a non-glasses photo in DCIM/CyanGem
//     by accident. It's a heuristic, not a security boundary.
//   - A read of the file contents. We don't decode pixels, EXIF, or
//     manufacturer tags. Just MediaStore metadata + our own marks.
// =============================================================================

object GlassesMediaIdentifier {

    // Folder fragments. Matched case-insensitive as a substring of the item's
    // relativePath. Order doesn't matter — any match wins.
    private val GLASSES_PATH_FRAGMENTS = listOf(
        // CyanGem app-driven path (legacy from the original BLE pipeline that
        // saved JPEGs to DCIM/CyanGem via MediaSyncManager).
        "DCIM/CyanGem",
        "Pictures/CyanGem",
        "Movies/CyanGem",

        // HeyCyan native app default save paths.
        "DCIM/HeyCyan",
        "Pictures/HeyCyan",
        "Movies/HeyCyan",
        "DCIM/Hey Cyan",
        "Pictures/Hey Cyan",

        // Glasssutdio / glasssutdio.wear app path candidate from Juan's
        // technical fix-forward document.
        "DCIM/glasssutdio",
        "Pictures/glasssutdio",
        "Movies/glasssutdio",
        "DCIM/GlassStudio",
        "Pictures/GlassStudio",

        // Generic vendor / model patterns observed in similar smart-glasses
        // workflows. False positives possible — see below.
        "DCIM/Oudmon",
        "Pictures/Oudmon",
        "DCIM/W630",
        "Pictures/W630",
        "DCIM/W610",
        "Pictures/W610"
    )

    // File-name fragments. Used as a fallback when RELATIVE_PATH is generic
    // (e.g., "DCIM/" without a sub-folder).
    private val GLASSES_NAME_FRAGMENTS = listOf(
        "HeyCyan",
        "CyanGem",
        "HEYCYAN",
        "CYANGEM",
        "glasssutdio",
        "Oudmon",
        "W630",
        "W610",
        "IMG_HC",
        "VID_HC"
    )

    /**
     * Returns true if [item] is likely a smart-glasses capture by ANY of the
     * four signals (path, name, capture-history, manual mark).
     */
    fun isLikelyGlassesMedia(context: Context, item: CyanMediaItem): Boolean {
        val uriStr = item.uri.toString()
        if (CaptureHistoryStore.containsUri(context, uriStr)) return true
        if (GlassesMarkStore.isMarked(context, uriStr)) return true
        if (matchesGlassesPath(item.relativePath)) return true
        if (matchesGlassesName(item.displayName)) return true
        return false
    }

    /** Path-only match. Useful when you don't have a Context (history/marks need one). */
    fun matchesGlassesPath(path: String?): Boolean {
        if (path.isNullOrBlank()) return false
        val lower = path.lowercase()
        return GLASSES_PATH_FRAGMENTS.any { frag -> lower.contains(frag.lowercase()) }
    }

    /** Name-only match. Substring check, case-insensitive. */
    fun matchesGlassesName(name: String?): Boolean {
        if (name.isNullOrBlank()) return false
        val lower = name.lowercase()
        return GLASSES_NAME_FRAGMENTS.any { frag -> lower.contains(frag.lowercase()) }
    }

    /**
     * Why an item was flagged. Useful for the detail UI in Gallery so the
     * user knows whether the identification came from a strong signal
     * (capture history) or a weaker one (path heuristic).
     */
    fun reason(context: Context, item: CyanMediaItem): String? {
        val uriStr = item.uri.toString()
        return when {
            CaptureHistoryStore.containsUri(context, uriStr) -> "Captured via Capture Session"
            GlassesMarkStore.isMarked(context, uriStr) -> "Manually marked as Glasses"
            matchesGlassesPath(item.relativePath) -> "Folder path matches: ${item.relativePath ?: "(unknown)"}"
            matchesGlassesName(item.displayName) -> "Filename pattern matches: ${item.displayName ?: "(unknown)"}"
            else -> null
        }
    }
}
