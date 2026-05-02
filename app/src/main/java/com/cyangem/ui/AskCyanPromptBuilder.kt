package com.cyangem.ui

// =============================================================================
// HC-011 — Ask Cyan in-app answer prompt builder.
//
// Used by MainViewModel.askCyanInApp to construct the OpenRouter engine with
// a CyanGem-flavored system prompt. The user's typed or voice-recognized
// question is passed unchanged as the user message; the system prompt
// contextualizes the response.
//
// Why a small dedicated file:
//   - Keeps prompt copy in one reviewable place.
//   - Allows future polish (e.g., per-Gem variants) without touching the VM.
//   - Avoids mixing system + user content in a single string.
// =============================================================================

internal object AskCyanPromptBuilder {

    /**
     * The Ask Cyan system prompt. Concise on purpose — overly long system
     * prompts waste tokens and rarely improve answer quality. The OpenRouter
     * engine forwards this as the OpenAI-compatible "system" role message.
     *
     * Edit guidance:
     *   - Stay short. ≤ 80 words.
     *   - Do not include private data (API keys, MAC addresses, secrets).
     *   - Voice mode and text mode share this prompt; do not branch by source.
     */
    const val ASK_CYAN_SYSTEM_PROMPT: String =
        "You are CyanGem, Juan's smart glasses companion. " +
        "Answer clearly and briefly unless the user asks for detail. " +
        "If the question relates to smart glasses, phone use, or app " +
        "troubleshooting, be practical and step-by-step."

    /**
     * Hook for future formatting (trim, light cleanup). Currently just
     * trims surrounding whitespace.
     */
    fun wrapUserPrompt(question: String): String = question.trim()
}
