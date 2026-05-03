package com.cyangem.ui

// =============================================================================
// HC-018 — Capture Check state machine.
//
// Refactored from HC-016/HC-017 to use the new domain types
// (CyanMediaItem, CyanMediaResult). State branches and behavior unchanged.
//
// Capture Check sits on the Home screen in HC-018 (per the technical
// fix-forward document — moved from Glasses, where the Glasses tab now
// hosts the hardware control map instead).
//
// Workflow:
//   1. User opens Home, sees the Capture Check card.
//   2. Taps "Start Capture Check" → records a baseline of newest phone media.
//   3. Takes a photo with the glasses (via the native HeyCyan import path
//      — CyanGem2 is NOT involved in capture or pull).
//   4. When the file lands on the phone (HeyCyan saves it to MediaStore),
//      Capture Check detects it via re-query and transitions to
//      NewMediaDetected.
// =============================================================================

/**
 * Snapshot of MediaStore state at the moment the user pressed Start.
 *
 *   - takenAtSec: wall-clock seconds when the user pressed Start.
 *   - newestSeenIdAtBaseline: id of the newest item present at baseline,
 *     or null if the phone had no media.
 *   - newestSeenAtBaselineSec: DATE_ADDED of that item.
 */
data class CaptureBaseline(
    val takenAtSec: Long,
    val newestSeenIdAtBaseline: Long?,
    val newestSeenAtBaselineSec: Long
)

/**
 * UI-facing state. Each branch maps to a specific render in HomeScreen
 * with its own copy and button set.
 */
sealed class CaptureCheckState {
    object Idle : CaptureCheckState()
    data class BaselineSet(val baseline: CaptureBaseline) : CaptureCheckState()
    data class NewMediaDetected(
        val baseline: CaptureBaseline,
        val newest: CyanMediaItem
    ) : CaptureCheckState()
    data class NoNewMedia(
        val baseline: CaptureBaseline,
        val latest: CyanMediaItem?
    ) : CaptureCheckState()
    object PermissionMissing : CaptureCheckState()
    data class ReadError(val message: String) : CaptureCheckState()
}

object CaptureCheck {

    /**
     * Build a baseline from the current MediaStore state. Use the wall-clock
     * for [takenAtSec] (not the newest item's DATE_ADDED) so capture
     * timestamps that match the baseline second still register as new via
     * the id check.
     */
    fun recordBaseline(currentItems: List<CyanMediaItem>): CaptureBaseline {
        val nowSec = System.currentTimeMillis() / 1000L
        val newest = currentItems.maxByOrNull { it.dateAddedSeconds }
        return CaptureBaseline(
            takenAtSec = nowSec,
            newestSeenIdAtBaseline = newest?.id,
            newestSeenAtBaselineSec = newest?.dateAddedSeconds ?: 0L
        )
    }

    /**
     * Evaluate a fresh MediaStore query result against a stored baseline.
     */
    fun evaluate(baseline: CaptureBaseline, fresh: CyanMediaResult): CaptureCheckState {
        return when (fresh) {
            is CyanMediaResult.PermissionMissing -> CaptureCheckState.PermissionMissing
            is CyanMediaResult.Error -> CaptureCheckState.ReadError(fresh.message)
            is CyanMediaResult.Empty -> CaptureCheckState.NoNewMedia(baseline, latest = null)
            is CyanMediaResult.Items -> {
                val newest = fresh.list.first()
                val baselineHadNoMedia = baseline.newestSeenIdAtBaseline == null
                val isDifferentItem = baseline.newestSeenIdAtBaseline != null &&
                        newest.id != baseline.newestSeenIdAtBaseline
                val isNewerThanBaselineSnapshot =
                    newest.dateAddedSeconds > baseline.newestSeenAtBaselineSec

                val isNew = baselineHadNoMedia ||
                        isDifferentItem ||
                        isNewerThanBaselineSnapshot

                if (isNew) {
                    CaptureCheckState.NewMediaDetected(baseline, newest)
                } else {
                    CaptureCheckState.NoNewMedia(baseline, newest)
                }
            }
        }
    }
}
