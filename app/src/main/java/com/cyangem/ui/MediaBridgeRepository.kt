package com.cyangem.ui

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// =============================================================================
// HC-019 — MediaStore + Photo Picker repository.
//
// Difference vs HC-018: now queries `MediaStore.MediaColumns.RELATIVE_PATH`
// so smart-glasses media can be identified by folder (e.g., DCIM/CyanGem,
// Pictures/HeyCyan). The path comes back populated on every CyanMediaItem
// produced by queryRecentMedia(). Picker items also fill the path when the
// underlying URI exposes it.
//
// Permission model unchanged from HC-018:
//   - Android 13+ (API 33+): READ_MEDIA_IMAGES + READ_MEDIA_VIDEO
//   - Older Android: READ_EXTERNAL_STORAGE (manifest declares with maxSdkVersion=32)
//
// Photo Picker still does NOT require these permissions — system grants
// per-URI access for picked items.
//
// What this is NOT:
//   - A glasses-camera driver. Read-only.
//   - A BLE photo puller. Read-only.
//   - A Wi-Fi sync agent. Read-only.
// =============================================================================

object MediaBridgeRepository {

    private const val TAG = "MediaBridgeRepo"

    // ---- Permissions --------------------------------------------------------

    fun hasMediaPermission(context: Context): Boolean {
        val perms = requiredMediaPermissions()
        return perms.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun requiredMediaPermissions(): Array<String> {
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

    // ---- MediaStore query ---------------------------------------------------

    suspend fun queryRecentMedia(context: Context, limit: Int = 30): CyanMediaResult {
        if (!hasMediaPermission(context)) return CyanMediaResult.PermissionMissing

        return withContext(Dispatchers.IO) {
            try {
                val combined = mutableListOf<CyanMediaItem>()
                combined.addAll(
                    queryCollection(
                        context = context,
                        collectionUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        type = CyanMediaType.Image,
                        limit = limit
                    )
                )
                combined.addAll(
                    queryCollection(
                        context = context,
                        collectionUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        type = CyanMediaType.Video,
                        limit = limit
                    )
                )
                val sorted = combined.sortedByDescending { it.dateAddedSeconds }.take(limit)
                if (sorted.isEmpty()) CyanMediaResult.Empty else CyanMediaResult.Items(sorted)
            } catch (e: SecurityException) {
                Log.w(TAG, "SecurityException during query: ${e.message}")
                CyanMediaResult.PermissionMissing
            } catch (e: Exception) {
                Log.w(TAG, "MediaStore query failed: ${e.message}", e)
                CyanMediaResult.Error(e.message ?: "MediaStore query failed")
            }
        }
    }

    private fun queryCollection(
        context: Context,
        collectionUri: Uri,
        type: CyanMediaType,
        limit: Int
    ): List<CyanMediaItem> {
        val results = mutableListOf<CyanMediaItem>()
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.RELATIVE_PATH
        )
        val sortOrder = "${MediaStore.MediaColumns.DATE_ADDED} DESC"

        context.contentResolver.query(collectionUri, projection, null, null, sortOrder)
            ?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
                val pathIdx = cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
                while (cursor.moveToNext() && results.size < limit) {
                    val id = cursor.getLong(idCol)
                    results.add(
                        CyanMediaItem(
                            id = id,
                            uri = Uri.withAppendedPath(collectionUri, id.toString()),
                            displayName = cursor.getString(nameCol),
                            mimeType = cursor.getString(mimeCol),
                            dateAddedSeconds = cursor.getLong(dateCol),
                            type = type,
                            pickedByUser = false,
                            relativePath = if (pathIdx >= 0) cursor.getString(pathIdx) else null
                        )
                    )
                }
            }
        return results
    }

    // ---- Photo Picker URI metadata -----------------------------------------

    suspend fun resolvePickerItem(context: Context, uri: Uri): CyanMediaItem? {
        return withContext(Dispatchers.IO) {
            try {
                val cr: ContentResolver = context.contentResolver
                val projection = arrayOf(
                    MediaStore.MediaColumns._ID,
                    MediaStore.MediaColumns.DISPLAY_NAME,
                    MediaStore.MediaColumns.MIME_TYPE,
                    MediaStore.MediaColumns.DATE_ADDED,
                    MediaStore.MediaColumns.RELATIVE_PATH
                )
                cr.query(uri, projection, null, null, null)?.use { cursor ->
                    if (!cursor.moveToFirst()) return@use null
                    val idIx = cursor.getColumnIndex(MediaStore.MediaColumns._ID)
                    val nameIx = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                    val mimeIx = cursor.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
                    val dateIx = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_ADDED)
                    val pathIx = cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
                    val mime = if (mimeIx >= 0) cursor.getString(mimeIx) else cr.getType(uri)
                    val type =
                        if (mime?.startsWith("video/") == true) CyanMediaType.Video
                        else CyanMediaType.Image
                    CyanMediaItem(
                        id = if (idIx >= 0) cursor.getLong(idIx) else 0L,
                        uri = uri,
                        displayName = if (nameIx >= 0) cursor.getString(nameIx) else null,
                        mimeType = mime,
                        dateAddedSeconds = if (dateIx >= 0) cursor.getLong(dateIx) else 0L,
                        type = type,
                        pickedByUser = true,
                        relativePath = if (pathIx >= 0) cursor.getString(pathIx) else null
                    )
                }
            } catch (e: SecurityException) {
                Log.w(TAG, "Picker URI access lost: ${e.message}")
                null
            } catch (e: Exception) {
                Log.w(TAG, "Picker URI metadata failed: ${e.message}")
                val mime = try { context.contentResolver.getType(uri) } catch (_: Exception) { null }
                CyanMediaItem(
                    id = 0L,
                    uri = uri,
                    displayName = null,
                    mimeType = mime,
                    dateAddedSeconds = 0L,
                    type = if (mime?.startsWith("video/") == true) CyanMediaType.Video else CyanMediaType.Image,
                    pickedByUser = true,
                    relativePath = null
                )
            }
        }
    }
}
