package com.prayerwheel.app

import android.content.Context
import android.app.Application
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.prayerwheel.app.data.datastore.UserPreferences
import com.prayerwheel.app.data.db.AppDatabase

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

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

    override fun onCreate() {
        super.onCreate()

        // Initialize Room database
        database = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "prayer_wheel_database"
        )
            .fallbackToDestructiveMigration()
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
