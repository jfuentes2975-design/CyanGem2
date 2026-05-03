package com.cyangem.ui

import android.content.Context

// =============================================================================
// HC-019 — Package detection helpers.
//
// Difference from HC-018: adds `com.glasssutdio.wear` to the HeyCyan
// candidate list per Juan's technical fix-forward document. Other
// candidates carried unchanged.
// =============================================================================

private const val SAMSUNG_GALLERY_PACKAGE = "com.sec.android.gallery3d"
private const val GOOGLE_PHOTOS_PACKAGE   = "com.google.android.apps.photos"

private val GEMINI_PACKAGE_CANDIDATES = listOf(
    "com.google.android.apps.bard",
    "com.google.android.apps.gemini",
    "com.google.android.googlequicksearchbox"
)

private val HEY_CYAN_PACKAGE_CANDIDATES = listOf(
    // HC-019 NEW — from Juan's technical fix-forward document.
    "com.glasssutdio.wear",
    // Carried from HC-013/HC-018:
    "com.heycyan.app",
    "com.heyx.heycyan",
    "io.heycyan.app",
    "com.oudmon.heycyan",
    "com.cyan.glasses"
)

object PackageDetector {

    fun isInstalled(context: Context, packageName: String): Boolean {
        return try {
            context.packageManager.getLaunchIntentForPackage(packageName) != null
        } catch (e: Exception) {
            false
        }
    }

    fun detectGemini(context: Context): String? =
        GEMINI_PACKAGE_CANDIDATES.firstOrNull { isInstalled(context, it) }

    fun isGeminiInstalled(context: Context): Boolean = detectGemini(context) != null

    fun detectHeyCyan(context: Context): String? =
        HEY_CYAN_PACKAGE_CANDIDATES.firstOrNull { isInstalled(context, it) }

    fun isHeyCyanInstalled(context: Context): Boolean = detectHeyCyan(context) != null

    fun isSamsungGalleryInstalled(context: Context): Boolean =
        isInstalled(context, SAMSUNG_GALLERY_PACKAGE)

    fun isGooglePhotosInstalled(context: Context): Boolean =
        isInstalled(context, GOOGLE_PHOTOS_PACKAGE)

    /** All HeyCyan candidate package names, in the order PackageDetector probes them. */
    fun heyCyanCandidates(): List<String> = HEY_CYAN_PACKAGE_CANDIDATES

    const val PKG_SAMSUNG_GALLERY = SAMSUNG_GALLERY_PACKAGE
    const val PKG_GOOGLE_PHOTOS   = GOOGLE_PHOTOS_PACKAGE
}
