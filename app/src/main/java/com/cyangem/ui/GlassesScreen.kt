package com.cyangem.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyangem.ui.theme.*
import kotlinx.coroutines.delay

// =============================================================================
// HC-019B -- Glasses tab.
//
// Fix-forward from HC-019. Same overall layout (3 guided capture flows above
// the hardware control map), but the GuidedCaptureCard is rewritten to
// never fail silently:
//
//   * "Check Again" button on the Active state (forces a parent re-query;
//     no longer relies only on MediaWatcher).
//   * Elapsed time counter while Active.
//   * Diagnostic block: baseline media, current newest media, baseline
//     timestamp, current newest timestamp, media permission state,
//     observer-watching state.
//   * After 25 seconds in Active without transition, an inline amber
//     warning surfaces inside the Active state telling the user to try
//     Check Again or Open HeyCyan.
//   * NoNewMedia state has expanded recovery: Open HeyCyan, Check Again,
//     Reset Session, plus an optional "Mark Latest Phone Photo as Glasses
//     Capture" manual recovery (writes to GlassesMarkStore).
//   * NoNewMedia state body lists the explicit 5-step HeyCyan import
//     recovery flow.
//
// Hardware control map and guided button test from HC-018/HC-019 carry
// unchanged below the new flows.
// =============================================================================

