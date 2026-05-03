package com.cyangem.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.cyangem.ui.theme.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

// =============================================================================
// HC-019 — Home: stable support app dashboard with capture-history wiring.
//
// Differences vs HC-018:
//   - When Capture Check transitions to NewMediaDetected, the URI is
//     written to [CaptureHistoryStore] so the Gallery Glasses filter can
//     recognize it as a confirmed capture (strongest signal in
//     GlassesMediaIdentifier).
//
// Same five cards as HC-018:
//   1. Gemini launch / guidance
//   2. HeyCyan launch
//   3. Bluetooth / glasses truthful status
//   4. Capture Check (auto-refresh + history write on detection)
//   5. Truth / Limitations
// =============================================================================

@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val btStatus by rememberBluetoothStatus(context)

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    var captureState by remember { mutableStateOf<CaptureCheckState>(CaptureCheckState.Idle) }
    var lastQueryResult by remember { mutableStateOf<CyanMediaResult?>(null) }
    var refreshTick by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> refreshTick++ }

    val baselineSet = captureState !is CaptureCheckState.Idle
    rememberMediaObserver(enabled = baselineSet) { refreshTick++ }
    OnLifecycleResume { refreshTick++ }

    LaunchedEffect(refreshTick) {
        isLoading = true
        val result =
            if (!MediaBridgeRepository.hasMediaPermission(context)) CyanMediaResult.PermissionMissing
            else MediaBridgeRepository.queryRecentMedia(context, limit = 30)
        lastQueryResult = result
        captureState = when (val s = captureState) {
            CaptureCheckState.Idle -> {
                if (result is CyanMediaResult.PermissionMissing) CaptureCheckState.PermissionMissing
                else CaptureCheckState.Idle
            }
            is CaptureCheckState.BaselineSet     -> CaptureCheck.evaluate(s.baseline, result)
            is CaptureCheckState.NoNewMedia      -> CaptureCheck.evaluate(s.baseline, result)
            is CaptureCheckState.NewMediaDetected -> CaptureCheck.evaluate(s.baseline, result)
            CaptureCheckState.PermissionMissing,
            is CaptureCheckState.ReadError -> {
                if (result is CyanMediaResult.PermissionMissing) CaptureCheckState.PermissionMissing
                else CaptureCheckState.Idle
            }
        }
        isLoading = false
    }

    // HC-019: when Capture Check transitions to NewMediaDetected, record
    // the URI to capture history. CaptureHistoryStore.add deduplicates by
    // URI so re-entering this state doesn't duplicate the entry.
    LaunchedEffect(captureState) {
        val s = captureState
        if (s is CaptureCheckState.NewMediaDetected) {
            CaptureHistoryStore.add(
                context,
                CaptureHistoryEntry(
                    sessionId = "home-cc-${System.currentTimeMillis()}",
                    capturedAtSec = s.newest.dateAddedSeconds,
                    mediaUri = s.newest.uri.toString(),
                    mediaName = s.newest.displayName,
                    mediaType = if (s.newest.type == CyanMediaType.Image) "Image" else "Video",
                    source = CaptureSource.Manual.name
                )
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(BackgroundLight)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp)
        ) {
            item { HomeHeader(btStatus) }
            item {
                GeminiGuidanceCard(
                    onOpenGemini = {
                        val ok = openGeminiApp(context)
                        val msg = if (ok)
                            "Gemini opened. Tap the Live button (waveform / mic icon) inside Gemini."
                        else
                            "Gemini app not detected. Install the Gemini or Google app."
                        coroutineScope.launchSnackbar(snackbarHostState, msg)
                    }
                )
            }
            item {
                HeyCyanLaunchCard(
                    onOpenHeyCyan = {
                        val ok = openHeyCyanApp(context)
                        if (!ok) coroutineScope.launchSnackbar(
                            snackbarHostState,
                            "HeyCyan app not detected. Install it to import captures from the glasses."
                        )
                    }
                )
            }
            item { GlassesStatusCard(btStatus) }
            item {
                CaptureCheckCard(
                    state = captureState,
                    isLoading = isLoading,
                    onStartCheck = {
                        val items = (lastQueryResult as? CyanMediaResult.Items)?.list.orEmpty()
                        captureState = CaptureCheckState.BaselineSet(CaptureCheck.recordBaseline(items))
                    },
                    onCheckAgain = { refreshTick++ },
                    onResetBaseline = {
                        val items = (lastQueryResult as? CyanMediaResult.Items)?.list.orEmpty()
                        captureState = CaptureCheckState.BaselineSet(CaptureCheck.recordBaseline(items))
                    },
                    onStartOver = { captureState = CaptureCheckState.Idle },
                    onOpenHeyCyan = {
                        val ok = openHeyCyanApp(context)
                        if (!ok) coroutineScope.launchSnackbar(
                            snackbarHostState, "HeyCyan app not detected."
                        )
                    },
                    onRequestPermission = {
                        permissionLauncher.launch(MediaBridgeRepository.requiredMediaPermissions())
                    },
                    onOpenAppSettings = {
                        val ok = openAppSettings(context)
                        if (!ok) coroutineScope.launchSnackbar(
                            snackbarHostState, "Could not open app settings."
                        )
                    },
                    onShareNewest = { item ->
                        val ok = shareMediaItem(context, item.uri, item.mimeType)
                        if (!ok) coroutineScope.launchSnackbar(
                            snackbarHostState, "Could not share."
                        )
                    },
                    onOpenInGallery = {
                        val ok = openSamsungGallery(context) ||
                                openGooglePhotos(context) ||
                                openSystemGallery(context)
                        if (!ok) coroutineScope.launchSnackbar(snackbarHostState, "No gallery app found.")
                    }
                )
            }
            item { TruthLimitationsCard() }
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
private fun HomeHeader(btStatus: BtAdapterStatus) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Home",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = OnSurface,
                modifier = Modifier.weight(1f)
            )
            ConnectionChip(btStatus)
        }
        Text(
            "CyanGem2 is a support app. Gemini Live is the AI. HeyCyan is the device app.",
            fontSize = 11.sp,
            color = OnSurfaceMuted
        )
    }
}

