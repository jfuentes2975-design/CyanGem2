package com.cyangem.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Stores the Gemini API key securely using Android Keystore-backed encryption.
 */
class ApiKeyStore(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "cyangem_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun setApiKey(key: String) {
        prefs.edit().putString(KEY_GEMINI_API, key.trim()).apply()
    }

    fun getApiKey(): String? {
        return prefs.getString(KEY_GEMINI_API, null)?.ifBlank { null }
    }

    fun hasApiKey(): Boolean = getApiKey() != null

    fun clearApiKey() {
        prefs.edit().remove(KEY_GEMINI_API).apply()
    }

    companion object {
        private const val KEY_GEMINI_API = "gemini_api_key"
    }
}
