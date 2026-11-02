package com.prayerwheel.app.ui.wheel.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.prayerwheel.app.data.datastore.SpinMode
import com.prayerwheel.app.data.model.Mantras
import com.prayerwheel.app.data.model.SavedWheel
import com.prayerwheel.app.data.model.Session
import com.prayerwheel.app.ui.components.NumberFormatter
import com.prayerwheel.app.ui.components.calculateDuration
import com.prayerwheel.app.ui.theme.StarGold
import com.prayerwheel.app.ui.theme.StarLightBlue
import java.math.BigInteger
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Enhanced session card showing detailed session information.
 */
@Composable
internal fun SessionCard(
    session: Session,
    savedWheels: List<SavedWheel> = emptyList(),
    onDeleteClick: () -> Unit,
    onLabelChange: (String?) -> Unit
) {
    val mantra = Mantras.byId(session.mantraId)
    val displayName = mantra?.displayName ?: session.mantraId
    val tibetan = mantra?.tibetan
    val wheelName = session.wheelId?.let { id -> savedWheels.find { it.id == id }?.name ?: "Deleted Wheel" }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Date, label chip, and duration row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = formatDateTime(session.startedAt),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    LabelChipWithEditor(
                        label = session.label,
                        onChange = onLabelChange
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    session.endedAt?.let { endedAt ->
                        val duration = calculateDuration(session.startedAt, endedAt)
                        if (duration.isNotEmpty()) {
                            Text(
                                text = duration,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                    }
                    IconButton(
                        onClick = onDeleteClick
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete session",
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Mantra name
            Column {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                tibetan?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Stats row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start
            ) {
                Text(
                    text = "${formatNumber(session.rotationCount)} rotations",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "${formatBigNumber(session.totalMantras)} mantras",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // RPM stats row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start
            ) {
                if (session.averageRpm > 0) {
                    Text(
                        text = "Avg: ${String.format(Locale.getDefault(), "%.1f", session.averageRpm)} RPM",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                }
                if (session.peakRpm > 0) {
                    Text(
                        text = "Peak: ${String.format(Locale.getDefault(), "%.1f", session.peakRpm)} RPM",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Mode and Wheel row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatSpinMode(session.mode),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
                if (wheelName != null) {
                    Text(
                        text = "Wheel: $wheelName",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }

            // Intention (if set)
            session.intention?.let { intention ->
                if (intention.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Intention: $intention",
                        style = MaterialTheme.typography.bodySmall,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Dedication (if present)
            session.dedication?.let { dedication ->
                if (dedication.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = dedication,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * Formats a number with K/M/B suffixes for large values.
 */
internal fun formatNumber(number: Long): String {
    return NumberFormatter.formatLong(number)
}

/**
 * Formats a BigInteger for display.
 */
internal fun formatBigNumber(mantras: BigInteger): String {
    return NumberFormatter.format(mantras)
}

/**
 * Formats a timestamp to a readable date/time string.
 */
private fun formatDateTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

/**
 * Formats spin mode string to display name.
 */
private fun formatSpinMode(mode: String): String {
    return try {
        when (SpinMode.valueOf(mode)) {
            SpinMode.MANUAL -> "Manual"
            SpinMode.TWO_HANDED -> "Two-Handed"
            SpinMode.AUTO_SPIN -> "Auto-Spin"
            SpinMode.TWO_HANDED_AUTO -> "Two-Handed Auto"
        }
    } catch (e: IllegalArgumentException) {
        mode
    }
}

@Composable
private fun LabelChipWithEditor(
    label: String?,
    onChange: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var showCustomDialog by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        LabelChip(
            label = label,
            onClick = { menuExpanded = true }
        )
        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("Morning") },
                onClick = {
                    menuExpanded = false
                    onChange("morning")
                }
            )
            DropdownMenuItem(
                text = { Text("Evening") },
                onClick = {
                    menuExpanded = false
                    onChange("evening")
                }
            )
            DropdownMenuItem(
                text = { Text("Custom…") },
                onClick = {
                    menuExpanded = false
                    showCustomDialog = true
                }
            )
            DropdownMenuItem(
                text = { Text("None") },
                onClick = {
                    menuExpanded = false
                    onChange(null)
                }
            )
        }
    }

    if (showCustomDialog) {
        val isPreset = label.equals("morning", ignoreCase = true) ||
            label.equals("evening", ignoreCase = true)
        LabelCustomDialog(
            initialText = if (label != null && !isPreset) label else "",
            onConfirm = { text ->
                onChange(text.ifBlank { null })
                showCustomDialog = false
            },
            onDismiss = { showCustomDialog = false }
        )
    }
}

@Composable
private fun LabelChip(
    label: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val normalized = label?.lowercase(Locale.getDefault())
    val (bgAlpha, fgColor, text) = when (normalized) {
        "morning" -> Triple(0.45f, MaterialTheme.colorScheme.onSurface, "Morning")
        "evening" -> Triple(0.5f, MaterialTheme.colorScheme.onSurface, "Evening")
        null -> Triple(
            0.4f,
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            "—"
        )
        else -> Triple(
            0.5f,
            MaterialTheme.colorScheme.onSurface,
            label
        )
    }

    val backgroundColor = when (normalized) {
        "morning" -> StarLightBlue
        "evening" -> StarGold
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .background(backgroundColor.copy(alpha = bgAlpha))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = fgColor,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun LabelCustomDialog(
    initialText: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initialText) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom Label") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                placeholder = { Text("e.g., Lunch break") },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text.trim()) }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
