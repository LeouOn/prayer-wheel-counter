package com.prayerwheel.app.ui.components

import java.text.NumberFormat
import java.util.Locale

/**
 * Calculates duration string from start to end time.
 */
internal fun calculateDuration(startedAt: Long, endedAt: Long): String {
    val durationMs = endedAt - startedAt
    val seconds = durationMs / 1000
    val minutes = seconds / 60
    val hours = minutes / 60

    return when {
        hours > 0 -> "${hours}h ${minutes % 60}m"
        minutes > 0 -> "${minutes}m"
        seconds > 0 -> "${seconds}s"
        else -> ""
    }
}

internal fun formatPracticeTime(seconds: Long): String {
    if (seconds <= 0L) return "0m"
    val totalMinutes = seconds / 60L
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    return if (hours > 0L) "${hours}h ${minutes}m" else "${minutes}m"
}

/**
 * Formats a number with locale-aware separators.
 */
internal fun formatNumber(value: Long): String {
    return NumberFormat.getNumberInstance(Locale.getDefault()).format(value)
}

/**
 * Parses capacity input that may include shorthand notation like "65B", "100M", "1K".
 */
internal fun parseCapacityInput(input: String): Long {
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
