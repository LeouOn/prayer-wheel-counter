package com.prayerwheel.app.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Theme options for the app.
 */
enum class AppTheme {
    LIGHT,
    DARK,
    SEPIA,
    DAWN_DUSK,
    SYSTEM
}

/**
 * Spin modes for the prayer wheel.
 */
enum class SpinMode {
    MANUAL,        // drag-to-spin (current behavior)
    TWO_HANDED,    // two simultaneous touches required
    AUTO_SPIN      // continuous at configurable RPM
}

/**
 * User preferences stored via DataStore.
 *
 * Holds settings that control the prayer wheel behavior and appearance:
 * - Mantra selection and capacity
 * - Spin mode and friction
 * - Theme preference
 * - Haptic feedback toggle
 */
class UserPreferences(private val dataStore: DataStore<Preferences>) {

    private object Keys {
        val SELECTED_MANTRA = stringPreferencesKey("selected_mantra")
        val MANTRAS_PER_ROTATION = longPreferencesKey("mantras_per_rotation")
        val SPIN_MODE = stringPreferencesKey("spin_mode")
        val AUTO_SPIN_RPM = intPreferencesKey("auto_spin_rpm")
        val THEME = stringPreferencesKey("theme")
        val FRICTION = floatPreferencesKey("friction")
        val HAPTIC_ENABLED = booleanPreferencesKey("haptic_enabled")
        val CUSTOM_DEDICATION = stringPreferencesKey("custom_dedication")
        val HAS_COMPLETED_ONBOARDING = booleanPreferencesKey("has_completed_onboarding")
        val CLOCKWISE_DIRECTION = booleanPreferencesKey("clockwise_direction")
        val RECITATION_ENABLED = booleanPreferencesKey("recitation_enabled")
        val RECITATION_VOLUME = floatPreferencesKey("recitation_volume")
        val BELL_AT_MILESTONES = booleanPreferencesKey("bell_at_milestones")
        val AMBIENT_ENABLED = booleanPreferencesKey("ambient_enabled")
        val AMBIENT_VOLUME = floatPreferencesKey("ambient_volume")
        val MASTER_VOLUME = floatPreferencesKey("master_volume")
        val SHOW_COUNTER = booleanPreferencesKey("show_counter")
        val MILESTONE_NOTIFICATIONS = booleanPreferencesKey("milestone_notifications")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val COUNTER_CLOCKWISE_ENABLED = booleanPreferencesKey("counter_clockwise_enabled")
        val DAILY_MANTRA_GOAL = longPreferencesKey("daily_mantra_goal")
        val SESSION_MANTRA_GOAL = longPreferencesKey("session_mantra_goal")
        val CURRENT_INTENTION = stringPreferencesKey("current_intention")
        val SELECTED_SKIN = stringPreferencesKey("selected_skin")
    }

    val selectedMantra: Flow<String> = dataStore.data.map { preferences ->
        preferences[Keys.SELECTED_MANTRA] ?: DEFAULT_MANTRA
    }

    val mantrasPerRotation: Flow<Long> = dataStore.data.map { preferences ->
        preferences[Keys.MANTRAS_PER_ROTATION] ?: DEFAULT_MANTRAS_PER_ROTATION.toLong()
    }

    val spinMode: Flow<SpinMode> = dataStore.data.map { preferences ->
        val modeString = preferences[Keys.SPIN_MODE] ?: DEFAULT_SPIN_MODE.name
        try {
            SpinMode.valueOf(modeString)
        } catch (e: IllegalArgumentException) {
            SpinMode.MANUAL
        }
    }

    val autoSpinRpm: Flow<Int> = dataStore.data.map { preferences ->
        preferences[Keys.AUTO_SPIN_RPM] ?: DEFAULT_AUTO_SPIN_RPM
    }

    val theme: Flow<String> = dataStore.data.map { preferences ->
        preferences[Keys.THEME] ?: DEFAULT_THEME
    }

    val friction: Flow<Float> = dataStore.data.map { preferences ->
        preferences[Keys.FRICTION] ?: DEFAULT_FRICTION
    }

