package com.cyangem.ui

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.cyangem.ui.theme.*
import com.cyangem.viewmodel.MainViewModel

@Composable
fun GalleryScreen(vm: MainViewModel) {
    val uiState by vm.uiState.collectAsState()
    var expandedUri by remember { mutableStateOf<Uri?>(null) }

    // Load photos each time this screen is visited
    LaunchedEffect(Unit) {
        vm.loadGalleryPhotos()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ── Header ────────────────────────────────────────────────────────
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
                Text(
                    "Gallery",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.Bold,
                        brush = Brush.horizontalGradient(listOf(CyanPrimary, CyanSecondary))
                    )
                )
                Text(
                    if (uiState.galleryPhotos.isEmpty() && !uiState.isGalleryLoading)
                        "No photos yet — sync from your glasses to get started"
                    else
                        "${uiState.galleryPhotos.size} photo(s) from your glasses",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // ── Loading state ─────────────────────────────────────────────────
            if (uiState.isGalleryLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = CyanPrimary)
                        Spacer(Modifier.height(8.dp))
                        Text("Loading photos…", color = OnSurfaceMuted, fontSize = 13.sp)
                    }
                }
                return@Column
            }

            // ── Empty state ───────────────────────────────────────────────────
            if (uiState.galleryPhotos.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Icon(
                            Icons.Default.PhotoLibrary,
                            contentDescription = null,
                            tint = OnSurfaceMuted,
                            modifier = Modifier.size(64.dp)
                        )
                        Text(
                            "No photos yet",
                            fontWeight = FontWeight.SemiBold,
                            color = OnSurface,
                            fontSize = 18.sp
                        )
                        Text(
                            "Photos captured with your glasses will appear here after syncing. Go to the Glasses tab and tap Sync Media.",
                            color = OnSurfaceMuted,
                            fontSize = 13.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
                return@Column
            }

            // ── Photo grid ────────────────────────────────────────────────────
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(uiState.galleryPhotos) { uri ->
                    GalleryThumbnail(
                        uri = uri,
                        onClick = { expandedUri = uri }
                    )
                }
            }
        }

        // ── Full-screen viewer ────────────────────────────────────────────────
        expandedUri?.let { uri ->
            PhotoViewer(
                uri = uri,
                onDismiss = { expandedUri = null },
                onAskGemini = {
                    expandedUri = null
                    vm.analyzeGalleryPhoto(uri)
                    vm.showSnackbar("📷 Analyzing photo with Gemini…")
                }
            )
        }
    }
}

// ── Thumbnail ─────────────────────────────────────────────────────────────────

@Composable
private fun GalleryThumbnail(uri: Uri, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(2.dp))
            .clickable(onClick = onClick)
            .background(SurfaceCard)
    ) {
        AsyncImage(
            model = uri,
            contentDescription = "Photo",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }
}

// ── Full-screen viewer ────────────────────────────────────────────────────────

@Composable
private fun PhotoViewer(
    uri: Uri,
    onDismiss: () -> Unit,
    onAskGemini: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            // Full-size photo
            AsyncImage(
                model = uri,
                contentDescription = "Full size photo",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .align(Alignment.Center)
            )

            // Close button — top right
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .background(
                        Color.Black.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(50)
                    )
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Close",
                    tint = Color.White
                )
            }

            // Ask Gemini button — bottom center
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                        )
                    )
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Button(
                    onClick = onAskGemini,
                    colors = ButtonDefaults.buttonColors(containerColor = CyanSecondary),
                    shape = RoundedCornerShape(50)
                ) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Ask Gemini about this",
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
