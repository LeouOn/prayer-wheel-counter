package com.prayerwheel.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToLong

/**
 * Capacity slider for selecting mantras per rotation.
 * Enhanced with direct number input, +/- buttons, and preset cards.
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
    var textInputValue by remember { mutableStateOf(formatNumberForInput(currentValue)) }
    var isTextFieldFocused by remember { mutableStateOf(false) }
    
    // Update text input when currentValue changes externally
    LaunchedEffect(currentValue) {
        if (!isTextFieldFocused) {
            textInputValue = formatNumberForInput(currentValue)
        }
    }

    data class PresetOption(val label: String, val value: Long)

    val presets = listOf(
        PresetOption("108", 108L),
        PresetOption("1K", 1_000L),
        PresetOption("10K", 10_000L),
        PresetOption("100K", 100_000L),
        PresetOption("1M", 1_000_000L),
        PresetOption("10M", 10_000_000L),
        PresetOption("100M", 100_000_000L),
        PresetOption("1B", 1_000_000_000L),
        PresetOption("10B", 10_000_000_000L),
        PresetOption("100B", 100_000_000_000L),
        PresetOption("1T", 1_000_000_000_000L)
    )

    // Calculate smart increment based on current value
    fun getSmartIncrement(): Long {
        return when {
            currentValue < 1_000L -> 1L
            currentValue < 1_000_000L -> 100L
            currentValue < 1_000_000_000L -> 100_000L
            else -> 100_000_000L
        }
    }

    // Calculate snap threshold
    val snapThreshold = 0.05f

    fun findNearestPreset(position: Float): Long? {
        val presetValues = listOf(108L, 1_000L, 10_000L, 100_000L, 1_000_000L, 10_000_000L, 100_000_000L, 1_000_000_000L, 10_000_000_000L, 100_000_000_000L, 1_000_000_000_000L)
        var nearestPreset: Long? = null
        var nearestDistance = Float.MAX_VALUE
        
        for (preset in presetValues) {
            val presetPosition = longValueToSliderPosition(preset)
            val distance = abs(position - presetPosition)
            if (distance < nearestDistance) {
                nearestDistance = distance
                nearestPreset = preset
            }
        }
        
        return if (nearestDistance <= snapThreshold) nearestPreset else null
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Mantras per Rotation",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Number input with +/- buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Minus button
            IconButton(
                onClick = {
                    val decrement = getSmartIncrement()
                    val newValue = (currentValue - decrement).coerceAtLeast(1L)
                    onValueChange(newValue)
                    onPresetSelected?.invoke()
                },
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Decrease",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Number input field
            Box(
                modifier = Modifier
                    .width(160.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                BasicTextField(
                    value = textInputValue,
                    onValueChange = { newValue ->
                        textInputValue = newValue
                        // Try to parse and update on valid input
                        val parsed = parseCapacityInput(newValue)
                        if (parsed > 0) {
                            onValueChange(parsed)
                        }
                    },
                    textStyle = MaterialTheme.typography.headlineSmall.copy(
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.onFocusChanged { focusState ->
                        isTextFieldFocused = focusState.isFocused
                        if (!focusState.isFocused) {
                            // Format the number when focus is lost
                            textInputValue = formatNumberForInput(currentValue)
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Plus button
            IconButton(
                onClick = {
                    val increment = getSmartIncrement()
                    val newValue = (currentValue + increment).coerceAtMost(Long.MAX_VALUE)
                    onValueChange(newValue)
                    onPresetSelected?.invoke()
                },
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Increase",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Slider (secondary to the number input)
        Slider(
            value = sliderPosition,
            onValueChange = { newPosition ->
                // Check for magnetic snap to nearest preset
                val nearestPreset = findNearestPreset(newPosition)
                val snappedPosition = if (nearestPreset != null) {
                    val presetPos = longValueToSliderPosition(nearestPreset)
                    // Snap directly to preset if very close
                    if (abs(newPosition - presetPos) < snapThreshold * 2) {
                        onValueChange(nearestPreset)
                        onPresetSelected?.invoke()
                        textInputValue = formatNumberForInput(nearestPreset)
                        presetPos
                    } else {
                        val newValue = sliderPositionToLongValue(newPosition)
                        onValueChange(newValue)
                        textInputValue = formatNumberForInput(newValue)
                        newPosition
                    }
                } else {
                    val newValue = sliderPositionToLongValue(newPosition)
                    onValueChange(newValue)
                    textInputValue = formatNumberForInput(newValue)
                    newPosition
                }
                sliderPosition = snappedPosition
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

        // Presets label
        Text(
            text = "Quick Select",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            modifier = Modifier.align(Alignment.Start)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Scrollable presets row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            presets.forEach { preset ->
                PresetChip(
                    label = preset.label,
                    value = preset.value,
                    isSelected = currentValue == preset.value,
                    onClick = {
                        sliderPosition = longValueToSliderPosition(preset.value)
                        onValueChange(preset.value)
                        textInputValue = formatNumberForInput(preset.value)
                        onPresetSelected?.invoke()
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Info button
        TextButton(
            onClick = { showInfoDialog = true },
            modifier = Modifier.align(Alignment.End)
        ) {
            Text(
                text = "Info",
                style = MaterialTheme.typography.bodyMedium
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
 * Converts a long value to a slider position (0..1) using logarithmic scale.
 */
private fun longValueToSliderPosition(value: Long): Float {
    if (value <= 1) return 0f
    if (value >= 1_000_000_000_000L) return 1f

    val logMax = 12f // log10(1 trillion) = 12
    val logValue = ln(value.toFloat()) / ln(10f)
    return (logValue / logMax).coerceIn(0f, 1f)
}

/**
 * Converts a slider position (0..1) to a long value using logarithmic scale.
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

/**
 * Formats a number for display in the input field, using shorthand for large values.
 */
private fun formatNumberForInput(value: Long): String {
    return when {
        value >= 1_000_000_000_000L -> "${value / 1_000_000_000_000L}T"
        value >= 1_000_000_000L -> "${value / 1_000_000_000L}B"
        value >= 1_000_000L -> "${value / 1_000_000L}M"
        value >= 1_000L -> "${value / 1_000L}K"
        else -> NumberFormat.getNumberInstance(Locale.getDefault()).format(value)
    }
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
