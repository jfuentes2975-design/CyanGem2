package com.cyangem.gemini

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

/**
 * AI engine backed by OpenRouter — free tier, vision-capable.
 *
 * Uses a fallback model list — if the primary model has no available endpoint,
 * automatically retries with the next model in the list.
 * This fixes the "No Endpoint found" error seen when a free model is temporarily
 * unavailable due to provider load.
 */
class OpenRouterEngine(
    private val apiKey: String,
    private val systemPrompt: String = DEFAULT_SYSTEM_PROMPT
) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val history = mutableListOf<JSONObject>()

    init {
        if (systemPrompt.isNotBlank()) {
            history.add(JSONObject().put("role", "system").put("content", systemPrompt))
        }
    }

    // ── Text chat ─────────────────────────────────────────────────────────────

    suspend fun sendMessage(userText: String): GeminiResult = withContext(Dispatchers.IO) {
        history.add(JSONObject().put("role", "user").put("content", userText))
        return@withContext try {
            val responseText = callApiWithFallback(buildMessages())
            history.add(JSONObject().put("role", "assistant").put("content", responseText))
            GeminiResult.Success(responseText)
        } catch (e: Exception) {
            history.removeLastOrNull()
            GeminiResult.Error(e.message ?: "OpenRouter error")
        }
    }

    fun sendMessageStream(userText: String): Flow<GeminiResult> = flow {
        when (val result = sendMessage(userText)) {
            is GeminiResult.Success -> {
                val words = result.text.split(" ")
                var first = true
                for (word in words) {
                    val chunk = if (first) { first = false; word } else " $word"
                    emit(GeminiResult.Streaming(chunk))
                    kotlinx.coroutines.delay(20)
                }
            }
            is GeminiResult.Error -> emit(result)
            else -> {}
        }
    }

    // ── Vision ────────────────────────────────────────────────────────────────

    suspend fun analyzeImage(bitmap: Bitmap, prompt: String = DEFAULT_IMAGE_PROMPT): GeminiResult =
        withContext(Dispatchers.IO) {
            return@withContext try {
                val base64 = bitmapToBase64(bitmap)
                val contentArray = JSONArray()
                    .put(JSONObject().put("type", "text").put("text", prompt))
                    .put(
                        JSONObject()
                            .put("type", "image_url")
                            .put("image_url", JSONObject().put("url", "data:image/jpeg;base64,$base64"))
                    )
                val messages = JSONArray()
                if (systemPrompt.isNotBlank()) {
                    messages.put(JSONObject().put("role", "system").put("content", systemPrompt))
                }
                messages.put(JSONObject().put("role", "user").put("content", contentArray))
                // Vision requires a vision-capable model — use vision fallback list
                val responseText = callApiWithFallback(messages, visionOnly = true)
                GeminiResult.Success(responseText)
            } catch (e: Exception) {
                GeminiResult.Error(e.message ?: "Vision error")
            }
        }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun buildMessages(): JSONArray {
        val arr = JSONArray()
        history.forEach { arr.put(it) }
        return arr
    }

    /**
     * Try each model in the fallback list until one succeeds.
     * Stops on first successful response. Only retries on "no endpoint" type errors.
     */
    private fun callApiWithFallback(messages: JSONArray, visionOnly: Boolean = false): String {
        val models = if (visionOnly) VISION_MODEL_FALLBACKS else TEXT_MODEL_FALLBACKS
        var lastError = "All models unavailable"
        for (model in models) {
            try {
                Log.d("CyanGem_AI", "Trying model: $model")
                val result = callApi(messages, model)
                Log.d("CyanGem_AI", "Success with model: $model")
                return result
            } catch (e: Exception) {
                val msg = e.message ?: ""
                Log.w("CyanGem_AI", "Model $model failed: $msg")
                lastError = msg
                // Only continue to next model for endpoint/availability errors
                // For auth errors, stop immediately
                if (msg.contains("401") || msg.contains("403") || msg.contains("Invalid API key")) {
                    throw Exception("API key invalid — check your OpenRouter key in Settings")
                }
                // Continue to next model for: no endpoint, 503, timeout, model not found
            }
        }
        throw Exception(lastError)
    }

    private fun callApi(messages: JSONArray, modelName: String): String {
        val body = JSONObject()
            .put("model", modelName)
            .put("messages", messages)
            .put("max_tokens", 1024)

        val request = Request.Builder()
            .url("https://openrouter.ai/api/v1/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .addHeader("HTTP-Referer", "https://cyangem.app")
            .addHeader("X-Title", "CyanGem")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val response = client.newCall(request).execute()
        val responseBody = response.body?.string()
            ?: throw Exception("Empty response from OpenRouter")

        // Parse the JSON first regardless of HTTP status
        // OpenRouter often returns HTTP 200 with {"error": {...}} for model issues
        val json = runCatching { JSONObject(responseBody) }.getOrNull()

        // Check for error field in body first — covers HTTP 200 + error body pattern
        json?.optJSONObject("error")?.let { err ->
            val msg = err.optString("message", "").ifBlank {
                err.optString("code", "Unknown error")
            }
            throw Exception(msg)
        }

        // Then check HTTP status
        if (!response.isSuccessful) {
            throw Exception("HTTP ${response.code}")
        }

        // Parse successful response
        return json
            ?.optJSONArray("choices")
            ?.optJSONObject(0)
            ?.optJSONObject("message")
            ?.optString("content")
            ?.takeIf { it.isNotBlank() }
            ?: throw Exception("Empty or malformed response from $modelName")
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val stream = ByteArrayOutputStream()
        val scaled = if (bitmap.width > 1024 || bitmap.height > 1024) {
            val scale = 1024f / maxOf(bitmap.width, bitmap.height)
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt(),
                (bitmap.height * scale).toInt(),
                true
            )
        } else bitmap
        scaled.compress(Bitmap.CompressFormat.JPEG, 80, stream)
        return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
    }

    companion object {
        // Vision-capable free models, tried in order until one responds
        val VISION_MODEL_FALLBACKS = listOf(
            "meta-llama/llama-3.2-11b-vision-instruct:free",
            "google/gemma-3-12b-it:free",
            "google/gemma-3-4b-it:free",
            "moonshotai/kimi-vl-a3b-thinking:free"
        )

        // Text-only free models for chat (faster, more reliable)
        val TEXT_MODEL_FALLBACKS = listOf(
            "meta-llama/llama-3.3-70b-instruct:free",
            "meta-llama/llama-3.2-11b-vision-instruct:free",
            "google/gemma-3-12b-it:free",
            "google/gemma-3-4b-it:free"
        )

        // Keep for backwards compat references
        const val MODEL_NAME = "meta-llama/llama-3.3-70b-instruct:free"

        const val DEFAULT_SYSTEM_PROMPT = """You are CyanGem, an AI assistant connected to smart glasses.
You receive text queries and visual context from what the user sees through their glasses.
Be concise, helpful, and context-aware. When analyzing images, describe what's important first.
Keep voice-friendly responses short unless the user asks for detail."""
        const val DEFAULT_IMAGE_PROMPT = "What do you see? Give a brief, useful description."

        fun createForGem(apiKey: String, gem: Gem): OpenRouterEngine {
            return OpenRouterEngine(apiKey, gem.systemPrompt)
        }
    }
}
