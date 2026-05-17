package com.prayerwheel.app.viewmodel

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.prayerwheel.app.data.datastore.SpinMode
import com.prayerwheel.app.data.datastore.UserPreferences
import com.prayerwheel.app.data.datastore.ViewMode
import com.prayerwheel.app.data.datastore.NumberFormatStyle
import com.prayerwheel.app.data.datastore.NumberNotation
import com.prayerwheel.app.data.db.dao.LifetimeStatsDao
import com.prayerwheel.app.data.db.dao.SessionDao
import com.prayerwheel.app.data.model.LifetimeStats
import com.prayerwheel.app.data.model.Mantra
import com.prayerwheel.app.data.model.Mantras
import com.prayerwheel.app.data.model.SavedWheel
import com.prayerwheel.app.data.model.WheelSkin
import com.prayerwheel.app.data.model.WheelSkins
import com.prayerwheel.app.data.model.Session
import com.prayerwheel.app.notification.SessionNotificationService
import com.prayerwheel.app.notification.SessionNotificationReceiver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.math.BigInteger
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2

/**
 * ViewModel for the prayer wheel screen.
 *
 * Implements drag-to-spin physics with friction decay, rotation counting,
 * and Room persistence for lifetime statistics and session tracking.
 * Supports multiple spin modes: Manual, Two-Handed, and Auto-Spin.
 */
