package com.prayerwheel.app.ui.wheel.render

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.prayerwheel.app.data.model.Mantra
import com.prayerwheel.app.data.model.WheelSkin
import com.prayerwheel.app.ui.theme.RainbowBlue
import com.prayerwheel.app.ui.theme.RainbowGreen
import com.prayerwheel.app.ui.theme.RainbowIndigo
import com.prayerwheel.app.ui.theme.RainbowOrange
import com.prayerwheel.app.ui.theme.RainbowRed
import com.prayerwheel.app.ui.theme.RainbowViolet
import com.prayerwheel.app.ui.theme.RainbowYellow
import com.prayerwheel.app.ui.theme.StarGold
import com.prayerwheel.app.ui.theme.StarLightBlue
import com.prayerwheel.app.ui.theme.StarWhite
import com.prayerwheel.app.ui.theme.TibetanFont
import com.prayerwheel.app.viewmodel.WheelViewModel
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Draws the stem/handle of the prayer wheel.
 */
internal fun DrawScope.drawStem(
    centerX: Float,
    topY: Float,
    stemWidth: Float,
    stemHeight: Float,
    skin: WheelSkin
) {
    // Dark wood/metal gradient
    val stemColor = Color(skin.stemColor)
    val gradient = Brush.verticalGradient(
        colors = listOf(
            stemColor.copy(alpha = 0.9f),
            stemColor.copy(alpha = 0.7f),
            stemColor.copy(alpha = 0.5f),
            stemColor.copy(alpha = 0.7f)
        ),
        startY = topY,
        endY = topY + stemHeight
    )

    // Main stem body with slight taper (wider at bottom)
    val stemPath = Path().apply {
        moveTo(centerX - stemWidth / 2f, topY)
        lineTo(centerX + stemWidth / 2f, topY)
        lineTo(centerX + stemWidth * 0.6f, topY + stemHeight)
        lineTo(centerX - stemWidth * 0.6f, topY + stemHeight)
        close()
    }

    drawPath(
        path = stemPath,
        brush = gradient
    )

    // Subtle highlight on left edge
    drawLine(
        color = stemColor.copy(alpha = 0.4f),
        start = Offset(centerX - stemWidth / 2f, topY),
        end = Offset(centerX - stemWidth * 0.6f, topY + stemHeight),
        strokeWidth = 2f
    )
}

/**
 * Draws the base/pommel of the prayer wheel.
 */
internal fun DrawScope.drawBase(
    centerX: Float,
    topY: Float,
    baseWidth: Float,
    baseHeight: Float,
    skin: WheelSkin
) {
    val capColor = Color(skin.capColor)
    // Metallic gradient based on cap color
    val gradient = Brush.verticalGradient(
        colors = listOf(
            capColor.copy(alpha = 0.9f),
            capColor.copy(alpha = 0.7f),
            capColor.copy(alpha = 0.5f),
            capColor.copy(alpha = 0.7f),
            capColor.copy(alpha = 0.9f)
        )
    )

    // Draw as a rounded shape
    drawOval(
        brush = gradient,
        topLeft = Offset(centerX - baseWidth, topY),
        size = Size(baseWidth * 2f, baseHeight * 1.5f)
    )

    // Outer ring
    drawOval(
        color = capColor.copy(alpha = 0.6f),
        topLeft = Offset(centerX - baseWidth, topY),
        size = Size(baseWidth * 2f, baseHeight * 1.5f),
        style = Stroke(width = 2f)
    )
}

/**
 * Draws light rays emanating from the cylinder.
 */
