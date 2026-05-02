package com.cyangem.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyangem.ui.theme.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// =============================================================================
// HC-013 — HomeScreen: stable Gemini Live support/control dashboard.
//
// Five sections per spec:
//   1. Gemini Live Daily Mode  — numbered usage steps
//   2. Confirmed Working       — status grid (works / flaky / not confirmed)
//   3. Shortcuts               — Open Gemini / Open Hey Cyan / Open Bluetooth
//                                Settings / Open Camera/Gallery (placeholder)
//   4. Camera / Video          — what works today + next goal + photo bridge
//                                marked "Not available yet"
//   5. Test Log                — pre-populated with confirmed test results;
//                                user can add new entries (ephemeral)
//
// No MainViewModel / engine / BLE / voice coupling. All state local. Designed
// to be the most stable surface possible after the HC-007..HC-012 churn.
// =============================================================================

@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val btStatus by rememberBluetoothStatus(context)

    // Test log entries seeded with the three known confirmed results from
    // Juan's pivot brief. Kept in a mutableStateList so user-added entries
    // stay in order (newest at top of user-added; seeded entries kept stable
    // at bottom).
    val seededLog = remember {
        listOf(
            TestLogEntry(
                id = "seed-1",
                text = "Test 1: Gemini Live through glasses with phone left behind — PASSED",
                timestamp = "Confirmed",
                outcome = TestOutcome.Passed
            ),
            TestLogEntry(
                id = "seed-2",
                text = "Test 2: Native Hey Cyan app works — PASSED",
                timestamp = "Confirmed",
                outcome = TestOutcome.Passed
            ),
            TestLogEntry(
                id = "seed-3",
                text = "Test 3: Photo bridge via CyanGem2 — NOT AVAILABLE YET",
                timestamp = "Future",
                outcome = TestOutcome.Pending
            )
        )
    }
    val userLog = remember { mutableStateListOf<TestLogEntry>() }
    var newEntryText by remember { mutableStateOf("") }

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            // ── Header ────────────────────────────────────────────────────
            Spacer(Modifier.height(16.dp))
            Text(
                "Hey Cyan",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = OnSurface,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Text(
                "Gemini Live + Hey Cyan glasses — companion controls",
                fontSize = 12.sp,
                color = OnSurfaceMuted,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            // Live BT chip in the header so it's always visible
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Bluetooth,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = OnSurfaceMuted
                )
                Text(
                    btStatus.displayLabel(),
                    fontSize = 11.sp,
                    color = OnSurfaceMuted
                )
            }
            Spacer(Modifier.height(16.dp))

            // ── 1. Gemini Live Daily Mode ────────────────────────────────
            HomeSection("Gemini Live Daily Mode") {
                NumberedStepsCard(
                    steps = listOf(
                        "Connect Hey Cyan glasses (OS Bluetooth)",
                        "Open Gemini",
                        "Start Gemini Live",
                        "Confirm audio is coming through the glasses",
                        "Put the phone nearby or in your pocket",
                        "Talk naturally — Gemini Live hears you through the glasses mic"
                    )
                )
            }

            // ── 2. Confirmed Working ─────────────────────────────────────
            HomeSection("Confirmed Working") {
                StatusGridCard(
                    items = listOf(
                        StatusItem("Bluetooth connection", StatusState.Works),
                        StatusItem("Music", StatusState.Works),
                        StatusItem("Calls", StatusState.Works),
                        StatusItem("Gemini Live audio", StatusState.Works),
                        StatusItem("Gemini Live hears through glasses mic", StatusState.Works),
                        StatusItem("Range / background test", StatusState.Works),
                        StatusItem("Volume controls", StatusState.Works),
                        StatusItem("Pause / play", StatusState.Flaky),
                        StatusItem("Direct Gemini wake from glasses", StatusState.NotConfirmed)
                    )
                )
            }

            // ── 3. Shortcuts ─────────────────────────────────────────────
            HomeSection("Shortcuts") {
                ShortcutsCard(
                    onOpenGemini = {
                        val ok = openGeminiApp(context)
                        if (!ok) coroutineScope.launchSnackbar(
                            snackbarHostState,
                            "Could not open Gemini. Check the Gemini or Google app is installed."
                        )
                    },
                    onOpenHeyCyan = {
                        val ok = openHeyCyanApp(context)
                        if (!ok) coroutineScope.launchSnackbar(
                            snackbarHostState,
                            "Hey Cyan app not detected. Open it from your app drawer if it's installed."
                        )
                    },
                    onOpenBluetoothSettings = {
                        val ok = openBluetoothSettings(context)
                        if (!ok) coroutineScope.launchSnackbar(
                            snackbarHostState,
                            "Could not open Bluetooth settings."
                        )
                    }
                )
            }

            // ── 4. Camera / Video ────────────────────────────────────────
            HomeSection("Camera / Video") {
                CameraVideoCard()
            }

            // ── 5. Test Log ─────────────────────────────────────────────
            HomeSection("Test Log") {
                TestLogCard(
                    seededEntries = seededLog,
                    userEntries = userLog,
                    newEntryText = newEntryText,
                    onNewEntryTextChange = { newEntryText = it },
                    onAdd = {
                        val text = newEntryText.trim()
                        if (text.isNotEmpty()) {
                            userLog.add(
                                0,
                                TestLogEntry(
                                    id = "user-${System.currentTimeMillis()}",
                                    text = text,
                                    timestamp = currentTimestamp(),
                                    outcome = TestOutcome.Note
                                )
                            )
                            newEntryText = ""
                        }
                    },
                    onClearUserEntries = { userLog.clear() }
                )
            }

            Spacer(Modifier.height(24.dp))
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

