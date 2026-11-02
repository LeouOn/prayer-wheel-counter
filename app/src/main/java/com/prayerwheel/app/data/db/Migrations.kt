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

/**
 * v4 -> v5: durable live-session checkpointing and stable session identity.
 *  - `sessions.session_key` (nullable, unique): one key per live-recorded
 *    session so the VM save, SessionSaveWorker and checkpoint recovery can't
 *    record the same session twice. Existing rows keep NULL (SQLite unique
 *    indexes allow many NULLs).
 *  - `active_session`: durable checkpoint of the in-progress session, keyed
 *    by session key (normally zero or one row).
 *  - `sessions.recorded_at` / `lifetime_stats.reset_at`: when a row was
 *    credited vs. when lifetime totals were last reset, so deleting a session
 *    recorded before a reset doesn't debit the new totals. NULL for existing
 *    rows (pre-v5 rows predate any reset recorded from v5 on).
 * Statements must match app/schemas/.../5.json exactly (checked by
 * MigrationsSchemaTest).
 */
internal val MIGRATION_4_5_SQL = listOf(
    "ALTER TABLE `sessions` ADD COLUMN `session_key` TEXT",
    "CREATE UNIQUE INDEX IF NOT EXISTS `index_sessions_session_key` ON `sessions` (`session_key`)",
    "ALTER TABLE `sessions` ADD COLUMN `recorded_at` INTEGER",
    "ALTER TABLE `lifetime_stats` ADD COLUMN `reset_at` INTEGER",
    "CREATE TABLE IF NOT EXISTS `active_session` (`sessionKey` TEXT NOT NULL, " +
        "`startedAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `rotationCount` INTEGER NOT NULL, " +
        "`mantrasPerRotation` INTEGER NOT NULL, `totalMantras` TEXT NOT NULL, `mantraId` TEXT NOT NULL, " +
        "`mode` TEXT NOT NULL, `averageRpm` REAL NOT NULL, `peakRpm` REAL NOT NULL, `intention` TEXT, " +
        "`sessionGoal` INTEGER, `wheelId` TEXT, `label` TEXT, `durationSeconds` INTEGER NOT NULL, " +
        "PRIMARY KEY(`sessionKey`))"
)

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(database: SupportSQLiteDatabase) {
        MIGRATION_4_5_SQL.forEach(database::execSQL)
    }
}
