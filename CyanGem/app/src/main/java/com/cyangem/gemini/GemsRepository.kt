package com.cyangem.gemini

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID

/**
 * A Gem is a named AI persona with a custom system prompt — mirrors Google Gemini Gems concept.
 * Gems are stored locally as JSON in SharedPreferences.
 */
data class Gem(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String,
    val systemPrompt: String,
    val emoji: String = "💎",
    val isDefault: Boolean = false
)

class GemsRepository(context: Context) {

    private val prefs = context.getSharedPreferences("cyangem_gems", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val key = "gems_list"

    fun getGems(): List<Gem> {
        val json = prefs.getString(key, null) ?: return defaultGems()
        return try {
            val type = object : TypeToken<List<Gem>>() {}.type
            gson.fromJson(json, type)
        } catch (e: Exception) {
            defaultGems()
        }
    }

    fun saveGem(gem: Gem) {
        val gems = getGems().toMutableList()
        val idx = gems.indexOfFirst { it.id == gem.id }
        if (idx >= 0) gems[idx] = gem else gems.add(gem)
        save(gems)
    }

    fun deleteGem(gemId: String) {
        val gems = getGems().filter { it.id != gemId && !it.isDefault }
        save(gems)
    }

    private fun save(gems: List<Gem>) {
        prefs.edit().putString(key, gson.toJson(gems)).apply()
    }

    fun resetToDefaults() {
        prefs.edit().remove(key).apply()
    }

    /** Built-in Gems — always available, cannot be deleted */
    private fun defaultGems(): List<Gem> = listOf(
        Gem(
            id = "default_assistant",
            name = "CyanGem Assistant",
            description = "General-purpose glasses assistant",
            systemPrompt = GeminiEngine.DEFAULT_SYSTEM_PROMPT,
            emoji = "💎",
            isDefault = true
        ),
        Gem(
            id = "visual_describer",
            name = "Visual Describer",
            description = "Describe everything you see in detail",
            systemPrompt = """You are a precise visual assistant connected to smart glasses.
When given an image, describe the scene in clear, structured detail:
1. Main subject/people
2. Environment and context
3. Any text visible
4. Notable objects or hazards
5. Overall mood/setting
Be thorough but organized.""",
            emoji = "👁️",
            isDefault = true
        ),
        Gem(
            id = "navigator",
            name = "Navigator",
            description = "Help with directions and spatial awareness",
            systemPrompt = """You are a navigation and spatial awareness assistant for smart glasses.
Help the user understand their environment, identify landmarks, read signs, and navigate.
Be concise and directional. Use compass directions and distances when helpful.
Prioritize safety-relevant information first.""",
            emoji = "🧭",
            isDefault = true
        ),
        Gem(
            id = "translator",
            name = "Translator",
            description = "Translate text and speech in real-time",
            systemPrompt = """You are a real-time translation assistant on smart glasses.
When given text or images with text, detect the language and translate to English (or the user's specified language).
Format: [Original Language] → [Translation]
For menus, signs, or documents, translate all visible text and preserve structure.""",
            emoji = "🌍",
            isDefault = true
        ),
        Gem(
            id = "research_assistant",
            name = "Research Assistant",
            description = "Identify objects and provide information",
            systemPrompt = """You are an encyclopedic research assistant on smart glasses.
When shown objects, artwork, plants, animals, landmarks, or products:
- Identify what it is with high confidence
- Provide key facts (3-5 bullet points)
- Add interesting or useful context
- Note if you're uncertain about identification
Keep responses scannable and useful.""",
            emoji = "🔬",
            isDefault = true
        ),
        Gem(
            id = "fitness_coach",
            name = "Fitness Coach",
            description = "Real-time exercise form and fitness guidance",
            systemPrompt = """You are a fitness and health coach assistant on smart glasses.
Analyze exercise form, identify workout equipment, suggest improvements, and provide encouragement.
When analyzing form: be specific about corrections (joint angles, posture, alignment).
Keep cues short and actionable — the user is mid-workout.
Always prioritize injury prevention.""",
            emoji = "💪",
            isDefault = true
        )
    )
}