    val hapticEnabled: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[Keys.HAPTIC_ENABLED] ?: DEFAULT_HAPTIC_ENABLED
    }

    val customDedication: Flow<String?> = dataStore.data.map { preferences ->
        preferences[Keys.CUSTOM_DEDICATION]
    }

    val hasCompletedOnboarding: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[Keys.HAS_COMPLETED_ONBOARDING] ?: false
    }

    val clockwiseDirection: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[Keys.CLOCKWISE_DIRECTION] ?: true
    }

    val recitationEnabled: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[Keys.RECITATION_ENABLED] ?: false
    }

    val recitationVolume: Flow<Float> = dataStore.data.map { preferences ->
        preferences[Keys.RECITATION_VOLUME] ?: 0.5f
    }

    val bellAtMilestones: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[Keys.BELL_AT_MILESTONES] ?: false
    }

    val ambientEnabled: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[Keys.AMBIENT_ENABLED] ?: false
    }

    val ambientVolume: Flow<Float> = dataStore.data.map { preferences ->
        preferences[Keys.AMBIENT_VOLUME] ?: 0.5f
    }

    val masterVolume: Flow<Float> = dataStore.data.map { preferences ->
        preferences[Keys.MASTER_VOLUME] ?: 1.0f
    }

    val showCounter: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[Keys.SHOW_COUNTER] ?: true
    }

    val milestoneNotifications: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[Keys.MILESTONE_NOTIFICATIONS] ?: true
    }

    val keepScreenOn: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[Keys.KEEP_SCREEN_ON] ?: false
    }

    val counterClockwiseEnabled: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[Keys.COUNTER_CLOCKWISE_ENABLED] ?: false
    }

    val dailyMantraGoal: Flow<Long> = dataStore.data.map { preferences ->
        preferences[Keys.DAILY_MANTRA_GOAL] ?: 0L
    }

    val sessionMantraGoal: Flow<Long> = dataStore.data.map { preferences ->
        preferences[Keys.SESSION_MANTRA_GOAL] ?: 0L
    }

    val currentIntention: Flow<String> = dataStore.data.map { preferences ->
        preferences[Keys.CURRENT_INTENTION] ?: ""
    }

    val selectedSkin: Flow<String> = dataStore.data.map { preferences ->
        preferences[Keys.SELECTED_SKIN] ?: "traditional_gold"
    }

    suspend fun setSelectedMantra(mantra: String) {
        dataStore.edit { preferences ->
            preferences[Keys.SELECTED_MANTRA] = mantra
        }
    }

    suspend fun setMantrasPerRotation(count: Long) {
        dataStore.edit { preferences ->
            preferences[Keys.MANTRAS_PER_ROTATION] = count
        }
    }

    suspend fun setSpinMode(mode: SpinMode) {
        dataStore.edit { preferences ->
            preferences[Keys.SPIN_MODE] = mode.name
        }
    }

    suspend fun setAutoSpinRpm(rpm: Int) {
        dataStore.edit { preferences ->
            preferences[Keys.AUTO_SPIN_RPM] = rpm.coerceIn(1, 120)
        }
    }

    suspend fun setTheme(theme: String) {
        dataStore.edit { preferences ->
            preferences[Keys.THEME] = theme
        }
    }

    suspend fun setFriction(friction: Float) {
        dataStore.edit { preferences ->
            preferences[Keys.FRICTION] = friction
        }
    }

    suspend fun setHapticEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.HAPTIC_ENABLED] = enabled
        }
    }

    suspend fun setCustomDedication(dedication: String?) {
        dataStore.edit { preferences ->
            if (dedication != null) {
                preferences[Keys.CUSTOM_DEDICATION] = dedication
            } else {
                preferences.remove(Keys.CUSTOM_DEDICATION)
            }
        }
    }

    suspend fun setHasCompletedOnboarding(completed: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.HAS_COMPLETED_ONBOARDING] = completed
        }
    }

    suspend fun setClockwiseDirection(clockwise: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.CLOCKWISE_DIRECTION] = clockwise
        }
    }

    suspend fun setRecitationEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.RECITATION_ENABLED] = enabled
        }
    }

    suspend fun setRecitationVolume(volume: Float) {
        dataStore.edit { preferences ->
            preferences[Keys.RECITATION_VOLUME] = volume.coerceIn(0f, 1f)
        }
    }

    suspend fun setBellAtMilestones(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.BELL_AT_MILESTONES] = enabled
        }
    }

    suspend fun setAmbientEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.AMBIENT_ENABLED] = enabled
        }
    }

    suspend fun setAmbientVolume(volume: Float) {
        dataStore.edit { preferences ->
            preferences[Keys.AMBIENT_VOLUME] = volume.coerceIn(0f, 1f)
        }
    }

    suspend fun setMasterVolume(volume: Float) {
        dataStore.edit { preferences ->
            preferences[Keys.MASTER_VOLUME] = volume.coerceIn(0f, 1f)
        }
    }

    suspend fun setShowCounter(show: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.SHOW_COUNTER] = show
        }
    }

    suspend fun setMilestoneNotifications(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.MILESTONE_NOTIFICATIONS] = enabled
        }
    }

    suspend fun setKeepScreenOn(keepOn: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.KEEP_SCREEN_ON] = keepOn
        }
    }

    suspend fun setCounterClockwiseEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.COUNTER_CLOCKWISE_ENABLED] = enabled
        }
    }

    suspend fun setDailyMantraGoal(goal: Long) {
        dataStore.edit { preferences ->
            preferences[Keys.DAILY_MANTRA_GOAL] = goal.coerceAtLeast(0L)
        }
    }

    suspend fun setSessionMantraGoal(goal: Long) {
        dataStore.edit { preferences ->
            preferences[Keys.SESSION_MANTRA_GOAL] = goal.coerceAtLeast(0L)
        }
    }

    suspend fun setCurrentIntention(intention: String) {
        dataStore.edit { preferences ->
            preferences[Keys.CURRENT_INTENTION] = intention
        }
    }

    suspend fun setSelectedSkin(skinId: String) {
        dataStore.edit { preferences ->
            preferences[Keys.SELECTED_SKIN] = skinId
        }
    }

    suspend fun clearAllData() {
        dataStore.edit { preferences ->
            preferences.clear()
        }
    }

    companion object {
        const val DEFAULT_MANTRA = "om_mani_padme_hum"
        const val DEFAULT_MANTRAS_PER_ROTATION = 1
        val DEFAULT_SPIN_MODE = SpinMode.MANUAL
        const val DEFAULT_AUTO_SPIN_RPM = 30
        const val DEFAULT_THEME = "system"
        const val DEFAULT_FRICTION = 0.97f
        const val DEFAULT_HAPTIC_ENABLED = true
        
        const val DEFAULT_DEDICATION_TEXT = "May the merit accumulated through this practice benefit all sentient beings, that they may realize the nature of Buddha's wisdom and be freed from samsara."
    }
}
