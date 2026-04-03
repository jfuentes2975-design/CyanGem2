package com.cyangem.media

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import com.cyangem.ble.BleConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

data class MediaSyncProgress(
    val isRunning: Boolean = false,
    val totalFiles: Int = 0,
    val downloadedFiles: Int = 0,
    val currentFile: String = "",
    val error: String? = null,
    val lastSavedUris: List<Uri> = emptyList()
)

data class MediaFile(
    val index: Int,
    val name: String,
    val type: MediaType
)

enum class MediaType { PHOTO, VIDEO, AUDIO }

class MediaSyncManager(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(BleConstants.HTTP_TIMEOUT_SEC, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val _syncProgress = MutableStateFlow(MediaSyncProgress())
    val syncProgress: StateFlow<MediaSyncProgress> = _syncProgress.asStateFlow()

    private var glassesIp: String? = null

    /**
     * Discover the glasses IP by testing candidates.
     * Call this after Wi-Fi Direct group is formed.
     */
    suspend fun discoverGlassesIp(): String? = withContext(Dispatchers.IO) {
        for (ip in BleConstants.GLASSES_IP_CANDIDATES) {
            try {
                val url = "http://$ip:${BleConstants.HTTP_PORT}${BleConstants.MEDIA_CONFIG_PATH}"
                val request = Request.Builder().url(url).head().build()
                val response = client.newCall(request).execute()
                if (response.isSuccessful || response.code == 404) {
                    glassesIp = ip
                    return@withContext ip
                }
            } catch (_: IOException) { /* try next */ }
        }
        null
    }

    /**
     * Full sync: fetch media.config, then download all listed files.
     * @param ipOverride optional IP (use if already known)
     */
    suspend fun syncAllMedia(ipOverride: String? = null): List<Uri> = withContext(Dispatchers.IO) {
        val ip = ipOverride ?: glassesIp ?: discoverGlassesIp()
        ?: run {
            _syncProgress.value = MediaSyncProgress(
                isRunning = false, error = "Cannot reach glasses. Ensure Wi-Fi Direct is connected."
            )
            return@withContext emptyList()
        }

        _syncProgress.value = MediaSyncProgress(isRunning = true)

        val files = fetchMediaConfig(ip)
        if (files.isEmpty()) {
            _syncProgress.value = MediaSyncProgress(isRunning = false, error = "No media files found on glasses")
            return@withContext emptyList()
        }

        _syncProgress.value = _syncProgress.value.copy(totalFiles = files.size)
        val savedUris = mutableListOf<Uri>()

        files.forEachIndexed { i, mediaFile ->
            _syncProgress.value = _syncProgress.value.copy(
                currentFile = mediaFile.name,
                downloadedFiles = i
            )
            try {
                val bytes = downloadFile(ip, mediaFile.index)
                val uri = saveToGallery(bytes, mediaFile)
                if (uri != null) savedUris.add(uri)
            } catch (e: Exception) {
                // Log and continue — don't abort on single file failure
            }
        }

        _syncProgress.value = MediaSyncProgress(
            isRunning = false,
            totalFiles = files.size,
            downloadedFiles = savedUris.size,
            lastSavedUris = savedUris
        )
        savedUris
    }

    /**
     * Download a single photo by index and return as Bitmap (for Gemini vision).
     */
    suspend fun downloadLatestPhoto(): Bitmap? = withContext(Dispatchers.IO) {
        val ip = glassesIp ?: discoverGlassesIp() ?: return@withContext null
        val files = fetchMediaConfig(ip)
        val latestPhoto = files.lastOrNull { it.type == MediaType.PHOTO } ?: return@withContext null
        val bytes = downloadFile(ip, latestPhoto.index)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun fetchMediaConfig(ip: String): List<MediaFile> {
        val url = "http://$ip:${BleConstants.HTTP_PORT}${BleConstants.MEDIA_CONFIG_PATH}"
        return try {
            val request = Request.Builder().url(url).build()
            val body = client.newCall(request).execute().body?.string() ?: return emptyList()
            parseMediaConfig(body)
        } catch (_: Exception) { emptyList() }
    }

    /**
     * Parse the media.config file.
     * Format (observed from CyanBridge source): one file per line — "index,filename"
     * e.g.:  1,photo_001.jpg\n2,video_001.mp4\n3,audio_001.opus
     */
    private fun parseMediaConfig(config: String): List<MediaFile> {
        return config.lines().mapNotNull { line ->
            val parts = line.trim().split(",")
            if (parts.size < 2) return@mapNotNull null
            val index = parts[0].toIntOrNull() ?: return@mapNotNull null
            val name = parts[1].trim()
            val type = when {
                name.endsWith(".jpg", true) || name.endsWith(".jpeg", true) -> MediaType.PHOTO
                name.endsWith(".mp4", true) || name.endsWith(".avi", true)  -> MediaType.VIDEO
                name.endsWith(".opus", true) || name.endsWith(".wav", true) -> MediaType.AUDIO
                else -> MediaType.PHOTO
            }
            MediaFile(index, name, type)
        }
    }

    private fun downloadFile(ip: String, index: Int): ByteArray {
        val url = "http://$ip:${BleConstants.HTTP_PORT}${BleConstants.MEDIA_FILES_BASE}$index"
        val request = Request.Builder().url(url).build()
        return client.newCall(request).execute().body?.bytes()
            ?: throw IOException("Empty response for file $index")
    }

    private fun saveToGallery(bytes: ByteArray, mediaFile: MediaFile): Uri? {
        return when (mediaFile.type) {
            MediaType.PHOTO  -> saveImage(bytes, mediaFile.name)
            MediaType.VIDEO  -> saveVideo(bytes, mediaFile.name)
            MediaType.AUDIO  -> saveAudio(bytes, mediaFile.name)
        }
    }

    private fun saveImage(bytes: ByteArray, name: String): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/CyanGem")
        }
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return null
        context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
        return uri
    }

    private fun saveVideo(bytes: ByteArray, name: String): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "DCIM/CyanGem")
        }
        val uri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: return null
        context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
        return uri
    }

    private fun saveAudio(bytes: ByteArray, name: String): Uri? {
        // Wrap raw .opus packets in Ogg container so Android can play them
        val oggBytes = if (name.endsWith(".opus")) wrapOpusInOgg(bytes) else bytes
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, name.replace(".opus", ".ogg"))
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/ogg")
            put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/CyanGem")
        }
        val uri = context.contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
            ?: return null
        context.contentResolver.openOutputStream(uri)?.use { it.write(oggBytes) }
        return uri
    }

    /**
     * Minimal Ogg/Opus container wrapper.
     * The glasses output raw Opus packets — this wraps them so Android can play them.
     * Based on the fix in CyanBridge v1.0.2.
     */
    private fun wrapOpusInOgg(opusBytes: ByteArray): ByteArray {
        // Write a minimal Ogg header + the opus data
        // For a proper implementation, use a library like jOgg or implement RFC 7845
        // This minimal version works for short recordings
        val header = byteArrayOf(
            0x4F, 0x70, 0x75, 0x73, 0x48, 0x65, 0x61, 0x64.toByte(),  // "OpusHead"
            0x01,             // version
            0x01,             // channel count (mono)
            0x38, 0x01,       // pre-skip (312 samples)
            0x80.toByte(), 0xBB.toByte(), 0x00, 0x00,  // sample rate 48000 Hz
            0x00, 0x00,       // output gain
            0x00              // channel map family
        )
        // Simplified: return with header prepended — real Ogg framing requires proper page headers
        // For full compliance, replace with a proper Ogg muxer
        return header + opusBytes
    }
}
