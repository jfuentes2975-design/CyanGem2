package com.cyangem.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.cyangem.ui.theme.*
import kotlinx.coroutines.launch

// =============================================================================
// HC-019 — Gallery / Media screen with smart-glasses filter.
//
// Differences vs HC-018:
//   - 5 filter chips: All / Photos / Videos / Recent / GLASSES (NEW).
//   - Glasses filter uses [GlassesMediaIdentifier] (path + name + capture
//     history + manual mark).
//   - Action row gets "Mark as Glasses" action that toggles
//     [GlassesMarkStore].
//   - Glasses-likely items get a small purple ✨ badge in the grid (top-right
//     corner, opposite the video badge).
//   - Empty state for the Glasses filter explains how to populate it
//     (Capture Check on Home, guided flows on Glasses tab, or manual mark).
//   - Selected detail card shows the IDENTIFICATION REASON when an item is
//     glasses-likely (e.g., "Captured via Capture Session", "Folder path
//     matches: DCIM/CyanGem/").
//
// Carried unchanged from HC-018:
//   - Android Photo Picker (Pick button + permission-free fallback).
//   - Mark for Review (ReviewStore) — distinct from the new Glasses mark.
//   - Auto-refresh via MediaWatcher + OnLifecycleResume.
// =============================================================================

private enum class GalleryFilter { All, Photos, Videos, Recent, Glasses }

