package com.prayerwheel.app.ui.wheel.views

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.prayerwheel.app.data.model.Mantra
import com.prayerwheel.app.data.model.WheelSkin
import com.prayerwheel.app.ui.theme.TibetanFont
import com.prayerwheel.app.ui.wheel.render.effectiveGlowIntensity
import com.prayerwheel.app.ui.wheel.render.getContrastTextColor
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**

 * Dual wheels view - shows two prayer wheels side by side.
 * Left wheel spins when touching the left half of the screen,
 * Right wheel spins when touching the right half.
 */
@Composable
internal fun DualWheelsView(
    leftRotationAngle: Float,
    rightRotationAngle: Float,
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
            val wheelRadius = minOf(canvasWidth, canvasHeight) * 0.25f

            // Draw left wheel on left side
            val leftCenterX = canvasWidth * 0.25f
            val leftCenterY = canvasHeight * 0.5f
            drawWheelInCircle(
                centerX = leftCenterX,
                centerY = leftCenterY,
                radius = wheelRadius,
                rotationAngle = leftRotationAngle,
                glowIntensity = glowIntensity,
                wheelSkin = wheelSkin,
                textMeasurer = textMeasurer
            )

            // Draw right wheel on right side
            val rightCenterX = canvasWidth * 0.75f
            val rightCenterY = canvasHeight * 0.5f
            drawWheelInCircle(
                centerX = rightCenterX,
                centerY = rightCenterY,
                radius = wheelRadius,
                rotationAngle = rightRotationAngle,
                glowIntensity = glowIntensity,
                wheelSkin = wheelSkin,
                textMeasurer = textMeasurer
            )

            // Draw separator line
            drawLine(
                color = Color.White.copy(alpha = 0.2f),
                start = Offset(canvasWidth / 2f, canvasHeight * 0.2f),
                end = Offset(canvasWidth / 2f, canvasHeight * 0.8f),
                strokeWidth = 1f
            )
        }
    }
}

/**
 * Draws a prayer wheel inside a circle.
 */
private fun DrawScope.drawWheelInCircle(
    centerX: Float,
    centerY: Float,
    radius: Float,
    rotationAngle: Float,
    glowIntensity: Float,
    wheelSkin: WheelSkin,
    textMeasurer: androidx.compose.ui.text.TextMeasurer
) {
    val wheelColor = Color(wheelSkin.cylinderColor)

    // Outer ring
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                wheelColor.copy(alpha = 0.9f),
                wheelColor.copy(alpha = 0.7f)
            ),
            center = Offset(centerX, centerY),
            radius = radius
        ),
        radius = radius,
        center = Offset(centerX, centerY)
    )

    // Inner decorative ring
    drawCircle(
        color = Color(wheelSkin.capColor).copy(alpha = 0.5f),
        radius = radius * 0.85f,
        center = Offset(centerX, centerY),
        style = Stroke(width = 2f)
    )

    // Draw rotating text around circumference
    val tibetanText = "ॐ मणि पद्मे हूँ"
    val textColor = getContrastTextColor(wheelColor)
    val textStyle = TextStyle(
        color = textColor.copy(alpha = 0.9f),
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = TibetanFont
    )

    val textLayout = textMeasurer.measure(text = tibetanText, style = textStyle)
    val charsVisible = 12

    for (i in 0 until charsVisible) {
        val charAngle = (i * 360f / charsVisible + rotationAngle * 3f) * PI.toFloat() / 180f
        val textX = centerX + cos(charAngle) * radius * 0.75f
        val textY = centerY + sin(charAngle) * radius * 0.75f

        val charText = tibetanText[i % tibetanText.length].toString()
        val charLayout = textMeasurer.measure(text = charText, style = textStyle)

        drawText(
            textLayoutResult = charLayout,
            topLeft = Offset(
                textX - charLayout.size.width / 2f,
                textY - charLayout.size.height / 2f
            )
        )
    }

    // Center crystal
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(wheelSkin.crystalColor).copy(alpha = 0.8f + glowIntensity * 0.2f),
                Color(wheelSkin.crystalColor).copy(alpha = 0.4f),
                Color.Transparent
            ),
            center = Offset(centerX - radius * 0.1f, centerY - radius * 0.1f),
            radius = radius * 0.25f
        ),
        radius = radius * 0.25f,
        center = Offset(centerX, centerY)
    )
}
