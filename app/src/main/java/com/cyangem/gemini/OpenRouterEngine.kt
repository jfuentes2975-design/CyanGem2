package com.cyangem.gemini

import android.graphics.Bitmap
import android.util.Base64
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
 * Uses llama-3.2-11b-vision-instruct by default:
 *   - Free on OpenRouter (no billing required)
 *   - Supports image analysis (glasses photos)
 *   - OpenAI-compatible REST API — no new SDK needed, uses OkHttp already in project
 *
 * API docs: https://openrouter.ai/docs
 * Model: meta-llama/llama-3.2-11b-vision-instruct:free
 */
class OpenRouterEngine(
    private val apiKey: String,
    private val systemPrompt: String = DEFAULT_SYSTEM_PROMPT
) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    // Conversation history — list of {role, content} maps
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
            val responseText = callApi(buildMessages())
            history.add(JSONObject().put("role", "assistant").put("content", responseText))
            GeminiResult.Success(responseText)
        } catch (e: Exception) {
            history.removeLastOrNull() // remove the user message if call failed
            GeminiResult.Error(e.message ?: "OpenRouter error")
        }
    }

    /** Streaming — OpenRouter supports SSE but for simplicity we emit as single chunk */
    fun sendMessageStream(userText: String): Flow<GeminiResult> = flow {
        when (val result = sendMessage(userText)) {
            is GeminiResult.Success -> {
                // Emit word by word to simulate streaming feel
                val words = result.text.split(" ")
                val sb = StringBuilder()
                for (word in words) {
                    sb.append(if (sb.isEmpty()) word else " $word")
                    emit(GeminiResult.Streaming(if (sb.isEmpty()) word else " $word"))
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
                            .put(
                                "image_url",
                                JSONObject().put("url", "data:image/jpeg;base64,$base64")
                            )
                    )
                val messages = JSONArray()
                if (systemPrompt.isNotBlank()) {
                    messages.put(
                        JSONObject().put("role", "system").put("content", systemPrompt)
                    )
                }
                messages.put(JSONObject().put("role", "user").put("content", contentArray))
                val responseText = callApi(messages)
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

    private fun callApi(messages: JSONArray): String {
        val body = JSONObject()
            .put("model", MODEL_NAME)
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

        if (!response.isSuccessful) {
            val errorMsg = runCatching {
                JSONObject(responseBody).optJSONObject("error")?.optString("message")
            }.getOrNull() ?: "HTTP ${response.code}"
            throw Exception(errorMsg)
        }

        return JSONObject(responseBody)
            .getJSONArray("choices")
            .getJSONObject(0)
            .getJSONObject("message")
            .getString("content")
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val stream = ByteArrayOutputStream()
        // Scale down if too large — OpenRouter has a 5MB base64 limit
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
        const val MODEL_NAME = "meta-llama/llama-3.2-11b-vision-instruct:free"
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