internal fun DrawScope.drawLightRays(
    centerX: Float,
    cylinderTop: Float,
    cylinderWidth: Float,
    cylinderHeight: Float,
    glowIntensity: Float,
    rotationAngle: Float,
    skin: WheelSkin,
    sendLightActive: Boolean
) {
    val rayCount = if (sendLightActive) 24 else 12
    val rayLength = if (sendLightActive) {
        cylinderWidth * 2f
    } else {
        cylinderWidth * (0.3f + glowIntensity * 0.5f)
    }
    val rayWidth = cylinderWidth * (0.02f + glowIntensity * 0.01f)
    val rayColor = Color(skin.rayColor)

    // Animate ray rotation slowly
    val rayRotation = rotationAngle * 0.3f

    for (i in 0 until rayCount) {
        val baseAngle = (i * 360f / rayCount + rayRotation) * (PI.toFloat() / 180f)

        // Rays emanate from center of cylinder
        val startX = centerX + cos(baseAngle) * cylinderWidth * 0.5f
        val startY = cylinderTop + cylinderHeight / 2f + sin(baseAngle) * cylinderHeight * 0.4f

        val endX = centerX + cos(baseAngle) * (cylinderWidth * 0.5f + rayLength)
        val endY = cylinderTop + cylinderHeight / 2f + sin(baseAngle) * (cylinderHeight * 0.4f + rayLength * 0.3f)

        // Fade alpha based on glow intensity and position
        val alpha = (0.1f + glowIntensity * 0.3f) * (0.5f + 0.5f * sin(baseAngle * 2f))

        drawLine(
            brush = Brush.linearGradient(
                colors = listOf(
                    rayColor.copy(alpha = alpha),
                    rayColor.copy(alpha = alpha * 0.3f),
                    Color(0x00000000)
                ),
                start = Offset(startX, startY),
                end = Offset(endX, endY)
            ),
            start = Offset(startX, startY),
            end = Offset(endX, endY),
            strokeWidth = rayWidth
        )
    }

    // Glow behind cylinder
    drawOval(
        brush = Brush.radialGradient(
            colors = listOf(
                rayColor.copy(alpha = glowIntensity * 0.5f),
                rayColor.copy(alpha = 0f)
            ),
            center = Offset(centerX, cylinderTop + cylinderHeight / 2f),
            radius = cylinderWidth * 0.8f
        ),
        topLeft = Offset(centerX - cylinderWidth * 0.8f, cylinderTop - cylinderHeight * 0.3f),
        size = Size(cylinderWidth * 1.6f, cylinderHeight * 1.6f)
    )

    // Send light: Draw radiating golden circles
    if (sendLightActive) {
        val pulseTime = (System.currentTimeMillis() % 3000) / 3000f
        for (pulse in 0..3) {
            val circleProgress = ((pulseTime + pulse * 0.25f) % 1f)
            val circleRadius = cylinderWidth * (0.5f + circleProgress * 2f)
            val circleAlpha = (1f - circleProgress) * 0.3f
            drawCircle(
                color = rayColor.copy(alpha = circleAlpha),
                radius = circleRadius,
                center = Offset(centerX, cylinderTop + cylinderHeight / 2f),
                style = Stroke(width = 4f * (1f - circleProgress))
            )
        }
    }
}

/**
 * Draws the string with weight based on physics.
 */
internal fun DrawScope.drawStringAndWeight(
    attachX: Float,
    attachY: Float,
    weightAngle: Float,
    glowIntensity: Float,
    skin: WheelSkin
) {
    val stringLength = 80f
    val weightRadius = 12f
    val weightColor = Color(skin.weightColor)

    // Calculate weight position based on angle
    val angleRad = weightAngle * (PI.toFloat() / 180f)
    val weightX = attachX + sin(angleRad) * stringLength
    val weightY = attachY + cos(angleRad) * stringLength

    // Draw curved string (quadratic bezier)
    val midX = attachX + sin(angleRad) * stringLength * 0.5f
    val midY = attachY + stringLength * 0.3f

    val stringPath = Path().apply {
        moveTo(attachX, attachY)
        quadraticTo(midX, midY, weightX, weightY)
    }

    drawPath(
        path = stringPath,
        color = Color(skin.stemColor).copy(alpha = 0.7f),
        style = Stroke(width = 3f)
    )

    // Draw weight/bead
    val weightGradient = Brush.radialGradient(
        colors = listOf(
            weightColor.copy(alpha = 0.9f),
            weightColor.copy(alpha = 0.7f),
            weightColor.copy(alpha = 0.5f)
        ),
        center = Offset(weightX - weightRadius * 0.3f, weightY - weightRadius * 0.3f),
        radius = weightRadius
    )

    drawCircle(
        brush = weightGradient,
        radius = weightRadius,
        center = Offset(weightX, weightY)
    )

    // Weight highlight
    drawCircle(
        color = Color(0x40FFFFFF),
        radius = weightRadius * 0.4f,
        center = Offset(weightX - weightRadius * 0.3f, weightY - weightRadius * 0.3f)
    )
}

