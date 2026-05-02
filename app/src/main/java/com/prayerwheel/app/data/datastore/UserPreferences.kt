package com.prayerwheel.app.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.prayerwheel.app.data.model.SavedWheel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

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
    AUTO_SPIN,     // continuous at configurable RPM
    TWO_HANDED_AUTO // auto-spin combined with two-handed friction
}

/**
 * View modes for the prayer wheel display.
 */
enum class ViewMode {
    SIDE_VIEW,     // side view with stem, cylinder, caps, crystal
    TOP_DOWN,      // top-down circular view with mantra around rim
    ABSTRACT,      // minimalist mandala/sacred geometry representation
    TABLE_TOP,     // table top view with a wider base
    GLOBE          // spherical wheel rotating on an axis
}

/**
 * Number formatting styles for the mantra counter.
 */
enum class NumberFormatStyle {
    STANDARD,      // 1.2M, 5B
    LONG_FORM,     // 1 Million 200 Thousand
    SCIENTIFIC,    // 1.2 x 10^6
    EXACT          // 1,200,000
}

/**
 * Number notation style for displaying large numbers.
 */
enum class NumberNotation {
    STANDARD,      // K, M, B, T, Qa, Qi, etc.
    EXTENDED       // aa, ab, ac, ... az, ba, bb, etc.
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
        val DAILY_TIME_GOAL_SECONDS = longPreferencesKey("daily_time_goal_seconds")
        val SESSION_TIME_GOAL_SECONDS = longPreferencesKey("session_time_goal_seconds")
        val CURRENT_INTENTION = stringPreferencesKey("current_intention")
        val SELECTED_SKIN = stringPreferencesKey("selected_skin")
        val VIEW_MODE = stringPreferencesKey("view_mode")
        val NUMBER_FORMAT_STYLE = stringPreferencesKey("number_format_style")
        val SAVED_WHEELS = stringPreferencesKey("saved_wheels")
        val AUTO_SPIN_ENABLED = booleanPreferencesKey("auto_spin_enabled")
        val TWO_HANDED_ENABLED = booleanPreferencesKey("two_handed_enabled")
        val NUMBER_NOTATION = stringPreferencesKey("number_notation")
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

    val dailyTimeGoalSeconds: Flow<Long> = dataStore.data.map { preferences ->
        preferences[Keys.DAILY_TIME_GOAL_SECONDS] ?: 0L
    }

    val sessionTimeGoalSeconds: Flow<Long> = dataStore.data.map { preferences ->
        preferences[Keys.SESSION_TIME_GOAL_SECONDS] ?: 0L
    }

    val currentIntention: Flow<String> = dataStore.data.map { preferences ->
        preferences[Keys.CURRENT_INTENTION] ?: ""
    }

    val selectedSkin: Flow<String> = dataStore.data.map { preferences ->
        preferences[Keys.SELECTED_SKIN] ?: "traditional_gold"
    }

    val viewMode: Flow<ViewMode> = dataStore.data.map { preferences ->
        val modeString = preferences[Keys.VIEW_MODE] ?: ViewMode.SIDE_VIEW.name
        try {
            ViewMode.valueOf(modeString)
        } catch (e: IllegalArgumentException) {
            ViewMode.SIDE_VIEW
        }
    }

    val numberFormatStyle: Flow<NumberFormatStyle> = dataStore.data.map { preferences ->
        val styleString = preferences[Keys.NUMBER_FORMAT_STYLE] ?: NumberFormatStyle.STANDARD.name
        try {
            NumberFormatStyle.valueOf(styleString)
        } catch (e: IllegalArgumentException) {
            NumberFormatStyle.STANDARD
        }
    }

    val savedWheels: Flow<List<SavedWheel>> = dataStore.data.map { preferences ->
        val jsonString = preferences[Keys.SAVED_WHEELS] ?: "[]"
        val parsed = parseSavedWheelsJson(jsonString)
        if (parsed.isEmpty()) {
            DEFAULT_SAVED_WHEELS
        } else {
            parsed
        }
    }