@Composable
fun GlassesScreen() {
    val context = LocalContext.current
    val btStatus by rememberBluetoothStatus(context)
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    // Shared MediaStore query -- every guided flow evaluates against this.
    var refreshTick by remember { mutableIntStateOf(0) }
    var queryResult by remember { mutableStateOf<CyanMediaResult?>(null) }
    val mediaPermissionGranted = remember(refreshTick) {
        MediaBridgeRepository.hasMediaPermission(context)
    }

    rememberMediaObserver(enabled = true) { refreshTick++ }
    OnLifecycleResume { refreshTick++ }

    LaunchedEffect(refreshTick) {
        queryResult = if (!mediaPermissionGranted)
            CyanMediaResult.PermissionMissing
        else
            MediaBridgeRepository.queryRecentMedia(context, limit = 30)
    }

    // Hardware test state (carry from HC-018)
    var a1Result by remember { mutableStateOf<TestResult>(TestResult.Untested) }
    var a2Result by remember { mutableStateOf<TestResult>(TestResult.Untested) }
    var touchpadResult by remember { mutableStateOf<TestResult>(TestResult.Untested) }
    var lightsResult by remember { mutableStateOf<TestResult>(TestResult.Untested) }

    // Force-refresh callback passed to each GuidedCaptureCard. Increments
    // refreshTick which fires the parent LaunchedEffect, re-queries
    // MediaStore, updates queryResult, which the cards observe to re-evaluate
    // their sessions. This is the manual escape hatch when ContentObserver
    // does not fire (e.g., HeyCyan held the file inside its own storage).
    val onForceRefresh: () -> Unit = { refreshTick++ }

    Box(modifier = Modifier.fillMaxSize().background(BackgroundLight)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp)
        ) {
            item { GlassesHeader() }
            item { GlassesStatusBlock(btStatus) }

            // ===== Guided capture flows =====
            item { GuidedFlowsHeader() }
            item {
                GuidedCaptureCard(
                    title = "Guided Photo Test",
                    icon = Icons.Default.PhotoCamera,
                    accent = CyanPrimary,
                    expectedType = ExpectedMediaType.Photo,
                    source = CaptureSource.PhysicalButton,
                    instructions = listOf(
                        "1. Tap Start Photo Capture below.",
                        "2. Press the A1 / front button on the glasses ONCE.",
                        "3. Wait -- when HeyCyan saves the photo to the phone, the card auto-detects.",
                        "4. If nothing detects, tap Check Again or Open HeyCyan."
                    ),
                    introBody = "Verifies that a single A1 press lands as a NEW image in phone media.",
                    queryResult = queryResult,
                    mediaPermissionGranted = mediaPermissionGranted,
                    observerEnabled = true,
                    onForceRefresh = onForceRefresh,
                    onOpenHeyCyan = {
                        val ok = openHeyCyanApp(context)
                        if (!ok) coroutineScope.launchSnackbar(snackbarHostState, "HeyCyan app not detected.")
                    },
                    onOpenInGallery = {
                        val ok = openSamsungGallery(context) ||
                                openGooglePhotos(context) ||
                                openSystemGallery(context)
                        if (!ok) coroutineScope.launchSnackbar(snackbarHostState, "No gallery app found.")
                    },
                    onShareItem = { item ->
                        val ok = shareMediaItem(context, item.uri, item.mimeType)
                        if (!ok) coroutineScope.launchSnackbar(snackbarHostState, "Could not share.")
                    },
                    onMarkLatestAsGlasses = { uri ->
                        GlassesMarkStore.add(context, uri)
                        coroutineScope.launchSnackbar(
                            snackbarHostState,
                            "Latest phone photo marked as Glasses Capture."
                        )
                    }
                )
            }
            item {
                GuidedCaptureCard(
                    title = "Guided Video Test",
                    icon = Icons.Default.Videocam,
                    accent = CyanSecondary,
                    expectedType = ExpectedMediaType.Video,
                    source = CaptureSource.PhysicalButton,
                    instructions = listOf(
                        "1. Tap Start Video Capture below.",
                        "2. Double-click A1 / front button to START recording.",
                        "3. Single-click A1 to STOP recording.",
                        "4. Wait for HeyCyan to finalize and import.",
                        "5. If nothing detects, tap Check Again or Open HeyCyan."
                    ),
                    introBody = "Verifies that a glasses video lands as a NEW video file in phone media.",
                    queryResult = queryResult,
                    mediaPermissionGranted = mediaPermissionGranted,
                    observerEnabled = true,
                    onForceRefresh = onForceRefresh,
                    onOpenHeyCyan = {
                        val ok = openHeyCyanApp(context)
                        if (!ok) coroutineScope.launchSnackbar(snackbarHostState, "HeyCyan app not detected.")
                    },
                    onOpenInGallery = {
                        val ok = openSamsungGallery(context) ||
                                openGooglePhotos(context) ||
                                openSystemGallery(context)
                        if (!ok) coroutineScope.launchSnackbar(snackbarHostState, "No gallery app found.")
                    },
                    onShareItem = { item ->
                        val ok = shareMediaItem(context, item.uri, item.mimeType)
                        if (!ok) coroutineScope.launchSnackbar(snackbarHostState, "Could not share.")
                    },
                    onMarkLatestAsGlasses = { uri ->
                        GlassesMarkStore.add(context, uri)
                        coroutineScope.launchSnackbar(
                            snackbarHostState,
                            "Latest phone video marked as Glasses Capture."
                        )
                    }
                )
            }
            item {
                GuidedCaptureCard(
                    title = "Guided HeyCyan Import",
                    icon = Icons.Default.OpenInNew,
                    accent = GeminiPurple,
                    expectedType = ExpectedMediaType.Unknown,
                    source = CaptureSource.NativeHeyCyan,
                    instructions = listOf(
                        "1. Tap Start Import Session below.",
                        "2. Tap Open HeyCyan.",
                        "3. In HeyCyan: find the album / media list and import / save the latest capture to the phone Gallery.",
                        "4. Return to CyanGem2.",
                        "5. Tap Check Again."
                    ),
                    introBody = "Verifies that the HeyCyan import / save-to-phone path is wired and working.",
                    queryResult = queryResult,
                    mediaPermissionGranted = mediaPermissionGranted,
                    observerEnabled = true,
                    onForceRefresh = onForceRefresh,
                    onOpenHeyCyan = {
                        val ok = openHeyCyanApp(context)
                        if (!ok) coroutineScope.launchSnackbar(snackbarHostState, "HeyCyan app not detected.")
                    },
                    onOpenInGallery = {
                        val ok = openSamsungGallery(context) ||
                                openGooglePhotos(context) ||
                                openSystemGallery(context)
                        if (!ok) coroutineScope.launchSnackbar(snackbarHostState, "No gallery app found.")
                    },
                    onShareItem = { item ->
                        val ok = shareMediaItem(context, item.uri, item.mimeType)
                        if (!ok) coroutineScope.launchSnackbar(snackbarHostState, "Could not share.")
                    },
                    onMarkLatestAsGlasses = { uri ->
                        GlassesMarkStore.add(context, uri)
                        coroutineScope.launchSnackbar(
                            snackbarHostState,
                            "Latest phone item marked as Glasses Capture."
                        )
                    }
                )
            }

            // ===== Hardware control map (carried from HC-018) =====
            item { ControlMapHeader() }
            item { ControlA1Card() }
            item { ControlA2Card() }
            item { ControlTouchpadCard() }
            item { ControlStatusLightsCard() }
            item {
                GuidedTestCard(
                    a1Result = a1Result,
                    a2Result = a2Result,
                    touchpadResult = touchpadResult,
                    lightsResult = lightsResult,
                    onSet = { which, result ->
                        when (which) {
                            "a1" -> a1Result = result
                            "a2" -> a2Result = result
                            "touchpad" -> touchpadResult = result
                            "lights" -> lightsResult = result
                        }
                    },
                    onResetAll = {
                        a1Result = TestResult.Untested
                        a2Result = TestResult.Untested
                        touchpadResult = TestResult.Untested
                        lightsResult = TestResult.Untested
                        coroutineScope.launchSnackbar(snackbarHostState, "Test results reset.")
                    }
                )
            }
            item {
                TroubleshootingCard(
                    onOpenHeyCyan = {
                        val ok = openHeyCyanApp(context)
                        if (!ok) coroutineScope.launchSnackbar(snackbarHostState, "HeyCyan app not detected.")
                    },
                    onOpenBluetoothSettings = { openBluetoothSettings(context) }
                )
            }
            item { AiButtonTruthCard() }
            item { NoCameraPreviewCard() }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

// =============================================================================
// Header
// =============================================================================

@Composable
private fun GlassesHeader() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            "Glasses",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = OnSurface
        )
        Text(
            "Guided capture flows + hardware control reference + button test + troubleshooting",
            fontSize = 12.sp, color = OnSurfaceMuted
        )
    }
}

