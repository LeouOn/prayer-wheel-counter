package com.prayerwheel.app.data.datastore

import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey

/**
 * Settings that were removed in 1.1.0 because nothing ever read them
 * (Direction duplicated the counter-clockwise toggle; recitation audio,
 * ambient volume and milestone notifications were never implemented; the
 * session merge threshold only appeared to work through a session-wipe bug).
 *
 * Their stored values are deleted once on upgrade so they don't linger in
 * backups. Only the key name matters for removal, so string keys are used
 * regardless of the original value type.
 */
internal val RETIRED_PREFERENCE_NAMES = listOf(
    "clockwise_direction",
    "recitation_enabled",
    "recitation_volume",
    "ambient_volume",
    "milestone_notifications",
    "session_merge_threshold_ms",
)

/** Removes [RETIRED_PREFERENCE_NAMES] from the preferences file if any are present. */
object RetiredPreferencesMigration : DataMigration<Preferences> {

    override suspend fun shouldMigrate(currentData: Preferences): Boolean =
        currentData.asMap().keys.any { it.name in RETIRED_PREFERENCE_NAMES }

    override suspend fun migrate(currentData: Preferences): Preferences {
        val prefs = currentData.toMutablePreferences()
        RETIRED_PREFERENCE_NAMES.forEach { prefs.remove(stringPreferencesKey(it)) }
        return prefs.toPreferences()
    }

    override suspend fun cleanUp() = Unit
}