/**
 * Draws the top cap/bevel of the prayer wheel.
 */
internal fun DrawScope.drawTopCap(
    centerX: Float,
    cylinderTop: Float,
    cylinderWidth: Float,
    capHeight: Float,
    skin: WheelSkin
) {
    val capColor = Color(skin.capColor)
    // Trapezoid shape wider at top
    val topWidth = cylinderWidth * 1.15f
    val bottomWidth = cylinderWidth * 0.95f

    val capPath = Path().apply {
        moveTo(centerX - topWidth / 2f, cylinderTop - capHeight)
        lineTo(centerX + topWidth / 2f, cylinderTop - capHeight)
        lineTo(centerX + bottomWidth / 2f, cylinderTop)
        lineTo(centerX - bottomWidth / 2f, cylinderTop)
        close()
    }

    // Metallic gradient based on skin
    val gradient = Brush.verticalGradient(
        colors = listOf(
            capColor.copy(alpha = 0.9f),
            capColor.copy(alpha = 0.8f),
            capColor.copy(alpha = 0.7f),
            capColor.copy(alpha = 0.6f),
            capColor.copy(alpha = 0.8f)
        ),
        startY = cylinderTop - capHeight,
        endY = cylinderTop
    )

    drawPath(path = capPath, brush = gradient)

    // Darker bottom edge for 3D effect
    drawLine(
        color = capColor.copy(alpha = 0.5f),
        start = Offset(centerX - bottomWidth / 2f, cylinderTop),
        end = Offset(centerX + bottomWidth / 2f, cylinderTop),
        strokeWidth = 2f
    )

    // Decorative ring
    drawLine(
        color = capColor.copy(alpha = 0.7f),
        start = Offset(centerX - topWidth / 2f + 4f, cylinderTop - capHeight + 2f),
        end = Offset(centerX + topWidth / 2f - 4f, cylinderTop - capHeight + 2f),
        strokeWidth = 1.5f
    )
}

/**
 * Draws the bottom cap/bevel of the prayer wheel.
 */
internal fun DrawScope.drawBottomCap(
    centerX: Float,
    cylinderBottom: Float,
    cylinderWidth: Float,
    capHeight: Float,
    skin: WheelSkin
) {
    val capColor = Color(skin.capColor)
    // Trapezoid shape wider at bottom
    val topWidth = cylinderWidth * 0.95f
    val bottomWidth = cylinderWidth * 1.15f

    val capPath = Path().apply {
        moveTo(centerX - topWidth / 2f, cylinderBottom)
        lineTo(centerX + topWidth / 2f, cylinderBottom)
        lineTo(centerX + bottomWidth / 2f, cylinderBottom + capHeight)
        lineTo(centerX - bottomWidth / 2f, cylinderBottom + capHeight)
        close()
    }

    // Metallic gradient based on skin (same as top but reversed)
    val gradient = Brush.verticalGradient(
        colors = listOf(
            capColor.copy(alpha = 0.7f),
            capColor.copy(alpha = 0.6f),
            capColor.copy(alpha = 0.8f),
            capColor.copy(alpha = 0.9f),
            capColor.copy(alpha = 0.8f)
        ),
        startY = cylinderBottom,
        endY = cylinderBottom + capHeight
    )

    drawPath(path = capPath, brush = gradient)

    // Darker top edge for 3D effect
    drawLine(
        color = capColor.copy(alpha = 0.5f),
        start = Offset(centerX - topWidth / 2f, cylinderBottom),
        end = Offset(centerX + topWidth / 2f, cylinderBottom),
        strokeWidth = 2f
    )
}