@Composable
private fun GlassesStatusBlock(btStatus: BtAdapterStatus) {
    HomeCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier.size(40.dp).background(CyanPrimary.copy(alpha = 0.14f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Visibility, null, modifier = Modifier.size(22.dp), tint = CyanPrimary)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Hey Cyan Glasses", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = OnSurface)
                Text(
                    when (btStatus) {
                        BtAdapterStatus.On ->
                            "Bluetooth adapter on. Hey Cyan device connection not directly verified by CyanGem2."
                        BtAdapterStatus.Off ->
                            "Bluetooth is off -- turn it on in Settings."
                        BtAdapterStatus.Unsupported,
                        BtAdapterStatus.PermissionMissing ->
                            "Status unavailable -- check Bluetooth permission."
                    },
                    fontSize = 12.sp, color = OnSurfaceMuted
                )
            }
            val (label, color) = when (btStatus) {
                BtAdapterStatus.On -> "BT: On" to SuccessColor
                BtAdapterStatus.Off -> "BT: Off" to WarningAmber
                else -> "Unknown" to OnSurfaceMuted
            }
            StatusPill(label, color)
        }
    }
}

// =============================================================================
// Guided flows header
// =============================================================================

@Composable
private fun GuidedFlowsHeader() {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Text(
            "Guided Capture Flows",
            fontSize = 13.sp, fontWeight = FontWeight.Bold, color = OnSurface,
            letterSpacing = 0.5.sp
        )
        Text(
            "Each flow records a baseline, gives you a button instruction, and watches MediaStore for the result. If auto-detect doesn't fire, tap Check Again to force a re-query.",
            fontSize = 11.sp, color = OnSurfaceMuted,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

// =============================================================================
// GuidedCaptureCard -- shared composable for all three flows
// =============================================================================

@Composable
private fun GuidedCaptureCard(
    title: String,
    icon: ImageVector,
    accent: Color,
    expectedType: ExpectedMediaType,
    source: CaptureSource,
    instructions: List<String>,
    introBody: String,
    queryResult: CyanMediaResult?,
    mediaPermissionGranted: Boolean,
    observerEnabled: Boolean,
    onForceRefresh: () -> Unit,
    onOpenHeyCyan: () -> Unit,
    onOpenInGallery: () -> Unit,
    onShareItem: (CyanMediaItem) -> Unit,
    onMarkLatestAsGlasses: (uri: String) -> Unit
) {
    val context = LocalContext.current
    var sessionState by remember { mutableStateOf<CaptureSessionState>(CaptureSessionState.Idle) }

    // Re-evaluate against the latest query result whenever it changes.
    LaunchedEffect(queryResult) {
        if (queryResult == null) return@LaunchedEffect
        sessionState = when (val s = sessionState) {
            CaptureSessionState.Idle,
            CaptureSessionState.PermissionMissing,
            is CaptureSessionState.ReadError -> {
                if (queryResult is CyanMediaResult.PermissionMissing)
                    CaptureSessionState.PermissionMissing
                else if (queryResult is CyanMediaResult.Error)
                    CaptureSessionState.ReadError(queryResult.message)
                else
                    CaptureSessionState.Idle
            }
            is CaptureSessionState.Active -> CaptureSessions.evaluate(s.session, queryResult)
            is CaptureSessionState.NoNewMedia -> CaptureSessions.evaluate(s.session, queryResult)
            is CaptureSessionState.NewPhoto -> CaptureSessions.evaluate(s.session, queryResult)
            is CaptureSessionState.NewVideo -> CaptureSessions.evaluate(s.session, queryResult)
            is CaptureSessionState.WrongType -> CaptureSessions.evaluate(s.session, queryResult)
        }
    }

    // History write on success.
    LaunchedEffect(sessionState) {
        when (val s = sessionState) {
            is CaptureSessionState.NewPhoto -> recordHistory(context, s.session, s.item, "Image")
            is CaptureSessionState.NewVideo -> recordHistory(context, s.session, s.item, "Video")
            is CaptureSessionState.WrongType -> recordHistory(
                context, s.session, s.item,
                if (s.item.type == CyanMediaType.Image) "Image" else "Video"
            )
            else -> { /* no-op */ }
        }
    }

    // Elapsed-time tick while Active. Uses the current session's id as the
    // remember key so a fresh session resets the counter.
    val activeSessionId = (sessionState as? CaptureSessionState.Active)?.session?.sessionId
    var elapsedSec by remember(activeSessionId) { mutableLongStateOf(0L) }
    LaunchedEffect(activeSessionId) {
        if (activeSessionId == null) return@LaunchedEffect
        while (true) {
            elapsedSec = (System.currentTimeMillis() / 1000L) -
                    (sessionState as? CaptureSessionState.Active)?.session?.startSec.let {
                        it ?: System.currentTimeMillis() / 1000L
                    }
            delay(1000L)
        }
    }

    // Newest item currently visible in MediaStore (for diagnostic display).
    val newestNow: CyanMediaItem? = (queryResult as? CyanMediaResult.Items)?.list?.firstOrNull()

    HomeCard(borderColor = accent.copy(alpha = 0.40f)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, null, modifier = Modifier.size(18.dp), tint = accent)
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = OnSurface, modifier = Modifier.weight(1f))
            StatusPill(sessionShortLabel(sessionState), accent)
        }
        Spacer(Modifier.height(4.dp))
        Text(introBody, fontSize = 11.sp, color = OnSurfaceMuted)
        Spacer(Modifier.height(10.dp))

        when (val s = sessionState) {
            CaptureSessionState.Idle -> IdleBlock(
                instructions = instructions,
                accent = accent,
                onStart = {
                    val items = (queryResult as? CyanMediaResult.Items)?.list.orEmpty()
                    val session = CaptureSessions.newSession(items, expectedType, source)
                    sessionState = CaptureSessionState.Active(session)
                }
            )
            is CaptureSessionState.Active -> ActiveBlock(
                session = s.session,
                instructions = instructions,
                accent = accent,
                elapsedSec = elapsedSec,
                newestNow = newestNow,
                mediaPermissionGranted = mediaPermissionGranted,
                observerEnabled = observerEnabled,
                onCheckAgain = onForceRefresh,
                onOpenHeyCyan = onOpenHeyCyan,
                onResetSession = {
                    val items = (queryResult as? CyanMediaResult.Items)?.list.orEmpty()
                    sessionState = CaptureSessionState.Active(
                        CaptureSessions.newSession(items, expectedType, source)
                    )
                },
                onCancel = { sessionState = CaptureSessionState.Idle }
            )
            is CaptureSessionState.NewPhoto -> SuccessBlock(
                title = "New PHOTO detected",
                item = s.item,
                accent = SuccessColor,
                onOpenInGallery = onOpenInGallery,
                onShare = { onShareItem(s.item) },
                onResetSession = {
                    val items = (queryResult as? CyanMediaResult.Items)?.list.orEmpty()
                    sessionState = CaptureSessionState.Active(
                        CaptureSessions.newSession(items, expectedType, source)
                    )
                },
                onDone = { sessionState = CaptureSessionState.Idle }
            )
            is CaptureSessionState.NewVideo -> SuccessBlock(
                title = "New VIDEO detected",
                item = s.item,
                accent = SuccessColor,
                onOpenInGallery = onOpenInGallery,
                onShare = { onShareItem(s.item) },
                onResetSession = {
                    val items = (queryResult as? CyanMediaResult.Items)?.list.orEmpty()
                    sessionState = CaptureSessionState.Active(
                        CaptureSessions.newSession(items, expectedType, source)
                    )
                },
                onDone = { sessionState = CaptureSessionState.Idle }
            )
            is CaptureSessionState.WrongType -> SuccessBlock(
                title = wrongTypeTitle(expectedType, s.item),
                item = s.item,
                accent = WarningAmber,
                onOpenInGallery = onOpenInGallery,
                onShare = { onShareItem(s.item) },
                onResetSession = {
                    val items = (queryResult as? CyanMediaResult.Items)?.list.orEmpty()
                    sessionState = CaptureSessionState.Active(
                        CaptureSessions.newSession(items, expectedType, source)
                    )
                },
                onDone = { sessionState = CaptureSessionState.Idle }
            )
            is CaptureSessionState.NoNewMedia -> NoNewMediaBlock(
                session = s.session,
                latest = s.latest,
                expectedType = expectedType,
                accent = accent,
                mediaPermissionGranted = mediaPermissionGranted,
                observerEnabled = observerEnabled,
                onOpenHeyCyan = onOpenHeyCyan,
                onCheckAgain = onForceRefresh,
                onResetSession = {
                    val items = (queryResult as? CyanMediaResult.Items)?.list.orEmpty()
                    sessionState = CaptureSessionState.Active(
                        CaptureSessions.newSession(items, expectedType, source)
                    )
                },
                onMarkLatestAsGlasses = {
                    s.latest?.let { onMarkLatestAsGlasses(it.uri.toString()) }
                },
                onCancel = { sessionState = CaptureSessionState.Idle }
            )
            CaptureSessionState.PermissionMissing -> PermissionBlock(
                onCheckAgain = onForceRefresh
            )
            is CaptureSessionState.ReadError -> ErrorBlock(
                message = s.message,
                onCheckAgain = onForceRefresh,
                onResetSession = { sessionState = CaptureSessionState.Idle }
            )
        }
    }
}

