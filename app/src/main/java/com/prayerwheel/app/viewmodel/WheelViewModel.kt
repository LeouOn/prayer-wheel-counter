package com.prayerwheel.app.viewmodel

import android.content.Context
import android.os.Build
import android.util.Log
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
import com.prayerwheel.app.data.db.dao.LedgerDao
import com.prayerwheel.app.data.db.dao.LifetimeStatsDao
import com.prayerwheel.app.data.db.dao.SessionDao
import com.prayerwheel.app.data.model.ActiveSessionCheckpoint
import com.prayerwheel.app.data.model.LifetimeStats
import com.prayerwheel.app.data.model.Mantra
import com.prayerwheel.app.data.model.Mantras
import com.prayerwheel.app.data.model.SavedWheel
import com.prayerwheel.app.data.model.WheelSkin
import com.prayerwheel.app.data.model.WheelSkins
import com.prayerwheel.app.data.model.Session
import com.prayerwheel.app.data.stats.LifetimeStatsMath
import com.prayerwheel.app.data.model.Achievement
import com.prayerwheel.app.audio.AudioEngine
import com.prayerwheel.app.audio.BreathModeController
import com.prayerwheel.app.notification.SessionNotificationService
import com.prayerwheel.app.notification.SessionNotificationReceiver
import com.prayerwheel.app.widget.PrayerWheelWidget
import com.prayerwheel.app.work.SessionSaveWorker
import com.prayerwheel.app.viewmodel.haptics.HapticPatterns
import com.prayerwheel.app.viewmodel.milestones.Milestones
import com.prayerwheel.app.viewmodel.physics.SpinPhysics
import com.prayerwheel.app.viewmodel.session.SessionRules
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
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
import java.time.ZoneId
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
    private val ledgerDao: LedgerDao,
    /** Process-lifetime scope: durable writes run here so VM teardown can't cancel them. */
    private val appScope: CoroutineScope,
    private val userPreferences: UserPreferences,
    private val vibrator: Vibrator,
    private val appContext: Context
) : ViewModel() {

    companion object {
        private const val FRICTION_COEFFICIENT = SpinPhysics.FRICTION_COEFFICIENT
        private const val STOP_THRESHOLD = SpinPhysics.STOP_THRESHOLD
        private const val SESSION_STOP_DELAY_MS = 30000L
        private const val SESSION_NEW_THRESHOLD_MS = 300000L // 5 minutes
        private const val ANIMATION_FRAME_TIME_MS = 16L
        private const val WIDGET_UPDATE_THROTTLE_MS = 1000L
        private const val COUNTER_CLOCKWISE_REMINDER_DELAY_MS = 3000L
        private const val COUNTER_CLOCKWISE_COOLDOWN_MS = 300_000L // 5 min between reminders

        // Haptic feedback durations in milliseconds
        private const val HAPTIC_PAUSE_DURATION = 50L
        private const val HAPTIC_SLIDER_DURATION = 3L
        private const val SESSION_TIMER_INTERVAL_MS = 1000L
        private const val DAY_WINDOW_REFRESH_MS = 60_000L

        // Breath-mode physics constants live in [SpinPhysics]; this alias keeps the
        // physics-loop keep-alive condition readable.
        private const val BREATH_ACTIVE_THRESHOLD = SpinPhysics.BREATH_ACTIVE_THRESHOLD
    }

    /**
     * Runs a ledger write in [appScope] and awaits it, so leaving the screen or
     * clearing the VM cannot cancel a write half-way (the caller may be
     * cancelled, the write is not). All session + lifetime_stats mutations go
     * through [LedgerDao]'s `@Transaction` methods, which SQLite serializes
     * with every other writer (SessionSaveWorker, resets) — replacing the old
     * VM-only mutex that the worker and Settings reset could not see.
     *
     * Disk-full and other failures are surfaced via [saveError]; returns null
     * on failure.
     */
    private suspend fun <T> durableWrite(what: String, block: suspend () -> T): T? = try {
        appScope.async { block() }.await()
    } catch (e: CancellationException) {
        throw e
    } catch (e: android.database.sqlite.SQLiteFullException) {
        Log.e("WheelViewModel", "Disk full - $what not saved", e)
        _saveError.value = "Storage is full. Unable to save session data. Please free up space."
        null
    } catch (e: Exception) {
        Log.e("WheelViewModel", "Failed to save $what", e)
        _saveError.value = "Unable to save $what. Please try again."
        null
    }

    /**
     * Procedural audio engine — singing bowl tones at lifetime milestones and an
     * RPM-driven ambient drone. Null until the init block constructs it; released
     * via [audioEngine]?.stopDroneLoop() in onCleared().
     */
    private var audioEngine: AudioEngine? = null

    /**
     * Microphone-amplitude driver for breath/wind mode. Null until [init]
     * constructs it; released via [breathModeController]?.stop() in onCleared().
     * Tied to [viewModelScope] so its IO coroutine is cancelled with the ViewModel.
     */
    private var breathModeController: BreathModeController? = null

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

    /**
     * Whether breath/wind mode is enabled. When true, microphone amplitude
     * drives the wheel via [BreathModeController].
     */
    private val _breathModeEnabled = MutableStateFlow(false)
    val breathModeEnabled: StateFlow<Boolean> = _breathModeEnabled.asStateFlow()

    /**
     * Highest lifetime-milestone tier the practitioner has crossed (0 = none,
     * 1 = 1K, … 8 = 1T). Drives the persistent aura floor: even at rest, a
     * practitioner with 1B+ lifetime mantras sees a gentle glow on their wheel.
     * Indices align with [Milestones.MILESTONE_THRESHOLDS] (tier = index + 1).
     */
    private val _highestMilestoneTier = MutableStateFlow(0)
    val highestMilestoneTier: StateFlow<Int> = _highestMilestoneTier.asStateFlow()

    private val _showLiveCounter = MutableStateFlow(true)
    val showLiveCounter: StateFlow<Boolean> = _showLiveCounter.asStateFlow()

    private val _keepScreenOn = MutableStateFlow(false)
    val keepScreenOn: StateFlow<Boolean> = _keepScreenOn.asStateFlow()

    private val _bgAnimationIntensity = MutableStateFlow(UserPreferences.DEFAULT_BG_ANIMATION_INTENSITY)
    val bgAnimationIntensity: StateFlow<Int> = _bgAnimationIntensity.asStateFlow()

    private val _isAppInForeground = MutableStateFlow(true)
    val isAppInForeground: StateFlow<Boolean> = _isAppInForeground.asStateFlow()

    private val _backgroundVibrationEnabled = MutableStateFlow(false)
    val backgroundVibrationEnabled: StateFlow<Boolean> = _backgroundVibrationEnabled.asStateFlow()

    private val _vibrationIntensity = MutableStateFlow(1.0f)
    val vibrationIntensity: StateFlow<Float> = _vibrationIntensity.asStateFlow()
    
    private val _unlockedAchievements = MutableStateFlow<Set<String>>(emptySet())
    val unlockedAchievements: StateFlow<Set<String>> = _unlockedAchievements.asStateFlow()

    /**
     * Transient error surfaced when a session/lifetime-stats write fails — most
     * commonly [android.database.sqlite.SQLiteFullException] (disk full).
     * The UI observes this and shows a brief snackbar; `dismissSaveError()`
     * clears it after acknowledgment.
     */
    private val _saveError = MutableStateFlow<String?>(null)
    val saveError: StateFlow<String?> = _saveError.asStateFlow()

    fun dismissSaveError() {
        _saveError.value = null
    }

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
    private var rpmSampleCount = 0
    private var sessionStartRpmSum = 0f

    // Drag tracking
    private val dragHistory = mutableListOf<SpinPhysics.DragSample>()
    private var sessionStartTime: Long? = null

    /**
     * Base of the live session timer (duration = now − base). Equals
     * [sessionStartTime] for a new session; for a resumed one it is set so the
     * timer continues from the row's recorded duration (idle gap excluded).
     */
    private var sessionTimerBase: Long? = null
    private var currentSessionRotations = 0L
    private var currentSessionMantras = BigInteger.ZERO
    /**
     * Stable identity of the live session (UUID, or the key of the saved row
     * being resumed). Copied into the checkpoint, the saved [Session] and the
     * SessionSaveWorker input so every writer upserts the same row. Null when
     * no session is live; cleared *before* a session is handed off for saving
     * so a late checkpoint write for it is skipped.
     */
    private var sessionKey: String? = null

    /** Periodic [ActiveSessionCheckpoint] writer for the live session. */
    private var checkpointJob: Job? = null

    /** Recovery of checkpoints left by a killed process; checkpoint writes wait for it. */
    private var recoveryJob: Job? = null

    /**
     * Orders this VM's checkpoint writes against its session saves/resets, so
     * a checkpoint can never be written after its session was saved (which
     * would leave a stale checkpoint behind).
     */
    private val checkpointMutex = Mutex()
    private var pendingDedicationSession: PendingSession? = null

    // T15: cached Flow values so the non-suspending session-start path can read them
    // (cached from preference flows). pendingSessionLabel is consumed by
    // createSessionFromCurrentState and the checkpoint.
    private var pendingSessionLabel: String? = null
    private var autoLabelSessionsEnabled: Boolean = true
    private var cachedReminderEveningHour: Int = 19

    // Notification service active flag
    private var notificationActive = false

    /**
     * A session that has ended and is awaiting the dedication prompt. The
     * [session] (endedAt) and [durationSeconds] are snapshotted at the moment
     * the session ended, and it is already being recorded by [recordJob] — the
     * prompt only adds the dedication afterwards, so leaving it open (even
     * overnight, or until the process dies) neither inflates the recorded
     * time nor risks losing the session.
     */
    private data class PendingSession(
        val session: Session,
        val durationSeconds: Long,
        val recordJob: Job
    )

    init {
        viewModelScope.launch {
            userPreferences.mantrasPerRotation.collect { value ->
                _mantrasPerRotation.value = value
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
        viewModelScope.launch {
            // distinctUntilChanged: DataStore re-emits on ANY preference write,
            // which used to restart the microphone (even in the background).
            userPreferences.breathModeEnabled.distinctUntilChanged().collect { enabled ->
                _breathModeEnabled.value = enabled
                if (enabled) {
                    if (_isAppInForeground.value) breathModeController?.start()
                    startSessionIfNeeded()
                    startPhysicsLoop()
                } else {
                    breathModeController?.stop()
                }
            }
        }
        viewModelScope.launch {
            userPreferences.showCounter.collect { enabled ->
                _showLiveCounter.value = enabled
            }
        }
        viewModelScope.launch {
            userPreferences.keepScreenOn.collect { enabled ->
                _keepScreenOn.value = enabled
            }
        }
        viewModelScope.launch {
            userPreferences.bgAnimationIntensity.collect { intensity ->
                _bgAnimationIntensity.value = intensity
            }
        }

        // Procedural audio: instantiate the engine, watch all three audio settings
        // simultaneously, and start the (silent-until-spun) ambient drone.
        audioEngine = AudioEngine().also { engine ->
            viewModelScope.launch {
                combine(
                    userPreferences.masterVolume,
                    userPreferences.bellAtMilestones,
                    userPreferences.ambientEnabled
                ) { masterVolume, bellAtMilestones, ambientEnabled ->
                    Triple(masterVolume, bellAtMilestones, ambientEnabled)
                }.collect { (masterVolume, bellAtMilestones, ambientEnabled) ->
                    engine.updateSettings(masterVolume, bellAtMilestones, ambientEnabled)
                }
            }
            viewModelScope.launch {
                engine.startDroneLoop()
            }
        }
        breathModeController = BreathModeController(appContext, viewModelScope)
        recoverInterruptedSessions()
        observeTodayProgress()
        loadLifetimeStats()
    }

    /**
     * Process-death recovery. Any `active_session` checkpoint left behind means
     * the app died (OOM kill, crash, force-stop, battery) while a session was
     * live. Each is recorded as a completed session with no dedication —
     * exactly what onCleared + SessionSaveWorker would have saved — rather than
     * restored as the live session: restoring would silently keep a timer
     * running across the downtime (hours, possibly) and the user may never
     * come back to end it. The existing "Resume your previous session?" banner
     * still offers to continue it, replacing the saved row in place.
     *
     * Runs in [appScope] so it can't be cancelled half-way; upsert-by-key
     * makes it safe to race with a pending SessionSaveWorker for the same
     * session.
     */
    private fun recoverInterruptedSessions() {
        recoveryJob = appScope.launch {
            val recovered = try {
                ledgerDao.recoverCheckpoints()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("WheelViewModel", "Checkpoint recovery failed", e)
                emptyList()
            }
            recovered.lastOrNull()?.let { checkAchievements(it.stats.totalMantras) }
        }
    }

    /** Snapshot of the live session for checkpointing, or null if there is nothing worth saving. */
    private fun buildCheckpoint(now: Long = System.currentTimeMillis()): ActiveSessionCheckpoint? {
        val key = sessionKey ?: return null
        val startTime = sessionStartTime ?: return null
        if (currentSessionRotations <= 0L) return null
        return ActiveSessionCheckpoint(
            sessionKey = key,
            startedAt = startTime,
            updatedAt = now,
            rotationCount = currentSessionRotations,
            mantrasPerRotation = _mantrasPerRotation.value,
            totalMantras = currentSessionMantras,
            mantraId = _currentMantra.value.id,
            mode = _spinMode.value.name,
            averageRpm = if (rpmSampleCount > 0) sessionStartRpmSum / rpmSampleCount else 0f,
            peakRpm = _peakRpm.value,
            intention = _currentIntention.value.ifBlank { null },
            sessionGoal = if (_sessionGoal.value > 0) _sessionGoal.value else null,
            wheelId = _currentWheelId.value,
            label = pendingSessionLabel,
            durationSeconds = _sessionDurationSeconds.value
        )
    }

    /**
     * Persists the live session's checkpoint now (no-op without rotations).
     * Called every [ActiveSessionCheckpoint.CHECKPOINT_INTERVAL_MS] and when
     * the app goes to the background. Runs in [appScope]; skipped if the
     * session was saved/discarded before the write got its turn.
     */
    private fun checkpointNow() {
        val checkpoint = buildCheckpoint() ?: return
        appScope.launch {
            recoveryJob?.join()
            checkpointMutex.withLock {
                if (sessionKey != checkpoint.sessionKey) return@withLock
                try {
                    ledgerDao.saveCheckpoint(checkpoint)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w("WheelViewModel", "Checkpoint write failed", e)
                }
            }
        }
    }

    private fun startCheckpointing() {
        checkpointJob?.cancel()
        checkpointJob = viewModelScope.launch {
            while (isActive) {
                delay(ActiveSessionCheckpoint.CHECKPOINT_INTERVAL_MS)
                checkpointNow()
            }
        }
    }

    private fun stopCheckpointing() {
        checkpointJob?.cancel()
        checkpointJob = null
    }

    /**
     * Collects today's sessions via SessionDao.observeSessionsForDay and reduces them
     * into the four TodayProgress state flows. Recomputed whenever sessions change,
     * when the user changes reminderEveningHour (it drives morning/evening
     * attribution), and whenever the local day or time zone changes.
     *
     * The day window is recomputed from the current wall-clock date and
     * `ZoneId.systemDefault()` immediately whenever the app returns to the
     * foreground, then every 60 s while it stays there. (It used to `delay()`
     * until midnight; coroutine delays on Main are uptime-based and stall in
     * deep sleep, so after an overnight sleep Jan 1 still showed Dec 31's
     * totals, and zone changes were ignored.)
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeTodayProgress() {
        viewModelScope.launch {
            val dayWindow = _isAppInForeground.flatMapLatest { inForeground ->
                if (inForeground) {
                    flow {
                        while (true) {
                            emit(SessionRules.dayWindow(System.currentTimeMillis(), ZoneId.systemDefault()))
                            delay(DAY_WINDOW_REFRESH_MS)
                        }
                    }
                } else {
                    emptyFlow() // keep the last window; refreshed on return to foreground
                }
            }.distinctUntilChanged()

            dayWindow.combine(userPreferences.reminderEveningHour) { window, eveningHour ->
                window to eveningHour
            }.distinctUntilChanged().flatMapLatest { (window, eveningHour) ->
                sessionDao.observeSessionsForDay(window.startMs, window.endMs)
                    .map { sessions -> Triple(sessions, eveningHour, window.zone) }
            }.collect { (sessions, eveningHour, zone) ->
                val progress = SessionRules.todayProgress(sessions, eveningHour, zone)
                _todayMorningCompleted.value = progress.morningCompleted
                _todayEveningCompleted.value = progress.eveningCompleted
                _todayMantraCount.value = progress.mantras
                _todayPracticeSeconds.value = progress.practiceSeconds
            }
        }
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

        dragHistory.add(SpinPhysics.DragSample(angle, timestamp))
        // Keep only last 5 events for velocity calculation
        while (dragHistory.size > SpinPhysics.DRAG_HISTORY_SIZE) {
            dragHistory.removeAt(0)
        }
    }

    /**
     * Checks rotation completion for the left wheel.
     */
    private fun checkLeftRotationCompletion(angularDiff: Float) {
        val totalRotation = _leftRotationAngle.value
        val crossed = SpinPhysics.revolutionsCrossed(totalRotation - angularDiff, totalRotation)
        if (crossed > 0) {
            _leftRotationCount.value += crossed
            onDualWheelRotationComplete(crossed)
        }
    }

    /**
     * Checks rotation completion for the right wheel.
     */
    private fun checkRightRotationCompletion(angularDiff: Float) {
        val totalRotation = _rightRotationAngle.value
        val crossed = SpinPhysics.revolutionsCrossed(totalRotation - angularDiff, totalRotation)
        if (crossed > 0) {
            _rightRotationCount.value += crossed
            onDualWheelRotationComplete(crossed)
        }
    }

    /**
     * Handles rotation completion for dual wheel mode.
     * Both wheels contribute to the same session mantras.
     */
    private fun onDualWheelRotationComplete(count: Long) {
        currentSessionRotations += count
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
        SpinPhysics.flickVelocity(dragHistory, _counterClockwiseEnabled.value)?.let { flick ->
            if (flick.showCounterClockwiseReminder) {
                // Show gentle reminder but still allow the motion
                showCounterClockwiseReminder()
            }
            _angularVelocity.value = flick.velocity
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
        // Cancel any deferred session save so it doesn't fire during pause.
        sessionStopJob?.cancel()
        sessionStopJob = null
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
     * Enables or disables breath/wind mode. Starts the microphone controller
     * and physics loop immediately on enable; stops the controller on disable.
     * If RECORD_AUDIO is not granted, the controller's start() is a graceful
     * no-op — the wheel simply does not respond to breath until permission is
     * granted and the toggle is re-flipped.
     */
    fun setBreathModeEnabled(enabled: Boolean) {
        viewModelScope.launch {
            _breathModeEnabled.value = enabled
            userPreferences.setBreathModeEnabled(enabled)
            if (enabled) {
                breathModeController?.start()
                startSessionIfNeeded()
                startPhysicsLoop()
            } else {
                breathModeController?.stop()
            }
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

            // Duration is the user-entered value (not endedAt - startedAt).
            val recorded = durableWrite("practice log") {
                ledgerDao.recordSession(session, durationSeconds)
            } ?: return@launch
            checkAchievements(recorded.stats.totalMantras)
        }
    }

    /**
     * Deletes a prayer wheel session and updates lifetime stats accordingly (without downgrading achievements).
     */
    fun deleteSession(session: Session) {
        viewModelScope.launch {
            durableWrite("deletion") { ledgerDao.deleteSessionAndDebit(session.id) }
        }
    }

    /**
     * Updates only the editable label on a session (T15). Persists via SessionDao.update.
     * Passing null clears the label ("None" in the UI). Lifetime stats are unaffected.
     */
    fun updateSessionLabel(session: Session, newLabel: String?) {
        viewModelScope.launch {
            // Targeted UPDATE: a whole-row write from this (possibly stale) copy
            // could revert counts of a row that was replaced in place.
            sessionDao.updateLabel(session.id, newLabel)
        }
    }

    /**
     * Restores a previously deleted session (undo). Re-inserts and re-adds to lifetime stats.
     */
    fun undoDeleteSession(session: Session) {
        viewModelScope.launch {
            durableWrite("restored session") { ledgerDao.restoreSession(session) }
        }
    }

    /**
     * Reset all lifetime accumulation counters to zero. Preserves the individual
     * session records in the sessions table (so history is still browsable).
     * Use this to start fresh after correcting a data-corruption bug.
     *
     * Runs as one [LedgerDao] transaction, serialized with every other
     * lifetime_stats writer (VM saves, SessionSaveWorker).
     */
    fun resetLifetimeStats() {
        viewModelScope.launch {
            durableWrite("lifetime reset") { ledgerDao.resetLifetime() } ?: return@launch
            // In-memory display reset so the wheel screen updates immediately.
            _lifetimeMantras.value = BigInteger.ZERO
            _highestMilestoneTier.value = 0
            // Trigger widget refresh (same pattern as the rotation-completion path).
            runCatching { PrayerWheelWidget().updateAll(appContext) }
        }
    }

    /**
     * "Reset All Data": discards the in-memory live session (and any pending
     * dedication), cancels a queued SessionSaveWorker, then deletes every
     * session, the lifetime row and all preferences.
     *
     * The live session is discarded synchronously, before the DB work, so a
     * session end / worker cannot re-insert it after the wipe. The DB work
     * runs in [appScope] — not the Settings composable's scope — so leaving
     * the screen mid-reset cannot leave a half-reset database.
     */
    fun resetAllData() {
        val pendingRecord = pendingDedicationSession?.recordJob
        discardLiveSession()
        SessionSaveWorker.cancel(appContext)
        appScope.launch {
            // An ended session still being recorded must not land after the wipe.
            pendingRecord?.join()
            try {
                checkpointMutex.withLock { ledgerDao.resetAll() }
                userPreferences.clearAllData()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("WheelViewModel", "Reset all data failed", e)
                _saveError.value = "Unable to reset data. Please try again."
                return@launch
            }
            _lifetimeMantras.value = BigInteger.ZERO
            _highestMilestoneTier.value = 0
            runCatching { PrayerWheelWidget().updateAll(appContext) }
        }
    }

    /** Drops the live session and any pending dedication without saving them. */
    private fun discardLiveSession() {
        sessionStopJob?.cancel()
        sessionStopJob = null
        stopPhysicsLoop()
        _angularVelocity.value = 0f
        pendingDedicationSession = null
        _showDedicationPrompt.value = false
        resetSessionState()
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
        // A session recovered from a checkpoint is the one to offer.
        recoveryJob?.join()
        val recent = sessionDao.getMostRecentSession() ?: return null
        val now = System.currentTimeMillis()
        return if (SessionRules.isResumable(recent, now, SESSION_NEW_THRESHOLD_MS)) recent else null
    }

    /**
     * T17: Resumes practice from a previously-interrupted session.
     *
     * Restores the in-memory counters and `startedAt` so the timer keeps counting
     * from the original start, and adopts the saved row's session key. The row
     * and its lifetime contribution are KEPT: the next save of this session
     * (End Session, onCleared/SessionSaveWorker, or checkpoint recovery) upserts
     * by key, replacing the row in place — debit old, credit new, update row —
     * in one transaction. (Previously the row was deleted and debited up
     * front, so a process death after resuming lost a persisted session.)
     *
     * A live session with rotations is saved first instead of being
     * overwritten by the restored state.
     *
     * Does NOT restore transient physics state (angularVelocity, drag history).
     */
    fun resumeFromSession(session: Session) {
        if (sessionStartTime != null && currentSessionRotations > 0L) {
            recordLiveSessionWithoutPrompt()
        }
        viewModelScope.launch {
            val key = if (session.id != 0L) {
                durableWrite("resumed session") {
                    ledgerDao.ensureSessionKey(session.id, java.util.UUID.randomUUID().toString())
                }
            } else null
            // Row gone (deleted meanwhile) or the write failed: continue under a
            // key of its own; the save will then insert it as a new session.
            sessionKey = key ?: session.sessionKey ?: java.util.UUID.randomUUID().toString()

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
            rpmSampleCount = 0
            sessionStartRpmSum = 0f
            // Continue from the recorded practice time, not from startedAt: the
            // gap between the original end and now was not practice.
            val now = System.currentTimeMillis()
            val timerBase = SessionRules.resumeTimerBase(now, LifetimeStatsMath.storedDurationSeconds(session))
            sessionTimerBase = timerBase
            _sessionDuration.value = now - timerBase
            _sessionDurationSeconds.value = (now - timerBase) / 1000
            pendingSessionLabel = session.label

            startNotificationService()

            // Restart the session timer (mirrors startSessionIfNeeded). The
            // timeGoalReached flag is pre-set when the resumed session already
            // meets the goal so we don't immediately fire the milestone haptic.
            startSessionTimer(timerBase, skipGoalIfAlreadyMet = true)
            startCheckpointing()
            checkpointNow()
        }
    }

    /**
     * Saves the live session as-is (no dedication prompt) and clears it.
     * Used when something else must take over the live-session slot.
     */
    private fun recordLiveSessionWithoutPrompt() {
        val session = createSessionFromCurrentState() ?: return
        val durationSeconds = _sessionDurationSeconds.value
        sessionKey = null
        resetSessionState()
        viewModelScope.launch { recordCompletedSession(session, durationSeconds) }
    }

    /**
     * Applies the dedication to the already-recorded session and dismisses
     * the prompt. The session itself was recorded when it ended.
     */
    fun confirmDedication(dedication: String?) {
        val pending = pendingDedicationSession
        dismissDedicationPrompt()
        viewModelScope.launch {
            // Save custom dedication if provided
            if (dedication != null && dedication.isNotBlank()) {
                userPreferences.setCustomDedication(dedication)
            }
            val key = pending?.session?.sessionKey
            if (dedication != null && key != null) {
                pending.recordJob.join()
                durableWrite("dedication") { sessionDao.updateDedicationByKey(key, dedication) }
            }
        }
    }

    /**
     * Skips the dedication prompt. The session was already recorded (with no
     * dedication) when it ended.
     */
    fun skipDedication() {
        dismissDedicationPrompt()
    }

    private fun dismissDedicationPrompt() {
        pendingDedicationSession = null
        _showDedicationPrompt.value = false
        // Clear the ended session's display — unless a new session has already
        // started while the prompt was open (spinning again), which must be kept.
        if (sessionStartTime == null) resetSessionState()
    }

    /**
     * Creates a Session from the current live state, ending at [endedAt]
     * (session end, onCleared, or when superseded by a resume).
     */
    private fun createSessionFromCurrentState(endedAt: Long = System.currentTimeMillis()): Session? {
        val startTime = sessionStartTime ?: return null
        if (currentSessionRotations == 0L) return null

        val avgRpm = if (rpmSampleCount > 0) sessionStartRpmSum / rpmSampleCount else 0f

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
            label = pendingSessionLabel,
            sessionKey = sessionKey
        )
    }

    /**
     * Inserts [session] and credits it to lifetime stats in ONE transaction.
     * SQLiteFullException (disk full) is surfaced via [_saveError]; on failure we
     * bail before achievements/milestone updates so a write failure does not
     * produce a misleading tier bump based on stale in-memory state.
     */
    private suspend fun recordCompletedSession(session: Session, durationSeconds: Long) {
        // Duration is the live session timer, not endedAt - startedAt. Upserts by
        // session key and clears the session's checkpoint in the same transaction.
        val recorded = durableWrite("session") {
            checkpointMutex.withLock { ledgerDao.recordSession(session, durationSeconds) }
        } ?: return
        checkAchievements(recorded.stats.totalMantras)
        updateHighestMilestoneTier(recorded.stats.totalMantras)
    }

    private fun startSessionIfNeeded() {
        val now = System.currentTimeMillis()
        if (SessionRules.needsNewSession(sessionStartTime)) {
            sessionStartTime = now
            sessionTimerBase = now
            sessionKey = java.util.UUID.randomUUID().toString()
            currentSessionRotations = 0L
            currentSessionMantras = BigInteger.ZERO
            _hasActiveSession.value = true
            _sessionDuration.value = 0L
            _peakRpm.value = 0f
            _averageRpm.value = 0f
            _sessionDurationSeconds.value = 0L
            _isPaused.value = false
            // The previous session's counters may still be displayed (dedication
            // prompt still open); this is a new session.
            _rotationCount.value = 0L
            _sessionMantras.value = BigInteger.ZERO
            rpmSampleCount = 0
            sessionStartRpmSum = 0f

            // T15: auto-suggest morning/evening label. Attribution matches the rule in
            // observeTodayProgress() / TodayProgressCard — hour is strictly less than
            // reminderEveningHour for "morning", otherwise "evening". Null when disabled.
            pendingSessionLabel = SessionRules.autoLabel(
                now, cachedReminderEveningHour, ZoneId.systemDefault(), autoLabelSessionsEnabled
            )

            // Start notification service
            startNotificationService()

            // Start session timer
            startSessionTimer(now, skipGoalIfAlreadyMet = false)
            startCheckpointing()
        }
    }

    /**
     * (Re)starts the 1 Hz session timer measuring elapsed time from [startTime].
     * Fires the time-goal milestone haptic once when the goal is first reached
     * and refreshes the foreground notification each tick.
     *
     * @param skipGoalIfAlreadyMet when true (resume path), the goal haptic is
     *   suppressed if the restored duration already meets the goal at timer start.
     */
    private fun startSessionTimer(startTime: Long, skipGoalIfAlreadyMet: Boolean) {
        sessionTimerJob?.cancel()
        sessionTimerJob = viewModelScope.launch {
            var timeGoalReached = skipGoalIfAlreadyMet &&
                _sessionTimeGoalSeconds.value > 0 &&
                _sessionDurationSeconds.value >= _sessionTimeGoalSeconds.value
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

    private fun startPhysicsLoop() {
        physicsJob?.cancel()
        lastFrameTime = System.currentTimeMillis()
        physicsJob = viewModelScope.launch {
            // Loop stays alive while there is motion, particles to animate, auto-spin
            // is driving (so the torque-limited ramp can accelerate from rest), OR
            // breath mode is actively receiving non-trivial microphone amplitude.
            while (isActive && (
                    abs(_angularVelocity.value) > STOP_THRESHOLD ||
                        _starParticles.value.isNotEmpty() ||
                        _autoSpinEnabled.value ||
                        (_breathModeEnabled.value &&
                            (breathModeController?.amplitude?.value ?: 0f) > BREATH_ACTIVE_THRESHOLD)
                )) {
                val currentTime = System.currentTimeMillis()
                val deltaTime = (currentTime - lastFrameTime).coerceAtLeast(1L) / 1000f
                lastFrameTime = currentTime

                // Determine friction coefficient based on mode and state
                val isTwoHandedMode = SpinPhysics.isTwoHandedMode(
                    _spinMode.value, _twoHandedEnabled.value, _autoSpinEnabled.value
                )

                // Breath mode: microphone amplitude applies clockwise torque (0 when off).
                val breathAmp = if (_breathModeEnabled.value)
                    breathModeController?.amplitude?.value ?: 0f else 0f

                // Calculate velocity - in auto mode we ramp toward target, otherwise apply friction
                val calculatedVelocity = SpinPhysics.stepVelocity(
                    currentOmega = _angularVelocity.value,
                    deltaTime = deltaTime,
                    autoSpinEnabled = _autoSpinEnabled.value,
                    isTwoHandedMode = isTwoHandedMode,
                    twoHandedEngaged = _twoHandedEngaged.value,
                    autoSpinRpm = _autoSpinRpm.value,
                    baseFriction = _baseFriction.value,
                    weightAngleDegrees = _weightAngle.value,
                    breathAmplitude = breathAmp
                )

                _angularVelocity.value = SpinPhysics.snapToStop(calculatedVelocity)

                // Update rotation angle
                val deltaAngle = calculatedVelocity * deltaTime
                val oldAngle = _rotationAngle.value
                val newAngle = oldAngle + deltaAngle
                _rotationAngle.value = newAngle

                // Count rotations when crossing 2π boundary
                val crossed = SpinPhysics.revolutionsCrossed(oldAngle, newAngle)
                if (crossed > 0) {
                    onRotationComplete(crossed)
                }

                // Update RPM tracking: RPM = |ω| * 60 / (2π)
                val currentRpm = SpinPhysics.rpm(_angularVelocity.value)
                _currentRpm.value = currentRpm
                audioEngine?.updateDrone(currentRpm)

                // Calculate weight angle based on centrifugal force
                // (atan(rpm / 30) clamped to 0..80 degrees), smoothly interpolated.
                _weightAngle.value = SpinPhysics.stepWeightAngle(_weightAngle.value, currentRpm)

                // Track peak RPM
                if (currentRpm > _peakRpm.value) {
                    _peakRpm.value = currentRpm
                }

                // Track samples for average RPM calculation
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
                    val spawnProbability = SpinPhysics.particleSpawnProbability(velocity)
                    // Scale spawn by background animation intensity (0=off, 1=subtle, 2=full).
                    // Off => 0 spawns; subtle => 0.15x; full => 1.0x.
                    val intensityScale = _bgAnimationIntensity.value / 2f
                    if (intensityScale > 0f &&
                        Math.random() < spawnProbability * intensityScale &&
                        currentParticles.size < 40) {
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
        // The session's end is when the wheel stopped, not when the 30 s grace
        // period below expired.
        val stoppedAt = System.currentTimeMillis()
        sessionStopJob?.cancel()
        sessionStopJob = viewModelScope.launch {
            delay(SESSION_STOP_DELAY_MS)
            if (abs(_angularVelocity.value) < STOP_THRESHOLD) {
                saveSessionWithDedication(endedAt = stoppedAt)
            }
        }
    }

    private var lastWidgetUpdateMs = 0L

    private fun onRotationComplete(count: Long) {
        currentSessionRotations += count

        viewModelScope.launch {
            // In-memory lifetime mantras only — provides the live display while spinning.
            // The lifetime_stats DB row is written EXACTLY ONCE per session at session end
            // via recordCompletedSession(). Writing it here on every rotation caused every
            // session's mantras to be double-counted: once incrementally per-rotation here,
            // then again at session save, inflating lifetime totals by N×.
            val mantrasThisRotation = _mantrasPerRotation.value.toBigInteger() * count.toBigInteger()
            val previousTotalMantras = _lifetimeMantras.value
            val newTotalMantras = previousTotalMantras + mantrasThisRotation
            _lifetimeMantras.value = newTotalMantras
            updateHighestMilestoneTier(newTotalMantras)

            // Singing bowl at lifetime-mantra milestones (1K → 1T, tiers 0-7).
            // Use indexOfLast so a single high-capacity rotation that crosses
            // several thresholds plays the brightest tier reached, not the first.
            val tierCrossed = Milestones.highestTierCrossed(previousTotalMantras, newTotalMantras)
            if (tierCrossed >= 0) {
                audioEngine?.playBowlTone(tierCrossed)
            }

            // Check achievements (reads the in-memory mantras value; does not touch lifetime_stats DB).
            checkAchievements(newTotalMantras)
        }

        // Spawn rotation completion particles (gated by bg animation intensity)
        if (canvasWidth > 0 && canvasHeight > 0 && _bgAnimationIntensity.value > 0) {
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

    /**
     * Ends the live session at [endedAt]: snapshots it (end time and duration),
     * stops the session timer, notification and checkpointing, records it
     * right away, and shows the dedication prompt.
     *
     * The duration is `endedAt − startedAt`, fixed now — previously the
     * prompt's confirm time was used as endedAt and the still-running live
     * timer as the lifetime duration, so a prompt left open overnight
     * recorded ~8 h of practice. The displayed counters are kept for the
     * prompt; `sessionStartTime` is cleared so a spin while the prompt is
     * open starts a new session instead of being silently dropped.
     */
    private fun saveSessionWithDedication(endedAt: Long = System.currentTimeMillis()) {
        val startTime = sessionStartTime ?: return
        if (currentSessionRotations == 0L) {
            resetSessionState()
            return
        }
        val end = endedAt.coerceAtLeast(startTime)
        val session = createSessionFromCurrentState(end) ?: return
        val timerBase = sessionTimerBase ?: startTime
        val durationSeconds = SessionRules.endedSessionDurationSeconds(timerBase, end)

        // Hand the session off: no further timer ticks, checkpoints or
        // notifications for it, and no onCleared/worker save (it's recorded below).
        sessionKey = null
        sessionStartTime = null
        sessionTimerBase = null
        sessionTimerJob?.cancel()
        sessionTimerJob = null
        stopCheckpointing()
        stopNotificationService()
        sessionStopJob?.cancel()
        sessionStopJob = null
        _sessionDuration.value = (end - timerBase).coerceAtLeast(0L)
        _sessionDurationSeconds.value = durationSeconds

        val recordJob = viewModelScope.launch { recordCompletedSession(session, durationSeconds) }
        pendingDedicationSession = PendingSession(session, durationSeconds, recordJob)

        _hasActiveSession.value = false
        _showDedicationPrompt.value = true
        stopPhysicsLoop()
        _angularVelocity.value = 0f
    }

    private fun resetSessionState() {
        sessionStartTime = null
        sessionTimerBase = null
        currentSessionRotations = 0L
        currentSessionMantras = BigInteger.ZERO
        sessionKey = null
        stopCheckpointing()
        _rotationCount.value = 0L
        _sessionMantras.value = BigInteger.ZERO
        _hasActiveSession.value = false
        _sessionDuration.value = 0L
        _currentRpm.value = 0f
        _peakRpm.value = 0f
        _averageRpm.value = 0f
        _sessionDurationSeconds.value = 0L
        _isPaused.value = false
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
                val total = stats?.totalMantras ?: BigInteger.ZERO
                _lifetimeMantras.value = total
                updateHighestMilestoneTier(total)
            }
        }
    }

    /**
     * Recomputes [_highestMilestoneTier] from a lifetime-mantra total via
     * [Milestones.tierFor]. Idempotent.
     */
    private fun updateHighestMilestoneTier(lifetimeMantras: BigInteger) {
        _highestMilestoneTier.value = Milestones.tierFor(lifetimeMantras)
    }

    private suspend fun checkAchievements(totalMantras: BigInteger) {
        val currentlyUnlocked = _unlockedAchievements.value
        // currentlyUnlocked is a snapshot, so filtering up front is equivalent to the
        // previous per-iteration check.
        for (achievement in Milestones.newlyUnlocked(totalMantras, currentlyUnlocked)) {
            userPreferences.unlockAchievement(achievement.id)
            val tierIndex = Milestones.thresholdIndexOf(achievement.mantrasRequired)
            if (tierIndex < 0) {
                Log.w(
                    "WheelViewModel",
                    "Unlocked achievement ${achievement.id} (mantrasRequired=${
                        achievement.mantrasRequired}) has no matching audio milestone threshold"
                )
            }
            val tier = tierIndex.coerceIn(0, 7)
            triggerMilestoneHaptic(tier)
            _newlyUnlockedAchievement.value = achievement
        }
    }

    fun dismissAchievementCelebration() {
        _newlyUnlockedAchievement.value = null
    }

    private fun calculateAngularChange(newAngle: Float): Float {
        if (dragHistory.isEmpty()) return 0f
        val lastAngle = dragHistory.last().angle
        // Normalize to [-PI, PI]
        return SpinPhysics.normalizeAngle(newAngle - lastAngle)
    }

    private fun checkRotationCompletion(angularDiff: Float) {
        // Track cumulative rotation for counting
        val totalRotation = _rotationAngle.value
        val crossed = SpinPhysics.revolutionsCrossed(totalRotation - angularDiff, totalRotation)
        if (crossed > 0) {
            _rotationCount.value += crossed
            onRotationComplete(crossed)
        }
    }

    private fun triggerHapticTick() {
        if (!shouldVibrate()) return
        val rpm = _currentRpm.value
        // Tick duration/amplitude scale inversely with RPM (see HapticPatterns).
        triggerScaledHaptic(HapticPatterns.tickDurationMs(rpm), HapticPatterns.tickAmplitudeScale(rpm))
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

    fun triggerMilestoneHaptic(tier: Int = 0) {
        if (!shouldVibrate()) return
        triggerMilestoneHapticInternal(tier.coerceIn(0, 7))
    }

    /**
     * Plays the milestone haptic for [tier] (0–7, matching
     * [Milestones.MILESTONE_THRESHOLDS]); the pattern choice lives in
     * [HapticPatterns.milestonePattern].
     */
    private fun triggerMilestoneHapticInternal(tier: Int) {
        val intensity = _vibrationIntensity.value
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect: VibrationEffect = when (val pattern = HapticPatterns.milestonePattern(tier, intensity)) {
                is HapticPatterns.Pattern.OneShot ->
                    VibrationEffect.createOneShot(pattern.durationMs, pattern.amplitude)
                is HapticPatterns.Pattern.Waveform ->
                    VibrationEffect.createWaveform(pattern.timings, pattern.amplitudes, -1)
            }
            vibrator.vibrate(effect)
        } else {
            val durationMs = HapticPatterns.legacyMilestoneDurationMs(tier)
            @Suppress("DEPRECATION")
            vibrator.vibrate(durationMs)
        }
    }

    private fun triggerScaledHaptic(durationMs: Long, amplitudeScale: Float) {
        val intensity = _vibrationIntensity.value
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val amplitude = HapticPatterns.scaledTickAmplitude(amplitudeScale, intensity)
            vibrator.vibrate(VibrationEffect.createOneShot(durationMs, amplitude))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(durationMs)
        }
    }

    private fun shouldVibrate(): Boolean {
        if (!_hapticEnabled.value) return false
        if (_isAppInForeground.value) return true
        return _backgroundVibrationEnabled.value
    }

    fun setAppInForeground(inForeground: Boolean) {
        _isAppInForeground.value = inForeground
        if (inForeground) {
            // Breath mode still on: resume listening (idempotent).
            if (_breathModeEnabled.value) breathModeController?.start()
        } else {
            // Backgrounded apps are the ones the OS kills: persist the live
            // session right away instead of waiting for the next periodic tick.
            checkpointNow()
            // No background microphone: API 34+ forbids it without a
            // microphone-type foreground service, and it is a privacy issue.
            breathModeController?.stop()
        }
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

    fun setShowLiveCounter(enabled: Boolean) {
        _showLiveCounter.value = enabled
        viewModelScope.launch {
            userPreferences.setShowCounter(enabled)
        }
    }

    fun setKeepScreenOn(enabled: Boolean) {
        _keepScreenOn.value = enabled
        viewModelScope.launch {
            userPreferences.setKeepScreenOn(enabled)
        }
    }

    fun setBgAnimationIntensity(intensity: Int) {
        val coerced = intensity.coerceIn(0, 2)
        _bgAnimationIntensity.value = coerced
        viewModelScope.launch {
            userPreferences.setBgAnimationIntensity(coerced)
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
            val amplitude = HapticPatterns.scaledAmplitude(VibrationEffect.DEFAULT_AMPLITUDE, intensity)
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
        //
        // The checkpoint for this session stays until the worker (or the next
        // launch's recovery) records it; both upsert by sessionKey, so whichever
        // runs second is a no-op or a newer-snapshot replace — never a double count.
        val snapshot = createSessionFromCurrentState()
        sessionKey = null // stop any in-flight checkpoint write for this session
        if (snapshot != null) {
            SessionSaveWorker.enqueue(
                context = appContext,
                sessionKey = snapshot.sessionKey,
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
        audioEngine?.stopDroneLoop()
        audioEngine?.release()
        breathModeController?.stop()
    }

    /**
     * Factory for creating WheelViewModel with dependencies.
     */
    class Factory(
        private val lifetimeStatsDao: LifetimeStatsDao,
        private val sessionDao: SessionDao,
        private val ledgerDao: LedgerDao,
        private val appScope: CoroutineScope,
        private val userPreferences: UserPreferences,
        private val vibrator: Vibrator,
        private val appContext: Context
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return WheelViewModel(
                lifetimeStatsDao, sessionDao, ledgerDao, appScope, userPreferences, vibrator, appContext
            ) as T
        }
    }
}