@Composable
private fun ConnectionChip(btStatus: BtAdapterStatus) {
    val (label, color) = when (btStatus) {
        BtAdapterStatus.On -> "Bluetooth: On" to SuccessColor
        BtAdapterStatus.Off -> "Bluetooth: Off" to WarningAmber
        BtAdapterStatus.Unsupported,
        BtAdapterStatus.PermissionMissing -> "Bluetooth status unknown" to OnSurfaceMuted
    }
    Surface(
        color = color.copy(alpha = 0.12f),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.30f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(modifier = Modifier.size(6.dp).background(color, CircleShape))
            Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

// =============================================================================
// Card 1 — Gemini launch / guidance (purple accent)
// =============================================================================

@Composable
private fun GeminiGuidanceCard(onOpenGemini: () -> Unit) {
    val context = LocalContext.current
    val installed = remember { detectInstalledGemini(context) }

    HomeCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier.size(40.dp).background(GeminiPurple.copy(alpha = 0.14f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(22.dp), tint = GeminiPurple)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Gemini Live", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = OnSurface)
                Text(
                    installed?.let { "Detected: $it" }
                        ?: "Gemini / Google app not detected — install one to use Gemini Live",
                    fontSize = 11.sp, color = OnSurfaceMuted
                )
            }
            StatusPill(if (installed != null) "Detected" else "Check", if (installed != null) SuccessColor else WarningAmber)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Gemini Live is the AI brain. CyanGem2 cannot start Live directly — Google does not expose a public Live action. Tapping Open Gemini brings you into the Gemini app; you tap the Live button there.",
            fontSize = 12.sp, color = OnSurface
        )
        Spacer(Modifier.height(8.dp))
        listOf(
            "1. Tap Open Gemini below.",
            "2. Tap the Live button (waveform / mic icon, bottom right).",
            "3. Confirm audio routes through the Hey Cyan glasses.",
            "4. Talk naturally."
        ).forEach { Text(it, fontSize = 12.sp, color = OnSurface) }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onOpenGemini,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = GeminiPurple, contentColor = Color.White)
        ) {
            Icon(Icons.Default.OpenInNew, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Open Gemini", fontWeight = FontWeight.Bold)
        }
    }
}

// =============================================================================
// Card 2 — HeyCyan launch
// =============================================================================

