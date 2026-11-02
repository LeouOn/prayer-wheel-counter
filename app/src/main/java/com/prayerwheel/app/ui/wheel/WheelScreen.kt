package com.prayerwheel.app.ui.wheel

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.prayerwheel.app.data.datastore.ViewMode
import com.prayerwheel.app.data.model.Session
import com.prayerwheel.app.ui.components.bounceClick
import com.prayerwheel.app.ui.components.CapacitySlider
import com.prayerwheel.app.ui.components.CounterDisplay
import com.prayerwheel.app.ui.components.IntentionDialog
import com.prayerwheel.app.ui.components.MantraSelector
import com.prayerwheel.app.ui.components.SavedWheelsManager
import com.prayerwheel.app.ui.components.MilestoneCelebration
import com.prayerwheel.app.ui.components.ShareIntentionDialog
import com.prayerwheel.app.ui.components.SpinModeSelector
import com.prayerwheel.app.ui.components.TodayProgressCard
import com.prayerwheel.app.ui.components.WheelCustomizer
import com.prayerwheel.app.viewmodel.WheelViewModel
import java.math.BigInteger
import com.prayerwheel.app.ui.wheel.render.MILESTONE_THRESHOLDS
import com.prayerwheel.app.ui.wheel.views.AbstractViewPrayerWheel
import com.prayerwheel.app.ui.wheel.views.DualWheelsView
import com.prayerwheel.app.ui.wheel.views.GlobeViewPrayerWheel
import com.prayerwheel.app.ui.wheel.views.SideViewPrayerWheel
import com.prayerwheel.app.ui.wheel.views.TableTopViewPrayerWheel
import com.prayerwheel.app.ui.wheel.views.TopDownViewPrayerWheel

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
    val rotationCount by viewModel.rotationCount.collectAsState()
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

    // Failed session/stats writes (e.g. disk full): a calm, dismissable notice.
    val saveError by viewModel.saveError.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(saveError) {
        saveError?.let { message ->
            snackbarHostState.showSnackbar(
                message = message,
                withDismissAction = true,
                duration = SnackbarDuration.Long
            )
            viewModel.dismissSaveError()
        }
    }
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

    var hasSpunOnce by remember { mutableStateOf(false) }
    LaunchedEffect(currentRpm, rotationCount) {
        if (currentRpm > 0f || rotationCount > 0L) hasSpunOnce = true
    }
    val showFirstSpinHint = !hasSpunOnce && rotationCount == 0L

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
            snackbarHost = { SnackbarHost(snackbarHostState) },
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

                if (showFirstSpinHint) {
                    Text(
                        text = "Drag the wheel to begin your practice",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
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
