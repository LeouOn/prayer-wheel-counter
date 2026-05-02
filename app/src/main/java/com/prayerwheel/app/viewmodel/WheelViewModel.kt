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
import com.prayerwheel.app.data.db.dao.LifetimeStatsDao
import com.prayerwheel.app.data.db.dao.SessionDao
import com.prayerwheel.app.data.model.LifetimeStats
import com.prayerwheel.app.data.model.Mantra
import com.prayerwheel.app.data.model.Mantras
import com.prayerwheel.app.data.model.WheelSkin
import com.prayerwheel.app.data.model.WheelSkins
import com.prayerwheel.app.data.model.Session
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
    private val vibrator: Vibrator
) : ViewModel() {

    companion object {
        private const val FRICTION_COEFFICIENT = 0.02f
        private const val TWO_HANDED_FRICTION = 0.0001f
        private const val STOP_THRESHOLD = 0.001f
        private const val TWO_PI = 2f * PI.toFloat()
        private const val SESSION_STOP_DELAY_MS = 30000L
        private const val SESSION_NEW_THRESHOLD_MS = 300000L // 5 minutes
        private const val ANIMATION_FRAME_TIME_MS = 16L

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
     * Session goal for this session.
     */
    private val _sessionGoal = MutableStateFlow(0L)
    val sessionGoal: StateFlow<Long> = _sessionGoal.asStateFlow()

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
            userPreferences.dailyMantraGoal.collect { goal ->
                _dailyMantraGoal.value = goal
            }
        }
        viewModelScope.launch {
            userPreferences.selectedSkin.collect { skinId ->
                _selectedSkin.value = WheelSkins.byId(skinId) ?: WheelSkins.default()
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
        _rotationAngle.value += angularDiff

        dragHistory.add(DragEvent(angle, timestamp))
        // Keep only last 5 events for velocity calculation
        while (dragHistory.size > 5) {
            dragHistory.removeAt(0)
        }
        // Check for rotation completion during drag too
        checkRotationCompletion(angularDiff)
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
                val velocity = totalWeightedAngularDiff / totalWeight
                // Only apply clockwise (positive) velocity, ignore counter-clockwise
                if (velocity > 0) {
                    _angularVelocity.value = velocity.coerceIn(0f, 50f)
                }
            }
        }

        startSessionIfNeeded()
        startPhysicsLoop()
    }

    /**
     * Pauses the wheel immediately (long press).
     */
    fun pauseWheel() {
        _angularVelocity.value = 0f
        stopPhysicsLoop()
        triggerHapticPause()
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
     * Sets the selected wheel skin.
     */
    fun setSelectedSkin(skin: WheelSkin) {
        viewModelScope.launch {
            _selectedSkin.value = skin
            userPreferences.setSelectedSkin(skin.id)
        }
    }

    /**
     * Triggers the send light animation.
     */
    fun triggerSendLight() {
        _sendLightActive.value = true
        viewModelScope.launch {
            // Trigger haptic pattern (3 gentle pulses)
            if (_hapticEnabled.value) {
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
            rpmSamples.clear()
            rpmSampleCount = 0
            sessionStartRpmSum = 0f
            
            // Start session timer
            sessionTimerJob?.cancel()
            sessionTimerJob = viewModelScope.launch {
                val startTime = now
                while (isActive) {
                    _sessionDuration.value = System.currentTimeMillis() - startTime
                    _sessionDurationSeconds.value = _sessionDuration.value / 1000
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

                // Determine friction coefficient
                val frictionCoefficient = when {
                    _spinMode.value == SpinMode.TWO_HANDED && _twoHandedEngaged.value -> TWO_HANDED_FRICTION
                    _spinMode.value == SpinMode.AUTO_SPIN && _autoSpinActive.value -> 0f // No friction in auto-spin
                    else -> _baseFriction.value
                }

                // Apply friction decay: ω_{t+1} = ω_t × (1 - μ × Δt)
                val newVelocity = if (frictionCoefficient > 0f) {
                    _angularVelocity.value * (1f - frictionCoefficient * deltaTime)
                } else {
                    // Auto-spin mode: maintain constant velocity
                    _angularVelocity.value
                }

                if (abs(newVelocity) < STOP_THRESHOLD && frictionCoefficient > 0f) {
                    _angularVelocity.value = 0f
                } else {
                    _angularVelocity.value = newVelocity
                }

                // Update rotation angle
                val deltaAngle = newVelocity * deltaTime
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
        rpmSamples.clear()
        rpmSampleCount = 0
        sessionStartRpmSum = 0f
        sessionTimerJob?.cancel()
        sessionTimerJob = null
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
        if (_hapticEnabled.value) {
            triggerHaptic(HAPTIC_TICK_DURATION)
        }
    }

    private fun triggerHapticPause() {
        if (_hapticEnabled.value) {
            triggerHaptic(HAPTIC_PAUSE_DURATION)
        }
    }

    private fun triggerHapticSlider() {
        if (_hapticEnabled.value) {
            triggerHaptic(HAPTIC_SLIDER_DURATION)
        }
    }

    /**
     * Triggers haptic feedback for milestone achievements (stronger pulse).
     */
    fun triggerMilestoneHaptic() {
        if (_hapticEnabled.value) {
            triggerHaptic(HAPTIC_MILESTONE_DURATION)
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

    private fun triggerHaptic(durationMs: Long) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect = VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE)
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
        private val vibrator: Vibrator
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return WheelViewModel(lifetimeStatsDao, sessionDao, userPreferences, vibrator) as T
        }
    }
}
