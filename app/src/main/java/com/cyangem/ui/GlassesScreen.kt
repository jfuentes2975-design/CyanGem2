package com.cyangem.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyangem.ui.theme.*

// =============================================================================
// HC-015 — Glasses / Camera screen.
//
// No longer a placeholder. Polished workflow screen:
//
//   - Header: "Glasses / Camera"
//   - Hey Cyan glasses status card (live BT adapter status)
//   - Native Hey Cyan app status card + primary "Open Native Hey Cyan App"
//   - Camera Preview Test card (still future work; clearly marked)
//   - Photo / Video Actions row (Open Hey Cyan, Open Samsung Gallery,
//     Open Google Photos, Refresh Media Check)
//   - Button & Wake Behavior info card
//   - Warning banner: "No direct in-app camera bridge yet — use native
//     Hey Cyan app for capture."
//   - **Last Capture Visibility card** (the headline of HC-015)
//
// Last Capture Visibility states (all rendered with clear UX, never silent):
//   - PermissionMissing       → "Grant Photo Access" button
//   - Loading                 → "Checking for recent media…"
//   - Empty                   → "No photos or videos found on this phone."
//   - Items, no new since baseline → shows latest item + "Tap Refresh after
//                                    the glasses click."
//   - Items, NEW since baseline    → green ✅ "New capture detected" + item
//   - Error                   → friendly error message + Retry
// =============================================================================

@Composable
fun GlassesScreen() {
    val context = LocalContext.current
    val btStatus by rememberBluetoothStatus(context)

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    // Last Capture Visibility state
    val baselineSec = remember { mutableStateOf<Long?>(null) }
    val queryResult = remember { mutableStateOf<MediaQueryResult?>(null) }
    val refreshTrigger = remember { mutableIntStateOf(0) }
    val isLoading = remember { mutableStateOf(false) }

    // Re-check on resume (when the user returns from native app)
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        // Whatever the user chose, re-run the query so UI reflects current state.
        refreshTrigger.intValue++
    }

    LaunchedEffect(refreshTrigger.intValue) {
        isLoading.value = true
        if (!MediaStoreQuery.hasMediaPermission(context)) {
            queryResult.value = MediaQueryResult.PermissionMissing
        } else {
            val result = MediaStoreQuery.queryRecentMedia(context, limit = 10)
            queryResult.value = result
            if (baselineSec.value == null) {
                baselineSec.value = System.currentTimeMillis() / 1000
            }
        }
        isLoading.value = false
    }

    Box(modifier = Modifier.fillMaxSize().background(BackgroundLight)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp)
        ) {
            item { GlassesHeader() }
            item { GlassesStatusBlock(btStatus) }
            item { NativeHeyCyanCard(onOpenHeyCyan = {
                val ok = openHeyCyanApp(context)
                if (!ok) coroutineScope.launchSnackbar(
                    snackbarHostState,
                    "Hey Cyan app not detected. Open it from your app drawer if installed."
                )
            }) }
            item { CameraPreviewTestCard() }
            item { PhotoVideoActionsCard(
                onOpenHeyCyan = {
                    val ok = openHeyCyanApp(context)
                    if (!ok) coroutineScope.launchSnackbar(snackbarHostState, "Hey Cyan app not found.")
                },
                onOpenSamsungGallery = {
                    val ok = openSamsungGallery(context)
                    if (!ok) coroutineScope.launchSnackbar(snackbarHostState, "Samsung Gallery not found.")
                },
                onOpenGooglePhotos = {
                    val ok = openGooglePhotos(context)
                    if (!ok) coroutineScope.launchSnackbar(snackbarHostState, "Google Photos not found.")
                },
                onRefreshMediaCheck = {
                    refreshTrigger.intValue++
                },
                photosInstalled = isGooglePhotosInstalled(context),
                galleryInstalled = isSamsungGalleryInstalled(context)
            ) }
            item { ButtonWakeBehaviorCard() }
            item { NoBridgeBanner() }
            item { LastCaptureVisibilityCard(
                queryResult = queryResult.value,
                isLoading = isLoading.value,
                baselineSec = baselineSec.value,
                onRequestPermission = {
                    permissionLauncher.launch(MediaStoreQuery.requiredPermissions())
                },
                onRefresh = {
                    refreshTrigger.intValue++
                },
                onResetBaseline = {
                    baselineSec.value = System.currentTimeMillis() / 1000
                    refreshTrigger.intValue++
                }
            ) }
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
            "Glasses / Camera",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = OnSurface
        )
        Text(
            "Capture, route, and verify media from your Hey Cyan glasses",
            fontSize = 12.sp,
            color = OnSurfaceMuted
        )
    }
}

