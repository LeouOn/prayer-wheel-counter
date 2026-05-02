package com.prayerwheel.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.math.BigInteger
import java.text.NumberFormat
import java.util.Locale

/**
 * Displays mantra counts for the current session and lifetime total.
 *
 * Shows the current mantra name above the counts.
 * Uses AnimatedContent for smooth digit transitions.
 */
@Composable
fun CounterDisplay(
    currentMantraName: String,
    sessionMantras: BigInteger,
    lifetimeMantras: BigInteger,
    currentRpm: Float = 0f,
    isSpinning: Boolean = false,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier.fillMaxWidth()
    ) {
        // Current mantra name
        Text(
            text = currentMantraName,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            fontStyle = FontStyle.Italic,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Live RPM display (only shown when spinning)
        if (isSpinning && currentRpm > 0f) {
            AnimatedContent(
                targetState = formatRpm(currentRpm),
                transitionSpec = {
                    fadeIn() togetherWith fadeOut()
                },
                label = "rpmAnimation"
            ) { displayValue ->
                Text(
                    text = "Speed: $displayValue RPM",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Lifetime mantras — larger and prominent
        AnimatedMantraCount(
            count = lifetimeMantras,
            label = "Lifetime",
            isPrimary = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Session mantras — smaller
        AnimatedMantraCount(
            count = sessionMantras,
            label = "Session",
            isPrimary = false
        )
    }
}

/**
 * Animated display for a single count value.
 */
@Composable
private fun AnimatedMantraCount(
    count: BigInteger,
    label: String,
    isPrimary: Boolean
) {
    val formattedCount = formatBigNumber(count)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = label,
            style = if (isPrimary) {
                MaterialTheme.typography.labelLarge
            } else {
                MaterialTheme.typography.labelMedium
            },
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Animated content for smooth digit transitions
        AnimatedContent(
            targetState = formattedCount,
            transitionSpec = {
                // Only animate if the number of digits changes
                if (targetState.length > initialState.length) {
                    slideInHorizontally { it } + fadeIn() togetherWith
                            slideOutHorizontally { -it } + fadeOut()
                } else if (targetState.length < initialState.length) {
                    slideInHorizontally { -it } + fadeIn() togetherWith
                            slideOutHorizontally { it } + fadeOut()
                } else {
                    fadeIn() togetherWith fadeOut()
                }
            },
            label = "mantraCountAnimation"
        ) { displayValue ->
            Text(
                text = displayValue,
                style = if (isPrimary) {
                    MaterialTheme.typography.displayMedium
                } else {
                    MaterialTheme.typography.headlineSmall
                },
                fontWeight = if (isPrimary) FontWeight.Bold else FontWeight.Normal,
                color = if (isPrimary) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onBackground
                }
            )
        }

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = "mantras",
            style = if (isPrimary) {
                MaterialTheme.typography.bodyLarge
            } else {
                MaterialTheme.typography.bodyMedium
            },
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
        )
    }
}

/**
 * Formats a BigInteger with locale-aware thousand separators.
 */
private fun formatBigNumber(value: BigInteger): String {
    if (value <= BigInteger.valueOf(Long.MAX_VALUE)) {
        return NumberFormat.getNumberInstance(Locale.getDefault()).format(value.toLong())
    }

    // For very large numbers, format with scientific notation hint
    val str = value.toString()
    if (str.length <= 15) {
        return NumberFormat.getNumberInstance(Locale.getDefault()).format(value)
    }

    // Format with groupings for readability
    val builder = StringBuilder()
    val strVal = str
    var count = 0
    for (i in strVal.length - 1 downTo 0) {
        if (count > 0 && count % 3 == 0) {
            builder.insert(0, ",")
        }
        builder.insert(0, strVal[i])
        count++
    }
    return builder.toString()
}

/**
 * Formats RPM for display.
 */
private fun formatRpm(rpm: Float): String {
    return if (rpm >= 100f) {
        rpm.toInt().toString()
    } else {
        String.format(Locale.getDefault(), "%.1f", rpm)
    }
}
