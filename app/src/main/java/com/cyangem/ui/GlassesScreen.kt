package com.cyangem.ui

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyangem.ble.ConnectionState
import com.cyangem.ble.GlassesDevice
import com.cyangem.ble.GlassesStatus
import com.cyangem.media.MediaSyncProgress
import com.cyangem.ui.theme.*
import com.cyangem.viewmodel.MainViewModel

@Composable
fun GlassesScreen(vm: MainViewModel) {
    val uiState by vm.uiState.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header
        item {
            Text(
                "CyanGem",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Bold,
                    brush = Brush.horizontalGradient(listOf(CyanPrimary, CyanSecondary))
                )
            )
            Text(
                "Smart Glasses Control",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
        }

        // Connection card
        item {
            ConnectionCard(
                state = uiState.connectionState,
                status = uiState.glassesStatus,
                onScan = vm::startScan,
                onDisconnect = vm::disconnect
            )
        }

        // Quick Connect button — bypasses scan, connects directly to saved glasses
        item {
            QuickConnectCard(
                mac = uiState.savedMac,
                connectionState = uiState.connectionState,
                onConnect = { vm.connectSavedMac() },
                onMacChange = { vm.connectByMac(it) }
            )
        }

        // Device list (shown during scan or if devices found)
        if (uiState.scannedDevices.isNotEmpty() || uiState.connectionState == ConnectionState.SCANNING) {
            item {
                Text(
                    "Nearby Devices",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                )
            }
            items(uiState.scannedDevices) { device ->
                DeviceRow(device = device, onClick = { vm.connectByMac(device.address) })
            }
            if (uiState.scannedDevices.isEmpty() && uiState.connectionState == ConnectionState.SCANNING) {
                item { ScanningPlaceholder() }
            }
        }

        // Controls (only when connected)
        if (uiState.connectionState == ConnectionState.CONNECTED) {
            item {
                ControlsCard(
                    status = uiState.glassesStatus,
                    onPhoto = vm::takePhoto,
                    onStartVideo = vm::startVideo,
                    onStopVideo = vm::stopVideo,
                    onStartAudio = vm::startAudio,
                    onStopAudio = vm::stopAudio,
                    onRefreshBattery = vm::requestBattery
                )
            }

            item {
                MediaSyncCard(
                    progress = uiState.syncProgress,
                    onSync = vm::syncMedia
                )
            }
        }

        // Debug: BLE char inspector
        if (uiState.discoveredChars.isNotEmpty()) {
            item { BleInspectorCard(chars = uiState.discoveredChars) }
        }
    }
}

@Composable
private fun ConnectionCard(
    state: ConnectionState,
    status: GlassesStatus,
    onScan: () -> Unit,
    onDisconnect: () -> Unit
) {
    val (stateColor, stateLabel) = when (state) {
        ConnectionState.CONNECTED    -> Pair(SuccessColor, "Connected")
        ConnectionState.CONNECTING   -> Pair(CyanPrimary,  "Connecting…")
        ConnectionState.SCANNING     -> Pair(CyanSecondary,"Scanning…")
        ConnectionState.DISCONNECTING -> Pair(ErrorColor,  "Disconnecting…")
        else                         -> Pair(OnSurfaceMuted, "Disconnected")
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(stateColor)
                    )
                    Text(stateLabel, fontWeight = FontWeight.Medium, color = OnSurface)
                }
                if (state == ConnectionState.CONNECTED && status.battery >= 0) {
                    BatteryIndicator(level = status.battery, charging = status.isCharging)
                }
            }

            Spacer(Modifier.height(12.dp))

            if (state == ConnectionState.CONNECTED) {
                if (status.firmwareVersion.isNotEmpty()) {
                    Text("FW: ${status.firmwareVersion}", fontSize = 12.sp, color = OnSurfaceMuted)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatusChip("📷 ${status.photoCount}", Modifier.weight(1f))
                    StatusChip("🎥 ${status.videoCount}", Modifier.weight(1f))
                    StatusChip("🎙 ${status.audioCount}", Modifier.weight(1f))
                }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onDisconnect,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorColor)
                ) { Text("Disconnect") }
            } else {
                Button(
                    onClick = onScan,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = state == ConnectionState.IDLE || state == ConnectionState.DISCONNECTED,
                    colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary, contentColor = Color(0xFF003731))
                ) {
                    if (state == ConnectionState.SCANNING) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color(0xFF003731), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text("Scan for Glasses", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun BatteryIndicator(level: Int, charging: Boolean) {
    val color = when {
        level > 50 -> SuccessColor
        level > 20 -> Color(0xFFFFB300)
        else       -> ErrorColor
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (charging) Icon(Icons.Default.ElectricBolt, contentDescription = null, tint = CyanPrimary, modifier = Modifier.size(14.dp))
        Text("$level%", color = color, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

@Composable
private fun StatusChip(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = SurfaceElevated,
        shape = RoundedCornerShape(8.dp)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 6.dp)) {
            Text(text, fontSize = 12.sp, color = OnSurfaceMuted)
        }
    }
}

@Composable
private fun DeviceRow(device: GlassesDevice, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Default.BluetoothConnected, contentDescription = null, tint = CyanPrimary)
                Column {
                    Text(device.name, fontWeight = FontWeight.Medium, color = OnSurface)
                    Text(device.address, fontSize = 11.sp, color = OnSurfaceMuted)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${device.rssi} dBm", fontSize = 11.sp, color = OnSurfaceMuted)
                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = OnSurfaceMuted)
            }
        }
    }
}