// =============================================================================
// Hey Cyan glasses status
// =============================================================================

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
                        BtAdapterStatus.On -> "Bluetooth ready — likely connected"
                        BtAdapterStatus.Off -> "Bluetooth is off — turn it on in Settings"
                        BtAdapterStatus.Unsupported,
                        BtAdapterStatus.PermissionMissing -> "Status unavailable"
                    },
                    fontSize = 12.sp, color = OnSurfaceMuted
                )
            }
            val (label, color) = when (btStatus) {
                BtAdapterStatus.On -> "Connected" to SuccessColor
                BtAdapterStatus.Off -> "Needs Check" to WarningAmber
                else -> "Unknown" to OnSurfaceMuted
            }
            StatusPill(label, color)
        }
    }
}

// =============================================================================
// Native Hey Cyan app
// =============================================================================

@Composable
private fun NativeHeyCyanCard(onOpenHeyCyan: () -> Unit) {
    val context = LocalContext.current
    val installed = remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { installed.value = openHeyCyanAppIsInstalled(context) }

    HomeCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier.size(40.dp).background(CyanPrimary.copy(alpha = 0.14f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Smartphone, null, modifier = Modifier.size(22.dp), tint = CyanPrimary)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Native Hey Cyan App", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = OnSurface)
                Text(
                    if (installed.value) "Detected on this device" else "Not detected — open it from your app drawer if installed",
                    fontSize = 12.sp, color = OnSurfaceMuted
                )
            }
            if (installed.value) {
                StatusPill("Detected", SuccessColor)
            } else {
                StatusPill("Check", WarningAmber)
            }
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onOpenHeyCyan,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = CyanPrimary,
                contentColor = Color.White
            )
        ) {
            Icon(Icons.Default.OpenInNew, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Open Native Hey Cyan App", fontWeight = FontWeight.Bold)
        }
    }
}

// Best-effort installed check via the existing isPackageInstalled helper.
// Tries each Hey Cyan candidate. Returns true if any resolves.
private fun openHeyCyanAppIsInstalled(context: android.content.Context): Boolean {
    val candidates = listOf("com.heycyan.app", "com.heyx.heycyan", "io.heycyan.app", "com.oudmon.heycyan", "com.cyan.glasses")
    return candidates.any { isPackageInstalled(context, it) }
}

// =============================================================================
// Camera Preview Test
// =============================================================================

@Composable
private fun CameraPreviewTestCard() {
    HomeCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier.size(40.dp).background(CyanPrimary.copy(alpha = 0.14f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.PhotoCamera, null, modifier = Modifier.size(22.dp), tint = CyanPrimary)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Camera Preview Test", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = OnSurface)
                Text(
                    "Live preview from the glasses camera through the native Hey Cyan app today, then later through CyanGem2.",
                    fontSize = 11.sp, color = OnSurfaceMuted
                )
            }
            StatusPill("Future", OnSurfaceMuted)
        }
    }
}

// =============================================================================
// Photo / Video Actions
// =============================================================================