class WheelViewModel(
    private val lifetimeStatsDao: LifetimeStatsDao,
    private val sessionDao: SessionDao,
    private val userPreferences: UserPreferences,
    private val vibrator: Vibrator,
    private val appContext: Context
) : ViewModel() {

    companion object {
        private const val FRICTION_COEFFICIENT = 0.008f
        private const val TWO_HANDED_FRICTION = 0.0001f
        private const val STOP_THRESHOLD = 0.001f
        private const val TWO_PI = 2f * PI.toFloat()
        private const val SESSION_STOP_DELAY_MS = 30000L
        private const val SESSION_NEW_THRESHOLD_MS = 300000L // 5 minutes
        private const val ANIMATION_FRAME_TIME_MS = 16L
        private const val FLICK_VELOCITY_MULTIPLIER = 1.5f
        private const val MAX_ANGULAR_VELOCITY = 75f
        private const val COUNTER_CLOCKWISE_REMINDER_DELAY_MS = 3000L

        // Haptic feedback durations in milliseconds
        private const val HAPTIC_TICK_DURATION = 5L
        private const val HAPTIC_PAUSE_DURATION = 50L
        private const val HAPTIC_SLIDER_DURATION = 3L
        private const val HAPTIC_MILESTONE_DURATION = 20L
        private const val SESSION_TIMER_INTERVAL_MS = 1000L
    }

    /**
     * Current angular velocity in radians per second.
     */
    private val _angularVelocity = MutableStateFlow(0f)
    val angularVelocity: StateFlow<Float> = _angularVelocity.asStateFlow()

    /**
     * Current rotation angle in radians.
     */
    private val _rotationAngle = MutableStateFlow(0f)
    val rotationAngle: StateFlow<Float> = _rotationAngle.asStateFlow()

    /**
     * Total rotations completed in the current session.
     */
    private val _rotationCount = MutableStateFlow(0L)
    val rotationCount: StateFlow<Long> = _rotationCount.asStateFlow()

    /**
     * Session mantra count (rotationCount × mantrasPerRotation).
     */
    private val _sessionMantras = MutableStateFlow(BigInteger.ZERO)
    val sessionMantras: StateFlow<BigInteger> = _sessionMantras.asStateFlow()

    /**
     * Lifetime total mantras from database.
     */
    private val _lifetimeMantras = MutableStateFlow(BigInteger.ZERO)
    val lifetimeMantras: StateFlow<BigInteger> = _lifetimeMantras.asStateFlow()

    /**
     * Base friction coefficient for physics simulation.
     */
    private val _baseFriction = MutableStateFlow(FRICTION_COEFFICIENT)
    val baseFriction: StateFlow<Float> = _baseFriction.asStateFlow()

    /**
     * Current mantras per rotation setting.
     */
    private val _mantrasPerRotation = MutableStateFlow(1L)
    val mantrasPerRotation: StateFlow<Long> = _mantrasPerRotation.asStateFlow()

    /**
     * Current selected mantra.
     */
    private val _currentMantra = MutableStateFlow(Mantras.OM_MANI_PADME_HUM)
    val currentMantra: StateFlow<Mantra> = _currentMantra.asStateFlow()

    /**
     * Whether haptic feedback is enabled.
     */
    private val _hapticEnabled = MutableStateFlow(true)
    val hapticEnabled: StateFlow<Boolean> = _hapticEnabled.asStateFlow()

    /**
     * Whether the wheel is currently spinning (velocity > 0).
     */
    private val _isSpinning = MutableStateFlow(false)
    val isSpinning: StateFlow<Boolean> = _isSpinning.asStateFlow()

    /**
     * Current spin mode.
     */
    private val _spinMode = MutableStateFlow(SpinMode.MANUAL)
    val spinMode: StateFlow<SpinMode> = _spinMode.asStateFlow()

    /**
     * Auto-spin RPM (revolutions per minute).
     */
    private val _autoSpinRpm = MutableStateFlow(30)
    val autoSpinRpm: StateFlow<Int> = _autoSpinRpm.asStateFlow()

    /**
     * Whether two-handed mode is currently engaged.
     */
    private val _twoHandedEngaged = MutableStateFlow(false)
    val twoHandedEngaged: StateFlow<Boolean> = _twoHandedEngaged.asStateFlow()

    /**
     * Whether auto-spin is currently active.
     */
    private val _autoSpinActive = MutableStateFlow(false)
    val autoSpinActive: StateFlow<Boolean> = _autoSpinActive.asStateFlow()

    /**
     * Whether a dedication prompt should be shown.
     */
    private val _showDedicationPrompt = MutableStateFlow(false)
    val showDedicationPrompt: StateFlow<Boolean> = _showDedicationPrompt.asStateFlow()

    /**
     * Custom dedication text saved by user.
     */
    private val _customDedication = MutableStateFlow<String?>(null)
    val customDedication: StateFlow<String?> = _customDedication.asStateFlow()

    /**
     * Whether an active session exists.
     */
    private val _hasActiveSession = MutableStateFlow(false)
    val hasActiveSession: StateFlow<Boolean> = _hasActiveSession.asStateFlow()

    /**
     * Duration of the current session in milliseconds.
     */
    private val _sessionDuration = MutableStateFlow(0L)
    val sessionDuration: StateFlow<Long> = _sessionDuration.asStateFlow()

    /**
     * Current RPM (rotations per minute) based on angular velocity.
     */
    private val _currentRpm = MutableStateFlow(0f)
    val currentRpm: StateFlow<Float> = _currentRpm.asStateFlow()

    /**
     * Peak RPM reached during the current session.
     */
    private val _peakRpm = MutableStateFlow(0f)
    val peakRpm: StateFlow<Float> = _peakRpm.asStateFlow()

    /**
     * Running average RPM over the session duration.
     */
    private val _averageRpm = MutableStateFlow(0f)
    val averageRpm: StateFlow<Float> = _averageRpm.asStateFlow()

    /**
     * Session duration in seconds.
     */
    private val _sessionDurationSeconds = MutableStateFlow(0L)
    val sessionDurationSeconds: StateFlow<Long> = _sessionDurationSeconds.asStateFlow()

    /**
     * Current intention text.
     */
    private val _currentIntention = MutableStateFlow("")
    val currentIntention: StateFlow<String> = _currentIntention.asStateFlow()

    /**
     * Weight angle for pendulum physics (degrees, 0 = hanging straight down).
     * Based on centrifugal force from RPM.
     */
    private val _weightAngle = MutableStateFlow(0f)
    val weightAngle: StateFlow<Float> = _weightAngle.asStateFlow()

    /**
     * Session goal for this session (mantras).
     */
    private val _sessionGoal = MutableStateFlow(0L)
    val sessionGoal: StateFlow<Long> = _sessionGoal.asStateFlow()

    /**
     * Session time goal in seconds.
     */
    private val _sessionTimeGoalSeconds = MutableStateFlow(0L)
    val sessionTimeGoalSeconds: StateFlow<Long> = _sessionTimeGoalSeconds.asStateFlow()

    /**
     * Daily mantra goal.
     */
    private val _dailyMantraGoal = MutableStateFlow(0L)
    val dailyMantraGoal: StateFlow<Long> = _dailyMantraGoal.asStateFlow()

    /**
     * Currently selected wheel skin.
     */
    private val _selectedSkin = MutableStateFlow(WheelSkins.default())
    val selectedSkin: StateFlow<WheelSkin> = _selectedSkin.asStateFlow()

    /**
     * Whether send light animation is active.
     */
    private val _sendLightActive = MutableStateFlow(false)
    val sendLightActive: StateFlow<Boolean> = _sendLightActive.asStateFlow()

    /**
     * Whether to show counter-clockwise reminder.
     */
    private val _showCounterClockwiseReminder = MutableStateFlow(false)
    val showCounterClockwiseReminder: StateFlow<Boolean> = _showCounterClockwiseReminder.asStateFlow()
    private var lastCounterClockwiseReminderTime = 0L

    /**
     * Whether counter-clockwise rotation is enabled by user.
     */
    private val _counterClockwiseEnabled = MutableStateFlow(false)
    val counterClockwiseEnabled: StateFlow<Boolean> = _counterClockwiseEnabled.asStateFlow()

    /**
     * Number formatting style.
     */
    private val _numberFormatStyle = MutableStateFlow(NumberFormatStyle.STANDARD)
    val numberFormatStyle: StateFlow<NumberFormatStyle> = _numberFormatStyle.asStateFlow()

    /**
     * Number notation style (Standard vs Extended).
     */
    private val _numberNotation = MutableStateFlow(NumberNotation.STANDARD)
    val numberNotation: StateFlow<NumberNotation> = _numberNotation.asStateFlow()

    /**
     * Current view mode for the prayer wheel display.
     */
    private val _viewMode = MutableStateFlow(ViewMode.SIDE_VIEW)
    val viewMode: StateFlow<ViewMode> = _viewMode.asStateFlow()

    /**
     * Star particles for visual effects.
     */
    private val _starParticles = MutableStateFlow<List<StarParticle>>(emptyList())
    val starParticles: StateFlow<List<StarParticle>> = _starParticles.asStateFlow()

    /**
     * Saved wheel profiles.
     */
    private val _savedWheels = MutableStateFlow<List<SavedWheel>>(emptyList())
    val savedWheels: StateFlow<List<SavedWheel>> = _savedWheels.asStateFlow()

    /**
     * Whether auto-spin toggle is enabled.
     */
    private val _autoSpinEnabled = MutableStateFlow(false)
    val autoSpinEnabled: StateFlow<Boolean> = _autoSpinEnabled.asStateFlow()

    /**
     * Whether two-handed toggle is enabled.
     */
    private val _twoHandedEnabled = MutableStateFlow(false)
    val twoHandedEnabled: StateFlow<Boolean> = _twoHandedEnabled.asStateFlow()

    private val _isAppInForeground = MutableStateFlow(true)
    val isAppInForeground: StateFlow<Boolean> = _isAppInForeground.asStateFlow()

    private val _backgroundVibrationEnabled = MutableStateFlow(false)
    val backgroundVibrationEnabled: StateFlow<Boolean> = _backgroundVibrationEnabled.asStateFlow()

    private val _vibrationIntensity = MutableStateFlow(1.0f)
    val vibrationIntensity: StateFlow<Float> = _vibrationIntensity.asStateFlow()

    /**
     * Left wheel angular velocity (for dual wheel mode).
     */
    private val _leftAngularVelocity = MutableStateFlow(0f)
    val leftAngularVelocity: StateFlow<Float> = _leftAngularVelocity.asStateFlow()

    /**
     * Right wheel angular velocity (for dual wheel mode).
     */
    private val _rightAngularVelocity = MutableStateFlow(0f)
    val rightAngularVelocity: StateFlow<Float> = _rightAngularVelocity.asStateFlow()

    /**
     * Left wheel rotation angle (for dual wheel mode).
     */
    private val _leftRotationAngle = MutableStateFlow(0f)
    val leftRotationAngle: StateFlow<Float> = _leftRotationAngle.asStateFlow()

    /**
     * Right wheel rotation angle (for dual wheel mode).
     */
    private val _rightRotationAngle = MutableStateFlow(0f)
    val rightRotationAngle: StateFlow<Float> = _rightRotationAngle.asStateFlow()

    /**
     * Left wheel rotation count (for dual wheel mode).
     */
    private val _leftRotationCount = MutableStateFlow(0L)
    val leftRotationCount: StateFlow<Long> = _leftRotationCount.asStateFlow()

    /**
     * Right wheel rotation count (for dual wheel mode).
     */
    private val _rightRotationCount = MutableStateFlow(0L)
    val rightRotationCount: StateFlow<Long> = _rightRotationCount.asStateFlow()

    /**
     * Data class for star particle effects.
     */
    data class StarParticle(
        val id: Int,
        val x: Float,
        val y: Float,
        val size: Float,
        val alpha: Float,
        val colorIndex: Int,
        val velocityY: Float,
        val lifetime: Float
    )

    // Physics state
    private var lastFrameTime = 0L
    private var physicsJob: Job? = null
    private var sessionStopJob: Job? = null
    private var autoSpinJob: Job? = null
    private var sessionTimerJob: Job? = null

    // RPM tracking state
    private var rpmSamples = mutableListOf<Float>()
    private var rpmSampleCount = 0
    private var sessionStartRpmSum = 0f

    // Drag tracking
    private val dragHistory = mutableListOf<DragEvent>()
    private var sessionStartTime: Long? = null
    private var currentSessionRotations = 0L
    private var currentSessionMantras = BigInteger.ZERO
    private var sessionId: Long? = null
    private var lastSessionEndTime: Long? = null
    private var pendingDedicationSession: PendingSession? = null

    // Notification service active flag
    private var notificationActive = false

    private data class PendingSession(
        val startTime: Long,
        val rotations: Long,
        val mantras: BigInteger,
        val mantrasPerRotation: Long,
        val mantraId: String,
        val mode: SpinMode,
        val averageRpm: Float,
        val peakRpm: Float,
        val intention: String?,
        val sessionGoal: Long?
    )

    init {
        viewModelScope.launch {
            userPreferences.mantrasPerRotation.collect { value ->
                _mantrasPerRotation.value = value
            }
        }
        viewModelScope.launch {
            userPreferences.selectedMantra.collect { mantraId ->
                val mantra = Mantras.byId(mantraId) ?: Mantras.OM_MANI_PADME_HUM
                _currentMantra.value = mantra
            }
        }
        viewModelScope.launch {
            userPreferences.hapticEnabled.collect { enabled ->
                _hapticEnabled.value = enabled
            }
        }
        viewModelScope.launch {
            userPreferences.spinMode.collect { mode ->
                _spinMode.value = mode
            }
        }
        viewModelScope.launch {
            userPreferences.autoSpinRpm.collect { rpm ->
                _autoSpinRpm.value = rpm
            }
        }
        viewModelScope.launch {
            userPreferences.customDedication.collect { dedication ->
                _customDedication.value = dedication
            }
        }
        viewModelScope.launch {
            userPreferences.friction.collect { friction ->
                _baseFriction.value = friction
            }
        }
        viewModelScope.launch {
            userPreferences.currentIntention.collect { intention ->
                _currentIntention.value = intention
            }
        }
        viewModelScope.launch {
            userPreferences.sessionMantraGoal.collect { goal ->
                _sessionGoal.value = goal
            }
        }
        viewModelScope.launch {
            userPreferences.sessionTimeGoalSeconds.collect { goal ->
                _sessionTimeGoalSeconds.value = goal
            }
        }
        viewModelScope.launch {
            userPreferences.dailyMantraGoal.collect { goal ->
                _dailyMantraGoal.value = goal
            }
        }
        viewModelScope.launch {
            userPreferences.selectedSkin.collect { skinId ->
                _selectedSkin.value = WheelSkins.byId(skinId) ?: WheelSkins.default()
            }
        }
        viewModelScope.launch {
            userPreferences.counterClockwiseEnabled.collect { enabled ->
                _counterClockwiseEnabled.value = enabled
            }
        }
        viewModelScope.launch {
            userPreferences.viewMode.collect { mode ->
                _viewMode.value = mode
            }
        }
        viewModelScope.launch {
            userPreferences.numberFormatStyle.collect { style ->
                _numberFormatStyle.value = style
            }
        }
        viewModelScope.launch {
            userPreferences.numberNotation.collect { notation ->
                _numberNotation.value = notation
            }
        }
        viewModelScope.launch {
            userPreferences.savedWheels.collect { wheels ->
                _savedWheels.value = wheels
            }
        }
        viewModelScope.launch {
            userPreferences.autoSpinEnabled.collect { enabled ->
                _autoSpinEnabled.value = enabled
            }
        }
        viewModelScope.launch {
            userPreferences.twoHandedEnabled.collect { enabled ->
                _twoHandedEnabled.value = enabled
            }
        }
        viewModelScope.launch {
            userPreferences.backgroundVibrationEnabled.collect { enabled ->
                _backgroundVibrationEnabled.value = enabled
            }
        }
        viewModelScope.launch {
            userPreferences.vibrationIntensity.collect { intensity ->
                _vibrationIntensity.value = intensity
            }
        }
        loadLifetimeStats()
    }

    /**
     * Sets the spin mode.
     */
    fun setSpinMode(mode: SpinMode) {
        viewModelScope.launch {
            _spinMode.value = mode
            userPreferences.setSpinMode(mode)

            // If switching to auto-spin, trigger haptic feedback
            if (mode == SpinMode.AUTO_SPIN) {
                triggerHapticTick()
            }

            // If switching away from two-handed, disengage
            if (mode != SpinMode.TWO_HANDED) {
                setTwoHandedEngaged(false)
            }

            // If switching away from auto-spin, stop it
            if (mode != SpinMode.AUTO_SPIN) {
                stopAutoSpin()
            }
        }
    }

    /**
     * Sets the auto-spin RPM.
     */
    fun setAutoSpinRpm(rpm: Int) {
        viewModelScope.launch {
            _autoSpinRpm.value = rpm.coerceIn(1, 120)
            userPreferences.setAutoSpinRpm(rpm)

            // If auto-spin is active, restart it with new RPM
            if (_autoSpinActive.value && _spinMode.value == SpinMode.AUTO_SPIN) {
                startAutoSpin()
            }
        }
    }

    /**
     * Sets whether two-handed mode is engaged.
     */
    fun setTwoHandedEngaged(engaged: Boolean) {
        if (_spinMode.value == SpinMode.TWO_HANDED) {
            _twoHandedEngaged.value = engaged
            if (engaged && _angularVelocity.value > STOP_THRESHOLD) {
                // Restart physics loop with reduced friction
                startPhysicsLoop()
            }
        }
    }

    /**
     * Called when two pointers are detected on screen.
     */
    fun onTwoPointersDown() {
        setTwoHandedEngaged(true)
    }

    /**
     * Called when pointer count drops below two.
     */
    fun onTwoPointersUp() {
        setTwoHandedEngaged(false)
    }

    /**
     * Starts auto-spin mode.
     */
    fun startAutoSpin() {
        if (_spinMode.value != SpinMode.AUTO_SPIN) return

        _autoSpinActive.value = true
        val rpm = _autoSpinRpm.value
        // Convert RPM to angular velocity (radians per second)
        // RPM * 2π / 60 = rad/s
        val targetOmega = rpm * 2 * PI.toFloat() / 60f
        _angularVelocity.value = targetOmega

        startSessionIfNeeded()
        startPhysicsLoop()
    }

    /**
     * Stops auto-spin mode.
     */
    fun stopAutoSpin() {
        _autoSpinActive.value = false
    }

    /**
     * Records a drag move event for velocity calculation.
     */
    fun onDragMove(x: Float, y: Float, centerX: Float, centerY: Float, timestamp: Long) {
        val angle = atan2(y - centerY, x - centerX)
        val angularDiff = calculateAngularChange(angle)

        // Check if dual wheels mode is enabled
        if (_twoHandedEnabled.value) {
            // In dual mode, apply drag based on which side of screen is touched
            if (x < centerX) {
                // Left wheel
                _leftRotationAngle.value += angularDiff
                checkLeftRotationCompletion(angularDiff)
            } else {
                // Right wheel
                _rightRotationAngle.value += angularDiff
                checkRightRotationCompletion(angularDiff)
            }
        } else {
            _rotationAngle.value += angularDiff
            checkRotationCompletion(angularDiff)
        }

        dragHistory.add(DragEvent(angle, timestamp))
        // Keep only last 5 events for velocity calculation
        while (dragHistory.size > 5) {
            dragHistory.removeAt(0)
        }
    }

    /**
     * Checks rotation completion for the left wheel.
     */
    private fun checkLeftRotationCompletion(angularDiff: Float) {
        val totalRotation = _leftRotationAngle.value
        val newRevolutions = (totalRotation / TWO_PI).toLong()
        val oldRevolutions = ((totalRotation - angularDiff) / TWO_PI).toLong()
        if (newRevolutions > oldRevolutions) {
            _leftRotationCount.value += (newRevolutions - oldRevolutions)
            onDualWheelRotationComplete(newRevolutions - oldRevolutions, isLeft = true)
        }
    }

    /**
     * Checks rotation completion for the right wheel.
     */
    private fun checkRightRotationCompletion(angularDiff: Float) {
        val totalRotation = _rightRotationAngle.value
        val newRevolutions = (totalRotation / TWO_PI).toLong()
        val oldRevolutions = ((totalRotation - angularDiff) / TWO_PI).toLong()
        if (newRevolutions > oldRevolutions) {
            _rightRotationCount.value += (newRevolutions - oldRevolutions)
            onDualWheelRotationComplete(newRevolutions - oldRevolutions, isLeft = false)
        }
    }

    /**
     * Handles rotation completion for dual wheel mode.
     * Both wheels contribute to the same session mantras.
     */
    private fun onDualWheelRotationComplete(count: Long, isLeft: Boolean) {
        if (isLeft) {
            currentSessionRotations += count
        } else {
            currentSessionRotations += count
        }
        updateSessionMantras()
        triggerHapticTick()
    }

    /**
     * Called on pointer down - starts tracking drag.
     */
    fun onDragStart() {
        dragHistory.clear()
        // Stop any ongoing momentum
        _angularVelocity.value = 0f
    }

    /**
     * Called on pointer up - calculates flick velocity and starts momentum.
     */
    fun onDragEnd() {
        if (dragHistory.size >= 2) {
            // Use weighted average of last 3-5 events for smoother velocity
            val eventsToConsider = dragHistory.takeLast(5)
            var totalWeightedAngularDiff = 0f
            var totalWeight = 0f

            eventsToConsider.forEachIndexed { index, event ->
                val weight = (index + 1).toFloat() // More recent events have higher weight
                if (index > 0) {
                    val prevEvent = eventsToConsider[index - 1]
                    val timeDelta = (event.timestamp - prevEvent.timestamp).coerceAtLeast(1L) / 1000f
                    if (timeDelta > 0) {
                        var angularDiff = event.angle - prevEvent.angle
                        // Handle angle wrap-around for clockwise detection
                        if (angularDiff > PI) angularDiff -= TWO_PI
                        if (angularDiff < -PI) angularDiff += TWO_PI
                        totalWeightedAngularDiff += (angularDiff / timeDelta) * weight
                        totalWeight += weight
                    }
                }
            }

            if (totalWeight > 0) {
                var velocity = totalWeightedAngularDiff / totalWeight

                // Apply flick velocity multiplier for stronger spinning
                velocity *= FLICK_VELOCITY_MULTIPLIER

                // Check for counter-clockwise motion
                if (velocity < 0) {
                    // Counter-clockwise detected
                    if (!_counterClockwiseEnabled.value) {
                        // Show reminder but allow the motion
                        showCounterClockwiseReminder()
                    }
                    // Allow counter-clockwise with reduced velocity or block based on setting
                    // For now, we let it spin counter-clockwise but at reduced sensitivity
                    velocity = velocity.coerceIn(-MAX_ANGULAR_VELOCITY, 0f)
                } else {
                    // Clockwise - apply higher cap
                    velocity = velocity.coerceIn(0f, MAX_ANGULAR_VELOCITY)
                }

                _angularVelocity.value = velocity
            }
        }

        startSessionIfNeeded()
        startPhysicsLoop()
    }

    /**
     * Shows the counter-clockwise reminder and auto-dismisses after 3 seconds.
     */
    private fun showCounterClockwiseReminder() {
        val now = System.currentTimeMillis()
        if (now - lastCounterClockwiseReminderTime < 60_000L) return // 1 minute cooldown

        lastCounterClockwiseReminderTime = now
        _showCounterClockwiseReminder.value = true
        viewModelScope.launch {
            delay(COUNTER_CLOCKWISE_REMINDER_DELAY_MS)
            _showCounterClockwiseReminder.value = false
        }
    }

    /**
     * Dismisses the counter-clockwise reminder.
     */
    fun dismissCounterClockwiseReminder() {
        _showCounterClockwiseReminder.value = false
    }

    /**
     * Whether the session is paused (for notification state).
     */
    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

    /**
     * Pauses the wheel immediately (long press).
     */
    fun pauseWheel() {
        _angularVelocity.value = 0f
        stopPhysicsLoop()
        _isPaused.value = true
        triggerHapticPause()
        // Update notification to reflect paused state
        if (notificationActive) {
            SessionNotificationService.update(
                _sessionDurationSeconds.value,
                _sessionMantras.value,
                paused = true
            )
        }
    }

    /**
     * Resumes the wheel from a paused state.
     */
    fun resumeWheel() {
        _isPaused.value = false
        // Restart physics if needed (re-engage spin)
        if (_spinMode.value == SpinMode.AUTO_SPIN || _autoSpinEnabled.value) {
            startAutoSpin()
        } else {
            // Give a small initial velocity so the wheel can be flicked again
            startPhysicsLoop()
        }
        // Update notification to reflect resumed state
        if (notificationActive) {
            SessionNotificationService.update(
                _sessionDurationSeconds.value,
                _sessionMantras.value,
                paused = false
            )
        }
    }

    /**
     * Triggers haptic feedback for slider selection.
     */
    fun onSliderPresetSelected() {
        triggerHapticSlider()
    }

    /**
     * Updates the mantras per rotation setting.
     */
    fun setMantrasPerRotation(count: Long) {
        viewModelScope.launch {
            _mantrasPerRotation.value = count
            userPreferences.setMantrasPerRotation(count)
            updateSessionMantras()
        }
    }

    /**
     * Updates the selected mantra.
     */
    fun setSelectedMantra(mantraId: String) {
        viewModelScope.launch {
            userPreferences.setSelectedMantra(mantraId)
        }
    }

    /**
     * Sets the current intention text.
     */
    fun setCurrentIntention(intention: String) {
        viewModelScope.launch {
            _currentIntention.value = intention
            userPreferences.setCurrentIntention(intention)
        }
    }

    /**
     * Sets the session mantra goal.
     */
    fun setSessionGoal(goal: Long) {
        viewModelScope.launch {
            _sessionGoal.value = goal
            userPreferences.setSessionMantraGoal(goal)
        }
    }

    /**
     * Sets the daily mantra goal.
     */
    fun setDailyGoal(goal: Long) {
        viewModelScope.launch {
            _dailyMantraGoal.value = goal
            userPreferences.setDailyMantraGoal(goal)
        }
    }

    /**
     * Sets the session time goal in seconds.
     */
    fun setSessionTimeGoal(seconds: Long) {
        viewModelScope.launch {
            _sessionTimeGoalSeconds.value = seconds
            userPreferences.setSessionTimeGoalSeconds(seconds)
        }
    }

    /**
     * Sets the daily time goal in seconds.
     */
    fun setDailyTimeGoal(seconds: Long) {
        viewModelScope.launch {
            userPreferences.setDailyTimeGoalSeconds(seconds)
        }
    }

    /**
     * Sets the selected wheel skin.
     */
    fun setSelectedSkin(skin: WheelSkin) {
        viewModelScope.launch {
            _selectedSkin.value = skin
            userPreferences.setSelectedSkin(skin.id)
        }
    }

    /**
     * Sets the view mode for the prayer wheel display.
     */
    fun setViewMode(mode: ViewMode) {
        viewModelScope.launch {
            _viewMode.value = mode
            userPreferences.setViewMode(mode)
        }
    }

    /**
     * Sets the number format style.
     */
    fun setNumberFormatStyle(style: NumberFormatStyle) {
        viewModelScope.launch {
            _numberFormatStyle.value = style
            userPreferences.setNumberFormatStyle(style)
        }
    }

    /**
     * Sets the number notation style.
     */
    fun setNumberNotation(notation: NumberNotation) {
        viewModelScope.launch {
            _numberNotation.value = notation
            userPreferences.setNumberNotation(notation)
        }
    }

    /**
     * Sets the auto-spin toggle enabled state.
     */
    fun setAutoSpinEnabled(enabled: Boolean) {
        viewModelScope.launch {
            _autoSpinEnabled.value = enabled
            userPreferences.setAutoSpinEnabled(enabled)
            if (enabled) {
                // Immediately start spinning at the set RPM
                val targetOmega = _autoSpinRpm.value * 2 * PI.toFloat() / 60f
                _angularVelocity.value = targetOmega
                startSessionIfNeeded()
                startPhysicsLoop()
            } else {
                stopAutoSpin()
            }
        }
    }

    /**
     * Sets the two-handed toggle enabled state.
     */
    fun setTwoHandedEnabled(enabled: Boolean) {
        viewModelScope.launch {
            _twoHandedEnabled.value = enabled
            userPreferences.setTwoHandedEnabled(enabled)
        }
    }

    /**
     * Adds a new saved wheel profile.
     */
    fun addSavedWheel(name: String, capacity: Long, mantraId: String) {
        viewModelScope.launch {
            val wheel = SavedWheel(
                id = java.util.UUID.randomUUID().toString(),
                name = name,
                capacity = capacity,
                mantraId = mantraId,
                skinId = _selectedSkin.value.id,
                createdAt = System.currentTimeMillis()
            )
            userPreferences.addSavedWheel(wheel)
        }
    }

    /**
     * Updates an existing saved wheel profile.
     */
    fun updateSavedWheel(wheel: SavedWheel) {
        viewModelScope.launch {
            userPreferences.updateSavedWheel(wheel)
        }
    }

    /**
     * Deletes a saved wheel profile.
     */
    fun deleteSavedWheel(wheelId: String) {
        viewModelScope.launch {
            userPreferences.deleteSavedWheel(wheelId)
        }
    }

    /**
     * Applies a saved wheel configuration (sets capacity and mantra).
     */
    fun applySavedWheel(wheel: SavedWheel) {
        viewModelScope.launch {
            _mantrasPerRotation.value = wheel.capacity
            userPreferences.setMantrasPerRotation(wheel.capacity)
            userPreferences.setSelectedMantra(wheel.mantraId)
            _currentMantra.value = Mantras.byId(wheel.mantraId) ?: Mantras.OM_MANI_PADME_HUM
        }
    }

    /**
     * Cycles to the next view mode.
     */
    fun cycleViewMode() {
        val nextMode = when (_viewMode.value) {
            ViewMode.SIDE_VIEW -> ViewMode.TOP_DOWN
            ViewMode.TOP_DOWN -> ViewMode.ABSTRACT
            ViewMode.ABSTRACT -> ViewMode.TABLE_TOP
            ViewMode.TABLE_TOP -> ViewMode.GLOBE
            ViewMode.GLOBE -> ViewMode.SIDE_VIEW
        }
        setViewMode(nextMode)
    }

    /**
     * Triggers the send light animation.
     */
    fun triggerSendLight() {
        _sendLightActive.value = true
        viewModelScope.launch {
            if (shouldVibrate()) {
                triggerSendLightHaptic()
            }
            // Duration of the animation
            delay(3000)
            _sendLightActive.value = false
        }
    }

    private fun triggerSendLightHaptic() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val timings = longArrayOf(0, 100, 100, 100, 100, 100)
            val amplitudes = intArrayOf(0, 50, 0, 50, 0, 30)
            val effect = VibrationEffect.createWaveform(timings, amplitudes, -1)
            vibrator.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(longArrayOf(0, 100, 100, 100, 100, 100), -1)
        }
    }

    /**
     * Ends the current session explicitly.
     */
    fun endSession() {
        stopNotificationService()
        _isPaused.value = false
        saveSessionWithDedication()
    }

    /**
     * Confirms dedication and saves the session.
     */
    fun confirmDedication(dedication: String?) {
        viewModelScope.launch {
            pendingDedicationSession?.let { pending ->
                // Save custom dedication if provided
                if (dedication != null && dedication.isNotBlank()) {
                    userPreferences.setCustomDedication(dedication)
                }

                // Save the session to Room
                val endedAt = System.currentTimeMillis()
                val session = createSessionFromPending(pending, endedAt, dedication)

                sessionDao.insert(session)

                // Update lifetime stats (carrying forward spinning time fields)
                updateLifetimeStats(session)

                lastSessionEndTime = endedAt
            }

            pendingDedicationSession = null
            _showDedicationPrompt.value = false
            resetSessionState()
        }
    }

    /**
     * Skips the dedication prompt.
     */
    fun skipDedication() {
        viewModelScope.launch {
            pendingDedicationSession?.let { pending ->
                val session = createSessionFromPending(pending, System.currentTimeMillis(), null)
                sessionDao.insert(session)
                updateLifetimeStats(session)
            }
            pendingDedicationSession = null
            _showDedicationPrompt.value = false
            resetSessionState()
        }
    }

    /**
     * Creates a Session from pending session data.
     */
    private fun createSessionFromPending(pending: PendingSession, endedAt: Long, dedication: String?): Session {
        return Session(
            startedAt = pending.startTime,
            endedAt = endedAt,
            rotationCount = pending.rotations,
            mantrasPerRotation = pending.mantrasPerRotation,
            totalMantras = pending.mantras,
            mantraId = pending.mantraId,
            dedication = dedication,
            mode = pending.mode.name,
            averageRpm = pending.averageRpm,
            peakRpm = pending.peakRpm,
            totalSpins = pending.rotations,
            intention = pending.intention,
            sessionGoal = pending.sessionGoal
        )
    }

    /**
     * Creates a Session from the current state (for onCleared).
     */
    private fun createSessionFromCurrentState(): Session? {
        val startTime = sessionStartTime ?: return null
        if (currentSessionRotations == 0L) return null

        val avgRpm = if (rpmSampleCount > 0) sessionStartRpmSum / rpmSampleCount else 0f
        val endedAt = System.currentTimeMillis()

        return Session(
            startedAt = startTime,
            endedAt = endedAt,
            rotationCount = currentSessionRotations,
            mantrasPerRotation = _mantrasPerRotation.value,
            totalMantras = currentSessionMantras,
            mantraId = _currentMantra.value.id,
            dedication = null,
            mode = _spinMode.value.name,
            averageRpm = avgRpm,
            peakRpm = _peakRpm.value,
            totalSpins = currentSessionRotations,
            intention = _currentIntention.value.ifBlank { null },
            sessionGoal = if (_sessionGoal.value > 0) _sessionGoal.value else null
        )
    }

    /**
     * Updates lifetime stats by upserting with carried-forward spinning time fields.
     */
    private suspend fun updateLifetimeStats(session: Session) {
        val sessionDurationSeconds = _sessionDurationSeconds.value
        val existing = lifetimeStatsDao.getStats()

        val newStats = existing?.copy(
            totalRotations = existing.totalRotations + session.rotationCount,
            totalMantras = existing.totalMantras + session.totalMantras,
            sessionsCompleted = existing.sessionsCompleted + 1,
            totalSpinningTimeSeconds = existing.totalSpinningTimeSeconds + sessionDurationSeconds,
            averageSessionDurationSeconds = if (existing.sessionsCompleted > 0) {
                (existing.totalSpinningTimeSeconds + sessionDurationSeconds) / (existing.sessionsCompleted + 1)
            } else sessionDurationSeconds
        ) ?: LifetimeStats(
            id = 1,
            totalRotations = session.rotationCount,
            totalMantras = session.totalMantras,
            sessionsCompleted = 1,
            firstSessionAt = session.startedAt,
            totalSpinningTimeSeconds = sessionDurationSeconds,
            averageSessionDurationSeconds = sessionDurationSeconds
        )

        lifetimeStatsDao.upsert(newStats)
    }

    private fun startSessionIfNeeded() {
        val now = System.currentTimeMillis()
        val needsNewSession = sessionStartTime == null ||
            (lastSessionEndTime != null && now - lastSessionEndTime!! > SESSION_NEW_THRESHOLD_MS)

        if (needsNewSession) {
            sessionStartTime = now
            currentSessionRotations = 0L
            currentSessionMantras = BigInteger.ZERO
            _hasActiveSession.value = true
            _sessionDuration.value = 0L
            _peakRpm.value = 0f
            _averageRpm.value = 0f
            _sessionDurationSeconds.value = 0L
            _isPaused.value = false
            rpmSamples.clear()
            rpmSampleCount = 0
            sessionStartRpmSum = 0f

            // Start notification service
            startNotificationService()

            // Start session timer
            sessionTimerJob?.cancel()
            sessionTimerJob = viewModelScope.launch {
                val startTime = now
                var timeGoalReached = false
                while (isActive) {
                    _sessionDuration.value = System.currentTimeMillis() - startTime
                    val newSeconds = _sessionDuration.value / 1000
                    _sessionDurationSeconds.value = newSeconds

                    val goalSecs = _sessionTimeGoalSeconds.value
                    if (goalSecs > 0 && newSeconds >= goalSecs && !timeGoalReached) {
                        timeGoalReached = true
                        triggerMilestoneHaptic()
                    }

                    // Update notification every second with current values
                    if (notificationActive && !_isPaused.value) {
                        SessionNotificationService.update(
                            newSeconds,
                            _sessionMantras.value,
                            paused = false
                        )
                    }

                    delay(SESSION_TIMER_INTERVAL_MS)
                }
            }
        }
    }

    private fun startPhysicsLoop() {
        physicsJob?.cancel()
        lastFrameTime = System.currentTimeMillis()
        physicsJob = viewModelScope.launch {
            while (isActive && abs(_angularVelocity.value) > STOP_THRESHOLD) {
                val currentTime = System.currentTimeMillis()
                val deltaTime = (currentTime - lastFrameTime).coerceAtLeast(1L) / 1000f
                lastFrameTime = currentTime

                // Determine friction coefficient based on mode and state
                val isTwoHandedMode = _spinMode.value == SpinMode.TWO_HANDED ||
                                     _spinMode.value == SpinMode.TWO_HANDED_AUTO ||
                                     (_twoHandedEnabled.value && _autoSpinEnabled.value)

                // Calculate velocity - in auto mode we maintain target, otherwise apply friction
                val currentOmega = _angularVelocity.value
                val calculatedVelocity = if (_autoSpinEnabled.value && !isTwoHandedMode) {
                    // Auto-spin mode: maintain constant velocity at target RPM
                    val targetOmega = _autoSpinRpm.value * 2 * PI.toFloat() / 60f
                    // Smoothly approach target velocity
                    currentOmega + (targetOmega - currentOmega) * 0.1f
                } else {
                    // Manual or two-handed mode: apply friction decay
                    val frictionCoefficient = when {
                        isTwoHandedMode && _twoHandedEngaged.value -> TWO_HANDED_FRICTION
                        else -> _baseFriction.value
                    }

                    // Apply friction decay: ω_{t+1} = ω_t × (1 - μ × Δt)
                    currentOmega * (1f - frictionCoefficient * deltaTime)
                }

                _angularVelocity.value = if (abs(calculatedVelocity) < STOP_THRESHOLD) 0f else calculatedVelocity

                // Update rotation angle
                val deltaAngle = calculatedVelocity * deltaTime
                val oldAngle = _rotationAngle.value
                val newAngle = oldAngle + deltaAngle
                _rotationAngle.value = newAngle

                // Count rotations when crossing 2π boundary
                val oldRevolutions = (oldAngle / TWO_PI).toLong()
                val newRevolutions = (newAngle / TWO_PI).toLong()
                if (newRevolutions > oldRevolutions) {
                    onRotationComplete(newRevolutions - oldRevolutions)
                }

                // Update RPM tracking: RPM = |ω| * 60 / (2π)
                val currentRpm = abs(_angularVelocity.value) * 60f / TWO_PI
                _currentRpm.value = currentRpm

                // Calculate weight angle based on centrifugal force
                // angle = atan(rpm / 30) clamped to 0..80 degrees
                val targetWeightAngle = (kotlin.math.atan(currentRpm.toDouble() / 30.0) * 180.0 / PI).toFloat()
                    .coerceIn(0f, 80f)
                // Smooth interpolation toward target angle
                _weightAngle.value += (targetWeightAngle - _weightAngle.value) * 0.1f

                // Track peak RPM
                if (currentRpm > _peakRpm.value) {
                    _peakRpm.value = currentRpm
                }

                // Track samples for average RPM calculation
                rpmSamples.add(currentRpm)
                rpmSampleCount++
                sessionStartRpmSum += currentRpm

                // Update running average RPM
                if (rpmSampleCount > 0) {
                    _averageRpm.value = sessionStartRpmSum / rpmSampleCount
                }

                // Update spinning state
                _isSpinning.value = abs(_angularVelocity.value) > STOP_THRESHOLD

                delay(ANIMATION_FRAME_TIME_MS)
            }

            _isSpinning.value = false
            scheduleSessionSave()
        }
    }

    private fun stopPhysicsLoop() {
        physicsJob?.cancel()
        physicsJob = null
        _isSpinning.value = false
    }

    private fun scheduleSessionSave() {
        sessionStopJob?.cancel()
        sessionStopJob = viewModelScope.launch {
            delay(SESSION_STOP_DELAY_MS)
            if (abs(_angularVelocity.value) < STOP_THRESHOLD) {
                saveSessionWithDedication()
            }
        }
    }

    private fun onRotationComplete(count: Long) {
        currentSessionRotations += count

        viewModelScope.launch {
            // Update lifetime stats
            val currentStats = lifetimeStatsDao.getStats()
            val newTotalRotations = (currentStats?.totalRotations ?: 0L) + count
            val mantrasThisRotation = _mantrasPerRotation.value.toBigInteger() * count.toBigInteger()
            val newTotalMantras = (currentStats?.totalMantras ?: BigInteger.ZERO) + mantrasThisRotation

            lifetimeStatsDao.upsert(
                LifetimeStats(
                    id = 1,
                    totalRotations = newTotalRotations,
                    totalMantras = newTotalMantras,
                    sessionsCompleted = currentStats?.sessionsCompleted ?: 0L,
                    firstSessionAt = currentStats?.firstSessionAt ?: System.currentTimeMillis()
                )
            )

            _lifetimeMantras.value = newTotalMantras
        }

        updateSessionMantras()
        triggerHapticTick()
    }

    private fun updateSessionMantras() {
        currentSessionMantras = currentSessionRotations.toBigInteger() * _mantrasPerRotation.value.toBigInteger()
        _sessionMantras.value = currentSessionMantras
    }

    private fun saveSessionWithDedication() {
        val startTime = sessionStartTime ?: return
        if (currentSessionRotations == 0L) {
            resetSessionState()
            return
        }

        // Calculate average RPM from samples
        val avgRpm = if (rpmSampleCount > 0) sessionStartRpmSum / rpmSampleCount else 0f

        // Store pending session data for dedication prompt
        pendingDedicationSession = PendingSession(
            startTime = startTime,
            rotations = currentSessionRotations,
            mantras = currentSessionMantras,
            mantrasPerRotation = _mantrasPerRotation.value,
            mantraId = _currentMantra.value.id,
            mode = _spinMode.value,
            averageRpm = avgRpm,
            peakRpm = _peakRpm.value,
            intention = _currentIntention.value.ifBlank { null },
            sessionGoal = if (_sessionGoal.value > 0) _sessionGoal.value else null
        )

        _hasActiveSession.value = false
        _showDedicationPrompt.value = true
        stopPhysicsLoop()
        _angularVelocity.value = 0f
    }

    private fun resetSessionState() {
        sessionStartTime = null
        currentSessionRotations = 0L
        currentSessionMantras = BigInteger.ZERO
        sessionId = null
        _rotationCount.value = 0L
        _sessionMantras.value = BigInteger.ZERO
        _hasActiveSession.value = false
        _sessionDuration.value = 0L
        _currentRpm.value = 0f
        _peakRpm.value = 0f
        _averageRpm.value = 0f
        _sessionDurationSeconds.value = 0L
        _isPaused.value = false
        rpmSamples.clear()
        rpmSampleCount = 0
        sessionStartRpmSum = 0f
        sessionTimerJob?.cancel()
        sessionTimerJob = null
        stopNotificationService()
    }

    // ── Notification service helpers ────────────────────────────────────────

    /**
     * Starts the foreground notification service for the active session.
     */
    private fun startNotificationService() {
        if (notificationActive) return
        notificationActive = true

        // Wire up the broadcast receiver callback so notification action
        // buttons (Pause/Resume/Close) trigger ViewModel methods
        SessionNotificationReceiver.callback = object : SessionNotificationReceiver.SessionNotificationCallback {
            override fun onPauseSession() {
                viewModelScope.launch {
                    pauseWheel()
                }
            }
            override fun onResumeSession() {
                viewModelScope.launch {
                    resumeWheel()
                }
            }
            override fun onCloseSession() {
                viewModelScope.launch {
                    endSession()
                }
            }
        }

        SessionNotificationService.start(
            appContext,
            _sessionDurationSeconds.value,
            _sessionMantras.value,
            _isPaused.value
        )
    }

    /**
     * Stops the foreground notification service.
     */
    private fun stopNotificationService() {
        if (!notificationActive) return
        notificationActive = false
        SessionNotificationReceiver.callback = null
        SessionNotificationService.stop(appContext)
    }

    private fun loadLifetimeStats() {
        viewModelScope.launch {
            lifetimeStatsDao.observeStats().collect { stats ->
                _lifetimeMantras.value = stats?.totalMantras ?: BigInteger.ZERO
            }
        }
    }

    private fun calculateAngularChange(newAngle: Float): Float {
        if (dragHistory.isEmpty()) return 0f
        val lastAngle = dragHistory.last().angle
        var diff = newAngle - lastAngle
        // Normalize to [-PI, PI]
        while (diff > PI) diff -= TWO_PI
        while (diff < -PI) diff += TWO_PI
        return diff
    }

    private fun checkRotationCompletion(angularDiff: Float) {
        // Track cumulative rotation for counting
        val totalRotation = _rotationAngle.value
        val newRevolutions = (totalRotation / TWO_PI).toLong()
        val oldRevolutions = ((totalRotation - angularDiff) / TWO_PI).toLong()
        if (newRevolutions > oldRevolutions) {
            _rotationCount.value += (newRevolutions - oldRevolutions)
            onRotationComplete(newRevolutions - oldRevolutions)
        }
    }

    private fun triggerHapticTick() {
        if (shouldVibrate()) {
            triggerHaptic(HAPTIC_TICK_DURATION)
        }
    }

    private fun triggerHapticPause() {
        if (shouldVibrate()) {
            triggerHaptic(HAPTIC_PAUSE_DURATION)
        }
    }

    private fun triggerHapticSlider() {
        if (shouldVibrate()) {
            triggerHaptic(HAPTIC_SLIDER_DURATION)
        }
    }

    fun triggerMilestoneHaptic() {
        if (shouldVibrate()) {
            triggerHaptic(HAPTIC_MILESTONE_DURATION)
        }
    }

    private fun shouldVibrate(): Boolean {
        if (!_hapticEnabled.value) return false
        if (_isAppInForeground.value) return true
        return _backgroundVibrationEnabled.value
    }

    fun setAppInForeground(inForeground: Boolean) {
        _isAppInForeground.value = inForeground
    }

    fun setBackgroundVibrationEnabled(enabled: Boolean) {
        _backgroundVibrationEnabled.value = enabled
        viewModelScope.launch {
            userPreferences.setBackgroundVibrationEnabled(enabled)
        }
    }

    fun setVibrationIntensity(intensity: Float) {
        _vibrationIntensity.value = intensity
        viewModelScope.launch {
            userPreferences.setVibrationIntensity(intensity)
        }
    }

    /**
     * Sets whether haptic feedback is enabled.
     */
    fun setHapticEnabled(enabled: Boolean) {
        _hapticEnabled.value = enabled
        viewModelScope.launch {
            userPreferences.setHapticEnabled(enabled)
        }
    }

    fun isAppInForeground(): Boolean = _isAppInForeground.value

    private fun triggerHaptic(durationMs: Long) {
        val intensity = _vibrationIntensity.value
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val amplitude = (VibrationEffect.DEFAULT_AMPLITUDE * intensity).toInt()
                .coerceIn(1, 255)
            val effect = VibrationEffect.createOneShot(durationMs, amplitude)
            vibrator.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(durationMs)
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Directly save session to Room without showing dedication prompt
        kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
            val session = createSessionFromCurrentState()
            if (session != null) {
                sessionDao.insert(session)
                updateLifetimeStats(session)
            }
        }
        physicsJob?.cancel()
        sessionTimerJob?.cancel()
    }

    private data class DragEvent(
        val angle: Float,
        val timestamp: Long
    )

    /**
     * Factory for creating WheelViewModel with dependencies.
     */
    class Factory(
        private val lifetimeStatsDao: LifetimeStatsDao,
        private val sessionDao: SessionDao,
        private val userPreferences: UserPreferences,
        private val vibrator: Vibrator,
        private val appContext: Context
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return WheelViewModel(lifetimeStatsDao, sessionDao, userPreferences, vibrator, appContext) as T
        }
    }
}