@Composable
private fun HeyCyanLaunchCard(onOpenHeyCyan: () -> Unit) {
    val context = LocalContext.current
    val installed = remember { detectInstalledHeyCyan(context) }

    HomeCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier.size(40.dp).background(CyanPrimary.copy(alpha = 0.14f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Smartphone, null, modifier = Modifier.size(22.dp), tint = CyanPrimary)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("HeyCyan native app", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = OnSurface)
                Text(
                    installed?.let { "Detected: $it" }
                        ?: "HeyCyan native app not detected — install it to import glasses captures",
                    fontSize = 11.sp, color = OnSurfaceMuted
                )
            }
            StatusPill(if (installed != null) "Detected" else "Check", if (installed != null) SuccessColor else WarningAmber)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "HeyCyan is the official device companion. It pairs the glasses, manages firmware, and imports photos / videos to the phone. CyanGem2 does not replace it — Capture Check below verifies whatever HeyCyan saves.",
            fontSize = 12.sp, color = OnSurface
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onOpenHeyCyan,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary, contentColor = Color.White)
        ) {
            Icon(Icons.Default.OpenInNew, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Open HeyCyan", fontWeight = FontWeight.Bold)
        }
    }
}

// =============================================================================
// Card 3 — Bluetooth / glasses truthful status
// =============================================================================

@Composable
private fun GlassesStatusCard(btStatus: BtAdapterStatus) {
    val context = LocalContext.current
    HomeCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier.size(40.dp).background(CyanPrimary.copy(alpha = 0.14f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Bluetooth, null, modifier = Modifier.size(22.dp), tint = CyanPrimary)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Bluetooth / glasses status", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = OnSurface)
                Text(
                    when (btStatus) {
                        BtAdapterStatus.On ->
                            "Bluetooth adapter on. Hey Cyan device connection not directly verified by CyanGem2."
                        BtAdapterStatus.Off ->
                            "Bluetooth is off. Turn it on in Settings to use the glasses."
                        BtAdapterStatus.Unsupported,
                        BtAdapterStatus.PermissionMissing ->
                            "Status unavailable. Check phone Bluetooth permission."
                    },
                    fontSize = 12.sp, color = OnSurfaceMuted
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = { openBluetoothSettings(context) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.OpenInNew, null, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text("Open Bluetooth settings")
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "For glasses pair / unpair, use Settings → Connections → Bluetooth, or the HeyCyan app.",
            fontSize = 11.sp, color = OnSurfaceMuted
        )
    }
}

// =============================================================================
// Card 4 — Capture Check (auto-refresh + writes to CaptureHistoryStore)
// =============================================================================

@Composable
private fun CaptureCheckCard(
    state: CaptureCheckState,
    isLoading: Boolean,
    onStartCheck: () -> Unit,
    onCheckAgain: () -> Unit,
    onResetBaseline: () -> Unit,
    onStartOver: () -> Unit,
    onOpenHeyCyan: () -> Unit,
    onRequestPermission: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onShareNewest: (CyanMediaItem) -> Unit,
    onOpenInGallery: () -> Unit
) {
    HomeCard(borderColor = CyanPrimary.copy(alpha = 0.40f)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.PhotoCamera, null, modifier = Modifier.size(18.dp), tint = CyanPrimary)
            Text(
                "Capture Check",
                fontSize = 14.sp, fontWeight = FontWeight.Bold, color = OnSurface,
                modifier = Modifier.weight(1f)
            )
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = CyanPrimary
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "Auto-refreshes when MediaStore changes or you return to the app. Detected captures are recorded so the Gallery Glasses filter can see them.",
            fontSize = 11.sp, color = OnSurfaceMuted
        )
        Spacer(Modifier.height(12.dp))

        when (state) {
            CaptureCheckState.Idle -> CaptureIdleBlock(onStartCheck)
            is CaptureCheckState.BaselineSet -> CaptureBaselineBlock(
                baseline = state.baseline,
                onOpenHeyCyan = onOpenHeyCyan,
                onCheckAgain = onCheckAgain,
                onResetBaseline = onResetBaseline,
                onStartOver = onStartOver
            )
            is CaptureCheckState.NewMediaDetected -> CaptureNewMediaBlock(
                newest = state.newest,
                onCheckAgain = onCheckAgain,
                onResetBaseline = onResetBaseline,
                onShareNewest = { onShareNewest(state.newest) },
                onOpenInGallery = onOpenInGallery,
                onStartOver = onStartOver
            )
            is CaptureCheckState.NoNewMedia -> CaptureNoNewMediaBlock(
                latest = state.latest,
                onOpenHeyCyan = onOpenHeyCyan,
                onCheckAgain = onCheckAgain,
                onResetBaseline = onResetBaseline,
                onStartOver = onStartOver
            )
            CaptureCheckState.PermissionMissing -> CapturePermissionBlock(
                onRequestPermission = onRequestPermission,
                onOpenAppSettings = onOpenAppSettings
            )
            is CaptureCheckState.ReadError -> CaptureErrorBlock(
                detail = state.message,
                onRetry = onCheckAgain,
                onStartOver = onStartOver
            )
        }
    }
}

