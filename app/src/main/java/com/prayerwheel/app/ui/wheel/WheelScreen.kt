package com.prayerwheel.app.ui.wheel

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prayerwheel.app.data.datastore.SpinMode
import com.prayerwheel.app.data.datastore.ViewMode
import com.prayerwheel.app.data.model.Mantra
import com.prayerwheel.app.data.model.SavedWheel
import com.prayerwheel.app.data.model.Session
import com.prayerwheel.app.data.model.WheelSkin
import com.prayerwheel.app.data.model.WheelSkins
import com.prayerwheel.app.ui.components.bounceClick
import com.prayerwheel.app.ui.components.CapacitySlider
import com.prayerwheel.app.ui.components.CounterDisplay
import com.prayerwheel.app.ui.components.IntentionDialog
import com.prayerwheel.app.ui.components.MantraSelector
import com.prayerwheel.app.ui.components.NumberFormatter
import com.prayerwheel.app.ui.components.SavedWheelsManager
import com.prayerwheel.app.ui.components.MilestoneCelebration
import com.prayerwheel.app.ui.components.ShareIntentionDialog
import com.prayerwheel.app.ui.components.SpinModeSelector
import com.prayerwheel.app.ui.components.TodayProgressCard
import com.prayerwheel.app.ui.components.WheelCustomizer
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
import java.math.BigInteger
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.min
import kotlin.math.max
import java.lang.Math.toDegrees

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
 * Tiered glow intensity across the 0–120 RPM devotional speed range.
 *
 * Maps |angularVelocity| (rad/s) to a 0..1 glow intensity through four
 * qualitative tiers — Still, Flowing, Blazing, Dissolving — each smoothly
 * interpolated at its boundaries. The tiers mirror traditional descriptions
 * of deepening samadhi: the visual energy grows in distinct stages rather
 * than along a single linear ramp.
 *
 * Boundary ω values come from RPM × 2π/60:
 *  20 RPM ≈ 2.0944, 50 RPM ≈ 5.2359, 90 RPM ≈ 9.4247, 120 RPM ≈ 12.566.
 */
private fun tieredGlowIntensity(angularVelocity: Float): Float {
    val omega = abs(angularVelocity)
    return when {
        // Tier 1 — Still (0–20 RPM): barely-there meditative glow (0.0–0.15).
        omega <= 2.0944f -> (omega / 2.0944f) * 0.15f
        // Tier 2 — Flowing (20–50 RPM): warm glow building up (0.15–0.5).
        omega <= 5.2359f -> 0.15f + ((omega - 2.0944f) / (5.2359f - 2.0944f)) * (0.5f - 0.15f)
        // Tier 3 — Blazing (50–90 RPM): full glow (0.5–0.85).
        omega <= 9.4247f -> 0.5f + ((omega - 5.2359f) / (9.4247f - 5.2359f)) * (0.85f - 0.5f)
        // Tier 4 — Dissolving (90–120 RPM): ethereal maximum glow (0.85–1.0).
        else -> 0.85f + ((omega - 9.4247f) / (12.566f - 9.4247f)).coerceIn(0f, 1f) * (1.0f - 0.85f)
    }
}

/**
 * Persistent aura floor beneath the tiered glow. A practitioner who has crossed
 * a lifetime milestone always sees at least this much ambient glow on their
 * wheel, even at 0 RPM — the wheel accumulates devotional "warmth" that never
 * fully fades. Maps lifetime tier (0..8, see [WheelViewModel.highestMilestoneTier])
 * to a minimum glow intensity.
 */
private fun milestoneAuraFloor(tier: Int): Float = when {
    tier >= 7 -> 0.15f   // 1B+: radiant
    tier >= 5 -> 0.10f   // 10M+: clearly visible
    tier >= 3 -> 0.06f   // 100K+: subtle
    tier >= 1 -> 0.03f   // 1K+: barely visible warmth
    else -> 0f
}

/**
 * Effective glow intensity: the larger of the velocity-driven tiered glow and
 * the lifetime-milestone aura floor. Used by every wheel renderer so the
 * persistent aura shows uniformly across all view modes.
 */
