package com.prayerwheel.app.ui.components

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Spin mode selector with two independent toggles for Auto-Spin and Two-Handed modes.
 * Allows combining both modes simultaneously.
 */
@Composable
fun SpinModeSelector(
    autoSpinEnabled: Boolean,
    twoHandedEnabled: Boolean,
    autoSpinRpm: Int,
    onAutoSpinToggle: (Boolean) -> Unit,
    onTwoHandedToggle: (Boolean) -> Unit,
    onRpmChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
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