@Composable
private fun CaptureIdleBlock(onStartCheck: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        StatusInfoBox(
            color = CyanPrimary,
            icon = Icons.Default.PlayCircleOutline,
            title = "Ready",
            body = "Tap Start Capture Check. The current newest phone media becomes the baseline. " +
                    "Take a photo or video with the glasses (HeyCyan handles the import) and the card auto-detects."
        )
        Button(
            onClick = onStartCheck,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary, contentColor = Color.White)
        ) {
            Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Start Capture Check", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun CaptureBaselineBlock(
    baseline: CaptureBaseline,
    onOpenHeyCyan: () -> Unit,
    onCheckAgain: () -> Unit,
    onResetBaseline: () -> Unit,
    onStartOver: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        StatusInfoBox(
            color = CyanSecondary,
            icon = Icons.Default.HourglassTop,
            title = "Watching for new phone media",
            body = "Take a photo or video with the glasses. Use the HeyCyan app to confirm the import. The card updates automatically when MediaStore sees the file.",
            footer = "Baseline taken: ${formatCyanTimestamp(baseline.takenAtSec)}"
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onOpenHeyCyan, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Smartphone, null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("Open HeyCyan", fontSize = 12.sp)
            }
            Button(
                onClick = onCheckAgain,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary, contentColor = Color.White)
            ) {
                Icon(Icons.Default.Refresh, null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("Check Now", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onResetBaseline, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.RestartAlt, null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("Reset Baseline", fontSize = 12.sp)
            }
            TextButton(onClick = onStartOver, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Cancel, null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("Start Over", fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun CaptureNewMediaBlock(
    newest: CyanMediaItem,
    onCheckAgain: () -> Unit,
    onResetBaseline: () -> Unit,
    onShareNewest: () -> Unit,
    onOpenInGallery: () -> Unit,
    onStartOver: () -> Unit
) {
    val title = if (newest.type == CyanMediaType.Video)
        "New video detected on phone"
    else
        "New photo detected on phone"
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        StatusInfoBox(
            color = SuccessColor,
            icon = Icons.Default.CheckCircle,
            title = title,
            body = "MediaStore reported a new item since the baseline. URI recorded in capture history."
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = SurfaceCardSubtle,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, BorderSubtle)
        ) {
            Row(
                modifier = Modifier.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(SurfaceElevated)
                ) {
                    AsyncImage(
                        model = newest.uri,
                        contentDescription = newest.displayName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        newest.displayName ?: "(unnamed)",
                        fontSize = 12.sp, fontWeight = FontWeight.Bold, color = OnSurface
                    )
                    Text(
                        (if (newest.type == CyanMediaType.Image) "Image • " else "Video • ") +
                                formatCyanTimestamp(newest.dateAddedSeconds),
                        fontSize = 11.sp, color = OnSurfaceMuted
                    )
                    newest.relativePath?.takeIf { it.isNotBlank() }?.let { p ->
                        Text("Path: $p", fontSize = 10.sp, color = OnSurfaceMuted)
                    }
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onOpenInGallery, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.OpenInNew, null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("Open in Gallery", fontSize = 12.sp)
            }
            OutlinedButton(onClick = onShareNewest, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Share, null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("Share", fontSize = 12.sp)
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onCheckAgain,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary, contentColor = Color.White)
            ) {
                Icon(Icons.Default.Refresh, null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("Check Again", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            OutlinedButton(onClick = onResetBaseline, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.RestartAlt, null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("Reset Baseline", fontSize = 12.sp)
            }
        }
        TextButton(onClick = onStartOver, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Cancel, null, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text("Start Over", fontSize = 12.sp)
        }
    }
}

@Composable
private fun CaptureNoNewMediaBlock(
    latest: CyanMediaItem?,
    onOpenHeyCyan: () -> Unit,
    onCheckAgain: () -> Unit,
    onResetBaseline: () -> Unit,
    onStartOver: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        StatusInfoBox(
            color = WarningAmber,
            icon = Icons.Default.Warning,
            title = "No new phone media detected",
            body = "Capture may still be inside the HeyCyan app. HeyCyan import / save-to-phone must complete before MediaStore sees it."
        )
        latest?.let {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = SurfaceCardSubtle,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(0.5.dp, BorderSubtle)
            ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Newest item on phone", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = OnSurfaceMuted)
                    Text(it.displayName ?: "(unnamed)", fontSize = 12.sp, color = OnSurface)
                    Text(
                        (if (it.type == CyanMediaType.Image) "Image • " else "Video • ") +
                                formatCyanTimestamp(it.dateAddedSeconds),
                        fontSize = 11.sp, color = OnSurfaceMuted
                    )
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onOpenHeyCyan, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Smartphone, null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("Open HeyCyan", fontSize = 12.sp)
            }
            Button(
                onClick = onCheckAgain,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary, contentColor = Color.White)
            ) {
                Icon(Icons.Default.Refresh, null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("Check Again", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onResetBaseline, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.RestartAlt, null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("Reset Baseline", fontSize = 12.sp)
            }
            TextButton(onClick = onStartOver, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Cancel, null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("Start Over", fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun CapturePermissionBlock(
    onRequestPermission: () -> Unit,
    onOpenAppSettings: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        StatusInfoBox(
            color = WarningAmber,
            icon = Icons.Default.Lock,
            title = "Photo / video permission required",
            body = "CyanGem2 needs read access to phone media so Capture Check can detect new captures."
        )
        Button(
            onClick = onRequestPermission,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary, contentColor = Color.White)
        ) {
            Icon(Icons.Default.LockOpen, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Grant Photo Access", fontWeight = FontWeight.Bold)
        }
        OutlinedButton(onClick = onOpenAppSettings, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Settings, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Open App Settings (if 'Don't ask again' was set)")
        }
    }
}

@Composable
private fun CaptureErrorBlock(
    detail: String,
    onRetry: () -> Unit,
    onStartOver: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        StatusInfoBox(
            color = ErrorColor,
            icon = Icons.Default.ErrorOutline,
            title = "Could not read phone media",
            body = detail
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onRetry,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary, contentColor = Color.White)
            ) {
                Icon(Icons.Default.Refresh, null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("Retry", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
            OutlinedButton(onClick = onStartOver, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Cancel, null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("Start Over", fontSize = 12.sp)
            }
        }
    }
}

// =============================================================================
// Card 5 — Truth / Limitations
// =============================================================================

@Composable
private fun TruthLimitationsCard() {
    HomeCard(borderColor = WarningAmber.copy(alpha = 0.30f)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.Info, null, modifier = Modifier.size(18.dp), tint = WarningAmber)
            Text("What CyanGem2 does NOT do", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = OnSurface)
        }
        Spacer(Modifier.height(8.dp))
        listOf(
            "Does not embed Gemini Live. Gemini Live runs in the Gemini app.",
            "Does not control Gemini Live. We open Gemini and you tap Live.",
            "Does not capture from the glasses camera directly.",
            "Does not pull media off the glasses over BLE or Wi-Fi.",
            "Does not remap or reprogram the glasses' physical buttons.",
            "Does not act as a Hey Cyan SDK driver. HeyCyan is the import path.",
            "Does not store or request any AI API keys."
        ).forEach { line ->
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("•", fontSize = 12.sp, color = WarningAmber)
                Text(line, fontSize = 12.sp, color = OnSurface)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "What CyanGem2 DOES do: launch Gemini and HeyCyan, show truthful Bluetooth status, verify glasses captures landed via MediaStore (Capture Check + Capture Sessions), provide a Smart-Glasses Gallery filter, and show diagnostic info in Settings.",
            fontSize = 11.sp, color = OnSurfaceMuted
        )
    }
}

// =============================================================================
// Reusable status info box
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

// =============================================================================
// Shared card chrome — used by Home AND by other screens (internal visibility)
// =============================================================================

@Composable
internal fun HomeCard(
    borderColor: Color? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        color = SurfaceCard,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, borderColor ?: BorderSubtle)
    ) {
        Column(modifier = Modifier.padding(14.dp), content = content)
    }
}

@Composable
internal fun StatusPill(label: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.12f),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.30f))
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

internal fun CoroutineScope.launchSnackbar(
    host: SnackbarHostState,
    message: String
) {
    launch {
        host.showSnackbar(message, duration = SnackbarDuration.Short)
    }
}
