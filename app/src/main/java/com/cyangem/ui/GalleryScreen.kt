package com.cyangem.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
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

// =============================================================================
// HC-015 — Gallery / Media screen.
//
// Real (no longer placeholder). Reads recent photos and videos from MediaStore.
//
// Sections:
//   - Header
//   - Filter chips (All / Photos / Videos / Recent — Recent = last 7 days)
//   - Recent media grid (3 columns, lazy)
//   - Empty / permission states
//   - Selected item detail card (filename, time, type, source)
//   - Action row: Open in Samsung Gallery / Share / Mark for Review
//   - Banner: Gemini analysis bridge not wired yet
//
// Permissions: requests READ_MEDIA_IMAGES + READ_MEDIA_VIDEO on Android 13+
// (or READ_EXTERNAL_STORAGE on older). Uses
// rememberLauncherForActivityResult — single tap from the empty state.
// =============================================================================

private enum class GalleryFilter { All, Photos, Videos, Recent }

@Composable
fun GalleryScreen() {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    var filter by remember { mutableStateOf(GalleryFilter.All) }
    var queryResult by remember { mutableStateOf<MediaQueryResult?>(null) }
    var refreshTrigger by remember { mutableIntStateOf(0) }
    var selectedItem by remember { mutableStateOf<RecentMedia?>(null) }
    var markedForReview by remember { mutableStateOf(setOf<String>()) }  // by uri.toString()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        refreshTrigger++
    }

    LaunchedEffect(refreshTrigger) {
        queryResult = if (!MediaStoreQuery.hasMediaPermission(context)) {
            MediaQueryResult.PermissionMissing
        } else {
            MediaStoreQuery.queryRecentMedia(context, limit = 60)
        }
    }

    val filteredItems: List<RecentMedia> = remember(filter, queryResult) {
        val items = (queryResult as? MediaQueryResult.Items)?.list.orEmpty()
        when (filter) {
            GalleryFilter.All -> items
            GalleryFilter.Photos -> items.filter { it.type == MediaType.Image }
            GalleryFilter.Videos -> items.filter { it.type == MediaType.Video }
            GalleryFilter.Recent -> {
                val sevenDaysAgo = System.currentTimeMillis() / 1000 - 7 * 24 * 60 * 60
                items.filter { it.dateAddedSeconds >= sevenDaysAgo }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(BackgroundLight)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    "Gallery / Media",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = OnSurface
                )
                Text(
                    "Recent photos and videos on this phone",
                    fontSize = 12.sp, color = OnSurfaceMuted
                )
            }

            // Filter chips
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                GalleryFilter.values().forEach { f ->
                    FilterChipLite(
                        label = f.displayName(),
                        selected = filter == f,
                        onClick = { filter = f }
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            // Body — grid OR state
            Box(modifier = Modifier.weight(1f)) {
                when (val r = queryResult) {
                    null -> CenteredText("Loading…")
                    is MediaQueryResult.PermissionMissing -> PermissionGate(
                        onRequestPermission = {
                            permissionLauncher.launch(MediaStoreQuery.requiredPermissions())
                        },
                        onOpenSystemGallery = {
                            val ok = openSystemGallery(context)
                            if (!ok) coroutineScope.launchSnackbar(snackbarHostState, "No gallery app found.")
                        }
                    )
                    is MediaQueryResult.Empty -> EmptyState(
                        title = "No photos or videos found",
                        detail = "When the glasses transfer media to the phone, it will appear here.",
                        onRefresh = { refreshTrigger++ }
                    )
                    is MediaQueryResult.Error -> ErrorState(detail = r.message, onRetry = { refreshTrigger++ })
                    is MediaQueryResult.Items -> {
                        if (filteredItems.isEmpty()) {
                            EmptyState(
                                title = "No items in this filter",
                                detail = "Try a different filter or refresh.",
                                onRefresh = { refreshTrigger++ }
                            )
                        } else {
                            MediaGrid(
                                items = filteredItems,
                                selectedUri = selectedItem?.uri?.toString(),
                                onSelect = { selectedItem = it }
                            )
                        }
                    }
                }
            }

            // Bottom: detail card + actions + banner
            selectedItem?.let { item ->
                SelectedDetailCard(item)
                ActionRow(
                    onOpenSamsungGallery = {
                        val ok = openSamsungGallery(context)
                        if (!ok) {
                            // Fallback to system gallery via ACTION_VIEW
                            val ok2 = openSystemGallery(context)
                            if (!ok2) coroutineScope.launchSnackbar(snackbarHostState, "No gallery app found.")
                        }
                    },
                    onShare = {
                        try {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = item.mimeType ?: if (item.type == MediaType.Image) "image/*" else "video/*"
                                putExtra(Intent.EXTRA_STREAM, item.uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share"))
                        } catch (e: Exception) {
                            coroutineScope.launchSnackbar(snackbarHostState, "Could not share: ${e.message ?: "unknown"}")
                        }
                    },
                    onMarkForReview = {
                        val key = item.uri.toString()
                        markedForReview = if (markedForReview.contains(key))
                            markedForReview - key else markedForReview + key
                        coroutineScope.launchSnackbar(
                            snackbarHostState,
                            if (markedForReview.contains(key)) "Marked for review" else "Unmarked"
                        )
                    },
                    isMarked = markedForReview.contains(item.uri.toString())
                )
            }
            AnalysisBanner()
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
}

@Composable
private fun FilterChipLite(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        color = if (selected) SurfaceTint else SurfaceCard,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, if (selected) CyanPrimary.copy(alpha = 0.40f) else BorderSubtle)
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = if (selected) CyanPrimary else OnSurfaceMuted
        )
    }
}