@Composable
private fun PhotoVideoActionsCard(
    onOpenHeyCyan: () -> Unit,
    onOpenSamsungGallery: () -> Unit,
    onOpenGooglePhotos: () -> Unit,
    onRefreshMediaCheck: () -> Unit,
    photosInstalled: Boolean,
    galleryInstalled: Boolean
) {
    HomeCard {
        Text("Photo / Video Actions", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = OnSurface)
        Spacer(Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionTile(Icons.Default.Smartphone, "Open Hey Cyan", Modifier.weight(1f), enabled = true, onClick = onOpenHeyCyan)
            ActionTile(Icons.Default.PhotoLibrary, "Open Samsung Gallery", Modifier.weight(1f), enabled = galleryInstalled, onClick = onOpenSamsungGallery)
        }
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionTile(Icons.Default.Image, "Open Google Photos", Modifier.weight(1f), enabled = photosInstalled, onClick = onOpenGooglePhotos)
            ActionTile(Icons.Default.Refresh, "Refresh Media Check", Modifier.weight(1f), enabled = true, onClick = onRefreshMediaCheck)
        }
    }
}

@Composable
private fun ActionTile(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val bg = if (enabled) SurfaceTint else SurfaceElevated
    val tint = if (enabled) CyanPrimary else OnSurfaceMuted
    val text = if (enabled) OnSurface else OnSurfaceMuted
    Surface(
        modifier = if (enabled) modifier.clickable(onClick = onClick) else modifier,
        color = bg,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, BorderSubtle)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(icon, null, modifier = Modifier.size(20.dp), tint = tint)
            Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = text)
        }
    }
}

// =============================================================================
// Button & Wake Behavior
// =============================================================================

@Composable
private fun ButtonWakeBehaviorCard() {
    HomeCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.TouchApp, null, modifier = Modifier.size(18.dp), tint = CyanPrimary)
            Text("Button & Wake Behavior", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = OnSurface)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "The Hey Cyan glasses' physical button captures locally. The native Hey Cyan app receives the capture and transfers it to the phone over its own pairing.",
            fontSize = 12.sp, color = OnSurface
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Wake-word and direct Gemini-from-glasses are not yet confirmed. CyanGem2 does not intercept hardware buttons on the glasses.",
            fontSize = 11.sp, color = OnSurfaceMuted
        )
    }
}

// =============================================================================
// Warning banner
// =============================================================================

@Composable
private fun NoBridgeBanner() {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        color = WarningAmber.copy(alpha = 0.10f),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, WarningAmber.copy(alpha = 0.30f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Default.Warning, null, modifier = Modifier.size(20.dp), tint = WarningAmber)
            Text(
                "No direct in-app camera bridge yet — use the native Hey Cyan app for capture.",
                fontSize = 12.sp, color = OnSurface, modifier = Modifier.weight(1f)
            )
        }
    }
}

// =============================================================================
// LAST CAPTURE VISIBILITY  (the HC-015 headline)
// =============================================================================

@Composable
private fun LastCaptureVisibilityCard(
    queryResult: MediaQueryResult?,
    isLoading: Boolean,
    baselineSec: Long?,
    onRequestPermission: () -> Unit,
    onRefresh: () -> Unit,
    onResetBaseline: () -> Unit
) {
    HomeCard(borderColor = CyanPrimary.copy(alpha = 0.40f)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.Visibility, null, modifier = Modifier.size(18.dp), tint = CyanPrimary)
            Text(
                "Last Capture Visibility",
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
            "Did the glasses photo or video actually land on the phone? Tap Refresh after the glasses click.",
            fontSize = 11.sp, color = OnSurfaceMuted
        )
        Spacer(Modifier.height(10.dp))

        when (val r = queryResult) {
            null -> {
                Text("Loading…", fontSize = 12.sp, color = OnSurfaceMuted)
            }

            is MediaQueryResult.PermissionMissing -> {
                PermissionMissingBlock(onRequestPermission)
            }

            is MediaQueryResult.Empty -> {
                EmptyMediaBlock(onRefresh)
            }

            is MediaQueryResult.Error -> {
                ErrorBlock(detail = r.message, onRetry = onRefresh)
            }

            is MediaQueryResult.Items -> {
                val newest = r.list.first()
                val isNewSinceBaseline = baselineSec != null && newest.dateAddedSeconds > baselineSec
                ItemsBlock(
                    newest = newest,
                    isNewSinceBaseline = isNewSinceBaseline,
                    baselineSec = baselineSec,
                    onRefresh = onRefresh,
                    onResetBaseline = onResetBaseline
                )
            }
        }
    }
}