private fun recordHistory(
    context: android.content.Context,
    session: CaptureSession,
    item: CyanMediaItem,
    mediaType: String
) {
    CaptureHistoryStore.add(
        context,
        CaptureHistoryEntry(
            sessionId = session.sessionId,
            capturedAtSec = item.dateAddedSeconds,
            mediaUri = item.uri.toString(),
            mediaName = item.displayName,
            mediaType = mediaType,
            source = session.source.name
        )
    )
}

private fun sessionShortLabel(state: CaptureSessionState): String = when (state) {
    CaptureSessionState.Idle -> "Idle"
    is CaptureSessionState.Active -> "Watching"
    is CaptureSessionState.NewPhoto -> "Photo"
    is CaptureSessionState.NewVideo -> "Video"
    is CaptureSessionState.WrongType -> "Wrong type"
    is CaptureSessionState.NoNewMedia -> "No new media"
    CaptureSessionState.PermissionMissing -> "Perm"
    is CaptureSessionState.ReadError -> "Error"
}

private fun wrongTypeTitle(expected: ExpectedMediaType, got: CyanMediaItem): String {
    val gotLabel = if (got.type == CyanMediaType.Image) "photo" else "video"
    val wantLabel = when (expected) {
        ExpectedMediaType.Photo -> "photo"
        ExpectedMediaType.Video -> "video"
        ExpectedMediaType.Unknown -> "media"
    }
    return "New $gotLabel detected (expected $wantLabel)"
}

// ----- Sub-blocks for GuidedCaptureCard ------------------------------------

