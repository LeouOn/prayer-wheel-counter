package com.prayerwheel.app.data.datastore

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RetiredPreferencesMigrationTest {

    private val kept = stringPreferencesKey("number_notation")

    @Test
    fun removesRetiredKeysOfAnyTypeAndKeepsOthers() = runTest {
        val prefs = mutablePreferencesOf(
            booleanPreferencesKey("clockwise_direction") to false,
            floatPreferencesKey("recitation_volume") to 0.3f,
            longPreferencesKey("session_merge_threshold_ms") to 60_000L,
            kept to "EXTENDED",
        )
        assertTrue(RetiredPreferencesMigration.shouldMigrate(prefs))

        val migrated = RetiredPreferencesMigration.migrate(prefs)

        assertEquals(setOf<Preferences.Key<*>>(kept), migrated.asMap().keys)
        assertEquals("EXTENDED", migrated[kept])
        assertFalse(RetiredPreferencesMigration.shouldMigrate(migrated))
    }

    @Test
    fun noRetiredKeys_noMigration() = runTest {
        assertFalse(RetiredPreferencesMigration.shouldMigrate(mutablePreferencesOf(kept to "STANDARD")))
    }
}
