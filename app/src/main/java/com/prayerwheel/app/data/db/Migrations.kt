package com.prayerwheel.app.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room migrations for the Prayer Wheel Counter database.
 *
 * Each migration is a non-destructive, incremental schema change.
 * Migrations are registered in [com.prayerwheel.app.PrayerWheelApp]
 * via [androidx.room.RoomDatabase.Builder.addMigrations].
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE sessions ADD COLUMN label TEXT")
    }
}
