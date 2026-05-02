package com.prayerwheel.app.ui.wheel

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prayerwheel.app.data.datastore.SpinMode
import com.prayerwheel.app.data.model.Mantra
import com.prayerwheel.app.data.model.WheelSkin
import com.prayerwheel.app.data.model.WheelSkins
import com.prayerwheel.app.ui.components.CapacitySlider
import com.prayerwheel.app.ui.components.CounterDisplay
import com.prayerwheel.app.ui.components.IntentionDialog
import com.prayerwheel.app.ui.components.MantraSelector
import com.prayerwheel.app.ui.components.ShareIntentionDialog
import com.prayerwheel.app.ui.components.SpinModeSelector
import com.prayerwheel.app.ui.components.WheelCustomizer
import com.prayerwheel.app.viewmodel.WheelViewModel
import java.math.BigInteger
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Power-of-ten thresholds for milestone animations.
 * 1,000 → 10,000 → 100,000 → 1,000,000 → 10,000,000 → 100,000,000 → 1,000,000,000 → 1,000,000,000,000
 */
private val MILESTONE_THRESHOLDS = listOf(
    BigInteger("1000"),
    BigInteger("10000"),
    BigInteger("100000"),
    BigInteger("1000000"),
    BigInteger("10000000"),
    BigInteger("100000000"),
    BigInteger("1000000000"),
    BigInteger("1000000000000")
)