// =============================================================================
// Subcomponents
// =============================================================================

@Composable
private fun HomeSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.padding(bottom = 4.dp)) {
        Text(
            title.uppercase(),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = OnSurfaceMuted,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            letterSpacing = 1.sp
        )
        content()
    }
}

@Composable
private fun NumberedStepsCard(steps: List<String>) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        color = SurfaceElevated,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            steps.forEachIndexed { index, step ->
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        color = CyanPrimary.copy(alpha = 0.18f),
                        shape = RoundedCornerShape(50)
                    ) {
                        Text(
                            "${index + 1}",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 1.dp),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = CyanPrimary
                        )
                    }
                    Text(step, fontSize = 13.sp, color = OnSurface, modifier = Modifier.padding(top = 1.dp))
                }
            }
        }
    }
}

private enum class StatusState { Works, Flaky, NotConfirmed }
private data class StatusItem(val label: String, val state: StatusState)

@Composable
private fun StatusGridCard(items: List<StatusItem>) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        color = SurfaceElevated,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items.forEach { item ->
                StatusRow(item)
            }
        }
    }
}

@Composable
private fun StatusRow(item: StatusItem) {
    val (icon, color, badge) = when (item.state) {
        StatusState.Works -> Triple(Icons.Default.CheckCircle, SuccessColor, "Works")
        StatusState.Flaky -> Triple(Icons.Default.WarningAmber, Color(0xFFFFB300), "Flaky")
        StatusState.NotConfirmed -> Triple(Icons.Default.HelpOutline, OnSurfaceMuted, "Not confirmed")
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = color)
        Text(item.label, fontSize = 13.sp, color = OnSurface, modifier = Modifier.weight(1f))
        Surface(color = color.copy(alpha = 0.15f), shape = RoundedCornerShape(20.dp)) {
            Text(
                badge,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 1.dp),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}

@Composable
private fun ShortcutsCard(
    onOpenGemini: () -> Unit,
    onOpenHeyCyan: () -> Unit,
    onOpenBluetoothSettings: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        color = SurfaceElevated,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ShortcutButton(
                icon = Icons.Default.GraphicEq,
                label = "Open Gemini",
                onClick = onOpenGemini,
                enabled = true
            )
            ShortcutButton(
                icon = Icons.Default.Smartphone,
                label = "Open native Hey Cyan app",
                onClick = onOpenHeyCyan,
                enabled = true
            )
            ShortcutButton(
                icon = Icons.Default.Bluetooth,
                label = "Open Bluetooth settings",
                onClick = onOpenBluetoothSettings,
                enabled = true
            )
            ShortcutButton(
                icon = Icons.Default.PhotoCamera,
                label = "Open Camera / Gallery — coming later",
                onClick = { /* disabled */ },
                enabled = false
            )
        }
    }
}