@Composable
private fun IdleBlock(
    instructions: List<String>,
    accent: Color,
    onStart: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        instructions.forEach { Text(it, fontSize = 12.sp, color = OnSurface) }
        Spacer(Modifier.height(6.dp))
        Button(
            onClick = onStart,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.White)
        ) {
            Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Start", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ActiveBlock(
    session: CaptureSession,
    instructions: List<String>,
    accent: Color,
    elapsedSec: Long,
    newestNow: CyanMediaItem?,
    mediaPermissionGranted: Boolean,
    observerEnabled: Boolean,
    onCheckAgain: () -> Unit,
    onOpenHeyCyan: () -> Unit,
    onResetSession: () -> Unit,
    onCancel: () -> Unit
) {
    val timedOut = elapsedSec >= 25L

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        StatusInfoBox(
            color = accent,
            icon = Icons.Default.HourglassTop,
            title = "Watching for new phone media",
            body = instructions.joinToString(" "),
            footer = "Session: ${session.sessionId}  *  Started: ${formatCyanTimestamp(session.startSec)}  *  Elapsed: ${elapsedSec}s"
        )

        // 25s timeout warning surfaces inline -- do not auto-transition
        // (preserves the baseline). Tells the user something is wrong.
        if (timedOut) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = WarningAmber.copy(alpha = 0.10f),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, WarningAmber.copy(alpha = 0.30f))
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Warning, null, modifier = Modifier.size(16.dp), tint = WarningAmber)
                    Text(
                        "No new media detected after ${elapsedSec}s. Try Check Again, or Open HeyCyan and import the latest capture to the phone Gallery.",
                        fontSize = 11.sp, color = OnSurface, modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        DiagnosticBox(
            session = session,
            newestNow = newestNow,
            mediaPermissionGranted = mediaPermissionGranted,
            observerEnabled = observerEnabled
        )

        // Primary action: Check Again -- the manual escape hatch.
        Button(
            onClick = onCheckAgain,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.White)
        ) {
            Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Check Again", fontWeight = FontWeight.Bold)
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onOpenHeyCyan, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Smartphone, null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("Open HeyCyan", fontSize = 12.sp)
            }
            OutlinedButton(onClick = onResetSession, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.RestartAlt, null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("New baseline", fontSize = 12.sp)
            }
        }
        TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Cancel, null, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text("Cancel session", fontSize = 12.sp)
        }
    }
}

@Composable
private fun DiagnosticBox(
    session: CaptureSession,
    newestNow: CyanMediaItem?,
    mediaPermissionGranted: Boolean,
    observerEnabled: Boolean
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = SurfaceCardSubtle,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(0.5.dp, BorderSubtle)
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Diagnostics", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = OnSurfaceMuted)
            DiagRow("Baseline newest id", session.baseline.newestSeenIdAtBaseline?.toString() ?: "(none)")
            DiagRow("Baseline newest at", formatCyanTimestamp(session.baseline.newestSeenAtBaselineSec))
            DiagRow("Baseline taken at", formatCyanTimestamp(session.baseline.takenAtSec))
            DiagRow("Newest now id", newestNow?.id?.toString() ?: "(none)")
            DiagRow("Newest now name", newestNow?.displayName ?: "(none)")
            DiagRow("Newest now at", newestNow?.let { formatCyanTimestamp(it.dateAddedSeconds) } ?: "(none)")
            DiagRow("Media permission", if (mediaPermissionGranted) "granted" else "MISSING")
            DiagRow("Watching observer", if (observerEnabled) "enabled" else "disabled")
        }
    }
}

