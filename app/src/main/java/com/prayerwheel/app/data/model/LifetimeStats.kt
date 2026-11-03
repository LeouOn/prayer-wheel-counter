package com.prayerwheel.app.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.math.BigInteger

/**
 * Singleton entity holding cumulative lifetime statistics.
 *
 * Uses BigInteger for totalMantras to support stupa-class capacity
 * where totals can reach into the trillions (or beyond).
 * Stored as TEXT in SQLite via TypeConverter.
 */
@Entity(tableName = "lifetime_stats")
data class LifetimeStats(
    @PrimaryKey
    val id: Int = 1,
    val totalRotations: Long,
    val totalMantras: BigInteger,
    val sessionsCompleted: Long,
    val firstSessionAt: Long?,
    @ColumnInfo(name = "total_spinning_time_seconds") val totalSpinningTimeSeconds: Long = 0L,
    @ColumnInfo(name = "average_session_duration_seconds") val averageSessionDurationSeconds: Long = 0L,
    /**
     * Time of the last "Reset lifetime stats" (sessions kept), or null if never
     * reset since v5. Sessions credited before it no longer count toward these
     * totals, so deleting/restoring them must not debit/credit.
     */
    @ColumnInfo(name = "reset_at") val resetAt: Long? = null,
    /**
     * Time of the last "Reset All Data". Live-session snapshots (keyed
     * sessions) that started before it are stale — e.g. a SessionSaveWorker
     * run already in flight when the wipe happened — and are dropped inside
     * the recording transaction instead of resurrecting a wiped session.
     */
    @ColumnInfo(name = "wiped_at") val wipedAt: Long? = null
)
