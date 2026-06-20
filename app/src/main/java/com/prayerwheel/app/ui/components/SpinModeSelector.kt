package com.prayerwheel.app.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

/**
 * Spin mode selector with three independent toggles for Auto-Spin, Two-Handed (Dual Wheels),
 * and Breath/Wind modes. Toggles may be combined.
 *
 * Breath mode requests RECORD_AUDIO on first enable via an in-app explanation dialog
 * followed by the system permission prompt. The microphone is used solely for
 * real-time amplitude detection — no audio is recorded or stored.
 */
@Composable
fun SpinModeSelector(
    autoSpinEnabled: Boolean,
    twoHandedEnabled: Boolean,
    breathModeEnabled: Boolean,
    autoSpinRpm: Int,
    onAutoSpinToggle: (Boolean) -> Unit,
    onTwoHandedToggle: (Boolean) -> Unit,
    onBreathModeToggle: (Boolean) -> Unit,
    onRpmChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Tracks whether the user has dismissed the breath-mode permission explanation
    // at least once this session. We re-show it whenever they re-attempt to enable
    // breath mode without having granted RECORD_AUDIO.
    var showBreathPermissionDialog by remember { mutableStateOf(false) }

    val recordAudioLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        // Only flip breath mode on if the user actually granted the mic.
        // If they denied, leave the toggle off — they can retry by toggling again.
        onBreathModeToggle(granted)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                shape = RoundedCornerShape(12.dp)
            )
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(16.dp)
    ) {
        // Title
        Text(
            text = "Spin Mode",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // Auto-Spin toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Auto-Spin",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Wheel rotates automatically",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            Switch(
                checked = autoSpinEnabled,
                onCheckedChange = onAutoSpinToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                    checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }

        // RPM slider (visible when auto-spin is enabled)
        if (autoSpinEnabled) {
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Speed",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Text(
                    text = "$autoSpinRpm RPM",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = if (autoSpinRpm >= 90) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                )
            }
            
            Slider(
                value = autoSpinRpm.toFloat(),
                onValueChange = { onRpmChange(it.toInt()) },
                valueRange = 1f..120f,
                steps = 118,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Divider
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Dual Wheels toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Dual Wheels",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Spin two wheels simultaneously",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            Switch(
                checked = twoHandedEnabled,
                onCheckedChange = onTwoHandedToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                    checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Description for combined mode
        if (autoSpinEnabled && twoHandedEnabled) {
            Text(
                text = "Auto-spin active. Dual wheels mode allows spinning two wheels simultaneously.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Divider
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Breath/Wind mode toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Unicode "wind face" glyph — breath/wind symbol. Used in place of
                // a Material icon because the project does not depend on
                // material-icons-extended, and the existing UI uses Unicode glyphs
                // (e.g. "⏸" pause, "↻" resume) for similar symbolic indicators.
                Text(
                    text = "\uD83C\uDF2C", // 🌬 — wind face
                    style = MaterialTheme.typography.titleMedium,
                    color = if (breathModeEnabled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    }
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Breath Mode",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Your breath spins the wheel",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
            Switch(
                checked = breathModeEnabled,
                onCheckedChange = { enabled ->
                    if (enabled) {
                        val granted = ContextCompat.checkSelfPermission(
                            context,
                            android.Manifest.permission.RECORD_AUDIO
                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                        if (granted) {
                            onBreathModeToggle(true)
                        } else {
                            // Show the explanation first; the actual system prompt fires
                            // when the user taps "Allow" in the dialog.
                            showBreathPermissionDialog = true
                        }
                    } else {
                        onBreathModeToggle(false)
                    }
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                    checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }

        if (breathModeEnabled) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Breathe steadily toward your phone. No audio is recorded or stored.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
            )
        }
    }

    // In-app permission explanation shown BEFORE the system permission prompt.
    // Android best practice: educate the user on why the permission is needed
    // before triggering the system dialog, so they can make an informed choice.
    if (showBreathPermissionDialog) {
        AlertDialog(
            onDismissRequest = {
                showBreathPermissionDialog = false
                // User declined the explanation — leave breath mode off.
                onBreathModeToggle(false)
            },
            title = { Text("Breath Mode needs microphone access") },
            text = {
                Text(
                    "Breath mode uses your microphone to detect your breathing and " +
                        "spin the wheel accordingly. No audio is recorded or stored — " +
                        "only the momentary loudness is measured in memory and then discarded."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showBreathPermissionDialog = false
                    recordAudioLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                }) { Text("Allow") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showBreathPermissionDialog = false
                    onBreathModeToggle(false)
                }) { Text("Not now") }
            }
        )
    }
}

/**
 * Legacy support for the old SpinMode enum-based selector.
 * This is kept for backwards compatibility but delegates to the new toggle-based UI.
 */
@Composable
fun SpinModeSelector(
    selectedMode: com.prayerwheel.app.data.datastore.SpinMode,
    onModeSelected: (com.prayerwheel.app.data.datastore.SpinMode) -> Unit,
    modifier: Modifier = Modifier
) {
    // For backwards compatibility, we convert the old enum mode to the new toggle states
    val autoSpinEnabled = selectedMode == com.prayerwheel.app.data.datastore.SpinMode.AUTO_SPIN ||
                          selectedMode == com.prayerwheel.app.data.datastore.SpinMode.TWO_HANDED_AUTO
    val twoHandedEnabled = selectedMode == com.prayerwheel.app.data.datastore.SpinMode.TWO_HANDED ||
                           selectedMode == com.prayerwheel.app.data.datastore.SpinMode.TWO_HANDED_AUTO
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                shape = RoundedCornerShape(12.dp)
            )
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        com.prayerwheel.app.data.datastore.SpinMode.entries.forEach { mode ->
            val isSelected = mode == selectedMode
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surface
                        }
                    )
                    .padding(vertical = 10.dp, horizontal = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = when (mode) {
                        com.prayerwheel.app.data.datastore.SpinMode.MANUAL -> "Manual"
                        com.prayerwheel.app.data.datastore.SpinMode.TWO_HANDED -> "Two-Handed"
                        com.prayerwheel.app.data.datastore.SpinMode.AUTO_SPIN -> "Auto"
                        com.prayerwheel.app.data.datastore.SpinMode.TWO_HANDED_AUTO -> "Both"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    }
                )
            }
        }
    }
}