/**
 * Main prayer wheel screen.
 *
 * Implements drag-to-spin interaction with momentum physics.
 * The wheel rotates when dragged horizontally, simulating cylinder turning.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WheelScreen(
    viewModel: WheelViewModel,
    onNavigateToHistory: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToStats: () -> Unit,
    onNavigateToCalculator: () -> Unit,
    modifier: Modifier = Modifier
) {
    val rotationAngle by viewModel.rotationAngle.collectAsState()
    val angularVelocity by viewModel.angularVelocity.collectAsState()
    val sessionMantras by viewModel.sessionMantras.collectAsState()
    val lifetimeMantras by viewModel.lifetimeMantras.collectAsState()
    val mantrasPerRotation by viewModel.mantrasPerRotation.collectAsState()
    val currentMantra by viewModel.currentMantra.collectAsState()
    val hapticEnabled by viewModel.hapticEnabled.collectAsState()
    val spinMode by viewModel.spinMode.collectAsState()
    val autoSpinRpm by viewModel.autoSpinRpm.collectAsState()
    val twoHandedEngaged by viewModel.twoHandedEngaged.collectAsState()
    val autoSpinActive by viewModel.autoSpinActive.collectAsState()
    val showDedicationPrompt by viewModel.showDedicationPrompt.collectAsState()
    val hasActiveSession by viewModel.hasActiveSession.collectAsState()
    val customDedication by viewModel.customDedication.collectAsState(initial = null)
    val sessionDuration by viewModel.sessionDuration.collectAsState()
    val currentRpm by viewModel.currentRpm.collectAsState()
    val isSpinning by viewModel.isSpinning.collectAsState()
    val currentIntention by viewModel.currentIntention.collectAsState()
    val sessionGoal by viewModel.sessionGoal.collectAsState()
    val weightAngle by viewModel.weightAngle.collectAsState()
    val selectedSkin by viewModel.selectedSkin.collectAsState()
    val sendLightActive by viewModel.sendLightActive.collectAsState()

    // Track pointer count for two-handed mode
    var pointerCount by remember { mutableIntStateOf(0) }
    
    // Show intention dialog state
    var showIntentionDialog by remember { mutableStateOf(false) }
    var showShareDialog by remember { mutableStateOf(false) }
    var showCustomizer by remember { mutableStateOf(false) }

    // Milestone glow animation state
    var milestoneGlowAlpha by remember { mutableStateOf(0f) }
    var lastMilestoneReached by remember { mutableStateOf<BigInteger?>(null) }

    // Background gradient drift
    val backgroundWarmth = (sessionDuration / 600000f).coerceIn(0f, 1f) // Max warmth after 10 minutes

    Box(modifier = modifier.fillMaxSize()) {
        // Animated background gradient
        val infiniteTransition = rememberInfiniteTransition(label = "backgroundDrift")
        val gradientOffset by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 30000, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "gradientOffset"
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFFF5F0E8).copy(alpha = 1f - backgroundWarmth * 0.3f),
                            Color(0xFFFFF8E7).copy(alpha = 1f - backgroundWarmth * 0.2f),
                            Color(0xFFFBF0D8).copy(alpha = 1f - backgroundWarmth * 0.1f)
                        ),
                        start = Offset(0f, gradientOffset * 500f),
                        end = Offset(1000f, 1000f + gradientOffset * 500f)
                    )
                )
        )

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "Prayer Wheel",
                            style = MaterialTheme.typography.titleLarge
                        )
                    },
                    actions = {
                        IconButton(onClick = onNavigateToCalculator) {
                            Icon(
                                imageVector = Icons.Default.DateRange,
                                contentDescription = "Calculator"
                            )
                        }
                        IconButton(onClick = { showCustomizer = true }) {
                            Icon(
                                imageVector = Icons.Default.Favorite,
                                contentDescription = "Customize"
                            )
                        }
                        IconButton(onClick = { showShareDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share"
                            )
                        }
                        IconButton(onClick = onNavigateToStats) {
                            Icon(
                                imageVector = Icons.Default.List,
                                contentDescription = "Statistics"
                            )
                        }
                        IconButton(onClick = onNavigateToSettings) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings"
                            )
                        }
                        IconButton(onClick = onNavigateToHistory) {
                            Icon(
                                imageVector = Icons.Default.DateRange,
                                contentDescription = "History"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
                    )
                )
            },
            containerColor = Color.Transparent,
            modifier = Modifier.fillMaxSize()
        ) { paddingValues ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                // Spin mode selector
                SpinModeSelector(
                    selectedMode = spinMode,
                    onModeSelected = { viewModel.setSpinMode(it) },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Prayer wheel with drag interaction (side view)
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .height(320.dp),
                    contentAlignment = Alignment.Center
                ) {
                    SideViewPrayerWheel(
                        rotationAngle = rotationAngle,
                        angularVelocity = angularVelocity,
                        currentMantra = currentMantra,
                        weightAngle = weightAngle,
                        milestoneGlowAlpha = milestoneGlowAlpha,
                        wheelSkin = selectedSkin,
                        sendLightActive = sendLightActive,
                        onDragStart = { viewModel.onDragStart() },
                        onDragMove = { x, y, centerX, centerY, timestamp ->
                            viewModel.onDragMove(x, y, centerX, centerY, timestamp)
                        },
                        onDragEnd = { viewModel.onDragEnd() },
                        onLongPress = { viewModel.pauseWheel() },
                        onPointerCountChange = { count ->
                            pointerCount = count
                            if (count >= 2) {
                                viewModel.onTwoPointersDown()
                            } else {
                                viewModel.onTwoPointersUp()
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Check for milestone crossing
                LaunchedEffect(lifetimeMantras) {
                    if (lifetimeMantras > BigInteger.ZERO) {
                        val reachedMilestone = MILESTONE_THRESHOLDS.find { threshold ->
                            lifetimeMantras >= threshold &&
                            (lastMilestoneReached == null || lastMilestoneReached!! < threshold)
                        }
                        if (reachedMilestone != null) {
                            lastMilestoneReached = reachedMilestone
                            // Trigger milestone animation
                            milestoneGlowAlpha = 1f
                            viewModel.triggerMilestoneHaptic()
                            // Animate glow fade
                            val animatable = Animatable(1f)
                            animatable.animateTo(
                                targetValue = 0f,
                                animationSpec = tween(durationMillis = 1000)
                            ) {
                                milestoneGlowAlpha = value
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Auto-spin controls
                if (spinMode == SpinMode.AUTO_SPIN) {
                    AutoSpinControls(
                        rpm = autoSpinRpm,
                        isActive = autoSpinActive,
                        onRpmChange = { viewModel.setAutoSpinRpm(it) },
                        onToggleAutoSpin = {
                            if (autoSpinActive) {
                                viewModel.stopAutoSpin()
                            } else {
                                viewModel.startAutoSpin()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                }

                // End session button (shown when session is active)
                if (hasActiveSession) {
                    OutlinedButton(
                        onClick = { viewModel.endSession() },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("End Session")
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Mantra selector
                MantraSelector(
                    selectedMantraId = currentMantra.id,
                    onMantraSelected = { viewModel.setSelectedMantra(it) },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Intention display
                if (currentIntention.isNotBlank()) {
                    Text(
                        text = "Intention: $currentIntention",
                        style = MaterialTheme.typography.bodyMedium,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }

                // Session goal progress
                if (sessionGoal > 0 && hasActiveSession) {
                    Spacer(modifier = Modifier.height(8.dp))
                    val progress = (sessionMantras.toFloat() / sessionGoal.toFloat()).coerceIn(0f, 1f)
                    GoalProgressDisplay(
                        current = sessionMantras,
                        goal = sessionGoal.toBigInteger(),
                        onSetIntention = { showIntentionDialog = true }
                    )
                } else {
                    // Intention button when no session is active
                    OutlinedButton(
                        onClick = { showIntentionDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Set Intention")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Counter display with RPM (striped coloring for high speed)
                CounterDisplay(
                    currentMantraName = currentMantra.displayName,
                    sessionMantras = sessionMantras,
                    lifetimeMantras = lifetimeMantras,
                    currentRpm = currentRpm,
                    isSpinning = isSpinning,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Capacity slider
                CapacitySlider(
                    currentValue = mantrasPerRotation,
                    onValueChange = { viewModel.setMantrasPerRotation(it) },
                    onPresetSelected = { viewModel.onSliderPresetSelected() },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Haptic feedback toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Haptic feedback",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                    )
                    Switch(
                        checked = hapticEnabled,
                        onCheckedChange = { viewModel.setHapticEnabled(it) }
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }

        // Dedication prompt overlay
        if (showDedicationPrompt) {
            DedicationScreen(
                sessionMantras = sessionMantras,
                mantraName = currentMantra.displayName,
                customDedication = customDedication,
                onConfirm = { viewModel.confirmDedication(it) },
                onDismiss = { viewModel.skipDedication() }
            )
        }

        // Intention dialog overlay
        if (showIntentionDialog) {
            IntentionDialog(
                currentIntention = currentIntention,
                currentSessionGoal = sessionGoal,
                currentDailyGoal = 0L,
                onSave = { intention, sessionGoalValue, dailyGoal ->
                    viewModel.setCurrentIntention(intention)
                    viewModel.setSessionGoal(sessionGoalValue)
                    viewModel.setDailyGoal(dailyGoal)
                    showIntentionDialog = false
                },
                onDismiss = { showIntentionDialog = false }
            )
        }

        // Share intention dialog
        if (showShareDialog) {
            ShareIntentionDialog(
                currentMantraName = currentMantra.displayName,
                sessionMantras = sessionMantras,
                currentIntention = currentIntention,
                onSendLight = { viewModel.triggerSendLight() },
                onDismiss = { showShareDialog = false }
            )
        }

        // Wheel customizer bottom sheet
        if (showCustomizer) {
            WheelCustomizer(
                selectedSkinId = selectedSkin.id,
                onSkinSelected = { skin -> viewModel.setSelectedSkin(skin) },
                onDismiss = { showCustomizer = false }
            )
        }
    }
}

/**
 * Displays goal progress when a session goal is set.
 */