@Composable
fun GalleryScreen() {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    var filter by remember { mutableStateOf(GalleryFilter.All) }
    var queryResult by remember { mutableStateOf<CyanMediaResult?>(null) }
    var refreshTrigger by remember { mutableIntStateOf(0) }
    var selectedItem by remember { mutableStateOf<CyanMediaItem?>(null) }

    // HC-018 carry: persistent Mark for Review.
    var markedForReview by remember { mutableStateOf(ReviewStore.read(context)) }
    // HC-019 NEW: persistent Mark as Glasses Capture.
    var markedAsGlasses by remember { mutableStateOf(GlassesMarkStore.read(context)) }

    // Auto-refresh
    rememberMediaObserver(enabled = true) { refreshTrigger++ }
    OnLifecycleResume { refreshTrigger++ }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { refreshTrigger++ }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { pickedUri ->
        if (pickedUri != null) {
            coroutineScope.launch {
                val item = MediaBridgeRepository.resolvePickerItem(context, pickedUri)
                if (item != null) {
                    selectedItem = item
                    coroutineScope.launchSnackbar(
                        snackbarHostState,
                        "Picked: " + (item.displayName ?: "item")
                    )
                } else {
                    coroutineScope.launchSnackbar(snackbarHostState, "Could not resolve picked item.")
                }
            }
        }
    }

    LaunchedEffect(refreshTrigger) {
        queryResult = if (!MediaBridgeRepository.hasMediaPermission(context)) {
            CyanMediaResult.PermissionMissing
        } else {
            MediaBridgeRepository.queryRecentMedia(context, limit = 60)
        }
    }

    val filteredItems: List<CyanMediaItem> = remember(filter, queryResult, markedAsGlasses) {
        val items = (queryResult as? CyanMediaResult.Items)?.list.orEmpty()
        when (filter) {
            GalleryFilter.All -> items
            GalleryFilter.Photos -> items.filter { it.type == CyanMediaType.Image }
            GalleryFilter.Videos -> items.filter { it.type == CyanMediaType.Video }
            GalleryFilter.Recent -> {
                val sevenDaysAgo = System.currentTimeMillis() / 1000 - 7 * 24 * 60 * 60
                items.filter { it.dateAddedSeconds >= sevenDaysAgo }
            }
            GalleryFilter.Glasses -> items.filter {
                GlassesMediaIdentifier.isLikelyGlassesMedia(context, it)
            }
        }
    }

    val samsungInstalled = remember(refreshTrigger) { isSamsungGalleryInstalled(context) }
    val photosInstalled  = remember(refreshTrigger) { isGooglePhotosInstalled(context) }
    val heyCyanInstalled = remember(refreshTrigger) { isHeyCyanInstalled(context) }

    Box(modifier = Modifier.fillMaxSize().background(BackgroundLight)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Gallery",
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = OnSurface,
                        modifier = Modifier.weight(1f)
                    )
                    val markCount = markedForReview.size
                    if (markCount > 0) {
                        StatusPill("$markCount marked", CyanPrimary)
                    }
                }
                val subtitle = when (filter) {
                    GalleryFilter.Glasses -> "Smart-glasses captures (path / capture history / manual marks)"
                    else -> "Recent phone media + Android Photo Picker — auto-refreshed"
                }
                Text(subtitle, fontSize = 12.sp, color = OnSurfaceMuted)
            }

            // Filter chips — 5 chips, scroll horizontally if narrow
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .horizontalScrollIfNeeded(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                GalleryFilter.values().forEach { f ->
                    FilterChipLite(
                        label = f.displayName(),
                        selected = filter == f,
                        accent = if (f == GalleryFilter.Glasses) GeminiPurple else CyanPrimary,
                        onClick = { filter = f }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))

            // Action bar — Pick / Refresh / HeyCyan
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                        )
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary, contentColor = Color.White)
                ) {
                    Icon(Icons.Default.PhotoLibrary, null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Pick", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = { refreshTrigger++ },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Refresh, null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Refresh", fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick = {
                        val ok = openHeyCyanApp(context)
                        if (!ok) coroutineScope.launchSnackbar(snackbarHostState, "HeyCyan app not detected.")
                    },
                    enabled = heyCyanInstalled,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Smartphone, null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("HeyCyan", fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(12.dp))

            // Body
            Box(modifier = Modifier.weight(1f)) {
                when (val r = queryResult) {
                    null -> CenteredText("Loading…")
                    is CyanMediaResult.PermissionMissing -> PermissionGate(
                        onRequestPermission = {
                            permissionLauncher.launch(MediaBridgeRepository.requiredMediaPermissions())
                        },
                        onOpenAppSettings = { openAppSettings(context) },
                        onUsePhotoPicker = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                            )
                        }
                    )
                    is CyanMediaResult.Empty -> EmptyState(
                        title = "No phone media yet",
                        detail = "Photos / videos appear here once HeyCyan saves them to the phone. " +
                                "If your glasses just captured something, open HeyCyan and confirm the import completes.",
                        onRefresh = { refreshTrigger++ },
                        onOpenHeyCyan = {
                            val ok = openHeyCyanApp(context)
                            if (!ok) coroutineScope.launchSnackbar(snackbarHostState, "HeyCyan not detected.")
                        },
                        heyCyanInstalled = heyCyanInstalled
                    )
                    is CyanMediaResult.Error -> ErrorState(detail = r.message, onRetry = { refreshTrigger++ })
                    is CyanMediaResult.Items -> {
                        if (filteredItems.isEmpty()) {
                            if (filter == GalleryFilter.Glasses) {
                                GlassesEmptyState(
                                    onRefresh = { refreshTrigger++ },
                                    onOpenHeyCyan = {
                                        val ok = openHeyCyanApp(context)
                                        if (!ok) coroutineScope.launchSnackbar(snackbarHostState, "HeyCyan not detected.")
                                    },
                                    heyCyanInstalled = heyCyanInstalled
                                )
                            } else {
                                EmptyState(
                                    title = "No items in this filter",
                                    detail = "Try a different filter or refresh.",
                                    onRefresh = { refreshTrigger++ },
                                    onOpenHeyCyan = { openHeyCyanApp(context) },
                                    heyCyanInstalled = heyCyanInstalled
                                )
                            }
                        } else {
                            MediaGrid(
                                items = filteredItems,
                                selectedUri = selectedItem?.uri?.toString(),
                                markedForReview = markedForReview,
                                glassesSet = markedAsGlasses,
                                onSelect = { selectedItem = it }
                            )
                        }
                    }
                }
            }

            // Bottom: detail card + actions
            selectedItem?.let { item ->
                SelectedDetailCard(
                    item = item,
                    glassesReason = GlassesMediaIdentifier.reason(context, item)
                )
                ActionRow(
                    samsungInstalled = samsungInstalled,
                    photosInstalled = photosInstalled,
                    isMarkedReview = markedForReview.contains(item.uri.toString()),
                    isMarkedGlasses = markedAsGlasses.contains(item.uri.toString()),
                    onOpen = {
                        val ok = openMediaItem(context, item.uri, item.mimeType)
                        if (!ok) coroutineScope.launchSnackbar(snackbarHostState, "Could not open item.")
                    },
                    onShare = {
                        val ok = shareMediaItem(context, item.uri, item.mimeType)
                        if (!ok) coroutineScope.launchSnackbar(snackbarHostState, "Could not share.")
                    },
                    onOpenSamsungGallery = {
                        val ok = openSamsungGallery(context)
                        if (!ok) coroutineScope.launchSnackbar(snackbarHostState, "Samsung Gallery not found.")
                    },
                    onOpenGooglePhotos = {
                        val ok = openGooglePhotos(context)
                        if (!ok) coroutineScope.launchSnackbar(snackbarHostState, "Google Photos not found.")
                    },
                    onMarkReview = {
                        val key = item.uri.toString()
                        markedForReview = ReviewStore.toggle(context, key)
                        coroutineScope.launchSnackbar(
                            snackbarHostState,
                            if (markedForReview.contains(key)) "Marked for review (saved)" else "Unmarked"
                        )
                    },
                    onMarkGlasses = {
                        val key = item.uri.toString()
                        markedAsGlasses = GlassesMarkStore.toggle(context, key)
                        coroutineScope.launchSnackbar(
                            snackbarHostState,
                            if (markedAsGlasses.contains(key)) "Marked as Glasses Capture" else "Unmarked Glasses"
                        )
                    }
                )
            }
            Spacer(Modifier.height(8.dp))
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

