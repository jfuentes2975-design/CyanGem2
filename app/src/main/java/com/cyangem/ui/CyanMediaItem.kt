package com.cyangem.ui

import android.net.Uri

// =============================================================================
// HC-019 — Domain types for media items handled by CyanGem2.
//
// Adds `relativePath` to support smart-glasses media identification.
// HC-018 had a flat domain model; HC-019 needs the path so we can decide
// whether a MediaStore item is a Hey Cyan / smart-glasses capture (e.g.,
// it lives in `DCIM/CyanGem/`, `Pictures/HeyCyan/`, etc.).
//
// Identification logic itself lives in [GlassesMediaIdentifier]. CyanMediaItem
// stays a plain data class.
//
// Sources of media items the app handles:
//   - MediaStore query (broad list of recent images/videos on the phone)
//   - Android Photo Picker (single user-picked item)
//
// CyanGem2 is read-only with respect to media: it does NOT capture frames,
// does NOT control the glasses camera, does NOT pull media off the glasses
// over BLE/Wi-Fi. Those are spike-doc concerns. CyanGem2 reads what's
// already in MediaStore + what the user picks via the system picker.
// =============================================================================

enum class CyanMediaType { Image, Video }

data class CyanMediaItem(
    val id: Long,
    val uri: Uri,
    val displayName: String?,
    val mimeType: String?,
    /** Seconds since epoch (MediaStore convention). May be 0 for picker-only items. */
    val dateAddedSeconds: Long,
    val type: CyanMediaType,
    /** True if the item was selected via the system Photo Picker (limited
     *  metadata available); false if it came from a MediaStore query. */
    val pickedByUser: Boolean = false,
    /** RELATIVE_PATH from MediaStore (e.g., "DCIM/CyanGem/", "Pictures/HeyCyan/").
     *  Null for picker items or where the column was not available. */
    val relativePath: String? = null
)

/**
 * Result of a media query (MediaStore-backed). Photo-Picker results are
 * not wrapped in this — they come back as a single CyanMediaItem from the
 * launcher contract.
 */
sealed class CyanMediaResult {
    object PermissionMissing : CyanMediaResult()
    object Empty : CyanMediaResult()
    data class Items(val list: List<CyanMediaItem>) : CyanMediaResult()
    data class Error(val message: String) : CyanMediaResult()
}

/**
 * Format a `dateAddedSeconds` as a relative timestamp ("5 min ago", "2 h ago",
 * "Mar 12, 14:32"). Best-effort. If [dateAddedSeconds] is 0 (picker item with
 * no metadata), returns "—".
 */
fun formatCyanTimestamp(dateAddedSeconds: Long): String {
    if (dateAddedSeconds <= 0L) return "—"
    val nowMs = System.currentTimeMillis()
    val itemMs = dateAddedSeconds * 1000L
    val deltaMs = nowMs - itemMs
    val deltaMin = deltaMs / 60_000
    return when {
        deltaMin < 1 -> "Just now"
        deltaMin < 60 -> "$deltaMin min ago"
        deltaMin < 60 * 24 -> "${deltaMin / 60} h ago"
        deltaMin < 60 * 24 * 7 -> "${deltaMin / (60 * 24)} d ago"
        else -> {
            val fmt = java.text.SimpleDateFormat("MMM d, HH:mm", java.util.Locale.getDefault())
            fmt.format(java.util.Date(itemMs))
        }
    }
}
