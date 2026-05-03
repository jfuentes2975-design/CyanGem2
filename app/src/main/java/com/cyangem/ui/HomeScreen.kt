package com.cyangem.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

// =============================================================================
// HC-015 — Home dashboard, light theme.
//
// Same five-card layout as HC-014 but restyled for the approved Product
// Design palette:
//   - White / pale-blue background
//   - White cards with subtle borders
//   - Cyan as primary accent (deeper than HC-014's neon)
//   - GeminiPurple accent on the Gemini Live Daily Mode card
//   - WarningAmber on Needs Check / amber badges
//   - Green only on Connected / Passed pills
//
// Layout matches HC-014:
//   Header  → title, ConnectionChip, battery/audio line
//   Card 1  → Hey Cyan Glasses status
//   Card 2  → Gemini Live Daily Mode (compact 6-step strip — purple accent)
//   Card 3  → Quick Actions (2x2 tile grid)
//   Card 4  → Recent Test Results
//   Card 5  → Next Recommended Test (cyan accent border, primary button)
// =============================================================================

@Composable
fun HomeScreen(onNavigateToGlasses: () -> Unit) {
    val context = LocalContext.current
    val btStatus by rememberBluetoothStatus(context)

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // LazyColumn item indices for animateScrollToItem.
    val INDEX_CARD_RECENT = 4

    Box(modifier = Modifier.fillMaxSize().background(BackgroundLight)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp)
        ) {
            item { HomeHeader(btStatus) }
            item { GlassesStatusCard(btStatus) }
            item { DailyModeCard() }
            item {
                QuickActionsCard(
                    onOpenGemini = {
                        val ok = openGeminiApp(context)
                        if (!ok) coroutineScope.launchSnackbar(
                            snackbarHostState,
                            "Could not open Gemini. Check the Gemini or Google app."
                        )
                    },
                    onOpenHeyCyan = {
                        val ok = openHeyCyanApp(context)
                        if (!ok) coroutineScope.launchSnackbar(
                            snackbarHostState,
                            "Hey Cyan app not detected. Open it from your app drawer if installed."
                        )
                    },
                    onOpenBluetoothSettings = {
                        val ok = openBluetoothSettings(context)
                        if (!ok) coroutineScope.launchSnackbar(
                            snackbarHostState,
                            "Could not open Bluetooth settings."
                        )
                    },
                    onTestLog = {
                        coroutineScope.launch {
                            listState.animateScrollToItem(INDEX_CARD_RECENT)
                        }
                    }
                )
            }
            item { RecentTestResultsCard() }
            item { NextRecommendedTestCard(onGoToGlasses = onNavigateToGlasses) }
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
            "Battery: — • Audio: check OS media output for Hey Cyan",
            fontSize = 11.sp,
            color = OnSurfaceMuted
        )
    }
}

@Composable
private fun ConnectionChip(btStatus: BtAdapterStatus) {
    val (label, color) = when (btStatus) {
        BtAdapterStatus.On -> "Connected" to SuccessColor
        BtAdapterStatus.Off -> "Needs Check" to WarningAmber
        BtAdapterStatus.Unsupported,
        BtAdapterStatus.PermissionMissing -> "Unknown" to OnSurfaceMuted
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
// Card 1 — Hey Cyan Glasses status
// =============================================================================

@Composable
private fun GlassesStatusCard(btStatus: BtAdapterStatus) {
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
                val statusText = when (btStatus) {
                    BtAdapterStatus.On -> "Connected"
                    BtAdapterStatus.Off -> "Check Bluetooth"
                    BtAdapterStatus.Unsupported,
                    BtAdapterStatus.PermissionMissing -> "Status unavailable"
                }
                Text(statusText, fontSize = 12.sp, color = OnSurfaceMuted)
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricPill(label = "Battery", value = "—")
            MetricPill(label = "Audio", value = if (btStatus == BtAdapterStatus.On) "To Glasses" else "Check Route")
        }
    }
}

@Composable
private fun MetricPill(label: String, value: String) {
    Surface(
        color = SurfaceElevated,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(0.5.dp, BorderSubtle)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("$label:", fontSize = 11.sp, color = OnSurfaceMuted)
            Text(value, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = OnSurface)
        }
    }
}

// =============================================================================
// Card 2 — Gemini Live Daily Mode (compact 6-step strip, GEMINI PURPLE accent)
// =============================================================================

private data class DailyStep(val number: Int, val shortLabel: String)

private val DAILY_STEPS = listOf(
    DailyStep(1, "Pair"),
    DailyStep(2, "Audio"),
    DailyStep(3, "Live"),
    DailyStep(4, "Range"),
    DailyStep(5, "Camera"),
    DailyStep(6, "Wrap")
)