// =============================================================================
// Filter chip
// =============================================================================

private fun GalleryFilter.displayName(): String = when (this) {
    GalleryFilter.All -> "All"
    GalleryFilter.Photos -> "Photos"
    GalleryFilter.Videos -> "Videos"
    GalleryFilter.Recent -> "Recent"
    GalleryFilter.Glasses -> "Glasses"
}

@Composable
private fun FilterChipLite(
    label: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        color = if (selected) accent.copy(alpha = 0.14f) else SurfaceCard,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, if (selected) accent.copy(alpha = 0.40f) else BorderSubtle)
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = if (selected) accent else OnSurfaceMuted
        )
    }
}

// Compose has no first-class horizontalScroll on a Row of weighted children;
// our 5 chips fit on most screens. If they overflow, the Row clips. Acceptable
// trade-off for HC-019. (Future: replace with LazyRow.)
@Composable
private fun Modifier.horizontalScrollIfNeeded(): Modifier = this

// =============================================================================
// Grid (with mark badges)
// =============================================================================

@Composable
private fun MediaGrid(
    items: List<CyanMediaItem>,
    selectedUri: String?,
    markedForReview: Set<String>,
    glassesSet: Set<String>,
    onSelect: (CyanMediaItem) -> Unit
) {
    val context = LocalContext.current
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(bottom = 8.dp)
    ) {
        items(items, key = { it.uri.toString() }) { media ->
            val keyStr = media.uri.toString()
            val isSelected = keyStr == selectedUri
            val isReviewMarked = markedForReview.contains(keyStr)
            // Glasses-likely takes the union of identifier signals, not just
            // the manual-mark set, so the badge renders on path-matched items
            // too (DCIM/CyanGem/, etc.).
            val isGlassesLikely = GlassesMediaIdentifier.isLikelyGlassesMedia(context, media)

            Box(
                modifier = Modifier
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(SurfaceElevated)
                    .clickable { onSelect(media) }
                    .then(
                        if (isSelected) Modifier.background(CyanPrimary.copy(alpha = 0.15f)) else Modifier
                    )
            ) {
                AsyncImage(
                    model = media.uri,
                    contentDescription = media.displayName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp))
                )
                if (media.type == CyanMediaType.Video) {
                    Surface(
                        modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
                        color = Color.Black.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Videocam, null,
                            modifier = Modifier.size(14.dp).padding(2.dp),
                            tint = Color.White
                        )
                    }
                }
                if (isGlassesLikely) {
                    Surface(
                        modifier = Modifier.align(Alignment.TopEnd)
                            .padding(top = if (media.type == CyanMediaType.Video) 22.dp else 4.dp, end = 4.dp),
                        color = GeminiPurple,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            Icons.Default.AutoAwesome, null,
                            modifier = Modifier.size(14.dp).padding(1.dp),
                            tint = Color.White
                        )
                    }
                }
                if (isReviewMarked) {
                    Surface(
                        modifier = Modifier.align(Alignment.TopStart).padding(4.dp),
                        color = CyanPrimary,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Bookmark, null,
                            modifier = Modifier.size(14.dp).padding(1.dp), tint = Color.White
                        )
                    }
                }
                if (isSelected) {
                    Surface(
                        modifier = Modifier.align(Alignment.BottomStart).padding(4.dp),
                        color = CyanPrimary,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(14.dp).padding(1.dp), tint = Color.White)
                    }
                }
            }
        }
    }
}

