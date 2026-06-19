package com.prayerwheel.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/**
 * Preset goal values for mantra sessions.
 */
private val GOAL_PRESETS = listOf(108L, 1000L, 10000L, 100000L)

/**
 * Contemplative traditional intentions to inspire practice.
 */
internal val INTENTION_SUGGESTIONS = listOf(
    "May all sentient beings be free from suffering and the causes of suffering.",
    "For the health, peace, and long life of all family members and friends.",
    "May my heart blossom with compassion, patience, and wisdom.",
    "To cultivate mindfulness, quietness of mind, and inner stillness.",
    "For the swift relief of those suffering from illness, poverty, or conflict.",
    "May I remain steady, present, and kind throughout the day.",
    "For the healing of the Earth and all living ecosystems.",
    "To overcome anger, attachment, and ignorance in my daily life.",
    "May the positive merit of this practice radiate to benefit all beings.",
    "For the guidance and strength to act with absolute compassion."
)

/**
 * Dialog for setting an intention and optional mantra goals.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IntentionDialog(
    currentIntention: String,
    currentSessionGoal: Long,
    currentDailyGoal: Long,
    onSave: (intention: String, sessionGoal: Long, dailyGoal: Long) -> Unit,
    onDismiss: () -> Unit
) {
    var intentionText by remember { mutableStateOf(currentIntention) }
    var sessionGoalText by remember { mutableLongStateOf(if (currentSessionGoal > 0) currentSessionGoal else 0L) }
    var dailyGoalText by remember { mutableLongStateOf(if (currentDailyGoal > 0) currentDailyGoal else 0L) }
    var selectedSessionPreset by remember { mutableStateOf<Long?>(null) }
    var selectedDailyPreset by remember { mutableStateOf<Long?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Set Intention",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Medium
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Intention text field with Inspire button
                Column(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = intentionText,
                        onValueChange = { intentionText = it },
                        label = { Text("Intention") },
                        placeholder = { Text("e.g., For the health of my family") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 4
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    TextButton(
                        onClick = {
                            intentionText = INTENTION_SUGGESTIONS.random()
                        },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Inspire Me ✨")
                    }
                }

                // Session goal section
                Text(
                    text = "Session Goal (optional)",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    GOAL_PRESETS.forEach { preset ->
                        FilterChip(
                            selected = selectedSessionPreset == preset,
                            onClick = {
                                selectedSessionPreset = if (selectedSessionPreset == preset) null else preset
                                sessionGoalText = if (selectedSessionPreset == preset) preset else 0L
                            },
                            label = { Text(formatGoalPreset(preset)) }
                        )
                    }
                }

                // Daily goal section
                Text(
                    text = "Daily Goal (optional)",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    GOAL_PRESETS.forEach { preset ->
                        FilterChip(
                            selected = selectedDailyPreset == preset,
                            onClick = {
                                selectedDailyPreset = if (selectedDailyPreset == preset) null else preset
                                dailyGoalText = if (selectedDailyPreset == preset) preset else 0L
                            },
                            label = { Text(formatGoalPreset(preset)) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(intentionText, sessionGoalText, dailyGoalText)
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        shape = RoundedCornerShape(16.dp)
    )
}

/**
 * Formats a goal preset for display.
 */
private fun formatGoalPreset(value: Long): String {
    return when {
        value >= 1_000_000 -> "${value / 1_000_000}M"
        value >= 1_000 -> "${value / 1_000}K"
        else -> value.toString()
    }
}
