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
 *    rows.
 *  - `sessions.duration_seconds`: the duration credited for the row, so a
 *    debit matches the credit and a resume continues from it.
 *  - `lifetime_stats.wiped_at`: time of the last "Reset All Data"; stale
 *    live-session snapshots from before it are dropped when they arrive.
 *  - v4 already had "Reset lifetime stats" but recorded no timestamp. If a
 *    reset evidently happened (lifetime row says fewer sessions than exist,
 *    or firstSessionAt was nulled, or the row is missing while sessions
 *    exist), reset_at is set to the migration time, so pre-v5 rows are not
 *    debited on delete. This deliberately errs toward under-debiting
 *    (keeping counts) rather than subtracting practice the totals never held.
 * Schema statements must match app/schemas/.../5.json exactly (checked by
 * MigrationsSchemaTest); UPDATE/INSERT statements are data-only.
 */
internal val MIGRATION_4_5_SQL = listOf(
    "ALTER TABLE `sessions` ADD COLUMN `session_key` TEXT",
    "CREATE UNIQUE INDEX IF NOT EXISTS `index_sessions_session_key` ON `sessions` (`session_key`)",
    "ALTER TABLE `sessions` ADD COLUMN `recorded_at` INTEGER",
    "ALTER TABLE `sessions` ADD COLUMN `duration_seconds` INTEGER",
    "ALTER TABLE `lifetime_stats` ADD COLUMN `reset_at` INTEGER",
    "ALTER TABLE `lifetime_stats` ADD COLUMN `wiped_at` INTEGER",
    "CREATE TABLE IF NOT EXISTS `active_session` (`sessionKey` TEXT NOT NULL, " +
        "`startedAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `rotationCount` INTEGER NOT NULL, " +
        "`mantrasPerRotation` INTEGER NOT NULL, `totalMantras` TEXT NOT NULL, `mantraId` TEXT NOT NULL, " +
        "`mode` TEXT NOT NULL, `averageRpm` REAL NOT NULL, `peakRpm` REAL NOT NULL, `intention` TEXT, " +
        "`sessionGoal` INTEGER, `wheelId` TEXT, `label` TEXT, `durationSeconds` INTEGER NOT NULL, " +
        "PRIMARY KEY(`sessionKey`))",
    // Data: infer a v4 lifetime reset (see above). Times in epoch millis.
    "UPDATE `lifetime_stats` SET `reset_at` = CAST(strftime('%s','now') AS INTEGER) * 1000 " +
        "WHERE `id` = 1 AND EXISTS (SELECT 1 FROM `sessions`) AND (`firstSessionAt` IS NULL " +
        "OR `sessionsCompleted` < (SELECT COUNT(*) FROM `sessions`))",
    "INSERT INTO `lifetime_stats` (`id`, `totalRotations`, `totalMantras`, `sessionsCompleted`, " +
        "`firstSessionAt`, `total_spinning_time_seconds`, `average_session_duration_seconds`, `reset_at`) " +
        "SELECT 1, 0, '0', 0, NULL, 0, 0, CAST(strftime('%s','now') AS INTEGER) * 1000 " +
        "WHERE NOT EXISTS (SELECT 1 FROM `lifetime_stats` WHERE `id` = 1) AND EXISTS (SELECT 1 FROM `sessions`)"
)

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(database: SupportSQLiteDatabase) {
        MIGRATION_4_5_SQL.forEach(database::execSQL)
    }
}
