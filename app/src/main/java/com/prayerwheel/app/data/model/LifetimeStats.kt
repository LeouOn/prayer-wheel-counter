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
    @ColumnInfo(name = "average_session_duration_seconds") val averageSessionDurationSeconds: Long = 0L
)