/**
 * Draws the cylinder body with scrolling mantra text.
 * Text wraps around the cylinder in multiple rows for larger display.
 */
internal fun DrawScope.drawCylinderBody(
    centerX: Float,
    cylinderTop: Float,
    cylinderWidth: Float,
    cylinderHeight: Float,
    rotationAngle: Float,
    angularVelocity: Float,
    mantra: Mantra,
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    skin: WheelSkin
) {
    val cylinderColor = Color(skin.cylinderColor)
    // Dynamic gradient based on speed
    val speedFactor = min(1f, abs(angularVelocity) / 8f)
    val bodyGradient = Brush.verticalGradient(
        colors = listOf(
            cylinderColor.copy(alpha = 0.8f + 0.1f * speedFactor),
            cylinderColor.copy(alpha = 0.9f + 0.1f * speedFactor),
            cylinderColor,
            cylinderColor.copy(alpha = 0.9f + 0.1f * speedFactor),
            cylinderColor.copy(alpha = 0.8f + 0.1f * speedFactor)
        ),
        startY = cylinderTop,
        endY = cylinderTop + cylinderHeight
    )

    // Main cylinder body with rounded corners
    drawRoundRect(
        brush = bodyGradient,
        topLeft = Offset(centerX - cylinderWidth / 2f, cylinderTop),
        size = Size(cylinderWidth, cylinderHeight),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f)
    )

    // 3D depth shadow on left edge
    drawRoundRect(
        brush = Brush.horizontalGradient(
            colors = listOf(
                cylinderColor.copy(alpha = 0.4f),
                Color(0x00000000)
            ),
            startX = centerX - cylinderWidth / 2f,
            endX = centerX - cylinderWidth / 2f + cylinderWidth * 0.15f
        ),
        topLeft = Offset(centerX - cylinderWidth / 2f, cylinderTop),
        size = Size(cylinderWidth * 0.15f, cylinderHeight),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f)
    )

    // 3D depth shadow on right edge
    drawRoundRect(
        brush = Brush.horizontalGradient(
            colors = listOf(
                Color(0x00000000),
                cylinderColor.copy(alpha = 0.3f)
            ),
            startX = centerX + cylinderWidth / 2f - cylinderWidth * 0.15f,
            endX = centerX + cylinderWidth / 2f
        ),
        topLeft = Offset(centerX + cylinderWidth / 2f - cylinderWidth * 0.15f, cylinderTop),
        size = Size(cylinderWidth * 0.15f, cylinderHeight),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f)
    )

    // Vertical lines representing cylinder surface texture — scroll with rotation
    val lineCount = 12
    val lineSpacing = cylinderWidth / (lineCount + 1)
    val lineScrollOffset = (rotationAngle * cylinderWidth / (2 * PI.toFloat())) % lineSpacing
    for (i in -1 until lineCount + 2) {
        val x = centerX - cylinderWidth / 2f + (i + 1) * lineSpacing + lineScrollOffset
        // Only draw lines within cylinder bounds
        if (x > centerX - cylinderWidth / 2f + 4f && x < centerX + cylinderWidth / 2f - 4f) {
            val alpha = 0.08f + 0.04f * sin(i.toFloat())
            drawLine(
                color = Color.Black.copy(alpha = alpha),
                start = Offset(x, cylinderTop + cylinderHeight * 0.05f),
                end = Offset(x, cylinderTop + cylinderHeight * 0.95f),
                strokeWidth = 1f
            )
        }
    }

    // Scrolling mantra text — 2 rows: Tibetan (upper) + Romanized (lower)
    val tibetanText = mantra.tibetan ?: mantra.romanized
    val romanizedText = mantra.romanized

    // Use contrast-aware text color based on cylinder background
    val textColor = getContrastTextColor(cylinderColor)

    // Row 1: Tibetan text — large and bold
    val tibetanTextStyle = TextStyle(
        color = textColor,
        fontSize = (cylinderHeight * 0.16f).sp,
        fontWeight = FontWeight.Bold,
        fontFamily = TibetanFont
    )

    // Row 2: Romanized text — medium, slightly transparent
    val romanizedTextStyle = TextStyle(
        color = textColor.copy(alpha = 0.8f),
        fontSize = (cylinderHeight * 0.09f).sp,
        fontWeight = FontWeight.Medium
    )

    val tibetanLayout = textMeasurer.measure(text = tibetanText, style = tibetanTextStyle)
    val tibetanTextWidth = tibetanLayout.size.width.toFloat().coerceAtLeast(1f)
    val tibetanSpacing = (tibetanTextWidth * 1.3f).coerceAtLeast(1f)

    val romanizedLayout = textMeasurer.measure(text = romanizedText, style = romanizedTextStyle)
    val romanizedWidth = romanizedLayout.size.width.toFloat().coerceAtLeast(1f)
    val romanizedSpacing = (romanizedWidth * 1.5f).coerceAtLeast(1f)

    // Calculate scroll offset based on rotation angle
    val scrollOffset = (rotationAngle * tibetanTextWidth / (2 * PI.toFloat())) % tibetanSpacing

    // Clip to cylinder bounds
    val clipLeft = centerX - cylinderWidth / 2f + 12f
    val clipRight = centerX + cylinderWidth / 2f - 12f

    // Row 1: Tibetan text at 35% height
    val tibetanY = cylinderTop + cylinderHeight * 0.35f
    val tibetanStartX = clipLeft + scrollOffset - tibetanSpacing
    var tibetanX = tibetanStartX

    while (tibetanX < clipRight + tibetanSpacing) {
        // Apply perspective distortion near edges
        val normalizedX = ((tibetanX - centerX) / (cylinderWidth / 2f)).coerceIn(-1f, 1f)
        val distortion = 1f - abs(normalizedX) * 0.3f

        if (tibetanX + tibetanTextWidth * distortion > clipLeft && tibetanX < clipRight) {
            drawText(
                textLayoutResult = textMeasurer.measure(
                    text = tibetanText,
                    style = tibetanTextStyle.copy(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                textColor.copy(alpha = 0.9f),
                                textColor.copy(alpha = 0.75f),
                                textColor.copy(alpha = 0.9f)
                            )
                        )
                    )
                ),
                topLeft = Offset(
                    x = tibetanX + (tibetanTextWidth * (1f - distortion) / 2f),
                    y = tibetanY - tibetanLayout.size.height / 2f
                )
            )
        }
        tibetanX += tibetanSpacing
    }

    // Row 2: Romanized text at 68% height, slightly different scroll speed
    val romanizedY = cylinderTop + cylinderHeight * 0.68f
    val romanizedScrollOffset = scrollOffset * 0.8f
    val romanizedStartX = clipLeft + romanizedScrollOffset - romanizedSpacing
    var romanizedX = romanizedStartX

    while (romanizedX < clipRight + romanizedSpacing) {
        val normalizedX = ((romanizedX - centerX) / (cylinderWidth / 2f)).coerceIn(-1f, 1f)
        val distortion = 1f - abs(normalizedX) * 0.3f

        if (romanizedX + romanizedWidth * distortion > clipLeft && romanizedX < clipRight) {
            drawText(
                textLayoutResult = romanizedLayout,
                topLeft = Offset(
                    x = romanizedX + (romanizedWidth * (1f - distortion) / 2f),
                    y = romanizedY - romanizedLayout.size.height / 2f
                )
            )
        }
        romanizedX += romanizedSpacing
    }

    // Edge fade gradients to simulate 3D curvature
    // Left edge fade
    drawRoundRect(
        brush = Brush.horizontalGradient(
            colors = listOf(
                cylinderColor.copy(alpha = 0.8f),
                Color.Transparent
            ),
            startX = centerX - cylinderWidth / 2f,
            endX = centerX - cylinderWidth / 2f + cylinderWidth * 0.12f
        ),
        topLeft = Offset(centerX - cylinderWidth / 2f, cylinderTop),
        size = Size(cylinderWidth * 0.12f, cylinderHeight),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f)
    )
    // Right edge fade
    drawRoundRect(
        brush = Brush.horizontalGradient(
            colors = listOf(
                Color.Transparent,
                cylinderColor.copy(alpha = 0.8f)
            ),
            startX = centerX + cylinderWidth / 2f - cylinderWidth * 0.12f,
            endX = centerX + cylinderWidth / 2f
        ),
        topLeft = Offset(centerX + cylinderWidth / 2f - cylinderWidth * 0.12f, cylinderTop),
        size = Size(cylinderWidth * 0.12f, cylinderHeight),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f)
    )

    // Inner decorative border
    drawRoundRect(
        color = cylinderColor.copy(alpha = 0.4f),
        topLeft = Offset(centerX - cylinderWidth / 2f + 4f, cylinderTop + 4f),
        size = Size(cylinderWidth - 8f, cylinderHeight - 8f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f),
        style = Stroke(width = 1.5f)
    )
}

