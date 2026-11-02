package com.prayerwheel.app.ui.wheel.views

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prayerwheel.app.data.model.Mantra
import com.prayerwheel.app.data.model.WheelSkin
import com.prayerwheel.app.ui.wheel.render.drawRainbowRays
import com.prayerwheel.app.ui.wheel.render.effectiveGlowIntensity
import com.prayerwheel.app.ui.wheel.render.getContrastTextColor
import java.lang.Math.toDegrees

/**
 * Top-down view prayer wheel - circular drum viewed from above.
 * Mantra text wraps around the circumference.
 */
@Composable
internal fun TopDownViewPrayerWheel(
    rotationAngle: Float,
    angularVelocity: Float,
    currentMantra: Mantra,
    milestoneGlowAlpha: Float,
    wheelSkin: WheelSkin,
    sendLightActive: Boolean,
    highestMilestoneTier: Int,
    onDragStart: () -> Unit,
    onDragMove: (Float, Float, Float, Float, Long) -> Unit,
    onDragEnd: () -> Unit,
    onLongPress: () -> Unit,
    onPointerCountChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val glowIntensity = effectiveGlowIntensity(angularVelocity, highestMilestoneTier)

    Box(
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures(onLongPress = { onLongPress() })
            }
            .pointerInput(Unit) {
                // Pointer counting only — NOT drag tracking. detectDragGestures below handles drag.
                awaitPointerEventScope {
                    var lastPointerCount = 0
                    while (true) {
                        val event = awaitPointerEvent()
                        val currentPointerCount = event.changes.count { it.pressed }
                        if (currentPointerCount != lastPointerCount) {
                            lastPointerCount = currentPointerCount
                            onPointerCountChange(currentPointerCount)
                        }
                    }
                }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { onDragStart() },
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragEnd() },
                    onDrag = { change, _ ->
                        val currentCenterX = size.width / 2f
                        val currentCenterY = size.height / 2f
                        onDragMove(
                            change.position.x,
                            change.position.y,
                            currentCenterX,
                            currentCenterY,
                            System.currentTimeMillis()
                        )
                        change.consume()
                    }
                )
            }
    ) {
        // Rotating content: circle + text
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    rotationZ = toDegrees(rotationAngle.toDouble()).toFloat()
                },
            contentAlignment = Alignment.Center
        ) {
            // Circle drawn with Canvas
            Canvas(modifier = Modifier.fillMaxSize()) {
                val canvasWidth = size.width
                val canvasHeight = size.height
                val centerX = canvasWidth / 2f
                val centerY = canvasHeight / 2f
                val wheelRadius = minOf(canvasWidth, canvasHeight) * 0.4f

                // Outer rim (gold ring)
                drawCircle(
                    color = Color(wheelSkin.capColor).copy(alpha = 0.8f),
                    radius = wheelRadius,
                    center = Offset(centerX, centerY)
                )

                // Inner drum surface
                val wheelColor = Color(wheelSkin.cylinderColor)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            wheelColor.copy(alpha = 0.95f),
                            wheelColor.copy(alpha = 0.85f),
                            wheelColor.copy(alpha = 0.9f)
                        ),
                        center = Offset(centerX, centerY),
                        radius = wheelRadius * 0.92f
                    ),
                    radius = wheelRadius * 0.92f,
                    center = Offset(centerX, centerY)
                )

                // Decorative rings
                drawCircle(
                    color = Color(wheelSkin.capColor).copy(alpha = 0.4f),
                    radius = wheelRadius * 0.85f,
                    center = Offset(centerX, centerY),
                    style = Stroke(width = 2f)
                )
                drawCircle(
                    color = Color(wheelSkin.capColor).copy(alpha = 0.3f),
                    radius = wheelRadius * 0.7f,
                    center = Offset(centerX, centerY),
                    style = Stroke(width = 1f)
                )

                // Center hub/jewel
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(wheelSkin.crystalColor).copy(alpha = 0.9f),
                            Color(wheelSkin.crystalColor).copy(alpha = 0.5f),
                            Color.Transparent
                        ),
                        center = Offset(centerX - wheelRadius * 0.05f, centerY - wheelRadius * 0.05f),
                        radius = wheelRadius * 0.2f
                    ),
                    radius = wheelRadius * 0.2f,
                    center = Offset(centerX, centerY)
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.4f),
                    radius = wheelRadius * 0.12f,
                    center = Offset(centerX, centerY)
                )
            }

            // Text overlaid using Compose Text (not Canvas drawText)
            // This ensures proper Tibetan rendering
            val tibetanText = currentMantra.tibetan
            val romanizedText = currentMantra.romanized
            val textColor = getContrastTextColor(Color(wheelSkin.cylinderColor))

            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (tibetanText != null) {
                    Text(
                        text = tibetanText,
                        color = textColor,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Serif,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                Text(
                    text = romanizedText,
                    color = textColor,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        // Light rays (drawn OUTSIDE the rotating box so they stay fixed)
        if (glowIntensity > 0.05f || sendLightActive) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val canvasWidth = size.width
                val canvasHeight = size.height
                val centerX = canvasWidth / 2f
                val centerY = canvasHeight / 2f
                val wheelRadius = minOf(canvasWidth, canvasHeight) * 0.4f

                // Rainbow rays
                if (glowIntensity > 0.05f) {
                    drawRainbowRays(centerX, centerY, angularVelocity, wheelRadius * 1.5f)
                }

                // Milestone glow
                if (milestoneGlowAlpha > 0f) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFFFFD700).copy(alpha = milestoneGlowAlpha * 0.8f),
                                Color(0xFFFFD700).copy(alpha = milestoneGlowAlpha * 0.4f),
                                Color(0x00FFD700)
                            ),
                            center = Offset(centerX, centerY),
                            radius = wheelRadius * 1.5f
                        ),
                        radius = wheelRadius * 1.5f,
                        center = Offset(centerX, centerY)
                    )
                }

                // Send light animation
                if (sendLightActive) {
                    val pulseTime = (System.currentTimeMillis() % 3000) / 3000f
                    for (pulse in 0..3) {
                        val circleProgress = ((pulseTime + pulse * 0.25f) % 1f)
                        val circleRadius = wheelRadius * (0.5f + circleProgress * 2f)
                        val circleAlpha = (1f - circleProgress) * 0.3f
                        drawCircle(
                            color = Color(wheelSkin.rayColor).copy(alpha = circleAlpha),
                            radius = circleRadius,
                            center = Offset(centerX, centerY),
                            style = Stroke(width = 4f * (1f - circleProgress))
                        )
                    }
                }
            }
        }
    }
}
