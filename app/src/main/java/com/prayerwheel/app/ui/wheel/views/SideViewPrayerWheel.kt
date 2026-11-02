package com.prayerwheel.app.ui.wheel.views

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.prayerwheel.app.data.model.Mantra
import com.prayerwheel.app.data.model.WheelSkin
import com.prayerwheel.app.ui.wheel.render.drawBase
import com.prayerwheel.app.ui.wheel.render.drawBottomCap
import com.prayerwheel.app.ui.wheel.render.drawCrystal
import com.prayerwheel.app.ui.wheel.render.drawCylinderBody
import com.prayerwheel.app.ui.wheel.render.drawLightRays
import com.prayerwheel.app.ui.wheel.render.drawMilestoneGlow
import com.prayerwheel.app.ui.wheel.render.drawStarParticles
import com.prayerwheel.app.ui.wheel.render.drawStem
import com.prayerwheel.app.ui.wheel.render.drawStringAndWeight
import com.prayerwheel.app.ui.wheel.render.drawTopCap
import com.prayerwheel.app.ui.wheel.render.effectiveGlowIntensity
import com.prayerwheel.app.viewmodel.WheelViewModel
import kotlin.math.sin

/**
 * Side view prayer wheel drawn with Canvas.
 * Shows a traditional Tibetan prayer wheel from the side with:
 * - Crystal/gem finial at top
 * - Top and bottom caps/bevels
 * - Cylinder body with scrolling mantra text
 * - Stem/handle
 * - String with weight (physics-based)
 * - Light rays when spinning
 */
@Composable
internal fun SideViewPrayerWheel(
    rotationAngle: Float,
    angularVelocity: Float,
    currentMantra: Mantra,
    weightAngle: Float,
    milestoneGlowAlpha: Float,
    wheelSkin: WheelSkin,
    sendLightActive: Boolean,
    starParticles: List<com.prayerwheel.app.viewmodel.WheelViewModel.StarParticle>,
    sessionTimeGoalSeconds: Long,
    hasActiveSession: Boolean,
    sessionDuration: Long,
    highestMilestoneTier: Int,
    onUpdateCanvasDimensions: (Float, Float) -> Unit,
    onDragStart: () -> Unit,
    onDragMove: (Float, Float, Float, Float, Long) -> Unit,
    onDragEnd: () -> Unit,
    onLongPress: () -> Unit,
    onPointerCountChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()

    // Calculate glow intensity based on angular velocity, floored by the persistent milestone aura
    val glowIntensity = effectiveGlowIntensity(angularVelocity, highestMilestoneTier)

    // Crystal sway based on angular velocity (small oscillation)
    val crystalSway = if (glowIntensity > 0.1f) {
        sin(rotationAngle * 3f) * glowIntensity * 0.1f
    } else 0f

    Box(
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = { onLongPress() }
                )
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
            
            // Update canvas dimensions in ViewModel for particle physics
            onUpdateCanvasDimensions(canvasWidth, canvasHeight)

            // Dimensions
            val cylinderWidth = canvasWidth * 0.55f
            val cylinderHeight = canvasHeight * 0.28f
            val cylinderTop = canvasHeight * 0.22f
            val capHeight = cylinderHeight * 0.15f
            val stemWidth = cylinderWidth * 0.08f
            val stemHeight = canvasHeight * 0.22f
            val baseHeight = cylinderHeight * 0.12f

            // Calculate attachment point for string (right side of bottom cap)
            val stringAttachX = centerX + cylinderWidth / 2f - capHeight * 0.3f
            val stringAttachY = cylinderTop + cylinderHeight + capHeight

            // Draw components from bottom to top (painter's order)

            // 1. Draw stem/handle
            drawStem(centerX, cylinderTop + cylinderHeight + capHeight * 2, stemWidth, stemHeight, wheelSkin)

            // 2. Draw base/pommel
            drawBase(centerX, cylinderTop + cylinderHeight + capHeight * 2 + stemHeight, cylinderWidth * 0.25f, baseHeight, wheelSkin)

            // 3. Draw light rays (behind cylinder)
            val rayIntensity = if (sendLightActive) {
                // Intensified rays for send light animation
                1f
            } else if (glowIntensity > 0.05f) {
                glowIntensity
            } else 0f

            if (rayIntensity > 0f || sendLightActive) {
                drawLightRays(centerX, cylinderTop, cylinderWidth, cylinderHeight, rayIntensity, rotationAngle, wheelSkin, sendLightActive)
            }

            // 4. Draw string and weight
            drawStringAndWeight(stringAttachX, stringAttachY, weightAngle, glowIntensity, wheelSkin)

            // 5. Draw bottom cap
            drawBottomCap(centerX, cylinderTop + cylinderHeight, cylinderWidth, capHeight, wheelSkin)

            // 6. Draw cylinder body with scrolling text
            drawCylinderBody(
                centerX, cylinderTop, cylinderWidth, cylinderHeight,
                rotationAngle, angularVelocity, currentMantra, textMeasurer, wheelSkin
            )

            // 7. Draw top cap
            drawTopCap(centerX, cylinderTop, cylinderWidth, capHeight, wheelSkin)

            // 8. Draw crystal/gem finial
            drawCrystal(centerX, cylinderTop - capHeight * 0.8f, crystalSway, glowIntensity, wheelSkin)

            // 9. Milestone time-goal progress ring
            if (sessionTimeGoalSeconds > 0 && hasActiveSession) {
                val progress = ((sessionDuration / 1000f) / sessionTimeGoalSeconds).coerceIn(0f, 1f)
                val ringRadius = minOf(canvasWidth, canvasHeight) * 0.38f
                val startAngle = -90f
                val sweepAngle = progress * 360f

                drawArc(
                    color = Color(wheelSkin.rayColor).copy(alpha = 0.15f),
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = Offset(centerX - ringRadius, cylinderTop + cylinderHeight / 2f - ringRadius),
                    size = Size(ringRadius * 2f, ringRadius * 2f),
                    style = Stroke(width = 3.dp.toPx())
                )

                drawArc(
                    color = Color(wheelSkin.rayColor).copy(alpha = 0.65f),
                    startAngle = startAngle,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    topLeft = Offset(centerX - ringRadius, cylinderTop + cylinderHeight / 2f - ringRadius),
                    size = Size(ringRadius * 2f, ringRadius * 2f),
                    style = Stroke(width = 3.dp.toPx())
                )
            }

            // 10. Milestone glow effect
            if (milestoneGlowAlpha > 0f) {
                drawMilestoneGlow(centerX, cylinderTop + cylinderHeight / 2f, cylinderWidth * 1.5f, milestoneGlowAlpha)
            }

            // 11. Draw particles
            if (starParticles.isNotEmpty()) {
                drawStarParticles(starParticles, canvasWidth, canvasHeight)
            }
        }
    }
}
