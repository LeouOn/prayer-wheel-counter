package com.prayerwheel.app.ui.wheel.views

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.prayerwheel.app.data.model.Mantra
import com.prayerwheel.app.data.model.WheelSkin
import com.prayerwheel.app.ui.theme.StarGold
import com.prayerwheel.app.ui.theme.TibetanFont
import com.prayerwheel.app.ui.wheel.render.effectiveGlowIntensity
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Abstract view - minimalist mandala/sacred geometry representation.
 * Enhanced with mandala patterns, lotus, aurora effects, and improved Tibetan text.
 */
@Composable
internal fun AbstractViewPrayerWheel(
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
    val textMeasurer = rememberTextMeasurer()
    val glowIntensity = effectiveGlowIntensity(angularVelocity, highestMilestoneTier)

    // Pulsing glow sync with mantra rhythm
    val infiniteTransition = rememberInfiniteTransition(label = "abstractPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )
    val effectivePulse = if (abs(angularVelocity) > 0.1f) pulseAlpha else 0f

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
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            val centerX = canvasWidth / 2f
            val centerY = canvasHeight / 2f
            val baseRadius = minOf(canvasWidth, canvasHeight) * 0.35f

            // Deep indigo/navy gradient background
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1A237E),  // Deep indigo
                        Color(0xFF311B92),  // Dark purple
                        Color(0xFF1A237E)
                    ),
                    startY = 0f,
                    endY = canvasHeight
                )
            )

            // Aurora borealis effect (flowing bands of color)
            if (glowIntensity > 0.2f) {
                val auroraColors = listOf(
                    Color(0x4000FF00),  // Green aurora
                    Color(0x3000FFFF),  // Cyan aurora
                    Color(0x35FF00FF),  // Magenta aurora
                    Color(0x25FFFF00)   // Yellow aurora
                )
                for (i in auroraColors.indices) {
                    val waveOffset = sin((System.currentTimeMillis() % 5000) / 5000f * 2 * PI.toFloat() + i) * 30f
                    val path = Path().apply {
                        moveTo(centerX - canvasWidth, centerY - canvasHeight * 0.3f + waveOffset)
                        cubicTo(
                            centerX - canvasWidth * 0.5f, centerY - canvasHeight * 0.4f + waveOffset + 50f * sin(i.toFloat()),
                            centerX + canvasWidth * 0.5f, centerY - canvasHeight * 0.2f + waveOffset + 50f * cos(i.toFloat()),
                            centerX + canvasWidth, centerY - canvasHeight * 0.3f + waveOffset
                        )
                        lineTo(centerX + canvasWidth, centerY - canvasHeight * 0.1f + waveOffset)
                        cubicTo(
                            centerX + canvasWidth * 0.5f, centerY - canvasHeight * 0.2f + waveOffset + 50f * cos(i.toFloat()),
                            centerX - canvasWidth * 0.5f, centerY - canvasHeight * 0.4f + waveOffset + 50f * sin(i.toFloat()),
                            centerX - canvasWidth, centerY - canvasHeight * 0.1f + waveOffset
                        )
                        close()
                    }
                    drawPath(
                        path = path,
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                auroraColors[i].copy(alpha = glowIntensity * 0.3f),
                                auroraColors[i].copy(alpha = glowIntensity * 0.5f),
                                auroraColors[i].copy(alpha = glowIntensity * 0.3f)
                            )
                        )
                    )
                }
            }

            // Stars with cross-shaped sparkle effects
            val starCount = 30
            val starRandom = (System.currentTimeMillis() / 100).toInt()
            for (i in 0 until starCount) {
                val seed = (i * 17 + starRandom) % 1000
                val starX = (seed / 10f) / 100f * canvasWidth
                val starY = ((seed * 7) % 1000) / 1000f * canvasHeight
                val starSize = 2f + (i % 5) * 1.5f
                val starAlpha = 0.5f + 0.3f * sin((System.currentTimeMillis() / 500f) + i)

                // Cross-shaped sparkle
                val sparkleColor = if (i % 3 == 0) StarGold else Color.White
                drawLine(
                    color = sparkleColor.copy(alpha = starAlpha),
                    start = Offset(starX - starSize, starY),
                    end = Offset(starX + starSize, starY),
                    strokeWidth = 1.5f
                )
                drawLine(
                    color = sparkleColor.copy(alpha = starAlpha),
                    start = Offset(starX, starY - starSize),
                    end = Offset(starX, starY + starSize),
                    strokeWidth = 1.5f
                )
                drawCircle(
                    color = sparkleColor.copy(alpha = starAlpha * 0.5f),
                    radius = starSize * 0.5f,
                    center = Offset(starX, starY)
                )
            }

            // Central lotus/jewel pattern (8-petal lotus)
            val lotusRotation = rotationAngle * 0.3f
            drawLotus(centerX, centerY, baseRadius * 0.35f, lotusRotation, glowIntensity, wheelSkin)

            // Concentric mandala rings with decorative patterns
            val ringCount = 6
            for (i in 1..ringCount) {
                val ringRadius = baseRadius * (i.toFloat() / ringCount)
                val ringRotation = rotationAngle * (if (i % 2 == 0) 1f else -1f) * 0.5f
                val alpha = 0.2f + 0.15f * (1f - i.toFloat() / ringCount)

                // Outer ring with dots
                drawCircle(
                    color = Color(0xFFFFD700).copy(alpha = alpha),
                    radius = ringRadius,
                    center = Offset(centerX, centerY),
                    style = Stroke(width = 1.5f)
                )

                // Decorative dots around ring
                val dotCount = i * 8
                for (j in 0 until dotCount) {
                    val dotAngle = (j * 360f / dotCount + ringRotation) * PI.toFloat() / 180f
                    val dotX = centerX + cos(dotAngle) * ringRadius
                    val dotY = centerY + sin(dotAngle) * ringRadius
                    drawCircle(
                        color = Color(0xFFFFD700).copy(alpha = alpha * 0.8f),
                        radius = 2f + glowIntensity * 2f,
                        center = Offset(dotX, dotY)
                    )
                }
            }

            // Central mandala star pattern (8-pointed)
            val starPoints = 8
            for (i in 0 until starPoints) {
                val angle = (i * 360f / starPoints + rotationAngle * 2f) * PI.toFloat() / 180f
                val endX = centerX + cos(angle) * baseRadius * 0.5f
                val endY = centerY + sin(angle) * baseRadius * 0.5f

                drawLine(
                    color = Color(0xFFFFD700).copy(alpha = 0.4f + glowIntensity * 0.4f),
                    start = Offset(centerX, centerY),
                    end = Offset(endX, endY),
                    strokeWidth = 2f
                )
            }

            // Orbiting mantra text using proper TextMeasurer
            // For Tibetan text, render as a straight line; for romanized, use orbit pattern
            val hasTibetan = currentMantra.tibetan != null
            val mantraText = if (hasTibetan) currentMantra.tibetan!! else currentMantra.romanized
            val textColor = if (hasTibetan) Color(0xFFFFD700) else Color(0xFFFFD700)
            val textStyle = TextStyle(
                color = textColor.copy(alpha = 0.8f + glowIntensity * 0.2f),
                fontSize = if (hasTibetan) 24.sp else 20.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = if (hasTibetan) TibetanFont else FontFamily.Serif
            )

            if (hasTibetan) {
                // Draw Tibetan text as a straight line (horizontal, centered)
                val textLayout = textMeasurer.measure(text = mantraText, style = textStyle)
                drawText(
                    textLayoutResult = textLayout,
                    topLeft = Offset(
                        centerX - textLayout.size.width / 2f,
                        centerY - textLayout.size.height / 2f
                    )
                )
            } else {
                // Draw romanized text in orbit pattern around mandala
                val orbitRadius = baseRadius * 0.75f
                val charCount = mantraText.length.coerceAtMost(30)

                for (i in 0 until charCount) {
                    val charAngle = (i * 360f / charCount + rotationAngle * 2f) * PI.toFloat() / 180f
                    val charX = centerX + cos(charAngle) * orbitRadius
                    val charY = centerY + sin(charAngle) * orbitRadius

                    val charText = mantraText[i].toString()
                    val charLayout = textMeasurer.measure(text = charText, style = textStyle)

                    drawText(
                        textLayoutResult = charLayout,
                        topLeft = Offset(
                            charX - charLayout.size.width / 2f,
                            charY - charLayout.size.height / 2f
                        )
                    )
                }
            }

            // Central pulsing glow that syncs with mantra rhythm
            val pulseGlowAlpha = 0.3f + effectivePulse * 0.4f + glowIntensity * 0.3f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFFFD700).copy(alpha = pulseGlowAlpha),
                        Color(0xFFFF8C00).copy(alpha = pulseGlowAlpha * 0.5f),
                        Color(0x00000000)
                    ),
                    center = Offset(centerX, centerY),
                    radius = baseRadius * 0.3f
                ),
                radius = baseRadius * 0.3f,
                center = Offset(centerX, centerY)
            )

            // Milestone glow
            if (milestoneGlowAlpha > 0f) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFFFD700).copy(alpha = milestoneGlowAlpha * 0.9f),
                            Color(0xFFFFD700).copy(alpha = milestoneGlowAlpha * 0.5f),
                            Color(0x00FFD700)
                        ),
                        center = Offset(centerX, centerY),
                        radius = baseRadius * 2f
                    ),
                    radius = baseRadius * 2f,
                    center = Offset(centerX, centerY)
                )
            }
        }
    }
}

