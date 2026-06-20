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
import com.prayerwheel.app.data.model.Achievement
import com.prayerwheel.app.data.model.Achievements
import com.prayerwheel.app.notification.SessionNotificationService
import com.prayerwheel.app.notification.SessionNotificationReceiver
import com.prayerwheel.app.widget.PrayerWheelWidget
import com.prayerwheel.app.work.SessionSaveWorker
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.math.BigInteger
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sin

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
        private const val WIDGET_UPDATE_THROTTLE_MS = 1000L
        // Reduced from 1.5f so flicks don't slam into the lowered velocity cap (12.566 rad/s).
        // At 1.0f, a typical drag of ~8 rad/s reaches the cap cleanly without an invisible wall.
        private const val FLICK_VELOCITY_MULTIPLIER = 1.0f
        // 120 RPM × 2π/60 ≈ 12.566 rad/s — vigorous but meditative devotional cap.
        // Real prayer wheels spin at 30-100 RPM; the previous 75f (~716 RPM) was frantic.
        private const val MAX_ANGULAR_VELOCITY = 12.566f
        private const val COUNTER_CLOCKWISE_REMINDER_DELAY_MS = 3000L
        private const val MIN_COUNTER_CLOCKWISE_VELOCITY = 3.0f // rad/s — below this, accidental flicks are ignored
        private const val COUNTER_CLOCKWISE_COOLDOWN_MS = 300_000L // 5 min between reminders

        // Static friction: wheel halts cleanly below this angular velocity (rad/s) when no torque is applied.
        private const val STATIC_FRICTION_THRESHOLD = 0.05f
        // Weight→wheel coupling: restoring torque coefficient from sin(weightAngle); acts as extra friction at low speed.
        private const val WEIGHT_COUPLING_COEFFICIENT = 0.03f
        // Auto-spin ramp: max rad/s change per frame at the 60fps baseline (~0.5-1.5s ramp to a typical target RPM).
        private const val AUTO_SPIN_MAX_ACCEL_PER_FRAME = 0.1f
        // Wheel-mass scaling default. Drag angular change is divided by wheelMass so heavier wheels resist acceleration.
        private const val WHEEL_MASS_DEFAULT = 1.0f

        // Haptic feedback durations in milliseconds
        private const val HAPTIC_TICK_DURATION = 5L
        private const val HAPTIC_PAUSE_DURATION = 50L
        private const val HAPTIC_SLIDER_DURATION = 3L
        private const val HAPTIC_MILESTONE_DURATION = 20L
        private const val SESSION_TIMER_INTERVAL_MS = 1000L
    }

    /**
     * Serializes all lifetime_stats DB read-modify-write sequences. Without this,
     * concurrent coroutines (session-end save, manual log, undo-delete, subtract
     * on delete/resume) can read the same row, increment separately, and the
     * loser's upsert clobbers the winner's increment — a silent lost update.
     */
    private val lifetimeStatsMutex = Mutex()

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
     * Wheel mass for drag scaling (α = F / (m·r)); heavier wheels accelerate less per identical drag.
     */
    private val _wheelMass = MutableStateFlow(1.0f)
    val wheelMass: StateFlow<Float> = _wheelMass.asStateFlow()

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
     * Daily time goal in seconds.
     */
    private val _dailyTimeGoalSeconds = MutableStateFlow(0L)
    val dailyTimeGoalSeconds: StateFlow<Long> = _dailyTimeGoalSeconds.asStateFlow()

    /**
     * Currently selected wheel skin.
     */
    private val _selectedSkin = MutableStateFlow(WheelSkins.default())
    val selectedSkin: StateFlow<WheelSkin> = _selectedSkin.asStateFlow()

    /**
     * Currently active wheel ID (from saved wheels). null if manually configured.
     */
    private val _currentWheelId = MutableStateFlow<String?>(null)
    val currentWheelId: StateFlow<String?> = _currentWheelId.asStateFlow()

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
    
    private val _unlockedAchievements = MutableStateFlow<Set<String>>(emptySet())
    val unlockedAchievements: StateFlow<Set<String>> = _unlockedAchievements.asStateFlow()

    private val _newlyUnlockedAchievement = MutableStateFlow<Achievement?>(null)
    val newlyUnlockedAchievement: StateFlow<Achievement?> = _newlyUnlockedAchievement.asStateFlow()

    // Today's Progress state (T8). Collected in init {} from SessionDao.observeSessionsForDay.
    // Attribution rule: a session is "morning" if Calendar.HOUR_OF_DAY of its startedAt is
    // strictly less than reminderEveningHour (default 19). Otherwise it is "evening".
    // Sessions spanning the boundary are attributed by their startedAt hour, not their end.
    private val _todayMorningCompleted = MutableStateFlow(false)
    val todayMorningCompleted: StateFlow<Boolean> = _todayMorningCompleted.asStateFlow()

    private val _todayEveningCompleted = MutableStateFlow(false)
    val todayEveningCompleted: StateFlow<Boolean> = _todayEveningCompleted.asStateFlow()

    private val _todayMantraCount = MutableStateFlow(BigInteger.ZERO)
    val todayMantraCount: StateFlow<BigInteger> = _todayMantraCount.asStateFlow()

    private val _todayPracticeSeconds = MutableStateFlow(0L)
    val todayPracticeSeconds: StateFlow<Long> = _todayPracticeSeconds.asStateFlow()

    /**
     * Today's progress toward [dailyMantraGoal], clamped to 0f..1f. Yields 0f
     * when no goal is set (goal <= 0) — callers must read [dailyMantraGoal]
     * to distinguish "no goal" from "not yet started".
     */
    val dailyMantraProgress: StateFlow<Float> =
        combine(_todayMantraCount, _dailyMantraGoal) { count, goal ->
            if (goal <= 0L) 0f
            else (count.toDouble() / goal.toDouble()).coerceIn(0.0, 1.0).toFloat()
        }.stateIn(viewModelScope, SharingStarted.Eagerly, 0f)

    /**
     * Today's progress toward [dailyTimeGoalSeconds], clamped to 0f..1f.
     * Yields 0f when no goal is set (goal <= 0).
     */
    val dailyTimeProgress: StateFlow<Float> =
        combine(_todayPracticeSeconds, _dailyTimeGoalSeconds) { seconds, goal ->
            if (goal <= 0L) 0f
            else (seconds.toFloat() / goal.toFloat()).coerceIn(0f, 1f)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, 0f)

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

    private var canvasWidth = 0f
    private var canvasHeight = 0f
    private var nextParticleId = 0

    fun updateCanvasDimensions(width: Float, height: Float) {
        canvasWidth = width
        canvasHeight = height
    }

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
    private var sessionMergeThresholdMs: Long = SESSION_NEW_THRESHOLD_MS

    // T15: cached Flow values so the non-suspending session-start path can read them
    // (same pattern as sessionMergeThresholdMs). pendingSessionLabel is consumed by
    // createSessionFromPending / createSessionFromCurrentState.
    private var pendingSessionLabel: String? = null
    private var autoLabelSessionsEnabled: Boolean = true
    private var cachedReminderEveningHour: Int = 19

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
        val sessionGoal: Long?,
        val wheelId: String?
    )

    init {
        viewModelScope.launch {
            userPreferences.mantrasPerRotation.collect { value ->
                _mantrasPerRotation.value = value
            }
        }
        viewModelScope.launch {
            userPreferences.sessionMergeThresholdMs.collect { value ->
                sessionMergeThresholdMs = value
            }
        }
        viewModelScope.launch {
            userPreferences.autoLabelSessions.collect { enabled ->
                autoLabelSessionsEnabled = enabled
            }
        }
        viewModelScope.launch {
            userPreferences.reminderEveningHour.collect { hour ->
                cachedReminderEveningHour = hour
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
            userPreferences.wheelMass.collect { mass ->
                _wheelMass.value = mass
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
            userPreferences.dailyTimeGoalSeconds.collect { goal ->
                _dailyTimeGoalSeconds.value = goal
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
        viewModelScope.launch {
            userPreferences.unlockedAchievements.collect { achievements ->
                _unlockedAchievements.value = achievements
            }
        }
        observeTodayProgress()
        loadLifetimeStats()
    }

    /**
     * Collects today's sessions via SessionDao.observeSessionsForDay and reduces them
     * into the four TodayProgress state flows. Recomputed whenever sessions change OR
     * when the local calendar day rolls over (polled every 60s) OR when the user
     * changes reminderEveningHour (since that drives morning/evening attribution).
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeTodayProgress() {
        viewModelScope.launch {
            val dayWindow = flow {
                while (isActive) {
                    emit(currentDayWindow())
                    val now = System.currentTimeMillis()
                    val tomorrow = startOfNextDay(now)
                    delay((tomorrow - now + 1000L).coerceAtLeast(60_000L))
                }
            }.distinctUntilChanged()

            dayWindow.combine(userPreferences.reminderEveningHour) { window, eveningHour ->
                window to eveningHour
            }.distinctUntilChanged().flatMapLatest { (window, eveningHour) ->
                sessionDao.observeSessionsForDay(window.first, window.second)
                    .map { sessions -> sessions to eveningHour }
            }.collect { (sessions, eveningHour) ->
                var morning = false
                var evening = false
                var mantras = BigInteger.ZERO
                var practiceMs = 0L
                val calendar = Calendar.getInstance(TimeZone.getDefault())
                for (session in sessions) {
                    calendar.timeInMillis = session.startedAt
                    val hour = calendar.get(Calendar.HOUR_OF_DAY)
                    if (hour < eveningHour) morning = true else evening = true
                    mantras = mantras.add(session.totalMantras)
                    val endMs = session.endedAt ?: session.startedAt
                    if (endMs >= session.startedAt) {
                        practiceMs += (endMs - session.startedAt)
                    }
                }
                _todayMorningCompleted.value = morning
                _todayEveningCompleted.value = evening
                _todayMantraCount.value = mantras
                _todayPracticeSeconds.value = practiceMs / 1000L
            }
        }
    }

    private fun startOfNextDay(epochMillis: Long): Long {
        val calendar = Calendar.getInstance(TimeZone.getDefault())
        calendar.timeInMillis = epochMillis
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        calendar.add(Calendar.DAY_OF_MONTH, 1)
        return calendar.timeInMillis
    }

    private fun currentDayWindow(): Pair<Long, Long> {
        val calendar = Calendar.getInstance(TimeZone.getDefault())
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startOfDay = calendar.timeInMillis
        calendar.add(Calendar.DAY_OF_MONTH, 1)
        val startOfNextDay = calendar.timeInMillis
        return startOfDay to startOfNextDay
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
        // Do not snap to target — the physics loop ramps up via torque-limited acceleration.
        startSessionIfNeeded()
        startPhysicsLoop()
    }

    /**
     * Stops auto-spin mode. The wheel coasts down via friction rather than snapping to a halt.
     */
    fun stopAutoSpin() {
        _autoSpinActive.value = false
    }

    /**
     * Records a drag move event for velocity calculation.
     */
    fun onDragMove(x: Float, y: Float, centerX: Float, centerY: Float, timestamp: Long) {
        val angle = atan2(y - centerY, x - centerX)
        // Wheel-mass scaling: heavier wheels rotate less per identical drag (α = F / (m·r)).
        val angularDiff = calculateAngularChange(angle) / _wheelMass.value

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

                // Check for counter-clockwise motion (only trigger for intentional CCW flicks)
                if (velocity < -MIN_COUNTER_CLOCKWISE_VELOCITY) {
                    // Significant counter-clockwise detected
                    if (!_counterClockwiseEnabled.value) {
                        // Show gentle reminder but still allow the motion
                        showCounterClockwiseReminder()
                    }
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
        if (now - lastCounterClockwiseReminderTime < COUNTER_CLOCKWISE_COOLDOWN_MS) return // 5 minute cooldown

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
            _currentWheelId.value = null // Cleared because user manually modified settings
            updateSessionMantras()
        }
    }

    /**
     * Updates the selected mantra.
     */
    fun setSelectedMantra(mantraId: String) {
        viewModelScope.launch {
            userPreferences.setSelectedMantra(mantraId)
            _currentWheelId.value = null // Cleared because user manually modified settings
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
                // The physics loop ramps the wheel up to the target RPM via torque-limited acceleration.
                startSessionIfNeeded()
                startPhysicsLoop()
            } else {
                // Coast down via friction — do not snap velocity to 0.
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
            _currentWheelId.value = wheel.id
        }
    }

    /**
     * Logs a manual/retrospective prayer wheel practice session.
     */
    fun logManualSession(
        wheelId: String?,
        startedAt: Long,
        durationSeconds: Long,
        rotationCount: Long,
        mantraId: String,
        mantrasPerRotation: Long,
        intention: String?,
        dedication: String?
    ) {
        viewModelScope.launch {
            val endedAt = startedAt + (durationSeconds * 1000)
            val totalMantras = java.math.BigInteger.valueOf(rotationCount)
                .multiply(java.math.BigInteger.valueOf(mantrasPerRotation))

            val session = Session(
                startedAt = startedAt,
                endedAt = endedAt,
                rotationCount = rotationCount,
                mantrasPerRotation = mantrasPerRotation,
                totalMantras = totalMantras,
                mantraId = mantraId,
                dedication = dedication,
                mode = "MANUAL",
                averageRpm = 0f,
                peakRpm = 0f,
                totalSpins = rotationCount,
                intention = intention?.ifBlank { null },
                sessionGoal = null,
                wheelId = wheelId
            )

            sessionDao.insert(session)

            // Update lifetime stats under the mutex so this read-modify-write cannot
            // interleave with session-end, undo-delete, or subtract operations.
            val newTotalMantras = lifetimeStatsMutex.withLock {
                val existing = lifetimeStatsDao.getStats()
                val updated = existing?.copy(
                    totalRotations = existing.totalRotations + session.rotationCount,
                    totalMantras = existing.totalMantras + session.totalMantras,
                    sessionsCompleted = existing.sessionsCompleted + 1,
                    totalSpinningTimeSeconds = existing.totalSpinningTimeSeconds + durationSeconds,
                    averageSessionDurationSeconds = if (existing.sessionsCompleted > 0) {
                        (existing.totalSpinningTimeSeconds + durationSeconds) / (existing.sessionsCompleted + 1)
                    } else durationSeconds
                ) ?: LifetimeStats(
                    id = 1,
                    totalRotations = session.rotationCount,
                    totalMantras = session.totalMantras,
                    sessionsCompleted = 1,
                    firstSessionAt = session.startedAt,
                    totalSpinningTimeSeconds = durationSeconds,
                    averageSessionDurationSeconds = durationSeconds
                )
                lifetimeStatsDao.upsert(updated)
                updated.totalMantras
            }
            checkAchievements(newTotalMantras)
        }
    }

    /**
     * Deletes a prayer wheel session and updates lifetime stats accordingly (without downgrading achievements).
     */
    fun deleteSession(session: Session) {
        viewModelScope.launch {
            sessionDao.deleteSessionById(session.id)
            subtractSessionFromStats(session)
        }
    }

    /**
     * Updates only the editable label on a session (T15). Persists via SessionDao.update.
     * Passing null clears the label ("None" in the UI). Lifetime stats are unaffected.
     */
    fun updateSessionLabel(session: Session, newLabel: String?) {
        viewModelScope.launch {
            sessionDao.update(session.copy(label = newLabel))
        }
    }

    /**
     * Restores a previously deleted session (undo). Re-inserts and re-adds to lifetime stats.
     */
    fun undoDeleteSession(session: Session) {        viewModelScope.launch {
            sessionDao.insert(session)
            val sessionDurationSeconds = ((session.endedAt ?: session.startedAt) - session.startedAt) / 1000
            // Hold the mutex across read-modify-write to serialize with other lifetime_stats writers.
            lifetimeStatsMutex.withLock {
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
        }
    }

    private suspend fun subtractSessionFromStats(session: Session) {
        // Hold the mutex across read-modify-write so concurrent adders (session save,
        // manual log, undo-delete) can't interleave with this subtraction and clobber it.
        lifetimeStatsMutex.withLock {
            val existing = lifetimeStatsDao.getStats() ?: return@withLock
            val sessionDuration = ((session.endedAt ?: session.startedAt) - session.startedAt) / 1000
            val newCount = (existing.sessionsCompleted - 1).coerceAtLeast(0)
            val newStats = existing.copy(
                totalRotations = (existing.totalRotations - session.rotationCount).coerceAtLeast(0),
                totalMantras = (existing.totalMantras - session.totalMantras).coerceAtLeast(java.math.BigInteger.ZERO),
                sessionsCompleted = newCount,
                totalSpinningTimeSeconds = (existing.totalSpinningTimeSeconds - sessionDuration).coerceAtLeast(0),
                averageSessionDurationSeconds = if (newCount > 0) {
                    (existing.totalSpinningTimeSeconds - sessionDuration).coerceAtLeast(0) / newCount
                } else 0L
            )
            lifetimeStatsDao.upsert(newStats)
        }
    }

    /**
     * Reset all lifetime accumulation counters to zero. Preserves the individual
     * session records in the sessions table (so history is still browsable).
     * Use this to start fresh after correcting a data-corruption bug.
     *
     * Holds the same [lifetimeStatsMutex] as every other lifetime_stats writer so
     * a concurrent session-end save cannot interleave with this reset.
     */
    fun resetLifetimeStats() {
        viewModelScope.launch {
            lifetimeStatsMutex.withLock {
                lifetimeStatsDao.upsert(
                    LifetimeStats(
                        id = 1,
                        totalRotations = 0L,
                        totalMantras = BigInteger.ZERO,
                        sessionsCompleted = 0L,
                        firstSessionAt = null,
                        totalSpinningTimeSeconds = 0L,
                        averageSessionDurationSeconds = 0L
                    )
                )
            }
            // In-memory display reset so the wheel screen updates immediately.
            _lifetimeMantras.value = BigInteger.ZERO
            // Trigger widget refresh (same pattern as the rotation-completion path).
            runCatching { PrayerWheelWidget().updateAll(appContext) }
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
     * T17: Detects whether the most-recent persisted session was interrupted and
     * is conservatively resumable. Returns the [Session] to offer the user, or null.
     *
     * All three conditions must hold:
     *  - recent: `endedAt == null` OR `startedAt` within [SESSION_NEW_THRESHOLD_MS]
     *  - had motion: `rotationCount > 0`
     *  - interrupted: `dedication == null` (a normally-ended session is not resumable)
     */
    suspend fun detectResumableSession(): Session? {
        val recent = sessionDao.getMostRecentSession() ?: return null
        val now = System.currentTimeMillis()
        val isRecent = recent.endedAt == null ||
            (now - recent.startedAt) <= SESSION_NEW_THRESHOLD_MS
        val hasRotations = recent.rotationCount > 0L
        val isInterrupted = recent.dedication == null
        return if (isRecent && hasRotations && isInterrupted) recent else null
    }

    /**
     * T17: Resumes practice from a previously-interrupted session.
     *
     * Restores the in-memory counters and `startedAt` so the timer keeps counting
     * from the original start. Deletes the prior DB record (it is being replaced
     * by the in-flight resumed session, which will be re-persisted on the next
     * endSession/onCleared). Re-enters active session state, restarts the
     * notification service and session timer.
     *
     * Does NOT restore transient physics state (angularVelocity, drag history).
     */
    fun resumeFromSession(session: Session) {
        viewModelScope.launch {
            // Drop the prior record so it isn't double-counted when the resumed
            // session is later saved. The in-memory state below replaces it.
            // Also roll back this session's contribution from lifetime_stats — without
            // this, the resumed session's mantras get double-counted: once when the
            // original was saved, again when updateLifetimeStats() runs at the resumed
            // session's end. updateLifetimeStats() re-adds the full accumulated total.
            if (session.id != 0L) {
                sessionDao.deleteSessionById(session.id)
                subtractSessionFromStats(session)
            }

            // Mirror startSessionIfNeeded() restoration, but preserve the
            // accumulated rotations/start time instead of zeroing them.
            sessionStartTime = session.startedAt
            currentSessionRotations = session.rotationCount
            currentSessionMantras = session.totalMantras
            _rotationCount.value = session.rotationCount
            _sessionMantras.value = session.totalMantras
            _hasActiveSession.value = true
            _isPaused.value = false
            _peakRpm.value = session.peakRpm
            _averageRpm.value = session.averageRpm
            // Transient RPM samples are not persisted — reset so the running
            // average re-converges from zero rather than from stale state.
            rpmSamples.clear()
            rpmSampleCount = 0
            sessionStartRpmSum = 0f
            val elapsedMs = (System.currentTimeMillis() - session.startedAt)
                .coerceAtLeast(0L)
            _sessionDuration.value = elapsedMs
            _sessionDurationSeconds.value = elapsedMs / 1000
            pendingSessionLabel = session.label

            startNotificationService()

            // Restart the session timer (mirrors startSessionIfNeeded). The
            // timeGoalReached flag is pre-set when the resumed session already
            // meets the goal so we don't immediately fire the milestone haptic.
            sessionTimerJob?.cancel()
            sessionTimerJob = viewModelScope.launch {
                var timeGoalReached = _sessionTimeGoalSeconds.value > 0 &&
                    _sessionDurationSeconds.value >= _sessionTimeGoalSeconds.value
                while (isActive) {
                    _sessionDuration.value = System.currentTimeMillis() - session.startedAt
                    val newSeconds = _sessionDuration.value / 1000
                    _sessionDurationSeconds.value = newSeconds

                    val goalSecs = _sessionTimeGoalSeconds.value
                    if (goalSecs > 0 && newSeconds >= goalSecs && !timeGoalReached) {
                        timeGoalReached = true
                        triggerMilestoneHaptic()
                    }

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
            sessionGoal = pending.sessionGoal,
            wheelId = pending.wheelId,
            label = pendingSessionLabel
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
            sessionGoal = if (_sessionGoal.value > 0) _sessionGoal.value else null,
            wheelId = _currentWheelId.value,
            label = pendingSessionLabel
        )
    }

    /**
     * Updates lifetime stats by upserting with carried-forward spinning time fields.
     */
    private suspend fun updateLifetimeStats(session: Session) {
        val sessionDurationSeconds = _sessionDurationSeconds.value
        // Hold the mutex across the read-modify-write so concurrent writers (manual log,
        // undo-delete, subtract) can't interleave and clobber each other's increment.
        val newStats = lifetimeStatsMutex.withLock {
            val existing = lifetimeStatsDao.getStats()

            val updated = existing?.copy(
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

            lifetimeStatsDao.upsert(updated)
            updated
        }
        checkAchievements(newStats.totalMantras)
    }

    private fun startSessionIfNeeded() {
        val now = System.currentTimeMillis()
        val needsNewSession = sessionStartTime == null ||
            (lastSessionEndTime != null && now - lastSessionEndTime!! > sessionMergeThresholdMs)

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

            // T15: auto-suggest morning/evening label. Attribution matches the rule in
            // observeTodayProgress() / TodayProgressCard — hour is strictly less than
            // reminderEveningHour for "morning", otherwise "evening". Null when disabled.
            pendingSessionLabel = if (autoLabelSessionsEnabled) {
                val cal = Calendar.getInstance(TimeZone.getDefault())
                cal.timeInMillis = now
                if (cal.get(Calendar.HOUR_OF_DAY) < cachedReminderEveningHour) "morning" else "evening"
            } else {
                null
            }

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
            // Loop stays alive while there is motion, particles to animate, or auto-spin is driving
            // (so the torque-limited ramp can accelerate the wheel up from rest).
            while (isActive && (abs(_angularVelocity.value) > STOP_THRESHOLD || _starParticles.value.isNotEmpty() || _autoSpinEnabled.value)) {
                val currentTime = System.currentTimeMillis()
                val deltaTime = (currentTime - lastFrameTime).coerceAtLeast(1L) / 1000f
                lastFrameTime = currentTime

                // Determine friction coefficient based on mode and state
                val isTwoHandedMode = _spinMode.value == SpinMode.TWO_HANDED ||
                                     _spinMode.value == SpinMode.TWO_HANDED_AUTO ||
                                     (_twoHandedEnabled.value && _autoSpinEnabled.value)

                // Calculate velocity - in auto mode we ramp toward target, otherwise apply friction
                val currentOmega = _angularVelocity.value
                val calculatedVelocity = if (_autoSpinEnabled.value && !isTwoHandedMode) {
                    // Auto-spin: torque-limited ramp toward target RPM (~0.5-1.5s to reach set speed)
                    val targetOmega = _autoSpinRpm.value * 2 * PI.toFloat() / 60f
                    val gap = targetOmega - currentOmega
                    val maxStepThisFrame = AUTO_SPIN_MAX_ACCEL_PER_FRAME * (deltaTime * 60f)
                    currentOmega + gap.coerceIn(-maxStepThisFrame, maxStepThisFrame)
                } else {
                    // Manual / two-handed / coast: friction decay ω_{t+1} = ω_t × (1 - μ × Δt)
                    val frictionCoefficient = when {
                        isTwoHandedMode && _twoHandedEngaged.value -> TWO_HANDED_FRICTION
                        else -> _baseFriction.value
                    }
                    val frictionDecayed = currentOmega * (1f - frictionCoefficient * deltaTime)

                    // Weight→wheel coupling: the chain weight acts as a pendulum restoring force.
                    // Applied as additional friction opposing motion, capped to never reverse direction.
                    val weightAngleRad = _weightAngle.value * PI.toFloat() / 180f
                    val couplingDecel = WEIGHT_COUPLING_COEFFICIENT * sin(weightAngleRad) * deltaTime
                    val withCoupling = when {
                        frictionDecayed > 0f -> (frictionDecayed - couplingDecel).coerceAtLeast(0f)
                        frictionDecayed < 0f -> (frictionDecayed + couplingDecel).coerceAtMost(0f)
                        else -> frictionDecayed
                    }

                    // Static friction: clean halt when very slow and no torque applied this frame.
                    if (abs(withCoupling) < STATIC_FRICTION_THRESHOLD) 0f else withCoupling
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

                // Update and prune particles
                val currentParticles = _starParticles.value.toMutableList()
                val iterator = currentParticles.listIterator()
                while (iterator.hasNext()) {
                    val p = iterator.next()
                    val newLifetime = p.lifetime - deltaTime
                    if (newLifetime <= 0f) {
                        iterator.remove()
                    } else {
                        iterator.set(
                            p.copy(
                                y = p.y - p.velocityY * deltaTime,
                                alpha = (newLifetime / 1.5f).coerceIn(0f, 1f),
                                lifetime = newLifetime
                            )
                        )
                    }
                }

                // Spawn new particles while spinning
                val velocity = abs(_angularVelocity.value)
                if (velocity > STOP_THRESHOLD && canvasWidth > 0 && canvasHeight > 0) {
                    val spawnProbability = (velocity / MAX_ANGULAR_VELOCITY) * 0.25f
                    if (Math.random() < spawnProbability && currentParticles.size < 40) {
                        currentParticles.add(
                            StarParticle(
                                id = nextParticleId++,
                                x = canvasWidth / 2f + (Math.random().toFloat() - 0.5f) * (canvasWidth * 0.4f),
                                y = canvasHeight * 0.35f + (Math.random().toFloat() - 0.5f) * (canvasHeight * 0.1f),
                                size = 6f + Math.random().toFloat() * 10f,
                                alpha = 1.0f,
                                colorIndex = (Math.random() * 3).toInt(),
                                velocityY = 60f + Math.random().toFloat() * 80f,
                                lifetime = 1.0f + Math.random().toFloat() * 0.8f
                            )
                        )
                    }
                }
                _starParticles.value = currentParticles

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

    private var lastWidgetUpdateMs = 0L

    private fun onRotationComplete(count: Long) {
        currentSessionRotations += count

        viewModelScope.launch {
            // In-memory lifetime mantras only — provides the live display while spinning.
            // The lifetime_stats DB row is written EXACTLY ONCE per session at session end
            // via updateLifetimeStats(). Writing it here on every rotation caused every
            // session's mantras to be double-counted: once incrementally per-rotation here,
            // then again at session save, inflating lifetime totals by N×.
            val mantrasThisRotation = _mantrasPerRotation.value.toBigInteger() * count.toBigInteger()
            val newTotalMantras = _lifetimeMantras.value + mantrasThisRotation
            _lifetimeMantras.value = newTotalMantras

            // Check achievements (reads the in-memory mantras value; does not touch lifetime_stats DB).
            checkAchievements(newTotalMantras)
        }

        // Spawn rotation completion particles
        if (canvasWidth > 0 && canvasHeight > 0) {
            val newParticles = mutableListOf<StarParticle>()
            for (i in 0 until 5) {
                newParticles.add(
                    StarParticle(
                        id = nextParticleId++,
                        x = canvasWidth / 2f + (Math.random().toFloat() - 0.5f) * (canvasWidth * 0.35f),
                        y = canvasHeight * 0.35f + (Math.random().toFloat() - 0.5f) * (canvasHeight * 0.1f),
                        size = 8f + Math.random().toFloat() * 10f,
                        alpha = 1.0f,
                        colorIndex = (Math.random() * 3).toInt(),
                        velocityY = 80f + Math.random().toFloat() * 80f,
                        lifetime = 1.2f + Math.random().toFloat() * 0.6f
                    )
                )
            }
            _starParticles.value = _starParticles.value + newParticles
            // Ensure physics loop is active to animate the particles
            if (abs(_angularVelocity.value) <= STOP_THRESHOLD) {
                startPhysicsLoop()
            }
        }

        updateSessionMantras()
        triggerHapticTick()

        val nowMs = System.currentTimeMillis()
        if (nowMs - lastWidgetUpdateMs >= WIDGET_UPDATE_THROTTLE_MS) {
            lastWidgetUpdateMs = nowMs
            viewModelScope.launch {
                runCatching { PrayerWheelWidget().updateAll(appContext) }
            }
        }
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
            sessionGoal = if (_sessionGoal.value > 0) _sessionGoal.value else null,
            wheelId = _currentWheelId.value
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

    private suspend fun checkAchievements(totalMantras: BigInteger) {
        val currentlyUnlocked = _unlockedAchievements.value
        val allAchievements = Achievements.ALL
        
        for (achievement in allAchievements) {
            if (achievement.id !in currentlyUnlocked && totalMantras >= achievement.mantrasRequired) {
                userPreferences.unlockAchievement(achievement.id)
                triggerMilestoneHaptic()
                _newlyUnlockedAchievement.value = achievement
            }
        }
    }

    fun dismissAchievementCelebration() {
        _newlyUnlockedAchievement.value = null
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

    fun startSessionFromWidget() {
        startSessionIfNeeded()
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
        // WorkManager (not GlobalScope) survives process death, so the session
        // is not lost if the app is killed mid-write. dedication=null because
        // no UI context is available here; user can edit in History.
        val snapshot = createSessionFromCurrentState()
        if (snapshot != null) {
            SessionSaveWorker.enqueue(
                context = appContext,
                startedAt = snapshot.startedAt,
                rotations = snapshot.rotationCount,
                mantrasPerRotation = snapshot.mantrasPerRotation,
                totalMantras = snapshot.totalMantras,
                mantraId = snapshot.mantraId,
                mode = snapshot.mode,
                averageRpm = snapshot.averageRpm,
                peakRpm = snapshot.peakRpm,
                intention = snapshot.intention,
                sessionGoal = snapshot.sessionGoal,
                wheelId = snapshot.wheelId,
                label = snapshot.label,
                sessionDurationSeconds = _sessionDurationSeconds.value
            )
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
