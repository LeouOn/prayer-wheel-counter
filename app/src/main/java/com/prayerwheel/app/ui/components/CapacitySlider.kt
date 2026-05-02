package com.prayerwheel.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.prayerwheel.app.R
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Capacity slider for selecting mantras per rotation.
 *
 * Uses a logarithmic scale from 1 to 1 trillion (10^12).
 * Includes preset buttons for common values.
 */
@Composable
fun CapacitySlider(
    currentValue: Long,
    onValueChange: (Long) -> Unit,
    onPresetSelected: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var showInfoDialog by remember { mutableStateOf(false) }
    var sliderPosition by remember {
        mutableFloatStateOf(longValueToSliderPosition(currentValue))
    }

    val presets = listOf(
        PresetOption("Personal mala", 108L),
        PresetOption("Pocket wheel", 10_000L),
        PresetOption("Hand wheel", 1_000_000L),
        PresetOption("Standing wheel", 100_000_000L),
        PresetOption("Stupa-class", 1_000_000_000_000L)
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Mantras per Rotation",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Current value display
        Text(
            text = formatNumber(currentValue.toLong()),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Slider
        Slider(
            value = sliderPosition,
            onValueChange = { newPosition ->
                sliderPosition = newPosition
                val value = sliderPositionToLongValue(newPosition)
                onValueChange(value)
            },
            valueRange = 0f..1f,
            steps = 999,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Scale labels
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "1",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
            Text(
                text = "1T",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Presets row
        Text(
            text = "Quick Select",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            modifier = Modifier.align(Alignment.Start)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // First row of presets
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                presets.take(3).forEach { preset ->
                    PresetChip(
                        label = preset.label,
                        value = preset.value,
                        isSelected = currentValue == preset.value,
                        onClick = {
                            sliderPosition = longValueToSliderPosition(preset.value)
                            onValueChange(preset.value)
                            onPresetSelected?.invoke()
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Second row of presets
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                presets.drop(3).forEach { preset ->
                    PresetChip(
                        label = preset.label,
                        value = preset.value,
                        isSelected = currentValue == preset.value,
                        onClick = {
                            sliderPosition = longValueToSliderPosition(preset.value)
                            onValueChange(preset.value)
                            onPresetSelected?.invoke()
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Info button
        TextButton(
            onClick = { showInfoDialog = true },
            modifier = Modifier.align(Alignment.End)
        ) {
            Text(
                text = "ℹ️",
                style = MaterialTheme.typography.titleMedium
            )
        }
    }

    if (showInfoDialog) {
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            title = {
                Text(text = "Mantras per Rotation")
            },
            text = {
                Text(
                    text = "Set how many mantras are recited with each complete rotation of the prayer wheel. " +
                            "A traditional personal mala has 108 beads. Larger prayer wheels may be set to higher values. " +
                            "Your lifetime total accumulates regardless of this setting."
                )
            },
            confirmButton = {
                TextButton(onClick = { showInfoDialog = false }) {
                    Text("Got it")
                }
            }
        )
    }
}

@Composable
private fun PresetChip(
    label: String,
    value: Long,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilterChip(
        selected = isSelected,
        onClick = onClick,
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1
            )
        },
        modifier = modifier,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    )
}

/**
 * Converts an long value to a slider position (0..1) using logarithmic scale.
 */
private fun longValueToSliderPosition(value: Long): Float {
    if (value <= 1) return 0f
    if (value >= 1_000_000_000_000L) return 1f

    val logMax = 12f // log10(1 trillion) = 12
    val logValue = kotlin.math.ln(value.toFloat()) / kotlin.math.ln(10f)
    return (logValue / logMax).coerceIn(0f, 1f)
}

/**
 * Converts a slider position (0..1) to an long value using logarithmic scale.
 */
private fun sliderPositionToLongValue(position: Float): Long {
    if (position <= 0f) return 1L

    val logMax = 12f // log10(1 trillion) = 12
    val logValue = position * logMax
    val value = 10f.pow(logValue)
    return value.roundToLong().coerceIn(1L, Long.MAX_VALUE)
}

/**
 * Formats a number with locale-aware thousand separators.
 */
private fun formatNumber(value: Long): String {
    return NumberFormat.getNumberInstance(Locale.getDefault()).format(value)
}

private data class PresetOption(
    val label: String,
    val value: Long
)
