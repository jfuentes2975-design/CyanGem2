package com.cyangem.ui

// =============================================================================
// HC-019 — CaptureSession model + state machine.
//
// Builds on CaptureCheck.kt (HC-018) but adds richer session metadata:
// session id, expected media type, capture source. Used by the Glasses tab
// guided flows (Photo Test / Video Test / HeyCyan Import) and recorded to
// CaptureHistoryStore on detection.
//
// CaptureCheck.kt stays in place for the generic Home Capture Check card.
// CaptureSession.kt is a higher-level wrapper that uses the same baseline
// math under the hood.
//
// State machine:
//
//                   +-----------+
//                   |   Idle    |  no session yet
//                   +-----+-----+
//                         |
//                         | start(...)
//                         v
//                  +---------------+
//                  |  Active       |  session running, watching MediaStore
//                  +-------+-------+
//                          |
//                          | evaluate(fresh) on each ContentObserver tick
//                          v
//                 ╔════════╧════════╗
//                 ║ NewPhoto        ║   matched expected: Photo
//                 ║ NewVideo        ║   matched expected: Video
//                 ║ WrongType       ║   new media but wrong type
//                 ║ NoNewMedia      ║   nothing new yet
//                 ║ PermissionLost  ║   permission revoked mid-session
//                 ║ ReadError(msg)  ║   MediaStore threw
//                 ╚═════════════════╝
//
// The "ImportDelayed" / "StayedInsideHeyCyan" cases mentioned in Juan's
// HC-019 spec are surfaced via the NoNewMedia body copy on the UI side —
// no separate state needed.
// =============================================================================

enum class ExpectedMediaType { Photo, Video, Unknown }

enum class CaptureSource {
    /** Press the physical A1 / front button on the glasses. */
    PhysicalButton,
    /** Use the HeyCyan native app to import / save to phone. */
    NativeHeyCyan,
    /** Generic / manual session (e.g., Capture Check on Home). */
    Manual
}

data class CaptureSession(
    val sessionId: String,
    val startSec: Long,
    val expectedType: ExpectedMediaType,
    val source: CaptureSource,
    val baseline: CaptureBaseline
)

sealed class CaptureSessionState {
    object Idle : CaptureSessionState()
    data class Active(val session: CaptureSession) : CaptureSessionState()
    data class NewPhoto(val session: CaptureSession, val item: CyanMediaItem) : CaptureSessionState()
    data class NewVideo(val session: CaptureSession, val item: CyanMediaItem) : CaptureSessionState()
    data class WrongType(val session: CaptureSession, val item: CyanMediaItem) : CaptureSessionState()
    data class NoNewMedia(val session: CaptureSession, val latest: CyanMediaItem?) : CaptureSessionState()
    object PermissionMissing : CaptureSessionState()
    data class ReadError(val message: String) : CaptureSessionState()
}

object CaptureSessions {

    /**
     * Build a fresh session. Records a baseline from the current MediaStore
     * snapshot so the evaluator can detect new items added afterward.
     */
    fun newSession(
        currentItems: List<CyanMediaItem>,
        expectedType: ExpectedMediaType,
        source: CaptureSource
    ): CaptureSession {
        val nowSec = System.currentTimeMillis() / 1000L
        val newest = currentItems.maxByOrNull { it.dateAddedSeconds }
        val baseline = CaptureBaseline(
            takenAtSec = nowSec,
            newestSeenIdAtBaseline = newest?.id,
            newestSeenAtBaselineSec = newest?.dateAddedSeconds ?: 0L
        )
        return CaptureSession(
            sessionId = generateSessionId(nowSec),
            startSec = nowSec,
            expectedType = expectedType,
            source = source,
            baseline = baseline
        )
    }

    /**
     * Evaluate a fresh MediaStore query against an active session.
     * Mirrors CaptureCheck.evaluate but adds expected-type matching and
     * returns a richer state.
     */
    fun evaluate(
        session: CaptureSession,
        fresh: CyanMediaResult
    ): CaptureSessionState {
        return when (fresh) {
            is CyanMediaResult.PermissionMissing -> CaptureSessionState.PermissionMissing
            is CyanMediaResult.Error -> CaptureSessionState.ReadError(fresh.message)
            is CyanMediaResult.Empty -> CaptureSessionState.NoNewMedia(session, latest = null)
            is CyanMediaResult.Items -> {
                val newest = fresh.list.first()
                val baseline = session.baseline
                val baselineHadNoMedia = baseline.newestSeenIdAtBaseline == null
                val isDifferentItem = baseline.newestSeenIdAtBaseline != null &&
                        newest.id != baseline.newestSeenIdAtBaseline
                val isNewer = newest.dateAddedSeconds > baseline.newestSeenAtBaselineSec
                val isNew = baselineHadNoMedia || isDifferentItem || isNewer

                if (!isNew) return CaptureSessionState.NoNewMedia(session, newest)

                // We have new media. Check type expectation.
                when (session.expectedType) {
                    ExpectedMediaType.Photo ->
                        if (newest.type == CyanMediaType.Image)
                            CaptureSessionState.NewPhoto(session, newest)
                        else
                            CaptureSessionState.WrongType(session, newest)
                    ExpectedMediaType.Video ->
                        if (newest.type == CyanMediaType.Video)
                            CaptureSessionState.NewVideo(session, newest)
                        else
                            CaptureSessionState.WrongType(session, newest)
                    ExpectedMediaType.Unknown ->
                        if (newest.type == CyanMediaType.Image)
                            CaptureSessionState.NewPhoto(session, newest)
                        else
                            CaptureSessionState.NewVideo(session, newest)
                }
            }
        }
    }

    private fun generateSessionId(seedSec: Long): String =
        "cs-" + seedSec.toString(16) + "-" + (System.nanoTime() and 0xFFFF).toString(16)
}