private fun effectiveGlowIntensity(angularVelocity: Float, milestoneTier: Int): Float =
    maxOf(tieredGlowIntensity(angularVelocity), milestoneAuraFloor(milestoneTier))

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
    val leftRotationAngle by viewModel.leftRotationAngle.collectAsState()
    val rightRotationAngle by viewModel.rightRotationAngle.collectAsState()
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
    val sessionTimeGoalSeconds by viewModel.sessionTimeGoalSeconds.collectAsState()
    val weightAngle by viewModel.weightAngle.collectAsState()
    val selectedSkin by viewModel.selectedSkin.collectAsState()
    val sendLightActive by viewModel.sendLightActive.collectAsState()
    val showCounterClockwiseReminder by viewModel.showCounterClockwiseReminder.collectAsState()
    val counterClockwiseEnabled by viewModel.counterClockwiseEnabled.collectAsState()
    val viewMode by viewModel.viewMode.collectAsState()
    val numberFormatStyle by viewModel.numberFormatStyle.collectAsState()
    val starParticles by viewModel.starParticles.collectAsState()
    val showLiveCounter by viewModel.showLiveCounter.collectAsState()
    val keepScreenOn by viewModel.keepScreenOn.collectAsState()
    val bgAnimationIntensity by viewModel.bgAnimationIntensity.collectAsState()
    val newlyUnlockedAchievement by viewModel.newlyUnlockedAchievement.collectAsState()
    val unlockedAchievements by viewModel.unlockedAchievements.collectAsState()
    val savedWheels by viewModel.savedWheels.collectAsState()
    val autoSpinEnabled by viewModel.autoSpinEnabled.collectAsState()
    val twoHandedEnabled by viewModel.twoHandedEnabled.collectAsState()
    val breathModeEnabled by viewModel.breathModeEnabled.collectAsState()
    val highestMilestoneTier by viewModel.highestMilestoneTier.collectAsState()
    val isPaused by viewModel.isPaused.collectAsState()
    val todayMorningCompleted by viewModel.todayMorningCompleted.collectAsState()
    val todayEveningCompleted by viewModel.todayEveningCompleted.collectAsState()
    val todayMantraCount by viewModel.todayMantraCount.collectAsState()
    val todayPracticeSeconds by viewModel.todayPracticeSeconds.collectAsState()
    val dailyMantraGoal by viewModel.dailyMantraGoal.collectAsState()
    val dailyMantraProgress by viewModel.dailyMantraProgress.collectAsState()
    val dailyTimeGoalSeconds by viewModel.dailyTimeGoalSeconds.collectAsState()
    val dailyTimeProgress by viewModel.dailyTimeProgress.collectAsState()

    // Star particle spawn counter
    var starSpawnTimer by remember { mutableFloatStateOf(0f) }

    // Track pointer count for two-handed mode
    var pointerCount by remember { mutableIntStateOf(0) }

    // Show intention dialog state
    var showIntentionDialog by remember { mutableStateOf(false) }
    var showShareDialog by remember { mutableStateOf(false) }
    var showCustomizer by remember { mutableStateOf(false) }
    var showSavedWheelsManager by remember { mutableStateOf(false) }

    // T17: resume-after-kill — populated once on launch by detectResumableSession().
    var resumableSession by remember { mutableStateOf<Session?>(null) }

    // Milestone glow animation state
    var milestoneGlowAlpha by remember { mutableStateOf(0f) }
    var lastMilestoneReached by remember { mutableStateOf<BigInteger?>(null) }

    // Background gradient drift
    val backgroundWarmth = (sessionDuration / 600000f).coerceIn(0f, 1f) // Max warmth after 10 minutes

    // Background gradient colors based on mantra
    val targetColors = remember(currentMantra.id, backgroundWarmth) {
        val warmth = backgroundWarmth
        when (currentMantra.id) {
            "om_mani_padme_hum" -> listOf(
                Color(0xFFFFF0F5).copy(alpha = 1f - warmth * 0.3f), // subtle rose
                Color(0xFFFFF5F8).copy(alpha = 1f - warmth * 0.2f),
                Color(0xFFFBE5EE).copy(alpha = 1f - warmth * 0.1f)
            )
            "vajra_guru" -> listOf(
                Color(0xFFF5F0E8).copy(alpha = 1f - warmth * 0.3f), // subtle gold
                Color(0xFFFFF8E7).copy(alpha = 1f - warmth * 0.2f),
                Color(0xFFFBF0D8).copy(alpha = 1f - warmth * 0.1f)
            )
            "arapacana" -> listOf(
                Color(0xFFE8F0F5).copy(alpha = 1f - warmth * 0.3f), // subtle cool blue
                Color(0xFFE7F8FF).copy(alpha = 1f - warmth * 0.2f),
                Color(0xFFD8F0FB).copy(alpha = 1f - warmth * 0.1f)
            )
            else -> listOf(
                Color(0xFFF5F0E8).copy(alpha = 1f - warmth * 0.3f),
                Color(0xFFFFF8E7).copy(alpha = 1f - warmth * 0.2f),
                Color(0xFFFBF0D8).copy(alpha = 1f - warmth * 0.1f)
            )
        }
    }

    val color1 by animateColorAsState(targetColors[0], label = "color1")
    val color2 by animateColorAsState(targetColors[1], label = "color2")
    val color3 by animateColorAsState(targetColors[2], label = "color3")

    Box(modifier = modifier.fillMaxSize()) {
        // Keep screen on during active sessions when the user has opted in
        val view = LocalView.current
        val isSessionActive = hasActiveSession && !isPaused
        DisposableEffect(keepScreenOn, isSessionActive) {
            view.keepScreenOn = keepScreenOn && isSessionActive
            onDispose { view.keepScreenOn = false }
        }

        // Animated background gradient — scaled by bgAnimationIntensity (0=off, 2=full)
        val infiniteTransition = rememberInfiniteTransition(label = "backgroundDrift")
        val intensityScale = (bgAnimationIntensity / 2f).coerceIn(0f, 1f)
        val gradientDurationMs = if (intensityScale > 0f) (30000f / intensityScale).toInt() else 30000
        val gradientOffset by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = gradientDurationMs, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "gradientOffset"
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(color1, color2, color3),
                        start = Offset(0f, gradientOffset * 500f * intensityScale),
                        end = Offset(1000f, 1000f + gradientOffset * 500f * intensityScale)
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
                        IconButton(
                            onClick = onNavigateToCalculator,
                            modifier = Modifier.bounceClick()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Calculator"
                            )
                        }
                        IconButton(
                            onClick = { showCustomizer = true },
                            modifier = Modifier.bounceClick()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Favorite,
                                contentDescription = "Customize"
                            )
                        }
                        IconButton(
                            onClick = { showShareDialog = true },
                            modifier = Modifier.bounceClick()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share"
                            )
                        }
                        IconButton(
                            onClick = onNavigateToStats,
                            modifier = Modifier.bounceClick()
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.List,
                                contentDescription = "Statistics"
                            )
                        }
                        IconButton(
                            onClick = onNavigateToSettings,
                            modifier = Modifier.bounceClick()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings"
                            )
                        }
                        IconButton(
                            onClick = onNavigateToHistory,
                            modifier = Modifier.bounceClick()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
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

                // Spin mode selector with two independent toggles
                SpinModeSelector(
                    autoSpinEnabled = autoSpinEnabled,
                    twoHandedEnabled = twoHandedEnabled,
                    breathModeEnabled = breathModeEnabled,
                    autoSpinRpm = autoSpinRpm,
                    onAutoSpinToggle = { viewModel.setAutoSpinEnabled(it) },
                    onTwoHandedToggle = { viewModel.setTwoHandedEnabled(it) },
                    onBreathModeToggle = { viewModel.setBreathModeEnabled(it) },
                    onRpmChange = { viewModel.setAutoSpinRpm(it) },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Prayer wheel with drag interaction (based on view mode and dual wheel mode)
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .height(320.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (twoHandedEnabled) {
                        // Dual wheels mode - show two wheels side by side
                        DualWheelsView(
                            leftRotationAngle = leftRotationAngle,
                            rightRotationAngle = rightRotationAngle,
                            angularVelocity = angularVelocity,
                            currentMantra = currentMantra,
                            milestoneGlowAlpha = milestoneGlowAlpha,
                            wheelSkin = selectedSkin,
                            sendLightActive = sendLightActive,
                            highestMilestoneTier = highestMilestoneTier,
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
else {
                        when (viewMode) {
                        ViewMode.SIDE_VIEW -> SideViewPrayerWheel(
                            rotationAngle = rotationAngle,
                            angularVelocity = angularVelocity,
                            currentMantra = currentMantra,
                            weightAngle = weightAngle,
                            milestoneGlowAlpha = milestoneGlowAlpha,
                            wheelSkin = selectedSkin,
                            sendLightActive = sendLightActive,
                            starParticles = starParticles,
                            sessionTimeGoalSeconds = sessionTimeGoalSeconds,
                            hasActiveSession = hasActiveSession,
                            sessionDuration = sessionDuration,
                            highestMilestoneTier = highestMilestoneTier,
                            onUpdateCanvasDimensions = { width, height ->
                                viewModel.updateCanvasDimensions(width, height)
                            },
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
                        ViewMode.TOP_DOWN -> TopDownViewPrayerWheel(
                            rotationAngle = rotationAngle,
                            angularVelocity = angularVelocity,
                            currentMantra = currentMantra,
                            milestoneGlowAlpha = milestoneGlowAlpha,
                            wheelSkin = selectedSkin,
                            sendLightActive = sendLightActive,
                            highestMilestoneTier = highestMilestoneTier,
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
                        ViewMode.ABSTRACT -> AbstractViewPrayerWheel(
                            rotationAngle = rotationAngle,
                            angularVelocity = angularVelocity,
                            currentMantra = currentMantra,
                            milestoneGlowAlpha = milestoneGlowAlpha,
                            wheelSkin = selectedSkin,
                            sendLightActive = sendLightActive,
                            highestMilestoneTier = highestMilestoneTier,
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
                        ViewMode.TABLE_TOP -> TableTopViewPrayerWheel(
                            rotationAngle = rotationAngle,
                            angularVelocity = angularVelocity,
                            currentMantra = currentMantra,
                            milestoneGlowAlpha = milestoneGlowAlpha,
                            wheelSkin = selectedSkin,
                            sendLightActive = sendLightActive,
                            highestMilestoneTier = highestMilestoneTier,
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
                        ViewMode.GLOBE -> GlobeViewPrayerWheel(
                            rotationAngle = rotationAngle,
                            angularVelocity = angularVelocity,
                            currentMantra = currentMantra,
                            milestoneGlowAlpha = milestoneGlowAlpha,
                            wheelSkin = selectedSkin,
                            sendLightActive = sendLightActive,
                            highestMilestoneTier = highestMilestoneTier,
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
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // View mode bottom bar selector
                ViewModeBottomBar(
                    currentMode = viewMode,
                    onModeSelected = { viewModel.setViewMode(it) },
                    modifier = Modifier.fillMaxWidth()
                )

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

                // T17: probe for an interrupted session once on launch
                LaunchedEffect(Unit) {
                    resumableSession = viewModel.detectResumableSession()
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Saved wheels quick select cards (shown if there are saved wheels)
                if (savedWheels.isNotEmpty()) {
                    SavedWheelsQuickSelect(
                        wheels = savedWheels,
                        currentCapacity = mantrasPerRotation,
                        onWheelSelected = { viewModel.applySavedWheel(it) },
                        onManageWheels = { showSavedWheelsManager = true },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // T17: resume-after-kill banner (dismissable; mirrors paused-banner style)
                resumableSession?.let { session ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "↻",
                                    style = MaterialTheme.typography.headlineSmall
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Resume your previous session?",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                    Text(
                                        text = "${session.rotationCount} rotations • tap Resume to continue, or Discard to keep it as recorded.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.75f)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(onClick = { resumableSession = null }) {
                                    Text("Discard")
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(onClick = {
                                    viewModel.resumeFromSession(session)
                                    resumableSession = null
                                }) {
                                    Text("Resume")
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Paused banner
                if (hasActiveSession && isPaused) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "⏸",
                                style = MaterialTheme.typography.headlineSmall
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Session Paused",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "The wheel is paused. The notification timer is frozen.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Session control buttons (shown when session is active)
                if (hasActiveSession) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Pause / Resume button
                        OutlinedButton(
                            onClick = {
                                if (isPaused) viewModel.resumeWheel() else viewModel.pauseWheel()
                            },
                            modifier = Modifier.weight(1f).bounceClick(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(if (isPaused) "Resume" else "Pause")
                        }

                        // End session button
                        OutlinedButton(
                            onClick = { viewModel.endSession() },
                            modifier = Modifier.weight(1f).bounceClick(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("End Session")
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Today's progress — quiet morning/evening markers + today's accumulation.
                TodayProgressCard(
                    morningCompleted = todayMorningCompleted,
                    eveningCompleted = todayEveningCompleted,
                    todayMantraCount = todayMantraCount,
                    todayPracticeSeconds = todayPracticeSeconds,
                    currentIntention = currentIntention,
                    numberFormatStyle = numberFormatStyle,
                    dailyMantraGoal = dailyMantraGoal,
                    dailyMantraProgress = dailyMantraProgress,
                    dailyTimeGoalSeconds = dailyTimeGoalSeconds,
                    dailyTimeProgress = dailyTimeProgress,
                    onGoalReached = { viewModel.triggerMilestoneHaptic() },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

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
                    GoalProgressDisplay(
                        current = sessionMantras,
                        goal = sessionGoal.toBigInteger(),
                        onSetIntention = { showIntentionDialog = true }
                    )
                }

                // Time goal progress
                if (sessionTimeGoalSeconds > 0 && hasActiveSession) {
                    Spacer(modifier = Modifier.height(8.dp))
                    TimeGoalProgressDisplay(
                        currentSeconds = sessionDuration / 1000,
                        goalSeconds = sessionTimeGoalSeconds,
                        currentIntention = currentIntention
                    )
                }

                if (sessionGoal <= 0 && sessionTimeGoalSeconds <= 0 && !hasActiveSession) {
                    // Intention button when no session is active and no goals set
                    OutlinedButton(
                        onClick = { showIntentionDialog = true },
                        modifier = Modifier.fillMaxWidth().bounceClick(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Set Intention")
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Counter display with RPM (striped coloring for high speed)
                if (showLiveCounter) {
                    CounterDisplay(
                        currentMantraName = currentMantra.displayName,
                        sessionMantras = sessionMantras,
                        lifetimeMantras = lifetimeMantras,
                        currentRpm = currentRpm,
                        isSpinning = isSpinning,
                        formatStyle = numberFormatStyle,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

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

        // Counter-clockwise reminder banner (shown at top when CCW detected)
        if (showCounterClockwiseReminder && !counterClockwiseEnabled) {
            CounterClockwiseReminderBanner(
                onDismiss = { viewModel.dismissCounterClockwiseReminder() },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 80.dp)
                    .padding(horizontal = 24.dp)
            )
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

        // Milestone celebration dialog
        newlyUnlockedAchievement?.let { achievement ->
            MilestoneCelebration(
                achievement = achievement,
                onDismiss = { viewModel.dismissAchievementCelebration() }
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
                unlockedAchievements = unlockedAchievements,
                onSkinSelected = { skin -> viewModel.setSelectedSkin(skin) },
                onDismiss = { showCustomizer = false }
            )
        }

        // Saved wheels manager bottom sheet
        if (showSavedWheelsManager) {
            SavedWheelsManager(
                viewModel = viewModel,
                onDismiss = { showSavedWheelsManager = false }
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
private fun formatNumber(number: Long, style: com.prayerwheel.app.data.datastore.NumberFormatStyle = com.prayerwheel.app.data.datastore.NumberFormatStyle.STANDARD): String {
    return NumberFormatter.formatWithStyle(number, style)
}

/**
 * Displays time goal progress when a time goal is set with an Intention Mandala.
 */
@Composable
private fun TimeGoalProgressDisplay(
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
 * Text wraps around the cylinder in multiple rows for larger display.
 */
private fun DrawScope.drawCylinderBody(
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
private fun DrawScope.drawCrystal(
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

/**
 * Draws rainbow light rays emanating from the cylinder when spinning.
 * Spiritual, not garish - uses subtle semi-transparent rays.
 */
private fun DrawScope.drawRainbowRays(
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
private fun DrawScope.drawStarParticles(
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
 * Bottom bar view mode selector with pill/chip style.
 * More discoverable than a small icon toggle.
 */
@Composable
private fun ViewModeBottomBar(
    currentMode: ViewMode,
    onModeSelected: (ViewMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModes = listOf(
        ViewMode.SIDE_VIEW to "Side",
        ViewMode.TOP_DOWN to "Top",
        ViewMode.ABSTRACT to "Abstract",
        ViewMode.TABLE_TOP to "Table",
        ViewMode.GLOBE to "Globe"
    )

    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .clip(RoundedCornerShape(24.dp))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                shape = RoundedCornerShape(24.dp)
            )
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        viewModes.forEach { (mode, label) ->
            val isSelected = mode == currentMode
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .clickable { onModeSelected(mode) },
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surface
                },
                shape = RoundedCornerShape(20.dp)
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    },
                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 12.dp)
                )
            }
        }
    }
}

/**
 * Quick select cards for saved wheels.
 */
@Composable
private fun SavedWheelsQuickSelect(
    wheels: List<SavedWheel>,
    currentCapacity: Long,
    onWheelSelected: (SavedWheel) -> Unit,
    onManageWheels: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Saved Wheels",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            )
            TextButton(onClick = onManageWheels) {
                Text(
                    text = "Manage",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            wheels.take(5).forEach { wheel ->
                SavedWheelCard(
                    wheel = wheel,
                    isSelected = wheel.capacity == currentCapacity,
                    onClick = { onWheelSelected(wheel) }
                )
            }
        }
    }
}

@Composable
private fun SavedWheelCard(
    wheel: SavedWheel,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .width(120.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = wheel.name,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = formatShortNumber(wheel.capacity),
                style = MaterialTheme.typography.bodySmall,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.primary
                }
            )
        }
    }
}

/**
 * Formats a large number in short form (e.g., 100M, 1B).
 */
private fun formatShortNumber(value: Long): String {
    return NumberFormatter.formatLong(value)
}

/**
 * Top-down view prayer wheel - circular drum viewed from above.
 * Mantra text wraps around the circumference.
 */
@Composable
private fun TopDownViewPrayerWheel(
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

/**
 * Abstract view - minimalist mandala/sacred geometry representation.
 * Enhanced with mandala patterns, lotus, aurora effects, and improved Tibetan text.
 */
@Composable
private fun AbstractViewPrayerWheel(
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

private fun min(a: Float, b: Float): Float = if (a < b) a else b

/**

 * Dual wheels view - shows two prayer wheels side by side.
 * Left wheel spins when touching the left half of the screen,
 * Right wheel spins when touching the right half.
 */
@Composable
private fun DualWheelsView(
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

/**
 * Returns a text color (black or white) that contrasts well with the given background color.
 * Uses relative luminance calculation to determine contrast.
 */
private fun getContrastTextColor(backgroundColor: Color): Color {
    val r = backgroundColor.red
    val g = backgroundColor.green
    val b = backgroundColor.blue
    // Calculate relative luminance using standard coefficients
    val luminance = 0.299 * r + 0.587 * g + 0.114 * b
    return if (luminance > 0.5) Color(0xFF1A1A1A) else Color(0xFFF5F5F5)
}

/**
 * Banner shown when counter-clockwise rotation is detected.
 */
@Composable
private fun CounterClockwiseReminderBanner(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Note: Traditional prayer wheels are spun clockwise. (Counter-clockwise counting can be enabled in Settings)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Dismiss",
                    tint = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
        }
    }
}

@Composable
private fun TableTopViewPrayerWheel(
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

@Composable
private fun GlobeViewPrayerWheel(
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
