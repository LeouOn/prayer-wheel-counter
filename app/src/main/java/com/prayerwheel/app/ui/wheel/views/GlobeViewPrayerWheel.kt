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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import com.prayerwheel.app.data.model.Mantra
import com.prayerwheel.app.data.model.WheelSkin
import com.prayerwheel.app.ui.wheel.render.effectiveGlowIntensity
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min

@Composable
internal fun GlobeViewPrayerWheel(
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
            .pointerInput(Unit) { detectTapGestures(onLongPress = { onLongPress() }) }
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
            val radius = min(canvasWidth, canvasHeight) * 0.35f

            // Draw sphere base
            val baseColor = Color(wheelSkin.cylinderColor)
            val sphereGradient = Brush.radialGradient(
                colors = listOf(baseColor.copy(alpha=0.9f), baseColor.copy(alpha=0.5f), Color.Black.copy(alpha=0.8f)),
                center = Offset(centerX - radius*0.3f, centerY - radius*0.3f),
                radius = radius * 1.5f
            )

            // Axis line
            drawLine(
                color = Color(wheelSkin.stemColor),
                start = Offset(centerX, centerY - radius * 1.3f),
                end = Offset(centerX, centerY + radius * 1.3f),
                strokeWidth = 16f
            )

            // Globe background
            drawCircle(brush = sphereGradient, radius = radius, center = Offset(centerX, centerY))

            // Lat/Lon wireframe rotating
            val latLonColor = Color(wheelSkin.capColor).copy(alpha = 0.6f + glowIntensity * 0.4f)
            val lines = 8
            for (i in 0 until lines) {
                val angleOffset = (i * 180f / lines + rotationAngle * 20f) * PI.toFloat() / 180f
                val widthFactor = abs(cos(angleOffset))

                // Only draw if width is significant to avoid artifacts
                if (widthFactor > 0.05f) {
                    drawOval(
                        color = latLonColor,
                        topLeft = Offset(centerX - radius * widthFactor, centerY - radius),
                        size = Size(radius * 2f * widthFactor, radius * 2f),
                        style = Stroke(width = 3f + glowIntensity * 2f)
                    )
                }
            }

            // Horizontal equator
            drawOval(
                color = latLonColor,
                topLeft = Offset(centerX - radius, centerY - radius * 0.3f),
                size = Size(radius * 2f, radius * 0.6f),
                style = Stroke(width = 4f)
            )

            // Glow and Send Light
            if (sendLightActive || milestoneGlowAlpha > 0f) {
                drawCircle(
                    color = Color(wheelSkin.rayColor).copy(alpha = maxOf(milestoneGlowAlpha, 0.4f)),
                    radius = radius * 1.2f,
                    center = Offset(centerX, centerY)
                )
            }
        }
    }
}