@Composable
private fun ScanningPlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CircularProgressIndicator(color = CyanPrimary)
            Text("Looking for glasses…", color = OnSurfaceMuted, fontSize = 13.sp)
        }
    }
}

@Composable
private fun ControlsCard(
    status: GlassesStatus,
    onPhoto: () -> Unit,
    onStartVideo: () -> Unit,
    onStopVideo: () -> Unit,
    onStartAudio: () -> Unit,
    onStopAudio: () -> Unit,
    onRefreshBattery: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Controls", fontWeight = FontWeight.SemiBold, color = OnSurface)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ControlButton(icon = Icons.Default.PhotoCamera, label = "Photo",
                    onClick = onPhoto, modifier = Modifier.weight(1f))
                ControlButton(
                    icon = if (status.isRecordingVideo) Icons.Default.StopCircle else Icons.Default.Videocam,
                    label = if (status.isRecordingVideo) "Stop" else "Video",
                    onClick = if (status.isRecordingVideo) onStopVideo else onStartVideo,
                    tint = if (status.isRecordingVideo) ErrorColor else CyanPrimary,
                    modifier = Modifier.weight(1f)
                )
                ControlButton(
                    icon = if (status.isRecordingAudio) Icons.Default.StopCircle else Icons.Default.Mic,
                    label = if (status.isRecordingAudio) "Stop" else "Audio",
                    onClick = if (status.isRecordingAudio) onStopAudio else onStartAudio,
                    tint = if (status.isRecordingAudio) ErrorColor else CyanPrimary,
                    modifier = Modifier.weight(1f)
                )
                ControlButton(icon = Icons.Default.BatteryFull, label = "Battery",
                    onClick = onRefreshBattery, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ControlButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: Color = CyanPrimary,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        color = SurfaceElevated,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(22.dp))
            Text(label, fontSize = 11.sp, color = OnSurfaceMuted)
        }
    }
}

@Composable
private fun MediaSyncCard(progress: MediaSyncProgress, onSync: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Media Sync", fontWeight = FontWeight.SemiBold, color = OnSurface)
            Text(
                "Syncs photos via Bluetooth — keep glasses nearby during transfer.",
                fontSize = 12.sp, color = OnSurfaceMuted
            )

            AnimatedVisibility(visible = progress.isRunning) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    LinearProgressIndicator(
                        progress = { if (progress.totalFiles > 0) progress.downloadedFiles.toFloat() / progress.totalFiles else 0f },
                        modifier = Modifier.fillMaxWidth(),
                        color = CyanPrimary
                    )
                    Text(
                        "Downloading ${progress.currentFile}… (${progress.downloadedFiles}/${progress.totalFiles})",
                        fontSize = 11.sp, color = OnSurfaceMuted
                    )
                }
            }

            progress.error?.let {
                Text("⚠️ $it", fontSize = 12.sp, color = ErrorColor)
            }

            Button(
                onClick = onSync,
                enabled = !progress.isRunning,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = CyanSecondary)
            ) {
                Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (progress.isRunning) "Syncing…" else "Sync Media to Gallery")
            }
        }
    }
}

@Composable
private fun QuickConnectCard(
    mac: String,
    connectionState: ConnectionState,
    onConnect: () -> Unit,
    onMacChange: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Quick Connect", fontWeight = FontWeight.SemiBold, color = OnSurface)
            Text("Saved glasses MAC — tap to connect instantly without scanning",
                fontSize = 12.sp, color = OnSurfaceMuted)
            Text(mac, fontSize = 12.sp, color = CyanPrimary,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
            Button(
                onClick = onConnect,
                enabled = connectionState == ConnectionState.IDLE ||
                          connectionState == ConnectionState.DISCONNECTED,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CyanPrimary,
                    contentColor = androidx.compose.ui.graphics.Color(0xFF003731)
                )
            ) {
                Text("Connect to W630_7B3B", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BleInspectorCard(chars: Map<String, List<String>>) {
    var expanded by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        shape = RoundedCornerShape(16.dp),
        onClick = { expanded = !expanded }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("🔍 BLE Inspector", fontWeight = FontWeight.Medium, color = OnSurfaceMuted, fontSize = 13.sp)
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null, tint = OnSurfaceMuted
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    chars.forEach { (svcUuid, charList) ->
                        Text("Service: $svcUuid", fontSize = 10.sp, color = CyanSecondary)
                        charList.forEach { charDesc ->
                            Text("  └ $charDesc", fontSize = 10.sp, color = OnSurfaceMuted)
                        }
                    }
                }
            }
        }
    }
}
