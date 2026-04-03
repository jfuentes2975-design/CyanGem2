package com.cyangem.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.cyangem.gemini.Gem
import com.cyangem.ui.theme.*
import com.cyangem.viewmodel.MainViewModel

@Composable
fun GemsScreen(vm: MainViewModel) {
    val uiState by vm.uiState.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }
    var editingGem by remember { mutableStateOf<Gem?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Gems", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold), color = OnSurface)
                    Text("Custom AI personas for your glasses", fontSize = 13.sp, color = OnSurfaceMuted)
                }
                FloatingActionButton(
                    onClick = { showCreateDialog = true },
                    modifier = Modifier.size(40.dp),
                    containerColor = CyanPrimary,
                    contentColor = Color(0xFF003731)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Create Gem", modifier = Modifier.size(20.dp))
                }
            }
        }

        // Active gem banner
        uiState.activeGem?.let { active ->
            item {
                ActiveGemBanner(gem = active)
            }
        }

        // Gem list
        items(uiState.gems) { gem ->
            GemCard(
                gem = gem,
                isActive = gem.id == uiState.activeGem?.id,
                onActivate = { vm.activateGem(gem) },
                onEdit = { editingGem = gem },
                onDelete = if (!gem.isDefault) ({ vm.deleteGem(gem.id) }) else null
            )
        }

        item { Spacer(Modifier.height(16.dp)) }
    }

    if (showCreateDialog) {
        GemEditorDialog(
            gem = null,
            onDismiss = { showCreateDialog = false },
            onSave = { gem ->
                vm.saveGem(gem)
                showCreateDialog = false
            }
        )
    }

    editingGem?.let { gem ->
        GemEditorDialog(
            gem = gem,
            onDismiss = { editingGem = null },
            onSave = { updated ->
                vm.saveGem(updated)
                editingGem = null
            }
        )
    }
}

@Composable
private fun ActiveGemBanner(gem: Gem) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = CyanPrimary.copy(alpha = 0.12f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, CyanPrimary.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(gem.emoji, fontSize = 24.sp)
            Column {
                Text("Active Gem", fontSize = 11.sp, color = CyanPrimary, fontWeight = FontWeight.Medium)
                Text(gem.name, fontWeight = FontWeight.SemiBold, color = OnSurface)
            }
        }
    }
}

@Composable
private fun GemCard(
    gem: Gem,
    isActive: Boolean,
    onActivate: () -> Unit,
    onEdit: () -> Unit,
    onDelete: (() -> Unit)?
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) CyanPrimary.copy(alpha = 0.08f) else SurfaceCard
        ),
        shape = RoundedCornerShape(14.dp),
        border = if (isActive) BorderStroke(1.dp, CyanPrimary.copy(alpha = 0.5f)) else null
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Emoji avatar
            Surface(
                modifier = Modifier.size(44.dp),
                color = SurfaceElevated,
                shape = RoundedCornerShape(12.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(gem.emoji, fontSize = 22.sp)
                }
            }

            // Info
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(gem.name, fontWeight = FontWeight.SemiBold, color = OnSurface, fontSize = 14.sp)
                    if (gem.isDefault) {
                        Surface(color = CyanSecondary.copy(alpha = 0.2f), shape = RoundedCornerShape(4.dp)) {
                            Text("Built-in", fontSize = 9.sp, color = CyanSecondary, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                        }
                    }
                }
                Text(gem.description, fontSize = 12.sp, color = OnSurfaceMuted, maxLines = 2)
            }

            // Actions
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (!isActive) {
                    FilledTonalButton(
                        onClick = onActivate,
                        modifier = Modifier.height(32.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = CyanPrimary.copy(alpha = 0.15f), contentColor = CyanPrimary)
                    ) { Text("Use", fontSize = 12.sp) }
                } else {
                    Icon(Icons.Default.CheckCircle, contentDescription = "Active", tint = CyanPrimary, modifier = Modifier.size(22.dp))
                }
                if (!gem.isDefault) {
                    Row {
                        IconButton(onClick = onEdit, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit", tint = OnSurfaceMuted, modifier = Modifier.size(14.dp))
                        }
                        IconButton(onClick = { showDeleteConfirm = true }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = ErrorColor.copy(alpha = 0.7f), modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete ${gem.name}?") },
            text = { Text("This Gem will be permanently removed.") },
            confirmButton = {
                TextButton(onClick = { onDelete?.invoke(); showDeleteConfirm = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = ErrorColor)) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun GemEditorDialog(gem: Gem?, onDismiss: () -> Unit, onSave: (Gem) -> Unit) {
    var name by remember { mutableStateOf(gem?.name ?: "") }
    var description by remember { mutableStateOf(gem?.description ?: "") }
    var systemPrompt by remember { mutableStateOf(gem?.systemPrompt ?: "") }
    var emoji by remember { mutableStateOf(gem?.emoji ?: "💎") }

    val emojiOptions = listOf("💎", "🤖", "🧭", "🔬", "🌍", "💪", "👁️", "🧠", "⚡", "🎯", "🔧", "📊", "✍️", "🎨", "🎵")

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = SurfaceCard,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    if (gem == null) "Create Gem" else "Edit Gem",
                    fontWeight = FontWeight.Bold, fontSize = 18.sp, color = OnSurface
                )

                // Emoji picker
                Text("Icon", fontSize = 12.sp, color = OnSurfaceMuted)
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    emojiOptions.forEach { e ->
                        Surface(
                            onClick = { emoji = e },
                            color = if (emoji == e) CyanPrimary.copy(alpha = 0.2f) else SurfaceElevated,
                            shape = RoundedCornerShape(8.dp),
                            border = if (emoji == e) BorderStroke(1.dp, CyanPrimary) else null,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) { Text(e, fontSize = 20.sp) }
                        }
                    }
                }

                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = gemTextFieldColors()
                )

                OutlinedTextField(
                    value = description, onValueChange = { description = it },
                    label = { Text("Short description") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 2,
                    colors = gemTextFieldColors()
                )

                OutlinedTextField(
                    value = systemPrompt, onValueChange = { systemPrompt = it },
                    label = { Text("System Prompt") },
                    placeholder = { Text("You are a…\nDescribe how this Gem should behave.", fontSize = 12.sp, color = OnSurfaceMuted) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 140.dp),
                    maxLines = 12,
                    colors = gemTextFieldColors()
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancel") }
                    Button(
                        onClick = {
                            if (name.isNotBlank() && systemPrompt.isNotBlank()) {
                                onSave(Gem(
                                    id = gem?.id ?: java.util.UUID.randomUUID().toString(),
                                    name = name.trim(),
                                    description = description.trim(),
                                    systemPrompt = systemPrompt.trim(),
                                    emoji = emoji
                                ))
                            }
                        },
                        enabled = name.isNotBlank() && systemPrompt.isNotBlank(),
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary, contentColor = Color(0xFF003731))
                    ) { Text("Save", fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

@Composable
private fun gemTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = CyanPrimary,
    unfocusedBorderColor = Color(0xFF30363D),
    focusedTextColor = OnSurface,
    unfocusedTextColor = OnSurface,
    focusedLabelColor = CyanPrimary,
    cursorColor = CyanPrimary
)
