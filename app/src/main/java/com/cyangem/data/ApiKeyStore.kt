package com.cyangem.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Stores the Gemini API key securely using Android Keystore-backed encryption.
 * Falls back to plain SharedPreferences if Keystore is unavailable (first boot,
 * corrupted state, or some Samsung devices with locked keystore).
 */
class ApiKeyStore(context: Context) {

    private val prefs: SharedPreferences = createPrefs(context)

    private fun createPrefs(context: Context): SharedPreferences {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                "cyangem_secure_prefs",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.e("CyanGem", "EncryptedSharedPreferences failed, using plain prefs: ${e.message}")
            // Fallback — app still launches; key is less secure but app won't crash
            context.getSharedPreferences("cyangem_prefs_fallback", Context.MODE_PRIVATE)
        }
    }

    fun setApiKey(key: String) {
        prefs.edit().putString(KEY_GEMINI_API, key.trim()).apply()
    }

    fun getApiKey(): String? {
        return runCatching { prefs.getString(KEY_GEMINI_API, null)?.ifBlank { null } }
            .getOrNull()
    }

    fun hasApiKey(): Boolean = getApiKey() != null

    fun clearApiKey() {
        prefs.edit().remove(KEY_GEMINI_API).apply()
    }

    // ── OpenRouter ────────────────────────────────────────────────────────────

    fun setOpenRouterKey(key: String) {
        prefs.edit().putString(KEY_OPENROUTER_API, key.trim()).apply()
    }

    fun getOpenRouterKey(): String? {
        return runCatching { prefs.getString(KEY_OPENROUTER_API, null)?.ifBlank { null } }
            .getOrNull()
    }

    fun hasOpenRouterKey(): Boolean = getOpenRouterKey() != null

    fun clearOpenRouterKey() {
        prefs.edit().remove(KEY_OPENROUTER_API).apply()
    }

    // ── Provider preference ───────────────────────────────────────────────────

    /** "openrouter" or "gemini" — defaults to openrouter if a key is saved, else gemini */
    fun setProvider(provider: String) {
        prefs.edit().putString(KEY_PROVIDER, provider).apply()
    }

    fun getProvider(): String {
        return runCatching { prefs.getString(KEY_PROVIDER, null) }.getOrNull()
            ?: if (hasOpenRouterKey()) PROVIDER_OPENROUTER else PROVIDER_GEMINI
    }

    companion object {
        private const val KEY_GEMINI_API      = "gemini_api_key"
        private const val KEY_OPENROUTER_API  = "openrouter_api_key"
        private const val KEY_PROVIDER        = "ai_provider"
        const val PROVIDER_GEMINI             = "gemini"
        const val PROVIDER_OPENROUTER         = "openrouter"
    }
}
