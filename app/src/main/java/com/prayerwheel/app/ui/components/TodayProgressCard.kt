package com.prayerwheel.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.prayerwheel.app.ui.theme.StarGold
import java.math.BigInteger

@Composable
fun TodayProgressCard(
    morningCompleted: Boolean,
    eveningCompleted: Boolean,
    todayMantraCount: BigInteger,
    todayPracticeSeconds: Long,
    currentIntention: String,
    numberFormatStyle: com.prayerwheel.app.data.datastore.NumberFormatStyle =
        com.prayerwheel.app.data.datastore.NumberFormatStyle.STANDARD,
    dailyMantraGoal: Long = 0L,
    dailyMantraProgress: Float = 0f,
    dailyTimeGoalSeconds: Long = 0L,
    dailyTimeProgress: Float = 0f,
    onGoalReached: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val onBackground = MaterialTheme.colorScheme.onBackground
    val muted = onBackground.copy(alpha = 0.6f)

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            val showMantraBar = dailyMantraGoal > 0L
            val showTimeBar = dailyTimeGoalSeconds > 0L
            if (showMantraBar || showTimeBar) {
                if (showMantraBar) {
                    QuietGoalBar(
                        progress = dailyMantraProgress,
                        onGoalReached = onGoalReached
                    )
                }
                if (showMantraBar && showTimeBar) {
                    Spacer(modifier = Modifier.height(3.dp))
                }
                if (showTimeBar) {
                    QuietGoalBar(
                        progress = dailyTimeProgress,
                        onGoalReached = onGoalReached
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                DaySlot(
                    label = "Morning",
                    done = morningCompleted,
                    onColor = onBackground,
                    mutedColor = muted
                )
                DaySlot(
                    label = "Evening",
                    done = eveningCompleted,
                    onColor = onBackground,
                    mutedColor = muted
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            val formattedMantras = NumberFormatter.formatWithStyle(todayMantraCount, numberFormatStyle)
            val practiceLabel = formatPracticeTime(todayPracticeSeconds)
            Text(
                text = "Today: $formattedMantras mantras  ·  $practiceLabel",
                style = MaterialTheme.typography.bodySmall,
                color = muted
            )

            if (currentIntention.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = currentIntention,
                    style = MaterialTheme.typography.bodySmall,
                    fontStyle = FontStyle.Italic,
                    color = muted
                )
            }
        }
    }
}

@Composable
private fun QuietGoalBar(
    progress: Float,
    onGoalReached: () -> Unit
) {
    val trackColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.12f)
    val fillColor = if (progress >= 1f) StarGold else MaterialTheme.colorScheme.primary
    val glow = remember { Animatable(0f) }
    var prevProgress by remember {
        mutableFloatStateOf(if (progress >= 1f) 1f else 0f)
    }

    LaunchedEffect(progress) {
        if (prevProgress < 1f && progress >= 1f) {
            onGoalReached()
            glow.snapTo(1f)
            glow.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing)
            )
        }
        prevProgress = progress
    }

    val glowValue = glow.value
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(3.dp)
            .clip(RoundedCornerShape(1.5.dp))
            .background(trackColor)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .fillMaxHeight()
                .drawBehind {
                    drawRect(color = fillColor)
                    if (glowValue > 0f) {
                        drawRect(color = Color.White.copy(alpha = 0.5f * glowValue))
                    }
                }
        )
    }
}

@Composable
private fun DaySlot(
    label: String,
    done: Boolean,
    onColor: Color,
    mutedColor: Color
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        DoneMarker(done = done, sizeDp = 12.dp, outlineColor = mutedColor)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (done) FontWeight.Medium else FontWeight.Normal,
            color = if (done) onColor else mutedColor
        )
    }
}

@Composable
private fun DoneMarker(done: Boolean, sizeDp: Dp, outlineColor: Color) {
    val sizePx = sizeDp.value
    val radius = sizePx / 2f
    if (done) {
        Spacer(
            modifier = Modifier
                .size(sizeDp)
                .clip(CircleShape)
                .drawBehind {
                    drawCircle(SolidColor(StarGold))
                }
        )
    } else {
        Spacer(
            modifier = Modifier
                .size(sizeDp)
                .drawBehind {
                    drawCircle(
                        color = outlineColor,
                        radius = radius - 0.75.dp.toPx(),
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                }
        )
    }
}

private fun formatPracticeTime(seconds: Long): String {
    if (seconds <= 0L) return "0m"
    val totalMinutes = seconds / 60L
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    return if (hours > 0L) "${hours}h ${minutes}m" else "${minutes}m"
}