@Composable
private fun ShortcutButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (enabled) CyanPrimary else Color(0xFF30363D),
            contentColor = if (enabled) Color(0xFF003731) else OnSurfaceMuted,
            disabledContainerColor = Color(0xFF30363D),
            disabledContentColor = OnSurfaceMuted
        )
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun CameraVideoCard() {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        color = SurfaceElevated,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(14.dp), tint = SuccessColor)
                Text(
                    "Native Hey Cyan app: working",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = OnSurface
                )
            }
            Text(
                "The native Hey Cyan app handles photo and video transfer from the glasses today. Use the shortcut above to launch it.",
                fontSize = 11.sp,
                color = OnSurfaceMuted
            )
            HorizontalDivider(color = Color(0xFF30363D), thickness = 0.5.dp)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.HourglassEmpty, contentDescription = null, modifier = Modifier.size(14.dp), tint = CyanPrimary)
                Text(
                    "Next goal",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = CyanPrimary
                )
            }
            Text(
                "Test camera preview and video behavior on the glasses through the native app. Confirm photos and videos transfer to the phone gallery as expected.",
                fontSize = 11.sp,
                color = OnSurface
            )
            HorizontalDivider(color = Color(0xFF30363D), thickness = 0.5.dp)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.Block, contentDescription = null, modifier = Modifier.size(14.dp), tint = OnSurfaceMuted)
                Text(
                    "Photo bridge in CyanGem2",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = OnSurfaceMuted
                )
            }
            Text(
                "Future feature — not available yet. CyanGem2 does not transfer photos or videos from the glasses today.",
                fontSize = 11.sp,
                color = OnSurfaceMuted
            )
        }
    }
}

private enum class TestOutcome { Passed, Failed, Pending, Note }
private data class TestLogEntry(
    val id: String,
    val text: String,
    val timestamp: String,
    val outcome: TestOutcome
)

@Composable
private fun TestLogCard(
    seededEntries: List<TestLogEntry>,
    userEntries: List<TestLogEntry>,
    newEntryText: String,
    onNewEntryTextChange: (String) -> Unit,
    onAdd: () -> Unit,
    onClearUserEntries: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        color = SurfaceCard,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, CyanPrimary.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Seeded entries — always visible, in fixed order
            seededEntries.forEach { entry -> TestLogRow(entry) }

            HorizontalDivider(color = Color(0xFF30363D), thickness = 0.5.dp)

            Text(
                "Add a session note (cleared when the app is closed):",
                fontSize = 11.sp,
                color = OnSurfaceMuted
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = newEntryText,
                    onValueChange = onNewEntryTextChange,
                    placeholder = { Text("Note (e.g. tested call routing)", color = OnSurfaceMuted) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Done
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyanPrimary,
                        unfocusedBorderColor = Color(0xFF30363D),
                        focusedTextColor = OnSurface,
                        unfocusedTextColor = OnSurface,
                        cursorColor = CyanPrimary
                    )
                )
                Button(
                    onClick = onAdd,
                    enabled = newEntryText.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CyanPrimary,
                        contentColor = Color(0xFF003731)
                    )
                ) { Text("Add", fontWeight = FontWeight.Bold) }
            }

            if (userEntries.isNotEmpty()) {
                userEntries.forEach { entry -> TestLogRow(entry) }
                TextButton(onClick = onClearUserEntries) {
                    Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Clear session notes")
                }
            }
        }
    }
}

@Composable
private fun TestLogRow(entry: TestLogEntry) {
    val (icon, color) = when (entry.outcome) {
        TestOutcome.Passed -> Icons.Default.CheckCircle to SuccessColor
        TestOutcome.Failed -> Icons.Default.Cancel to ErrorColor
        TestOutcome.Pending -> Icons.Default.HourglassEmpty to OnSurfaceMuted
        TestOutcome.Note -> Icons.Default.NoteAlt to CyanPrimary
    }
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp).padding(top = 2.dp), tint = color)
        Column(modifier = Modifier.weight(1f)) {
            Text(entry.text, fontSize = 12.sp, color = OnSurface)
            Text(entry.timestamp, fontSize = 10.sp, color = OnSurfaceMuted)
        }
    }
}

private fun currentTimestamp(): String {
    val fmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    return fmt.format(Date())
}

private fun CoroutineScope.launchSnackbar(
    host: SnackbarHostState,
    message: String
) {
    launch {
        host.showSnackbar(message, duration = SnackbarDuration.Short)
    }
}