/**
 * Draws the crystal/gem finial at the top.
 * Enhanced with 2.5x larger size, 6-pointed facets, pulsing glow, and internal sparkles.
 */
internal fun DrawScope.drawCrystal(
    centerX: Float,
    topY: Float,
    swayAmount: Float,
    glowIntensity: Float,
    skin: WheelSkin
) {
    // 2.5x larger crystal (was 40f height, 24f width)
    val crystalHeight = 100f
    val crystalWidth = 60f
    val crystalColor = Color(skin.crystalColor)
    val capColor = Color(skin.capColor)

    // Apply sway based on angular velocity
    val offsetX = swayAmount * 8f

    // Pulsing glow that intensifies with spinning speed
    val pulseTime = (System.currentTimeMillis() % 1000) / 1000f
    val pulseGlow = 1f + sin(pulseTime * 2 * PI.toFloat()) * 0.2f
    val effectiveGlow = glowIntensity * pulseGlow

    // Draw outer glow (larger and more intense)
    if (effectiveGlow > 0.1f) {
        drawOval(
            brush = Brush.radialGradient(
                colors = listOf(
                    crystalColor.copy(alpha = effectiveGlow * 0.7f),
                    crystalColor.copy(alpha = effectiveGlow * 0.4f),
                    crystalColor.copy(alpha = 0f)
                ),
                center = Offset(centerX + offsetX, topY + crystalHeight / 2f),
                radius = crystalWidth * 2.5f
            ),
            topLeft = Offset(centerX + offsetX - crystalWidth * 2.5f, topY - crystalHeight * 0.3f),
            size = Size(crystalWidth * 5f, crystalHeight * 1.8f)
        )
    }

    // Draw 6-pointed star/diamond crystal shape
    val crystalPath = Path().apply {
        // Create a 6-pointed crystal with 3 main facets
        val points = 6
        for (i in 0 until points) {
            val angle = i * 60f * PI.toFloat() / 180f - PI.toFloat() / 2
            val radius = if (i % 2 == 0) crystalHeight / 2f else crystalHeight / 4f
            val x = centerX + offsetX + radius * cos(angle)
            val y = topY + crystalHeight / 2f + radius * sin(angle)
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }

    // Crystal gradient with light refraction effect
    val crystalGradient = Brush.verticalGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.95f),
            crystalColor.copy(alpha = 0.9f),
            crystalColor.copy(alpha = 0.7f),
            Color.White.copy(alpha = 0.8f),
            crystalColor.copy(alpha = 0.6f)
        ),
        startY = topY,
        endY = topY + crystalHeight
    )

    drawPath(path = crystalPath, brush = crystalGradient)

    // Inner facets for light refraction
    val facetCount = 3
    for (i in 0 until facetCount) {
        val angle = i * 120f * PI.toFloat() / 180f
        val facetPath = Path().apply {
            val centerPtX = centerX + offsetX
            val centerPtY = topY + crystalHeight / 2f
            val tipX = centerX + offsetX + crystalHeight / 3f * cos(angle)
            val tipY = topY + crystalHeight / 2f + crystalHeight / 3f * sin(angle)
            val leftX = centerX + offsetX + crystalWidth / 2f * cos(angle + PI.toFloat() / 6f)
            val leftY = topY + crystalHeight / 2f + crystalWidth / 2f * sin(angle + PI.toFloat() / 6f)
            val rightX = centerX + offsetX + crystalWidth / 2f * cos(angle - PI.toFloat() / 6f)
            val rightY = topY + crystalHeight / 2f + crystalWidth / 2f * sin(angle - PI.toFloat() / 6f)

            moveTo(centerPtX, centerPtY)
            lineTo(tipX, tipY)
            lineTo(leftX, leftY)
            close()
        }
        drawPath(
            path = facetPath,
            color = Color.White.copy(alpha = 0.25f)
        )
    }

    // Crystal highlight (main bright spot)
    val highlightPath = Path().apply {
        moveTo(centerX + offsetX - crystalWidth * 0.25f, topY + crystalHeight * 0.15f)
        lineTo(centerX + offsetX - crystalWidth * 0.05f, topY + crystalHeight * 0.35f)
        lineTo(centerX + offsetX - crystalWidth * 0.25f, topY + crystalHeight * 0.25f)
        close()
    }

    drawPath(
        path = highlightPath,
        color = Color.White.copy(alpha = 0.8f)
    )

    // Internal sparkles that move with angular velocity
    if (glowIntensity > 0.1f) {
        val sparkleCount = 4
        for (i in 0 until sparkleCount) {
            val sparkleAngle = (glowIntensity * 20 + i * 90) * PI.toFloat() / 180f
            val sparkleRadius = crystalWidth * 0.3f
            val sx = centerX + offsetX + sparkleRadius * cos(sparkleAngle)
            val sy = topY + crystalHeight / 2f + sparkleRadius * sin(sparkleAngle)
            val sparkleSize = 3f * glowIntensity
            drawCircle(
                color = Color.White.copy(alpha = glowIntensity * 0.9f),
                radius = sparkleSize,
                center = Offset(sx, sy)
            )
        }
    }

    // Crystal outline
    drawPath(
        path = crystalPath,
        color = crystalColor.copy(alpha = 0.6f),
        style = Stroke(width = 2f)
    )

    // Rainbow shimmer effect when spinning fast
    if (glowIntensity > 0.5f) {
        val shimmerColors = listOf(
            RainbowRed.copy(alpha = 0.3f * glowIntensity),
            RainbowOrange.copy(alpha = 0.3f * glowIntensity),
            RainbowYellow.copy(alpha = 0.3f * glowIntensity),
            RainbowGreen.copy(alpha = 0.3f * glowIntensity),
            RainbowBlue.copy(alpha = 0.3f * glowIntensity)
        )
        for (i in shimmerColors.indices) {
            val shimmerAngle = (i * 72 + glowIntensity * 30) * PI.toFloat() / 180f
            val shimmerX = centerX + offsetX + crystalWidth * 0.4f * cos(shimmerAngle)
            val shimmerY = topY + crystalHeight / 2f + crystalWidth * 0.4f * sin(shimmerAngle)
            drawCircle(
                color = shimmerColors[i],
                radius = 2f * glowIntensity,
                center = Offset(shimmerX, shimmerY)
            )
        }
    }

    // Small decorative ring between crystal and top cap
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                capColor.copy(alpha = 0.9f),
                capColor.copy(alpha = 0.7f)
            )
        ),
        radius = 8f,
        center = Offset(centerX, topY + crystalHeight / 2f + 6f)
    )
}