@Composable
private fun PermissionMissingBlock(onRequestPermission: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = WarningAmber.copy(alpha = 0.10f),
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, WarningAmber.copy(alpha = 0.30f))
        ) {
            Row(
                modifier = Modifier.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Lock, null, modifier = Modifier.size(18.dp), tint = WarningAmber)
                Text(
                    "Photo access is required to detect new captures.",
                    fontSize = 12.sp, color = OnSurface, modifier = Modifier.weight(1f)
                )
            }
        }
        Button(
            onClick = onRequestPermission,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary, contentColor = Color.White)
        ) {
            Icon(Icons.Default.LockOpen, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Grant Photo Access", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun EmptyMediaBlock(onRefresh: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "No photos or videos found on this phone yet.",
            fontSize = 13.sp, color = OnSurface
        )
        Text(
            "When the glasses capture and the native Hey Cyan app transfers a file to the phone, it should appear here.",
            fontSize = 11.sp, color = OnSurfaceMuted
        )
        OutlinedButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Refresh")
        }
    }
}

@Composable
private fun ErrorBlock(detail: String, onRetry: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = ErrorColor.copy(alpha = 0.08f),
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, ErrorColor.copy(alpha = 0.30f))
        ) {
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Could not read media.", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ErrorColor)
                Text(detail, fontSize = 11.sp, color = OnSurfaceMuted)
            }
        }
        OutlinedButton(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Retry")
        }
    }
}

@Composable
private fun ItemsBlock(
    newest: RecentMedia,
    isNewSinceBaseline: Boolean,
    baselineSec: Long?,
    onRefresh: () -> Unit,
    onResetBaseline: () -> Unit
) {
    val statusColor = if (isNewSinceBaseline) SuccessColor else OnSurfaceMuted
    val statusLabel = if (isNewSinceBaseline) "New capture detected" else "Already seen"
    val statusIcon = if (isNewSinceBaseline) Icons.Default.CheckCircle else Icons.Default.History

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = statusColor.copy(alpha = 0.10f),
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, statusColor.copy(alpha = 0.30f))
        ) {
            Row(
                modifier = Modifier.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(statusIcon, null, modifier = Modifier.size(18.dp), tint = statusColor)
                Text(statusLabel, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = statusColor, modifier = Modifier.weight(1f))
            }
        }

        // Newest item details
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                "Latest media on phone",
                fontSize = 11.sp, color = OnSurfaceMuted, fontWeight = FontWeight.Bold
            )
            Text(
                newest.displayName ?: "(unnamed)",
                fontSize = 12.sp, color = OnSurface
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetaPill(if (newest.type == MediaType.Image) "Image" else "Video")
                MetaPill(formatMediaTimestamp(newest.dateAddedSeconds))
                newest.mimeType?.let { MetaPill(it) }
            }
        }

        if (!isNewSinceBaseline && baselineSec != null) {
            Text(
                "This file is from before this session. Tap Refresh after taking a photo with the glasses, or Reset Baseline to start fresh from now.",
                fontSize = 11.sp, color = OnSurfaceMuted
            )
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onRefresh,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary, contentColor = Color.White)
            ) {
                Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Refresh", fontWeight = FontWeight.Bold)
            }
            OutlinedButton(onClick = onResetBaseline, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.RestartAlt, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Reset Baseline")
            }
        }
    }
}

@Composable
private fun MetaPill(text: String) {
    Surface(
        color = SurfaceElevated,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(0.5.dp, BorderSubtle)
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            fontSize = 10.sp,
            color = OnSurface
        )
    }
}
