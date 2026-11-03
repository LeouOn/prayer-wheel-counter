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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.rememberTextMeasurer
import com.prayerwheel.app.data.model.Mantra
import com.prayerwheel.app.data.model.WheelSkin
import com.prayerwheel.app.ui.wheel.render.drawBottomCap
import com.prayerwheel.app.ui.wheel.render.drawCylinderBody
import com.prayerwheel.app.ui.wheel.render.drawTopCap
import com.prayerwheel.app.ui.wheel.render.effectiveGlowIntensity
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
internal fun TableTopViewPrayerWheel(
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

            // Table top proportions: shorter and wider
            val cylinderWidth = canvasWidth * 0.7f
            val cylinderHeight = canvasHeight * 0.2f
            val cylinderTop = canvasHeight * 0.4f
            val capHeight = cylinderHeight * 0.2f
            val baseHeight = canvasHeight * 0.15f

            // Draw wide base
            val baseColor = Color(wheelSkin.capColor)
            val baseGradient = Brush.verticalGradient(
                colors = listOf(baseColor.copy(alpha=0.9f), baseColor.copy(alpha=0.6f)),
                startY = cylinderTop + cylinderHeight + capHeight,
                endY = cylinderTop + cylinderHeight + capHeight + baseHeight
            )
            drawOval(
                brush = baseGradient,
                topLeft = Offset(centerX - cylinderWidth * 0.6f, cylinderTop + cylinderHeight + capHeight),
                size = Size(cylinderWidth * 1.2f, baseHeight * 1.5f)
            )

            // Light rays
            if (glowIntensity > 0f || sendLightActive) {
                val rayCount = if (sendLightActive) 24 else 12
                for (i in 0 until rayCount) {
                    val angle = (i * 360f / rayCount + rotationAngle * 0.5f) * PI.toFloat() / 180f
                    val length = cylinderWidth * (0.5f + glowIntensity)
                    drawLine(
                        color = Color(wheelSkin.rayColor).copy(alpha = 0.2f * glowIntensity),
                        start = Offset(centerX, cylinderTop + cylinderHeight/2),
                        end = Offset(centerX + cos(angle)*length, cylinderTop + cylinderHeight/2 + sin(angle)*length),
                        strokeWidth = 10f
                    )
                }
            }

            drawBottomCap(centerX, cylinderTop + cylinderHeight, cylinderWidth, capHeight, wheelSkin)
            drawCylinderBody(centerX, cylinderTop, cylinderWidth, cylinderHeight, rotationAngle, angularVelocity, currentMantra, textMeasurer, wheelSkin)
            drawTopCap(centerX, cylinderTop, cylinderWidth, capHeight, wheelSkin)

            if (milestoneGlowAlpha > 0f) {
                drawCircle(
                    color = Color(0xFFFFD700).copy(alpha = milestoneGlowAlpha * 0.5f),
                    radius = cylinderWidth,
                    center = Offset(centerX, cylinderTop + cylinderHeight / 2f)
                )
            }
        }
    }
}
