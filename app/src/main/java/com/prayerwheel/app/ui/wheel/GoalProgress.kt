package com.prayerwheel.app.ui.wheel

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.prayerwheel.app.ui.components.NumberFormatter
import java.math.BigInteger

/**
 * Displays goal progress when a session goal is set.
 */
@Composable
internal fun GoalProgressDisplay(
    current: BigInteger,
    goal: BigInteger,
    onSetIntention: () -> Unit
) {
    val progress = (current.toFloat() / goal.toFloat()).coerceIn(0f, 1f)
    val isGoalReached = current >= goal

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Goal: ${formatNumber(current)} / ${formatNumber(goal)} mantras",
                style = MaterialTheme.typography.bodySmall,
                color = if (isGoalReached) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                }
            )
            TextButton(onClick = onSetIntention) {
                Text("Edit", style = MaterialTheme.typography.bodySmall)
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Progress bar
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(2.dp)
                )
        ) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .fillMaxHeight()
                    .background(
                        color = if (isGoalReached) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                        },
                        shape = RoundedCornerShape(2.dp)
                    )
            )
        }
    }
}

/**
 * Formats a BigInteger for display.
 */
private fun formatNumber(number: BigInteger, style: com.prayerwheel.app.data.datastore.NumberFormatStyle = com.prayerwheel.app.data.datastore.NumberFormatStyle.STANDARD): String {
    return NumberFormatter.formatWithStyle(number, style)
}

/**
 * Displays time goal progress when a time goal is set with an Intention Mandala.
 */
@Composable
internal fun TimeGoalProgressDisplay(
    currentSeconds: Long,
    goalSeconds: Long,
    currentIntention: String
) {
    val progress = (currentSeconds.toFloat() / goalSeconds.toFloat()).coerceIn(0f, 1f)
    val isGoalReached = currentSeconds >= goalSeconds

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Intention Mandala
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(140.dp)) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val maxRadius = size.width / 2f
                
                // Draw base mandala petals (faint)
                for (i in 0 until 12) {
                    rotate(degrees = i * 30f) {
                        drawPath(
                            path = createPetalPath(center, maxRadius),
                            color = Color.LightGray.copy(alpha = 0.3f),
                            style = Fill
                        )
                    }
                }
                
                // Draw filled mandala petals based on progress
                val filledPetals = (progress * 12).toInt()
                val partialProgress = (progress * 12) % 1
                
                for (i in 0..filledPetals) {
                    if (i == 12) break
                    val petalAlpha = if (i == filledPetals) partialProgress else 1f
                    rotate(degrees = i * 30f) {
                        drawPath(
                            path = createPetalPath(center, maxRadius),
                            color = Color(0xFFFFB74D).copy(alpha = petalAlpha * 0.8f),
                            style = Fill
                        )
                    }
                }
                
                // Inner circle
                drawCircle(
                    color = Color.White.copy(alpha = 0.9f),
                    radius = maxRadius * 0.4f,
                    center = center
                )
            }
            
            // Intention text in center
            if (currentIntention.isNotBlank()) {
                Text(
                    text = currentIntention,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(80.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val currentMins = currentSeconds / 60
            val goalMins = goalSeconds / 60
            Text(
                text = "Time Goal: ${currentMins}m / ${goalMins}m",
                style = MaterialTheme.typography.bodySmall,
                color = if (isGoalReached) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                }
            )
        }
    }
}

private fun createPetalPath(center: Offset, maxRadius: Float): Path {
    return Path().apply {
        moveTo(center.x, center.y)
        quadraticBezierTo(
            center.x + maxRadius * 0.3f, center.y - maxRadius * 0.5f,
            center.x, center.y - maxRadius
        )
        quadraticBezierTo(
            center.x - maxRadius * 0.3f, center.y - maxRadius * 0.5f,
            center.x, center.y
        )
        close()
    }
}
