package com.cyangem.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.ContextCompat

// =============================================================================
// HC-015 — MediaStore query helper.
//
// Powers the "Last Capture Visibility" card on Glasses and the recent media
// grid on Gallery. Read-only: queries images + videos, sorted newest first.
//
// Permission model:
//   - Android 13+ (Tiramisu / API 33+): READ_MEDIA_IMAGES + READ_MEDIA_VIDEO
//   - Android 12-: READ_EXTERNAL_STORAGE
//
// Returns a typed [MediaQueryResult] so the UI can render specific states
// (PermissionMissing / Empty / Items / Error) without try/catch sprinkled
// in composables.
// =============================================================================

private const val TAG = "CyanGem_MediaStore"

enum class MediaType { Image, Video }

data class RecentMedia(
    val uri: Uri,
    val displayName: String?,
    val mimeType: String?,
    /** Seconds since epoch (MediaStore convention). */
    val dateAddedSeconds: Long,
    val type: MediaType
)

sealed class MediaQueryResult {
    object PermissionMissing : MediaQueryResult()
    object Empty : MediaQueryResult()
    data class Items(val list: List<RecentMedia>) : MediaQueryResult()
    data class Error(val message: String) : MediaQueryResult()
}

object MediaStoreQuery {

    /** Returns true if the app has ALL required media permissions for the running OS version. */
    fun hasMediaPermission(context: Context): Boolean {
        val perms = requiredPermissions()
        return perms.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Returns the array of permission strings required to read images + videos
     * on the running OS version. Pass directly to a
     * [androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions]
     * launcher.
     */
    fun requiredPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO
            )
        } else {
            @Suppress("DEPRECATION")
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    /**
     * Query the most recent images + videos from MediaStore, merged and
     * sorted newest-first by `DATE_ADDED`. Returns up to [limit] items.
     *
     * Safe to call from any thread — but prefer Dispatchers.IO for cursor
     * iteration. For HC-015 we call from the main thread inside a
     * `LaunchedEffect`; the cursor iteration is bounded by [limit] so it
     * doesn't block long.
     */
    fun queryRecentMedia(context: Context, limit: Int = 30): MediaQueryResult {
        if (!hasMediaPermission(context)) return MediaQueryResult.PermissionMissing

        return try {
            val combined = mutableListOf<RecentMedia>()
            combined.addAll(
                queryCollection(
                    context = context,
                    collectionUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    type = MediaType.Image,
                    limit = limit
                )
            )
            combined.addAll(
                queryCollection(
                    context = context,
                    collectionUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    type = MediaType.Video,
                    limit = limit
                )
            )
            val sorted = combined.sortedByDescending { it.dateAddedSeconds }.take(limit)
            if (sorted.isEmpty()) MediaQueryResult.Empty else MediaQueryResult.Items(sorted)
        } catch (e: SecurityException) {
            // Race: permission could be revoked between hasMediaPermission and the query.
            Log.w(TAG, "SecurityException during query: ${e.message}")
            MediaQueryResult.PermissionMissing
        } catch (e: Exception) {
            Log.w(TAG, "MediaStore query failed: ${e.message}", e)
            MediaQueryResult.Error(e.message ?: "MediaStore query failed")
        }
    }

    private fun queryCollection(
        context: Context,
        collectionUri: Uri,
        type: MediaType,
        limit: Int
    ): List<RecentMedia> {
        val results = mutableListOf<RecentMedia>()
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.DATE_ADDED
        )
        val sortOrder = "${MediaStore.MediaColumns.DATE_ADDED} DESC"

        context.contentResolver.query(collectionUri, projection, null, null, sortOrder)
            ?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
                while (cursor.moveToNext() && results.size < limit) {
                    val id = cursor.getLong(idCol)
                    results.add(
                        RecentMedia(
                            uri = Uri.withAppendedPath(collectionUri, id.toString()),
                            displayName = cursor.getString(nameCol),
                            mimeType = cursor.getString(mimeCol),
                            dateAddedSeconds = cursor.getLong(dateCol),
                            type = type
                        )
                    )
                }
            }
        return results
    }
}

/**
 * Format a `dateAddedSeconds` (MediaStore epoch-seconds) as a relative timestamp
 * like "5 minutes ago" or "Today, 14:32" or "Yesterday, 09:15" or
 * "Mar 12, 14:32". Best-effort — locale follows the device.
 */
fun formatMediaTimestamp(dateAddedSeconds: Long): String {
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
