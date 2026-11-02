package com.prayerwheel.app

import android.content.Context
import android.app.Application
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.prayerwheel.app.data.datastore.UserPreferences
import com.prayerwheel.app.data.db.AppDatabase
import com.prayerwheel.app.data.db.MIGRATION_3_4
import com.prayerwheel.app.data.db.MIGRATION_4_5
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * A corrupted preferences file (e.g. torn write on power loss) would otherwise
 * throw CorruptionException from every read, crashing the app on each launch.
 * Replace it with empty preferences (defaults) instead. Practice data lives in
 * Room, not here, so only settings are reset.
 */
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "user_preferences",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() }
)

/**
 * Application class for Prayer Wheel Counter.
 *
 * Initializes the Room database and DataStore preferences.
 */
class PrayerWheelApp : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var userPreferences: UserPreferences
        private set

    lateinit var vibrator: Vibrator
        private set

    /**
     * Process-lifetime scope for writes that must not be cancelled when a
     * screen or the ViewModel goes away (session saves, checkpoints, resets).
     */
    val applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()

        // Initialize Room database.
        // Only the v1 schema (early development, no longer recoverable, no
        // MIGRATION_1_2) may be wiped. Every later version must migrate
        // explicitly: an unbounded `fallbackToDestructiveMigration()` silently
        // deleted years of practice history whenever a migration was missing,
        // and also on any downgrade. A missing migration now fails loudly
        // instead of erasing data. Schemas are exported to app/schemas.
        database = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "prayer_wheel_database"
        )
            .addMigrations(AppDatabase.MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
            .fallbackToDestructiveMigrationFrom(dropAllTables = true, 1)
            .build()

        // Initialize DataStore preferences
        userPreferences = UserPreferences(applicationContext.dataStore)

        // Initialize vibrator
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as Vibrator
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        database.close()
    }
}