/**
 * Draws an 8-petal lotus mandala pattern.
 */
private fun DrawScope.drawLotus(
    centerX: Float,
    centerY: Float,
    size: Float,
    rotation: Float,
    glowIntensity: Float,
    skin: WheelSkin
) {
    val petalCount = 8
    val petalPath = Path()

    // Draw 8 petals
    for (i in 0 until petalCount) {
        val angle = (i * 360f / petalCount + rotation) * PI.toFloat() / 180f
        val petalLength = size
        val petalWidth = size * 0.4f

        // Calculate petal endpoints using bezier curves
        val tipX = centerX + cos(angle) * petalLength
        val tipY = centerY + sin(angle) * petalLength

        val leftAngle = angle + PI.toFloat() / 2
        val rightAngle = angle - PI.toFloat() / 2
        val leftX = centerX + cos(leftAngle) * petalWidth * 0.3f
        val leftY = centerY + sin(leftAngle) * petalWidth * 0.3f
        val rightX = centerX + cos(rightAngle) * petalWidth * 0.3f
        val rightY = centerY + sin(rightAngle) * petalWidth * 0.3f

        petalPath.apply {
            if (i == 0) {
                moveTo(centerX, centerY)
            }
            quadraticTo(
                leftX + cos(angle) * petalLength * 0.5f,
                leftY + sin(angle) * petalLength * 0.5f,
                tipX,
                tipY
            )
            quadraticTo(
                rightX + cos(angle) * petalLength * 0.5f,
                rightY + sin(angle) * petalLength * 0.5f,
                centerX,
                centerY
            )
        }
    }

    // Draw lotus with gradient
    drawPath(
        path = petalPath,
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFFFFB6C1).copy(alpha = 0.8f + glowIntensity * 0.2f),  // Light pink
                Color(0xFFFF69B4).copy(alpha = 0.6f),  // Hot pink
                Color(0xFFFFFFFF).copy(alpha = 0.4f)   // White center
            ),
            center = Offset(centerX, centerY),
            radius = size
        )
    )

    // Draw central jewel
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFFFFD700).copy(alpha = 0.9f),
                Color(0xFFFF8C00).copy(alpha = 0.7f),
                Color(0xFFFFD700).copy(alpha = 0.5f)
            ),
            center = Offset(centerX - size * 0.1f, centerY - size * 0.1f),
            radius = size * 0.15f
        ),
        radius = size * 0.15f,
        center = Offset(centerX, centerY)
    )
}
