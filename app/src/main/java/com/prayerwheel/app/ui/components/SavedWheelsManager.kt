package com.prayerwheel.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.prayerwheel.app.data.model.Mantras
import com.prayerwheel.app.data.model.SavedWheel
import com.prayerwheel.app.viewmodel.WheelViewModel
import java.text.NumberFormat
import java.util.Locale

/**
 * Bottom sheet manager for saved prayer wheel profiles.
 * Allows users to create, edit, delete, and select saved wheels.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedWheelsManager(
    viewModel: WheelViewModel,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val savedWheels by viewModel.savedWheels.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    
    var showAddDialog by remember { mutableStateOf(false) }
    var editingWheel by remember { mutableStateOf<SavedWheel?>(null) }
    var wheelToDelete by remember { mutableStateOf<SavedWheel?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Saved Wheels",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = { showAddDialog = true }) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add Wheel"
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (savedWheels.isEmpty()) {
                // Empty state
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "No saved wheels yet",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Save your current wheel configuration to quickly switch between different prayer wheels",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                    }
                }
            } else {
                // Saved wheels list
                LazyColumn(
                    modifier = Modifier.height(400.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(savedWheels, key = { it.id }) { wheel ->
                        SavedWheelCard(
                            wheel = wheel,
                            onSelect = {
                                viewModel.applySavedWheel(wheel)
                                onDismiss()
                            },
                            onEdit = { editingWheel = wheel },
                            onDelete = { wheelToDelete = wheel }
                        )
                    }
                }
            }
        }
    }

    // Add wheel dialog
    if (showAddDialog) {
        AddEditWheelDialog(
            title = "Add Wheel",
            initialName = "",
            initialCapacity = viewModel.mantrasPerRotation.value,
            initialMantraId = viewModel.currentMantra.value.id,
            onSave = { name, capacity, mantraId ->
                viewModel.addSavedWheel(name, capacity, mantraId)
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false }
        )
    }

    // Edit wheel dialog
    editingWheel?.let { wheel ->
        AddEditWheelDialog(
            title = "Edit Wheel",
            initialName = wheel.name,
            initialCapacity = wheel.capacity,
            initialMantraId = wheel.mantraId,
            onSave = { name, capacity, mantraId ->
                viewModel.updateSavedWheel(wheel.copy(name = name, capacity = capacity, mantraId = mantraId))
                editingWheel = null
            },
            onDismiss = { editingWheel = null }
        )
    }

    // Delete confirmation dialog
    wheelToDelete?.let { wheel ->
        AlertDialog(
            onDismissRequest = { wheelToDelete = null },
            title = { Text("Delete Wheel") },
            text = { Text("Are you sure you want to delete \"${wheel.name}\"?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteSavedWheel(wheel.id)
                        wheelToDelete = null
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { wheelToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun SavedWheelCard(
    wheel: SavedWheel,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onSelect() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = wheel.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formatNumber(wheel.capacity),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = Mantras.byId(wheel.mantraId)?.displayName ?: wheel.mantraId,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }

            Row {
                IconButton(onClick = onEdit) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun AddEditWheelDialog(
    title: String,
    initialName: String,
    initialCapacity: Long,
    initialMantraId: String,
    onSave: (String, Long, String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var capacityText by remember { mutableStateOf(formatNumber(initialCapacity)) }
    var selectedMantraId by remember { mutableStateOf(initialMantraId) }
    var showMantraSelector by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Wheel Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = capacityText,
                    onValueChange = { capacityText = it },
                    label = { Text("Capacity") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedButton(
                    onClick = { showMantraSelector = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = Mantras.byId(selectedMantraId)?.displayName ?: "Select Mantra"
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val capacity = parseCapacityInput(capacityText)
                    if (name.isNotBlank() && capacity > 0) {
                        onSave(name.trim(), capacity, selectedMantraId)
                    }
                },
                enabled = name.isNotBlank() && parseCapacityInput(capacityText) > 0
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )

    if (showMantraSelector) {
        MantraSelectorDialog(
            selectedMantraId = selectedMantraId,
            onMantraSelected = {
                selectedMantraId = it
                showMantraSelector = false
            },
            onDismiss = { showMantraSelector = false }
        )
    }
}

@Composable
private fun MantraSelectorDialog(
    selectedMantraId: String,
    onMantraSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Mantra") },
        text = {
            LazyColumn(
                modifier = Modifier.height(300.dp)
            ) {
                items(Mantras.ALL) { mantra ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onMantraSelected(mantra.id) },
                        color = if (mantra.id == selectedMantraId) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = mantra.displayName,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (mantra.id == selectedMantraId) FontWeight.Medium else FontWeight.Normal
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Formats a number with locale-aware separators.
 */
private fun formatNumber(value: Long): String {
    return NumberFormat.getNumberInstance(Locale.getDefault()).format(value)
}

/**
 * Parses capacity input that may include shorthand notation like "65B", "100M", "1K".
 */
private fun parseCapacityInput(input: String): Long {
    val trimmed = input.trim().uppercase(Locale.getDefault())
    
    return when {
        trimmed.isEmpty() -> 0L
        
        // Check for shorthand notation
        trimmed.endsWith("T") -> {
            val numberPart = trimmed.dropLast(1).replace(",", "").replace(" ", "")
            ((numberPart.toDoubleOrNull() ?: return 0L) * 1_000_000_000_000L).toLong()
        }
        trimmed.endsWith("B") -> {
            val numberPart = trimmed.dropLast(1).replace(",", "").replace(" ", "")
            ((numberPart.toDoubleOrNull() ?: return 0L) * 1_000_000_000L).toLong()
        }
        trimmed.endsWith("M") -> {
            val numberPart = trimmed.dropLast(1).replace(",", "").replace(" ", "")
            ((numberPart.toDoubleOrNull() ?: return 0L) * 1_000_000L).toLong()
        }
        trimmed.endsWith("K") -> {
            val numberPart = trimmed.dropLast(1).replace(",", "").replace(" ", "")
            ((numberPart.toDoubleOrNull() ?: return 0L) * 1_000L).toLong()
        }
        
        // Regular number
        else -> {
            trimmed.replace(",", "").replace(" ", "").toLongOrNull() ?: 0L
        }
    }
}