/**
 * Draws milestone glow effect.
 */
internal fun DrawScope.drawMilestoneGlow(
    centerX: Float,
    centerY: Float,
    radius: Float,
    alpha: Float
) {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFFFFD700).copy(alpha = alpha * 0.8f),
                Color(0xFFFFD700).copy(alpha = alpha * 0.4f),
                Color(0x00FFD700)
            ),
            center = Offset(centerX, centerY),
            radius = radius
        ),
        radius = radius,
        center = Offset(centerX, centerY)
    )
}

/**
 * Draws rainbow light rays emanating from the cylinder when spinning.
 * Spiritual, not garish - uses subtle semi-transparent rays.
 */
internal fun DrawScope.drawRainbowRays(
    centerX: Float,
    centerY: Float,
    angularVelocity: Float,
    radius: Float
) {
    val intensity = (abs(angularVelocity) / 12f).coerceIn(0f, 1f)
    if (intensity < 0.05f) return

    val rainbowColors = listOf(
        RainbowRed.copy(alpha = intensity * 0.25f),
        RainbowOrange.copy(alpha = intensity * 0.25f),
        RainbowYellow.copy(alpha = intensity * 0.25f),
        RainbowGreen.copy(alpha = intensity * 0.25f),
        RainbowBlue.copy(alpha = intensity * 0.25f),
        RainbowIndigo.copy(alpha = intensity * 0.25f),
        RainbowViolet.copy(alpha = intensity * 0.25f)
    )

    val rayCount = rainbowColors.size
    val rayRotation = angularVelocity * 0.05f

    for (i in 0 until rayCount) {
        val baseAngle = (i * 360f / rayCount + rayRotation) * PI.toFloat() / 180f
        val rayLength = radius * (1.5f + intensity)

        val startX = centerX + cos(baseAngle) * radius * 0.8f
        val startY = centerY + sin(baseAngle) * radius * 0.8f
        val endX = centerX + cos(baseAngle) * rayLength
        val endY = centerY + sin(baseAngle) * rayLength

        drawLine(
            brush = Brush.linearGradient(
                colors = listOf(
                    rainbowColors[i],
                    rainbowColors[i].copy(alpha = 0f)
                ),
                start = Offset(startX, startY),
                end = Offset(endX, endY)
            ),
            start = Offset(startX, startY),
            end = Offset(endX, endY),
            strokeWidth = 3f * intensity
        )
    }
}

