package com.cyangem.gemini

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.BlockThreshold
import com.google.ai.client.generativeai.type.HarmCategory
import com.google.ai.client.generativeai.type.SafetySetting
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

data class ChatMessage(
    val role: String,   // "user" or "model"
    val text: String,
    val imageUri: String? = null,
    val timestampMs: Long = System.currentTimeMillis()
)

sealed class GeminiResult {
    data class Success(val text: String) : GeminiResult()
    data class Error(val message: String) : GeminiResult()
    data class Streaming(val chunk: String) : GeminiResult()
}

class GeminiEngine(apiKey: String, systemPrompt: String = DEFAULT_SYSTEM_PROMPT) {

    private val model = GenerativeModel(
        modelName = MODEL_NAME,
        apiKey = apiKey,
        generationConfig = generationConfig {
            temperature = 0.7f
            topK = 40
            topP = 0.95f
            maxOutputTokens = 2048
        },
        safetySettings = listOf(
            SafetySetting(HarmCategory.HARASSMENT, BlockThreshold.MEDIUM_AND_ABOVE),
            SafetySetting(HarmCategory.HATE_SPEECH, BlockThreshold.MEDIUM_AND_ABOVE),
        ),
        systemInstruction = if (systemPrompt.isNotBlank()) content { text(systemPrompt) } else null
    )

    private val chat = model.startChat(history = emptyList())

    // ── Text chat ─────────────────────────────────────────────────────────────
    suspend fun sendMessage(userText: String): GeminiResult {
        return try {
            val response = chat.sendMessage(userText)
            GeminiResult.Success(response.text ?: "")
        } catch (e: Exception) {
            GeminiResult.Error(e.message ?: "Unknown error")
        }
    }

    /** Streaming version — emits chunks as they arrive */
    fun sendMessageStream(userText: String): Flow<GeminiResult> = flow {
        try {
            chat.sendMessageStream(userText).collect { chunk ->
                emit(GeminiResult.Streaming(chunk.text ?: ""))
            }
        } catch (e: Exception) {
            emit(GeminiResult.Error(e.message ?: "Unknown error"))
        }
    }

    // ── Vision — analyze image from glasses ───────────────────────────────────
    suspend fun analyzeImage(imageBytes: ByteArray, prompt: String = DEFAULT_IMAGE_PROMPT): GeminiResult {
        return try {
            val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                ?: return GeminiResult.Error("Could not decode image")
            val response = model.generateContent(
                content {
                    image(bitmap)
                    text(prompt)
                }
            )
            GeminiResult.Success(response.text ?: "")
        } catch (e: Exception) {
            GeminiResult.Error(e.message ?: "Vision error")
        }
    }

    suspend fun analyzeImage(bitmap: Bitmap, prompt: String = DEFAULT_IMAGE_PROMPT): GeminiResult {
        return try {
            val response = model.generateContent(
                content {
                    image(bitmap)
                    text(prompt)
                }
            )
            GeminiResult.Success(response.text ?: "")
        } catch (e: Exception) {
            GeminiResult.Error(e.message ?: "Vision error")
        }
    }

    // ── One-shot (no history) — used for Gems with fresh context ─────────────
    companion object {
        const val MODEL_NAME = "gemini-2.0-flash"
        const val DEFAULT_SYSTEM_PROMPT = """You are CyanGem, an AI assistant connected to smart glasses.
You receive text queries and visual context from what the user sees through their glasses.
Be concise, helpful, and context-aware. When analyzing images, describe what's important first.
Keep voice-friendly responses short unless the user asks for detail."""

        const val DEFAULT_IMAGE_PROMPT = "What do you see? Give a brief, useful description."

        /**
         * Create a one-shot model with a custom Gem (system prompt).
         * No chat history — each call is independent.
         */
        fun createForGem(apiKey: String, gem: Gem): GeminiEngine {
            return GeminiEngine(apiKey, gem.systemPrompt)
        }
    }
}
