package com.cyangem.media

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import com.cyangem.ble.BleConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
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

    // ── BLE-based save (primary path for W610 glasses) ─────────────────────────

    /**
     * Save a JPEG byte array received via BLE to the device gallery.
     * Saves to DCIM/CyanGem — visible in the Photos app immediately.
     *
     * This is the correct save path for BLE-only glasses like the W610.
     * The Wi-Fi sync methods below are preserved but not the primary path.
     *
     * @param jpeg  Raw JPEG bytes received from glasses via BLE
     * @param name  Filename — defaults to a timestamp-based name
     * @return      Content URI of the saved image, or null on failure
     */
    fun saveJpegToGallery(
        jpeg: ByteArray,
        name: String = "cyangem_${System.currentTimeMillis()}.jpg"
    ): Uri? = saveImage(jpeg, name)

    // ── Wi-Fi / HTTP sync (preserved but not used for BLE-only glasses) ────────

    /**
     * Discover the glasses IP by testing candidates.
     * NOTE: This path only works if the glasses expose a Wi-Fi HTTP server.
     * The W610 uses BLE-only — use fetchLatestPhoto() via CyanBleManager instead.
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
     * Full sync via Wi-Fi HTTP.
     * NOTE: Not functional for W610. Use saveJpegToGallery() with BLE-received bytes.
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
     * Download latest photo via Wi-Fi for Gemini analysis.
     * NOTE: Not functional for W610. MainViewModel.analyzeLatestGlassesPhoto()
     * now uses the BLE path via CyanBleManager.fetchLatestPhoto() instead.
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

    private fun wrapOpusInOgg(opusBytes: ByteArray): ByteArray {
        val header = byteArrayOf(
            0x4F, 0x70, 0x75, 0x73, 0x48, 0x65, 0x61, 0x64.toByte(),
            0x01, 0x01, 0x38, 0x01, 0x80.toByte(), 0xBB.toByte(), 0x00, 0x00,
            0x00, 0x00, 0x00
        )
        return header + opusBytes
    }
}