@Composable
private fun DiagRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(label, fontSize = 10.sp, color = OnSurfaceMuted, modifier = Modifier.width(140.dp))
        Text(value, fontSize = 10.sp, color = OnSurface, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun SuccessBlock(
    title: String,
    item: CyanMediaItem,
    accent: Color,
    onOpenInGallery: () -> Unit,
    onShare: () -> Unit,
    onResetSession: () -> Unit,
    onDone: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        StatusInfoBox(
            color = accent,
            icon = Icons.Default.CheckCircle,
            title = title,
            body = "Detected " + (item.displayName ?: "(unnamed)") + " at " +
                    formatCyanTimestamp(item.dateAddedSeconds) + ". URI recorded in capture history."
        )
        item.relativePath?.takeIf { it.isNotBlank() }?.let { p ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = SurfaceCardSubtle,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(0.5.dp, BorderSubtle)
            ) {
                Text(
                    "Path: $p",
                    modifier = Modifier.padding(8.dp),
                    fontSize = 11.sp, color = OnSurfaceMuted
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onOpenInGallery, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.OpenInNew, null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("Open", fontSize = 12.sp)
            }
            OutlinedButton(onClick = onShare, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Share, null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("Share", fontSize = 12.sp)
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onResetSession, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.RestartAlt, null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("Run again", fontSize = 12.sp)
            }
            TextButton(onClick = onDone, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Check, null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("Done", fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun NoNewMediaBlock(
    session: CaptureSession,
    latest: CyanMediaItem?,
    expectedType: ExpectedMediaType,
    accent: Color,
    mediaPermissionGranted: Boolean,
    observerEnabled: Boolean,
    onOpenHeyCyan: () -> Unit,
    onCheckAgain: () -> Unit,
    onResetSession: () -> Unit,
    onMarkLatestAsGlasses: () -> Unit,
    onCancel: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        StatusInfoBox(
            color = WarningAmber,
            icon = Icons.Default.Warning,
            title = "No new phone media detected after this capture session started",
            body = "The glasses may have saved the photo inside the native HeyCyan app, HeyCyan may not have imported it to the phone Gallery yet, or sync may be delayed. Open HeyCyan, import / save the latest photo, then return to CyanGem2."
        )

        // Explicit 5-step recovery flow per Juan's HC-019B spec
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = SurfaceCardSubtle,
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(0.5.dp, BorderSubtle)
        ) {
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("HeyCyan import recovery", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = OnSurfaceMuted)
                Text("Step 1. Tap Open HeyCyan below.", fontSize = 11.sp, color = OnSurface)
                Text("Step 2. In HeyCyan, find the album / media list / import section.", fontSize = 11.sp, color = OnSurface)
                Text("Step 3. Save / export the latest capture to the phone Gallery.", fontSize = 11.sp, color = OnSurface)
                Text("Step 4. Return to CyanGem2.", fontSize = 11.sp, color = OnSurface)
                Text("Step 5. Tap Check Again.", fontSize = 11.sp, color = OnSurface)
            }
        }

        DiagnosticBox(
            session = session,
            newestNow = latest,
            mediaPermissionGranted = mediaPermissionGranted,
            observerEnabled = observerEnabled
        )

        latest?.let {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = SurfaceCardSubtle,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(0.5.dp, BorderSubtle)
            ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Newest item on phone now", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = OnSurfaceMuted)
                    Text(it.displayName ?: "(unnamed)", fontSize = 12.sp, color = OnSurface)
                    Text(
                        (if (it.type == CyanMediaType.Image) "Image * " else "Video * ") +
                                formatCyanTimestamp(it.dateAddedSeconds),
                        fontSize = 11.sp, color = OnSurfaceMuted
                    )
                    it.relativePath?.takeIf { p -> p.isNotBlank() }?.let { p ->
                        Text("Path: $p", fontSize = 10.sp, color = OnSurfaceMuted)
                    }
                }
            }
        }

        // Primary recovery action -- bigger, full-width.
        Button(
            onClick = onOpenHeyCyan,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary, contentColor = Color.White)
        ) {
            Icon(Icons.Default.Smartphone, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Open HeyCyan Import", fontWeight = FontWeight.Bold)
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onCheckAgain, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Refresh, null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("Check Again", fontSize = 12.sp)
            }
            OutlinedButton(onClick = onResetSession, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.RestartAlt, null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("Reset Session", fontSize = 12.sp)
            }
        }

        // Optional manual recovery: mark the newest phone item as a glasses
        // capture even if the session didn't auto-detect it. Useful when the
        // photo did land but our heuristic missed it (e.g., HeyCyan saved
        // but at the same DATE_ADDED second as the baseline).
        if (latest != null) {
            OutlinedButton(
                onClick = onMarkLatestAsGlasses,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = GeminiPurple),
                border = BorderStroke(1.dp, GeminiPurple.copy(alpha = 0.40f))
            ) {
                Icon(Icons.Default.AutoAwesomeMosaic, null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("Mark Latest Phone Photo as Glasses Capture", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Cancel, null, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text("Cancel session", fontSize = 12.sp)
        }
    }
}

@Composable
private fun PermissionBlock(onCheckAgain: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        StatusInfoBox(
            color = WarningAmber,
            icon = Icons.Default.Lock,
            title = "Photo / video permission required",
            body = "Grant media permission via the Home tab Capture Check or Settings -> Permissions, then tap Check Again."
        )
        OutlinedButton(onClick = onCheckAgain, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Refresh, null, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text("Check Again", fontSize = 12.sp)
        }
    }
}

@Composable
private fun ErrorBlock(
    message: String,
    onCheckAgain: () -> Unit,
    onResetSession: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        StatusInfoBox(
            color = ErrorColor,
            icon = Icons.Default.ErrorOutline,
            title = "Could not read phone media",
            body = message
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onCheckAgain,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary, contentColor = Color.White)
            ) {
                Icon(Icons.Default.Refresh, null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("Check Again", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
            OutlinedButton(onClick = onResetSession, modifier = Modifier.weight(1f)) {
                Text("Reset", fontSize = 12.sp)
            }
        }
    }
}

// =============================================================================
// Hardware control map (carried unchanged from HC-018/HC-019)
// =============================================================================