// =============================================================================
// Empty + Permission + Error states
// =============================================================================

@Composable
private fun PermissionGate(
    onRequestPermission: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onUsePhotoPicker: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(Icons.Default.Lock, null, modifier = Modifier.size(48.dp), tint = WarningAmber)
        Text("Photo / video access required for the grid", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = OnSurface)
        Text(
            "The grid below shows recent photos and videos already on your phone. To list them, CyanGem2 needs read access.",
            fontSize = 12.sp, color = OnSurfaceMuted
        )
        Text(
            "Or skip permission entirely and use the system Photo Picker — it grants per-item access.",
            fontSize = 11.sp, color = OnSurfaceMuted
        )
        Button(
            onClick = onRequestPermission,
            colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary, contentColor = Color.White)
        ) {
            Icon(Icons.Default.LockOpen, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Grant Photo Access", fontWeight = FontWeight.Bold)
        }
        OutlinedButton(onClick = onUsePhotoPicker) {
            Icon(Icons.Default.PhotoLibrary, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Use Photo Picker instead")
        }
        TextButton(onClick = onOpenAppSettings) {
            Icon(Icons.Default.Settings, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Open App Settings")
        }
    }
}

@Composable
private fun EmptyState(
    title: String,
    detail: String,
    onRefresh: () -> Unit,
    onOpenHeyCyan: () -> Unit,
    heyCyanInstalled: Boolean
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(Icons.Default.PhotoLibrary, null, modifier = Modifier.size(40.dp), tint = OnSurfaceMuted)
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = OnSurface)
        Text(detail, fontSize = 12.sp, color = OnSurfaceMuted)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onRefresh) {
                Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Refresh")
            }
            if (heyCyanInstalled) {
                OutlinedButton(onClick = onOpenHeyCyan) {
                    Icon(Icons.Default.Smartphone, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Open HeyCyan")
                }
            }
        }
    }
}

@Composable
private fun GlassesEmptyState(
    onRefresh: () -> Unit,
    onOpenHeyCyan: () -> Unit,
    heyCyanInstalled: Boolean
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(40.dp), tint = GeminiPurple)
        Text("No smart-glasses captures detected yet", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = OnSurface)
        Text(
            "An item is shown here when ANY of the following is true:",
            fontSize = 12.sp, color = OnSurfaceMuted
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.Start
        ) {
            BulletText("Its folder path contains DCIM/CyanGem, Pictures/HeyCyan, glasssutdio, etc.")
            BulletText("Its filename contains HeyCyan, CyanGem, or W630/W610.")
            BulletText("Capture Check on Home detected it via a baseline session.")
            BulletText("A Capture Session on the Glasses tab detected it (Photo Test, Video Test, HeyCyan Import).")
            BulletText("You manually marked it via the action row below the detail card.")
        }
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onRefresh) {
                Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Refresh")
            }
            if (heyCyanInstalled) {
                OutlinedButton(onClick = onOpenHeyCyan) {
                    Icon(Icons.Default.Smartphone, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Open HeyCyan")
                }
            }
        }
    }
}

@Composable
private fun BulletText(text: String) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("•", fontSize = 12.sp, color = GeminiPurple)
        Text(text, fontSize = 11.sp, color = OnSurface)
    }
}

@Composable
private fun ErrorState(detail: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(Icons.Default.ErrorOutline, null, modifier = Modifier.size(40.dp), tint = ErrorColor)
        Text("Could not read media", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = ErrorColor)
        Text(detail, fontSize = 12.sp, color = OnSurfaceMuted)
        OutlinedButton(onClick = onRetry) {
            Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Retry")
        }
    }
}