@Composable
private fun GoalProgressDisplay(
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
                text = "Goal: ${formatNumber(current.toLong())} / ${formatNumber(goal.toLong())} mantras",
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
private fun formatNumber(number: Long): String {
    return when {
        number >= 1_000_000_000 -> "${number / 1_000_000_000}B"
        number >= 1_000_000 -> "${number / 1_000_000}M"
        number >= 1_000 -> "${number / 1_000}K"
        else -> NumberFormat.getNumberInstance(Locale.getDefault()).format(number)
    }
}

/**
 * Auto-spin RPM controls with 1-120 range.
 */
@Composable
private fun AutoSpinControls(
    rpm: Int,
    isActive: Boolean,
    onRpmChange: (Int) -> Unit,
    onToggleAutoSpin: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Speed",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            )
            // Striped coloring for 90-120 RPM range
            Text(
                text = "$rpm RPM",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = if (rpm >= 90) {
                    // Striped red/orange for high speed zone
                    Color(0xFFE65100)
                } else {
                    MaterialTheme.colorScheme.onBackground
                }
            )
        }

        Slider(
            value = rpm.toFloat(),
            onValueChange = { onRpmChange(it.toInt()) },
            valueRange = 1f..120f,
            steps = 118,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = onToggleAutoSpin,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isActive) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                }
            )
        ) {
            Text(
                text = if (isActive) "Stop Auto-Spin" else "Start Auto-Spin",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

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
private fun SideViewPrayerWheel(
    rotationAngle: Float,
    angularVelocity: Float,
    currentMantra: Mantra,
    weightAngle: Float,
    milestoneGlowAlpha: Float,
    wheelSkin: WheelSkin,
    sendLightActive: Boolean,
    onDragStart: () -> Unit,
    onDragMove: (Float, Float, Float, Float, Long) -> Unit,
    onDragEnd: () -> Unit,
    onLongPress: () -> Unit,
    onPointerCountChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    
    // Calculate glow intensity based on angular velocity
    val glowIntensity = min(1f, abs(angularVelocity) / 10f)
    
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
                awaitPointerEventScope {
                    var currentPointerCount = 0
                    while (true) {
                        val event = awaitPointerEvent()
                        val newPointerCount = event.changes.size
                        if (newPointerCount != currentPointerCount) {
                            currentPointerCount = newPointerCount
                            onPointerCountChange(newPointerCount)
                        }
                        if (currentPointerCount == 0) {
                            // All pointers are up
                            if (event.changes.any { it.pressed }) {
                                // Handle drag start
                                val change = event.changes.first { it.pressed }
                                onDragStart()
                            }
                        } else {
                            // Handle drag events
                            val changes = event.changes.filter { it.pressed }
                            if (changes.isNotEmpty()) {
                                val change = changes.first()
                                val currentCenterX = this.size.width / 2f
                                val currentCenterY = this.size.height / 2f
                                onDragMove(
                                    change.position.x,
                                    change.position.y,
                                    currentCenterX,
                                    currentCenterY,
                                    System.currentTimeMillis()
                                )
                            }
                            
                            // Check if drag ended
                            if (event.changes.all { !it.pressed }) {
                                onDragEnd()
                            }
                        }
                    }
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            val centerX = canvasWidth / 2f
            
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
            val effectiveGlowIntensity = if (sendLightActive) {
                // Intensified rays for send light animation
                1f
            } else if (glowIntensity > 0.05f) {
                glowIntensity
            } else 0f
            
            if (effectiveGlowIntensity > 0f || sendLightActive) {
                drawLightRays(centerX, cylinderTop, cylinderWidth, cylinderHeight, effectiveGlowIntensity, rotationAngle, wheelSkin, sendLightActive)
            }
            
            // 4. Draw string and weight
            drawStringAndWeight(stringAttachX, stringAttachY, weightAngle, glowIntensity, wheelSkin)
            
            // 5. Draw bottom cap
            drawBottomCap(centerX, cylinderTop + cylinderHeight, cylinderWidth, capHeight, wheelSkin)
            
            // 6. Draw cylinder body with scrolling text
            drawCylinderBody(
                centerX, cylinderTop, cylinderWidth, cylinderHeight,
                rotationAngle, currentMantra, textMeasurer, wheelSkin
            )
            
            // 7. Draw top cap
            drawTopCap(centerX, cylinderTop, cylinderWidth, capHeight, wheelSkin)
            
            // 8. Draw crystal/gem finial
            drawCrystal(centerX, cylinderTop - capHeight * 0.8f, crystalSway, glowIntensity, wheelSkin)
            
            // 9. Milestone glow effect
            if (milestoneGlowAlpha > 0f) {
                drawMilestoneGlow(centerX, cylinderTop + cylinderHeight / 2f, cylinderWidth * 1.5f, milestoneGlowAlpha)
            }
        }
    }
}

/**
 * Draws the stem/handle of the prayer wheel.
 */
private fun DrawScope.drawStem(
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
private fun DrawScope.drawBase(
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
private fun DrawScope.drawLightRays(
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
private fun DrawScope.drawStringAndWeight(
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
        quadraticBezierTo(midX, midY, weightX, weightY)
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
private fun DrawScope.drawTopCap(
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
private fun DrawScope.drawBottomCap(
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
 */
private fun DrawScope.drawCylinderBody(
    centerX: Float,
    cylinderTop: Float,
    cylinderWidth: Float,
    cylinderHeight: Float,
    rotationAngle: Float,
    mantra: Mantra,
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    skin: WheelSkin
) {
    val cylinderColor = Color(skin.cylinderColor)
    // Cylinder body background gradient based on skin
    val bodyGradient = Brush.verticalGradient(
        colors = listOf(
            cylinderColor.copy(alpha = 0.8f),
            cylinderColor.copy(alpha = 0.9f),
            cylinderColor,
            cylinderColor.copy(alpha = 0.9f),
            cylinderColor.copy(alpha = 0.8f)
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
    
    // Vertical lines representing cylinder surface texture
    val lineCount = 8
    for (i in 0 until lineCount) {
        val x = centerX - cylinderWidth / 2f + (i + 1) * cylinderWidth / (lineCount + 1)
        val alpha = 0.1f + 0.05f * sin(i.toFloat())
        drawLine(
            color = cylinderColor.copy(alpha = alpha * 0.5f),
            start = Offset(x, cylinderTop + cylinderHeight * 0.1f),
            end = Offset(x, cylinderTop + cylinderHeight * 0.9f),
            strokeWidth = 1f
        )
    }
    
    // Scrolling mantra text
    val textToDisplay = mantra.tibetan ?: mantra.romanized
    val textStyle = TextStyle(
        color = Color(0xFF3D2B1F),
        fontSize = 20.sp,
        fontWeight = FontWeight.Medium
    )
    
    // Calculate text scroll offset based on rotation angle
    val textLayout = textMeasurer.measure(text = textToDisplay, style = textStyle)
    val textWidth = textLayout.size.width.toFloat()
    val spacing = textWidth * 1.5f
            
    // Calculate offset - the text scrolls as cylinder rotates
    val scrollOffset = (rotationAngle * textWidth / (2 * PI.toFloat())) % spacing
            
    // Clip to cylinder bounds
    val clipRect = Rect(
        left = centerX - cylinderWidth / 2f + 12f,
        top = cylinderTop + cylinderHeight * 0.25f,
        right = centerX + cylinderWidth / 2f - 12f,
        bottom = cylinderTop + cylinderHeight * 0.75f
    )
    
    // Draw multiple instances of text to create scrolling effect
    val savedSize = size
    val centerY = cylinderTop + cylinderHeight / 2f
    
    // Calculate how many text instances needed to fill the visible area
    val visibleWidth = clipRect.width
    val startX = clipRect.left + scrollOffset - spacing
    
    // Use translate to position text
    translate(left = 0f, top = 0f) {
        var currentX = startX
        while (currentX < clipRect.right + spacing) {
            // Draw text if within clip bounds
            if (currentX + textWidth > clipRect.left && currentX < clipRect.right) {
                drawText(
                    textLayoutResult = textMeasurer.measure(
                        text = textToDisplay,
                        style = textStyle.copy(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFF2D1F14),
                                    Color(0xFF4A3728),
                                    Color(0xFF2D1F14)
                                )
                            )
                        )
                    ),
                    topLeft = Offset(
                        x = currentX + (textWidth - textMeasurer.measure(textToDisplay, textStyle).size.width) / 2f,
                        y = centerY - textMeasurer.measure(textToDisplay, textStyle).size.height / 2f
                    )
                )
            }
            currentX += spacing
        }
    }
    
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
 */
private fun DrawScope.drawCrystal(
    centerX: Float,
    topY: Float,
    swayAmount: Float,
    glowIntensity: Float,
    skin: WheelSkin
) {
    val crystalHeight = 40f
    val crystalWidth = 24f
    val crystalColor = Color(skin.crystalColor)
    val capColor = Color(skin.capColor)
    
    // Apply sway based on angular velocity
    val offsetX = swayAmount * 8f
    
    // Draw glow behind crystal
    if (glowIntensity > 0.1f) {
        drawOval(
            brush = Brush.radialGradient(
                colors = listOf(
                    crystalColor.copy(alpha = glowIntensity * 0.6f),
                    crystalColor.copy(alpha = 0f)
                ),
                center = Offset(centerX + offsetX, topY),
                radius = crystalWidth * 2f
            ),
            topLeft = Offset(centerX + offsetX - crystalWidth * 2f, topY - crystalHeight / 2f),
            size = Size(crystalWidth * 4f, crystalHeight * 2f)
        )
    }
    
    // Draw diamond/crystal shape
    val crystalPath = Path().apply {
        moveTo(centerX + offsetX, topY - crystalHeight / 2f) // Top point
        lineTo(centerX + offsetX + crystalWidth / 2f, topY) // Right middle
        lineTo(centerX + offsetX, topY + crystalHeight / 2f) // Bottom point
        lineTo(centerX + offsetX - crystalWidth / 2f, topY) // Left middle
        close()
    }
    
    // Crystal gradient based on skin
    val crystalGradient = Brush.verticalGradient(
        colors = listOf(
            crystalColor.copy(alpha = 0.9f),
            crystalColor.copy(alpha = 0.7f),
            crystalColor.copy(alpha = 0.8f),
            crystalColor.copy(alpha = 0.6f),
            crystalColor.copy(alpha = 0.7f)
        ),
        startY = topY - crystalHeight / 2f,
        endY = topY + crystalHeight / 2f
    )
    
    drawPath(path = crystalPath, brush = crystalGradient)
    
    // Crystal highlight
    val highlightPath = Path().apply {
        moveTo(centerX + offsetX - crystalWidth * 0.3f, topY - crystalHeight * 0.2f)
        lineTo(centerX + offsetX - crystalWidth * 0.1f, topY)
        lineTo(centerX + offsetX - crystalWidth * 0.3f, topY + crystalHeight * 0.1f)
        close()
    }
    
    drawPath(
        path = highlightPath,
        color = Color(0xFFFFFFFF).copy(alpha = 0.7f)
    )
    
    // Crystal outline
    drawPath(
        path = crystalPath,
        color = crystalColor.copy(alpha = 0.5f),
        style = Stroke(width = 1.5f)
    )
    
    // Small decorative ring between crystal and top cap
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                capColor.copy(alpha = 0.9f),
                capColor.copy(alpha = 0.7f)
            )
        ),
        radius = 6f,
        center = Offset(centerX, topY + crystalHeight / 2f + 4f)
    )
}

/**
 * Draws milestone glow effect.
 */
private fun DrawScope.drawMilestoneGlow(
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

private fun min(a: Float, b: Float): Float = if (a < b) a else b