/**
 * Draws a 5-pointed star shape.
 */
private fun DrawScope.drawStar(
    centerX: Float,
    centerY: Float,
    size: Float,
    color: Color,
    alpha: Float
) {
    val starPath = Path().apply {
        val points = 5
        val outerRadius = size
        val innerRadius = size * 0.4f

        for (i in 0 until points * 2) {
            val angle = (i * 144f - 90f) * PI.toFloat() / 180f
            val radius = if (i % 2 == 0) outerRadius else innerRadius
            val x = centerX + radius * cos(angle)
            val y = centerY + radius * sin(angle)
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }

    drawPath(
        path = starPath,
        color = color.copy(alpha = alpha)
    )
}

/**
 * Draws star particles floating upward.
 */
internal fun DrawScope.drawStarParticles(
    stars: List<WheelViewModel.StarParticle>,
    canvasWidth: Float,
    canvasHeight: Float
) {
    val starColors = listOf(StarGold, StarWhite, StarLightBlue)

    for (star in stars) {
        if (star.y > 0 && star.y < canvasHeight && star.x > 0 && star.x < canvasWidth) {
            drawStar(
                centerX = star.x,
                centerY = star.y,
                size = star.size,
                color = starColors.getOrElse(star.colorIndex) { StarGold },
                alpha = star.alpha
            )
        }
    }
}

/**
 * Returns a text color (black or white) that contrasts well with the given background color.
 * Uses relative luminance calculation to determine contrast.
 */
internal fun getContrastTextColor(backgroundColor: Color): Color {
    val r = backgroundColor.red
    val g = backgroundColor.green
    val b = backgroundColor.blue
    // Calculate relative luminance using standard coefficients
    val luminance = 0.299 * r + 0.587 * g + 0.114 * b
    return if (luminance > 0.5) Color(0xFF1A1A1A) else Color(0xFFF5F5F5)
}
