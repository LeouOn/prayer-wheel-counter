package com.prayerwheel.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.prayerwheel.app.data.db.dao.LifetimeStatsDao
import com.prayerwheel.app.data.db.dao.SessionDao
import com.prayerwheel.app.data.model.LifetimeStats
import com.prayerwheel.app.data.model.Session

/**
 * Room database for the Prayer Wheel Counter application.
 *
 * Stores session history and lifetime cumulative statistics.
 * All data remains local on the device.
 */
@Database(
    entities = [
        Session::class,
        LifetimeStats::class
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun lifetimeStatsDao(): LifetimeStatsDao
}