    val autoSpinEnabled: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[Keys.AUTO_SPIN_ENABLED] ?: false
    }

    val twoHandedEnabled: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[Keys.TWO_HANDED_ENABLED] ?: false
    }

    val numberNotation: Flow<NumberNotation> = dataStore.data.map { preferences ->
        val notationString = preferences[Keys.NUMBER_NOTATION] ?: NumberNotation.STANDARD.name
        try {
            NumberNotation.valueOf(notationString)
        } catch (e: IllegalArgumentException) {
            NumberNotation.STANDARD
        }
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

    suspend fun setDailyTimeGoalSeconds(goal: Long) {
        dataStore.edit { preferences ->
            preferences[Keys.DAILY_TIME_GOAL_SECONDS] = goal.coerceAtLeast(0L)
        }
    }

    suspend fun setSessionTimeGoalSeconds(goal: Long) {
        dataStore.edit { preferences ->
            preferences[Keys.SESSION_TIME_GOAL_SECONDS] = goal.coerceAtLeast(0L)
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

    suspend fun setViewMode(mode: ViewMode) {
        dataStore.edit { preferences ->
            preferences[Keys.VIEW_MODE] = mode.name
        }
    }

    suspend fun setNumberFormatStyle(style: NumberFormatStyle) {
        dataStore.edit { preferences ->
            preferences[Keys.NUMBER_FORMAT_STYLE] = style.name
        }
    }

    suspend fun setSavedWheels(wheels: List<SavedWheel>) {
        dataStore.edit { preferences ->
            preferences[Keys.SAVED_WHEELS] = savedWheelsToJson(wheels)
        }
    }

    suspend fun addSavedWheel(wheel: SavedWheel) {
        dataStore.edit { preferences ->
            val currentJson = preferences[Keys.SAVED_WHEELS] ?: "[]"
            val currentWheels = parseSavedWheelsJson(currentJson).toMutableList()
            currentWheels.add(wheel)
            preferences[Keys.SAVED_WHEELS] = savedWheelsToJson(currentWheels)
        }
    }

    suspend fun updateSavedWheel(wheel: SavedWheel) {
        dataStore.edit { preferences ->
            val currentJson = preferences[Keys.SAVED_WHEELS] ?: "[]"
            val currentWheels = parseSavedWheelsJson(currentJson).toMutableList()
            val index = currentWheels.indexOfFirst { it.id == wheel.id }
            if (index >= 0) {
                currentWheels[index] = wheel
                preferences[Keys.SAVED_WHEELS] = savedWheelsToJson(currentWheels)
            }
        }
    }

    suspend fun deleteSavedWheel(wheelId: String) {
        dataStore.edit { preferences ->
            val currentJson = preferences[Keys.SAVED_WHEELS] ?: "[]"
            val currentWheels = parseSavedWheelsJson(currentJson).toMutableList()
            currentWheels.removeAll { it.id == wheelId }
            preferences[Keys.SAVED_WHEELS] = savedWheelsToJson(currentWheels)
        }
    }

    suspend fun setAutoSpinEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.AUTO_SPIN_ENABLED] = enabled
        }
    }

    suspend fun setTwoHandedEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.TWO_HANDED_ENABLED] = enabled
        }
    }

    suspend fun setNumberNotation(notation: NumberNotation) {
        dataStore.edit { preferences ->
            preferences[Keys.NUMBER_NOTATION] = notation.name
        }
    }

    private fun parseSavedWheelsJson(jsonString: String): List<SavedWheel> {
        return try {
            val jsonArray = JSONArray(jsonString)
            (0 until jsonArray.length()).map { i ->
                val obj = jsonArray.getJSONObject(i)
                SavedWheel(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    capacity = obj.getLong("capacity"),
                    mantraId = obj.getString("mantraId"),
                    skinId = obj.optString("skinId", "traditional_gold"),
                    createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun savedWheelsToJson(wheels: List<SavedWheel>): String {
        val jsonArray = JSONArray()
        wheels.forEach { wheel ->
            val obj = JSONObject().apply {
                put("id", wheel.id)
                put("name", wheel.name)
                put("capacity", wheel.capacity)
                put("mantraId", wheel.mantraId)
                put("skinId", wheel.skinId)
                put("createdAt", wheel.createdAt)
            }
            jsonArray.put(obj)
        }
        return jsonArray.toString()
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

        /**
         * Default saved wheels shown when user has no saved wheels.
         */
        val DEFAULT_SAVED_WHEELS = listOf(
            SavedWheel(
                id = "default_100m",
                name = "100M Prayer Wheel",
                capacity = 100_000_000L,
                mantraId = DEFAULT_MANTRA,
                skinId = "traditional_gold",
                createdAt = 0L
            ),
            SavedWheel(
                id = "default_65b",
                name = "65B Prayer Wheel",
                capacity = 65_000_000_000L,
                mantraId = DEFAULT_MANTRA,
                skinId = "traditional_gold",
                createdAt = 0L
            )
        )
    }
}