@Composable
private fun ControlMapHeader() {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Text(
            "Hardware Control Map",
            fontSize = 13.sp, fontWeight = FontWeight.Bold, color = OnSurface,
            letterSpacing = 0.5.sp
        )
        Text(
            "Reference. Behaviors below are typical for the Hey Cyan W630 series. Consult the HeyCyan native app for exact firmware behavior.",
            fontSize = 11.sp, color = OnSurfaceMuted,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

@Composable
private fun ControlA1Card() {
    HomeCard {
        ControlHeader(badge = "A1", title = "Front button", subtitle = "Primary capture / power", color = CyanPrimary)
        Spacer(Modifier.height(8.dp))
        ControlGesture("Short press", "Capture photo (or start/stop video, depending on mode)")
        ControlGesture("Long press (~3 sec)", "Power on / off the glasses")
        ControlGesture("Double press", "Toggle photo / video mode (firmware-dependent)")
        Spacer(Modifier.height(6.dp))
        Text(
            "Captures land in HeyCyan native app first. The Guided flows above watch MediaStore for HeyCyan's import to phone media.",
            fontSize = 11.sp, color = OnSurfaceMuted
        )
    }
}

@Composable
private fun ControlA2Card() {
    HomeCard {
        ControlHeader(badge = "A2", title = "Rear button (AI button)", subtitle = "Wakes HeyCyan native AI", color = GeminiPurple)
        Spacer(Modifier.height(8.dp))
        ControlGesture("Short press", "Wake the HeyCyan native AI assistant on the glasses")
        ControlGesture("Long press", "Cancel / dismiss the assistant (firmware-dependent)")
        Spacer(Modifier.height(6.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = WarningAmber.copy(alpha = 0.10f),
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, WarningAmber.copy(alpha = 0.30f))
        ) {
            Row(
                modifier = Modifier.padding(10.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Info, null, modifier = Modifier.size(16.dp), tint = WarningAmber)
                Text(
                    "Important: the rear button wakes the HeyCyan native AI. It does NOT automatically start Gemini Live. CyanGem2 cannot remap or intercept this button.",
                    fontSize = 11.sp, color = OnSurface, modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ControlTouchpadCard() {
    HomeCard {
        ControlHeader(badge = "T", title = "Touchpad", subtitle = "Side-arm gestures", color = CyanSecondary)
        Spacer(Modifier.height(8.dp))
        ControlGesture("Tap", "Confirm / play / pause (context-dependent)")
        ControlGesture("Swipe forward", "Next item in playback / volume up")
        ControlGesture("Swipe back", "Previous item / volume down")
        ControlGesture("Long press", "Voice command (firmware-dependent)")
        Spacer(Modifier.height(6.dp))
        Text(
            "Touchpad gestures route to whatever app holds the audio focus on the phone. CyanGem2 does not intercept them.",
            fontSize = 11.sp, color = OnSurfaceMuted
        )
    }
}

@Composable
private fun ControlStatusLightsCard() {
    HomeCard {
        ControlHeader(badge = "L", title = "Status lights", subtitle = "LED indicator reference", color = SuccessColor)
        Spacer(Modifier.height(8.dp))
        LightRow(Color(0xFFFFFFFF), Color(0xFFE5E7EB), "Solid white", "Booting / firmware ready")
        LightRow(Color(0xFF2563EB), Color(0xFFDBEAFE), "Solid blue", "Paired and connected to phone")
        LightRow(Color(0xFF2563EB), Color(0xFFDBEAFE), "Pulsing blue", "Pairing mode")
        LightRow(Color(0xFF16A34A), Color(0xFFDCFCE7), "Green flash", "Capture taken (photo) / capture started (video)")
        LightRow(Color(0xFFDC2626), Color(0xFFFEE2E2), "Red", "Recording video / battery critical")
        LightRow(Color(0xFFE65100), Color(0xFFFFEDD5), "Amber", "Charging / low battery warning")
        Spacer(Modifier.height(6.dp))
        Text(
            "Exact light meanings vary by firmware revision. Use the HeyCyan native app for the authoritative reference.",
            fontSize = 11.sp, color = OnSurfaceMuted
        )
    }
}

@Composable
private fun ControlHeader(badge: String, title: String, subtitle: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(
            modifier = Modifier.size(36.dp),
            color = color.copy(alpha = 0.14f),
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, color.copy(alpha = 0.30f))
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(badge, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = color)
            }
        }
        Column {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = OnSurface)
            Text(subtitle, fontSize = 11.sp, color = OnSurfaceMuted)
        }
    }
}

@Composable
private fun ControlGesture(gesture: String, effect: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(gesture, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CyanPrimary, modifier = Modifier.width(120.dp))
        Text(effect, fontSize = 12.sp, color = OnSurface, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun LightRow(color: Color, bg: Color, label: String, meaning: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(modifier = Modifier.size(18.dp).background(bg, CircleShape).padding(3.dp)) {
            Box(modifier = Modifier.fillMaxSize().background(color, CircleShape))
        }
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = OnSurface, modifier = Modifier.width(110.dp))
        Text(meaning, fontSize = 11.sp, color = OnSurfaceMuted, modifier = Modifier.weight(1f))
    }
}

// =============================================================================
// Guided button test (carry from HC-018)
// =============================================================================

private sealed class TestResult {
    object Untested : TestResult()
    object Pass : TestResult()
    object Fail : TestResult()
}

@Composable
private fun GuidedTestCard(
    a1Result: TestResult,
    a2Result: TestResult,
    touchpadResult: TestResult,
    lightsResult: TestResult,
    onSet: (which: String, result: TestResult) -> Unit,
    onResetAll: () -> Unit
) {
    HomeCard(borderColor = CyanPrimary.copy(alpha = 0.40f)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.Checklist, null, modifier = Modifier.size(18.dp), tint = CyanPrimary)
            Text("Guided button test", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = OnSurface, modifier = Modifier.weight(1f))
            TextButton(onClick = onResetAll) {
                Icon(Icons.Default.RestartAlt, null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("Reset", fontSize = 11.sp)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "Walk through each test on the glasses; tap Pass / Fail. Results are local to this session.",
            fontSize = 11.sp, color = OnSurfaceMuted
        )
        Spacer(Modifier.height(10.dp))
        TestRow("A1", "Press the FRONT button briefly. The glasses should signal a capture (light flash, sound).", a1Result, { onSet("a1", TestResult.Pass) }, { onSet("a1", TestResult.Fail) })
        Spacer(Modifier.height(8.dp))
        TestRow("A2", "Press the REAR button briefly. The HeyCyan native AI should activate (chime / voice prompt).", a2Result, { onSet("a2", TestResult.Pass) }, { onSet("a2", TestResult.Fail) })
        Spacer(Modifier.height(8.dp))
        TestRow("T", "Swipe forward on the touchpad. If audio is playing on the phone, the next track / item should respond.", touchpadResult, { onSet("touchpad", TestResult.Pass) }, { onSet("touchpad", TestResult.Fail) })
        Spacer(Modifier.height(8.dp))
        TestRow("L", "Verify the LED status -- solid blue means paired and connected to the phone.", lightsResult, { onSet("lights", TestResult.Pass) }, { onSet("lights", TestResult.Fail) })
    }
}

@Composable
private fun TestRow(
    badge: String,
    instruction: String,
    result: TestResult,
    onPass: () -> Unit,
    onFail: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = SurfaceCardSubtle,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(0.5.dp, BorderSubtle)
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    modifier = Modifier.size(28.dp),
                    color = CyanPrimary.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(badge, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CyanPrimary)
                    }
                }
                Text(instruction, fontSize = 12.sp, color = OnSurface, modifier = Modifier.weight(1f))
                StatusPill(
                    when (result) { TestResult.Untested -> "-"; TestResult.Pass -> "Pass"; TestResult.Fail -> "Fail" },
                    when (result) { TestResult.Untested -> OnSurfaceMuted; TestResult.Pass -> SuccessColor; TestResult.Fail -> ErrorColor }
                )
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onPass,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = SuccessColor),
                    border = BorderStroke(1.dp, SuccessColor.copy(alpha = 0.40f))
                ) {
                    Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Pass", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = onFail,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorColor),
                    border = BorderStroke(1.dp, ErrorColor.copy(alpha = 0.40f))
                ) {
                    Icon(Icons.Default.Cancel, null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Fail", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// =============================================================================
// Troubleshooting + AI button truth + No camera preview (carry from HC-018)
// =============================================================================

@Composable
private fun TroubleshootingCard(
    onOpenHeyCyan: () -> Unit,
    onOpenBluetoothSettings: () -> Unit
) {
    HomeCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.Construction, null, modifier = Modifier.size(18.dp), tint = WarningAmber)
            Text("Troubleshooting", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = OnSurface)
        }
        Spacer(Modifier.height(8.dp))
        TroubleRow("Rear button does nothing",
            "1) Confirm Bluetooth is on and the glasses are paired (LED solid blue). 2) Open the HeyCyan app -- it owns the AI activation. 3) Re-pair if HeyCyan still does not respond.")
        TroubleRow("Photo doesn't show in HeyCyan import",
            "1) Wait -- HeyCyan transfers asynchronously. 2) Open HeyCyan and check its capture log. 3) Save / export to phone Gallery. 4) Tap Check Again in the Guided flow.")
        TroubleRow("Audio plays through phone speaker, not glasses",
            "Expand the OS media-output panel and select Hey Cyan. CyanGem2 does not control audio routing.")
        TroubleRow("LED stays amber",
            "Glasses are charging or low battery. Continue charging; LED returns to white/blue when ready.")
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onOpenHeyCyan, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Smartphone, null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("Open HeyCyan", fontSize = 12.sp)
            }
            OutlinedButton(onClick = onOpenBluetoothSettings, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Bluetooth, null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("BT Settings", fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun TroubleRow(issue: String, steps: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(issue, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = WarningAmberSoft)
        Text(steps, fontSize = 11.sp, color = OnSurface, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun AiButtonTruthCard() {
    HomeCard(borderColor = GeminiPurple.copy(alpha = 0.30f)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(18.dp), tint = GeminiPurple)
            Text("AI button truth", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = OnSurface)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "The rear button (A2) wakes the HeyCyan native AI on the glasses. It does NOT automatically start Gemini Live, even if you've used Gemini before.",
            fontSize = 12.sp, color = OnSurface
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "If you want Gemini Live: open the Gemini app from Home -> Open Gemini -> tap the Live button there. The glasses' BT mic and speaker carry the audio once Live is running.",
            fontSize = 11.sp, color = OnSurfaceMuted
        )
    }
}

@Composable
private fun NoCameraPreviewCard() {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        color = SurfaceCardSubtle,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, WarningAmber.copy(alpha = 0.30f))
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.VideocamOff, null, modifier = Modifier.size(18.dp), tint = WarningAmber)
                Text("No live camera preview", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = WarningAmberSoft)
            }
            Text(
                "CyanGem2 does not show a continuous camera feed from the glasses. Captures arrive via HeyCyan's import path; CyanGem2 reads what HeyCyan saved and confirms it landed.",
                fontSize = 12.sp, color = OnSurface
            )
        }
    }
}

// =============================================================================
// Reusable status info box (file-private)
// =============================================================================

@Composable
private fun StatusInfoBox(
    color: Color,
    icon: ImageVector,
    title: String,
    body: String,
    footer: String? = null
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = color.copy(alpha = 0.10f),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.30f))
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(icon, null, modifier = Modifier.size(18.dp), tint = color)
                Text(title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = color)
            }
            Text(body, fontSize = 12.sp, color = OnSurface)
            footer?.let { Text(it, fontSize = 10.sp, color = OnSurfaceMuted) }
        }
    }
}