// =============================================================================
// Grid
// =============================================================================

@Composable
private fun MediaGrid(
    items: List<RecentMedia>,
    selectedUri: String?,
    onSelect: (RecentMedia) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(bottom = 8.dp)
    ) {
        items(items, key = { it.uri.toString() }) { media ->
            val isSelected = media.uri.toString() == selectedUri
            Box(
                modifier = Modifier
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(SurfaceElevated)
                    .clickable { onSelect(media) }
                    .then(
                        if (isSelected)
                            Modifier.background(CyanPrimary.copy(alpha = 0.15f))
                        else Modifier
                    )
            ) {
                AsyncImage(
                    model = media.uri,
                    contentDescription = media.displayName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp))
                )
                if (media.type == MediaType.Video) {
                    Surface(
                        modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
                        color = Color.Black.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Icon(Icons.Default.Videocam, null, modifier = Modifier.size(11.dp), tint = Color.White)
                        }
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
    onOpenSystemGallery: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(Icons.Default.Lock, null, modifier = Modifier.size(48.dp), tint = WarningAmber)
        Text(
            "Photo access required",
            fontSize = 16.sp, fontWeight = FontWeight.Bold, color = OnSurface
        )
        Text(
            "CyanGem2 needs read access to photos and videos to show recent media from your phone. The app does not modify or upload anything.",
            fontSize = 12.sp, color = OnSurfaceMuted
        )
        Button(
            onClick = onRequestPermission,
            colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary, contentColor = Color.White)
        ) {
            Icon(Icons.Default.LockOpen, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Grant Photo Access", fontWeight = FontWeight.Bold)
        }
        OutlinedButton(onClick = onOpenSystemGallery) {
            Icon(Icons.Default.PhotoLibrary, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Open Gallery in OS")
        }
    }
}

@Composable
private fun EmptyState(title: String, detail: String, onRefresh: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(Icons.Default.PhotoLibrary, null, modifier = Modifier.size(40.dp), tint = OnSurfaceMuted)
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = OnSurface)
        Text(detail, fontSize = 12.sp, color = OnSurfaceMuted)
        OutlinedButton(onClick = onRefresh) {
            Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Refresh")
        }
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
// Selected detail card
// =============================================================================

@Composable
private fun SelectedDetailCard(item: RecentMedia) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        color = SurfaceCard,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, CyanPrimary.copy(alpha = 0.30f))
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                item.displayName ?: "(unnamed)",
                fontSize = 13.sp, fontWeight = FontWeight.Bold, color = OnSurface
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetaPill(if (item.type == MediaType.Image) "Image" else "Video")
                MetaPill(formatMediaTimestamp(item.dateAddedSeconds))
                item.mimeType?.let { MetaPill(it) }
            }
            Text(
                "Source: phone media store",
                fontSize = 11.sp, color = OnSurfaceMuted
            )
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

// =============================================================================
// Action row + Banner
// =============================================================================

@Composable
private fun ActionRow(
    onOpenSamsungGallery: () -> Unit,
    onShare: () -> Unit,
    onMarkForReview: () -> Unit,
    isMarked: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedButton(onClick = onOpenSamsungGallery, modifier = Modifier.weight(1f)) {
            Icon(Icons.Default.OpenInNew, null, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text("Open in Gallery", fontSize = 11.sp)
        }
        OutlinedButton(onClick = onShare, modifier = Modifier.weight(1f)) {
            Icon(Icons.Default.Share, null, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text("Share", fontSize = 11.sp)
        }
        OutlinedButton(onClick = onMarkForReview, modifier = Modifier.weight(1f)) {
            Icon(
                if (isMarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                null,
                modifier = Modifier.size(14.dp),
                tint = if (isMarked) CyanPrimary else LocalContentColor.current
            )
            Spacer(Modifier.width(4.dp))
            Text(if (isMarked) "Marked" else "Mark for Review", fontSize = 11.sp)
        }
    }
}

@Composable
private fun AnalysisBanner() {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        color = WarningAmber.copy(alpha = 0.10f),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, WarningAmber.copy(alpha = 0.30f))
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Default.Info, null, modifier = Modifier.size(16.dp), tint = WarningAmber)
            Text(
                "Gemini analysis bridge not wired yet — manual review for now.",
                fontSize = 11.sp, color = OnSurface, modifier = Modifier.weight(1f)
            )
        }
    }
}