@Composable
private fun DailyModeCard() {
    HomeCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier.size(28.dp).background(GeminiPurple.copy(alpha = 0.14f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(16.dp), tint = GeminiPurple)
            }
            Text("Gemini Live Daily Mode", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = OnSurface)
        }
        Spacer(Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            DAILY_STEPS.forEachIndexed { index, step ->
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier.size(28.dp).background(GeminiPurple.copy(alpha = 0.14f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "${step.number}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = GeminiPurple
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        step.shortLabel,
                        fontSize = 9.sp,
                        color = OnSurfaceMuted,
                        fontWeight = FontWeight.Medium
                    )
                }
                if (index < DAILY_STEPS.size - 1) {
                    Box(
                        modifier = Modifier
                            .padding(top = 13.dp)
                            .height(2.dp)
                            .width(8.dp)
                            .background(GeminiPurple.copy(alpha = 0.30f))
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Pair → Audio → Live → Range → Camera → Wrap Up",
            fontSize = 11.sp,
            color = OnSurfaceMuted
        )
    }
}

// =============================================================================
// Card 3 — Quick Actions (2x2 tiles)
// =============================================================================

@Composable
private fun QuickActionsCard(
    onOpenGemini: () -> Unit,
    onOpenHeyCyan: () -> Unit,
    onOpenBluetoothSettings: () -> Unit,
    onTestLog: () -> Unit
) {
    HomeCard {
        Text("Quick Actions", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = OnSurface)
        Spacer(Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QuickActionTile(Icons.Default.GraphicEq, "Open Gemini", Modifier.weight(1f), onOpenGemini)
            QuickActionTile(Icons.Default.Smartphone, "Open Hey Cyan", Modifier.weight(1f), onOpenHeyCyan)
        }
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QuickActionTile(Icons.Default.Bluetooth, "Bluetooth Settings", Modifier.weight(1f), onOpenBluetoothSettings)
            QuickActionTile(Icons.Default.NoteAlt, "Test Log", Modifier.weight(1f), onTestLog)
        }
    }
}

@Composable
private fun QuickActionTile(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.clickable { onClick() },
        color = SurfaceTint,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, BorderSubtle)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(icon, null, modifier = Modifier.size(22.dp), tint = CyanPrimary)
            Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = OnSurface)
        }
    }
}

// =============================================================================
// Card 4 — Recent Test Results
// =============================================================================

private enum class ResultBadge { Passed, NotAvailable, Failed }
private data class TestResult(val label: String, val badge: ResultBadge)

private val SEEDED_RESULTS = listOf(
    TestResult("Gemini Live range test", ResultBadge.Passed),
    TestResult("Native Hey Cyan app works", ResultBadge.Passed),
    TestResult("Photo bridge", ResultBadge.NotAvailable)
)

@Composable
private fun RecentTestResultsCard() {
    HomeCard {
        Text("Recent Test Results", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = OnSurface)
        Spacer(Modifier.height(10.dp))
        SEEDED_RESULTS.forEachIndexed { i, row ->
            TestResultRow(row)
            if (i < SEEDED_RESULTS.size - 1) {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = BorderSubtle, thickness = 0.5.dp)
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun TestResultRow(row: TestResult) {
    val (badgeText, color) = when (row.badge) {
        ResultBadge.Passed -> "Passed" to SuccessColor
        ResultBadge.NotAvailable -> "Not Available" to WarningAmber
        ResultBadge.Failed -> "Failed" to ErrorColor
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(row.label, fontSize = 13.sp, color = OnSurface, modifier = Modifier.weight(1f))
        StatusPill(badgeText, color)
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

// =============================================================================
// Card 5 — Next Recommended Test
// =============================================================================

@Composable
private fun NextRecommendedTestCard(onGoToGlasses: () -> Unit) {
    HomeCard(borderColor = CyanPrimary.copy(alpha = 0.40f)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.Flag, null, modifier = Modifier.size(18.dp), tint = CyanPrimary)
            Text("Next Recommended Test", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = OnSurface)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Validate camera preview and capture using the native Hey Cyan app, and confirm the photo lands on the phone.",
            fontSize = 13.sp,
            color = OnSurface
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onGoToGlasses,
            colors = ButtonDefaults.buttonColors(
                containerColor = CyanPrimary,
                contentColor = Color.White
            )
        ) {
            Icon(Icons.Default.PhotoCamera, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Go to Glasses / Camera", fontWeight = FontWeight.Bold)
        }
    }
}

// =============================================================================
// Shared card chrome — used by Home AND by other screens (internal)
// =============================================================================

@Composable
internal fun HomeCard(
    borderColor: Color? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val baseModifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp)
    Surface(
        modifier = baseModifier,
        color = SurfaceCard,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, borderColor ?: BorderSubtle)
    ) {
        Column(modifier = Modifier.padding(14.dp), content = content)
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