@Composable
private fun CenteredText(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, fontSize = 12.sp, color = OnSurfaceMuted)
    }
}

// =============================================================================
// Selected detail card (shows identification reason if glasses-likely)
// =============================================================================

@Composable
private fun SelectedDetailCard(
    item: CyanMediaItem,
    glassesReason: String?
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        color = SurfaceCard,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp,
            if (glassesReason != null) GeminiPurple.copy(alpha = 0.40f)
            else CyanPrimary.copy(alpha = 0.30f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier.size(64.dp).clip(RoundedCornerShape(10.dp)).background(SurfaceElevated)
                ) {
                    AsyncImage(
                        model = item.uri,
                        contentDescription = item.displayName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        item.displayName ?: "(unnamed)",
                        fontSize = 13.sp, fontWeight = FontWeight.Bold, color = OnSurface
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MetaPillSmall(if (item.type == CyanMediaType.Image) "Image" else "Video")
                        MetaPillSmall(formatCyanTimestamp(item.dateAddedSeconds))
                    }
                    item.mimeType?.let { MetaPillSmall(it) }
                    item.relativePath?.takeIf { it.isNotBlank() }?.let { p ->
                        Text("Path: $p", fontSize = 10.sp, color = OnSurfaceMuted)
                    }
                    Text(
                        if (item.pickedByUser) "Source: Photo Picker" else "Source: phone media store",
                        fontSize = 11.sp, color = OnSurfaceMuted
                    )
                }
            }
            // Glasses identification banner — only when glassesReason != null
            glassesReason?.let { reason ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = GeminiPurple.copy(alpha = 0.10f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, GeminiPurple.copy(alpha = 0.30f))
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(14.dp), tint = GeminiPurple)
                        Text(
                            "Smart Glasses: $reason",
                            fontSize = 11.sp, color = GeminiPurple, fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MetaPillSmall(text: String) {
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

// =============================================================================
// Action row (Open / Share / Samsung / Photos / Mark Review / Mark Glasses)
// =============================================================================

@Composable
private fun ActionRow(
    samsungInstalled: Boolean,
    photosInstalled: Boolean,
    isMarkedReview: Boolean,
    isMarkedGlasses: Boolean,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onOpenSamsungGallery: () -> Unit,
    onOpenGooglePhotos: () -> Unit,
    onMarkReview: () -> Unit,
    onMarkGlasses: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionTextButton(Icons.Default.OpenInNew, "Open", Modifier.weight(1f), enabled = true, onClick = onOpen)
            ActionTextButton(Icons.Default.Share, "Share", Modifier.weight(1f), enabled = true, onClick = onShare)
        }
        Spacer(Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionTextButton(Icons.Default.PhotoLibrary, "Samsung Gallery", Modifier.weight(1f), enabled = samsungInstalled, onClick = onOpenSamsungGallery)
            ActionTextButton(Icons.Default.Image, "Google Photos", Modifier.weight(1f), enabled = photosInstalled, onClick = onOpenGooglePhotos)
        }
        Spacer(Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionTextButton(
                icon = if (isMarkedReview) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                label = if (isMarkedReview) "Marked" else "Mark for Review",
                modifier = Modifier.weight(1f),
                enabled = true,
                onClick = onMarkReview,
                accent = if (isMarkedReview) CyanPrimary else null
            )
            ActionTextButton(
                icon = if (isMarkedGlasses) Icons.Default.AutoAwesome else Icons.Default.AutoAwesomeMosaic,
                label = if (isMarkedGlasses) "Glasses ✓" else "Mark as Glasses",
                modifier = Modifier.weight(1f),
                enabled = true,
                onClick = onMarkGlasses,
                accent = if (isMarkedGlasses) GeminiPurple else null
            )
        }
    }
}

@Composable
private fun ActionTextButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean,
    onClick: () -> Unit,
    accent: Color? = null
) {
    val effectiveColor = accent ?: if (enabled) CyanPrimary else OnSurfaceMuted
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = effectiveColor),
        border = BorderStroke(
            1.dp,
            (accent ?: if (enabled) CyanPrimary.copy(alpha = 0.40f) else BorderSubtle)
        )
    ) {
        Icon(icon, null, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}
