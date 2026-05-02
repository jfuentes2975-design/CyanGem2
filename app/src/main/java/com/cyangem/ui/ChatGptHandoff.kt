package com.cyangem.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.cyangem.viewmodel.MainViewModel

// =============================================================================
// HC-007 — Shared ChatGPT Android app handoff helpers.
//
// Used by:
//   - SettingsScreen.ChatGptHandoffCard (test button under "AI Mode")
//   - ChatScreen (HC-007B; collects MainViewModel.chatGptHandoffRequest and
//     fires the intent for typed and voice-recognized prompts)
//
// No API calls. No stored credentials. No AccessibilityService. No Gemini /
// OpenRouter / OpenAI calls.
//
// Requires: AndroidManifest.xml <queries><package android:name="com.openai.chatgpt" />
// (shipped together in this same HC-007A package).
// =============================================================================

internal const val HC007_TEST_PROMPT =
    "Hello ChatGPT. This is a CyanGem2 handoff test from Juan's smart glasses app. " +
    "If you can see this, the app-to-ChatGPT handoff works."

internal const val HC007_CHATGPT_PACKAGE = "com.openai.chatgpt"

internal fun isChatGptInstalled(context: Context): Boolean = try {
    context.packageManager.getApplicationInfo(HC007_CHATGPT_PACKAGE, 0)
    true
} catch (e: PackageManager.NameNotFoundException) {
    false
}

internal fun copyToClipboard(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("CyanGem2 handoff", text))
}

/**
 * Hand off [prompt] to the installed ChatGPT Android app.
 *
 * Always copies [prompt] to the clipboard first as a safety net, even on
 * intent success. Then attempts in order:
 *   1. Direct ACTION_SEND targeted at com.openai.chatgpt.
 *   2. Android share sheet (chooser).
 *   3. Clipboard-only fallback (a snackbar tells the user).
 *
 * One short snackbar is emitted via [vm] to confirm what happened.
 */
internal fun handoffPromptToChatGpt(context: Context, prompt: String, vm: MainViewModel) {
    val trimmed = prompt.trim()
    if (trimmed.isEmpty()) return

    copyToClipboard(context, trimmed)

    val installed = isChatGptInstalled(context)
    if (!installed) {
        vm.showSnackbar("ChatGPT not found. Prompt copied.")
        return
    }

    val targeted = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, trimmed)
        setPackage(HC007_CHATGPT_PACKAGE)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(targeted)
        vm.showSnackbar("Opening ChatGPT — tap Send inside ChatGPT to deliver")
        return
    } catch (e: ActivityNotFoundException) {
        // fall through
    }

    val chooser = Intent.createChooser(
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, trimmed)
        },
        "Send to ChatGPT"
    ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
    try {
        context.startActivity(chooser)
        vm.showSnackbar("Opening share sheet — pick ChatGPT")
        return
    } catch (e: ActivityNotFoundException) {
        // fall through
    }

    vm.showSnackbar("Share failed. Prompt copied to clipboard.")
}

internal fun handoffTestPromptToChatGpt(context: Context, vm: MainViewModel) =
    handoffPromptToChatGpt(context, HC007_TEST_PROMPT, vm)
