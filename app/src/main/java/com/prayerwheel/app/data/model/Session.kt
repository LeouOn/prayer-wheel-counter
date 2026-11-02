package com.prayerwheel.app.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.math.BigInteger

/**
 * Represents a single prayer wheel session.
 *
 * A session records one continuous period of prayer wheel practice,
 * from the first rotation to when the practitioner ends the session.
 */
@Entity(
    tableName = "sessions",
    indices = [Index(value = ["session_key"], unique = true)]
)
data class Session(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val startedAt: Long,
    val endedAt: Long?,
    val rotationCount: Long,
    val mantrasPerRotation: Long,
    val totalMantras: BigInteger,
    val mantraId: String,
    val dedication: String?,
    val mode: String,
    @ColumnInfo(name = "average_rpm") val averageRpm: Float = 0f,
    @ColumnInfo(name = "peak_rpm") val peakRpm: Float = 0f,
    @ColumnInfo(name = "total_spins") val totalSpins: Long = 0L,
    @ColumnInfo(name = "intention") val intention: String? = null,
    @ColumnInfo(name = "session_goal") val sessionGoal: Long? = null,
    @ColumnInfo(name = "wheel_id") val wheelId: String? = null,
    @ColumnInfo(name = "label") val label: String? = null,
    /**
     * Stable identity of a live-recorded session (UUID), shared by the
     * ViewModel save, SessionSaveWorker and checkpoint recovery so the same
     * session is never recorded twice. Null for manual logs and pre-v5 rows.
     */
    @ColumnInfo(name = "session_key") val sessionKey: String? = null,
    /**
     * When this row was credited to lifetime_stats (set by the ledger on
     * insert). Compared with [LifetimeStats.resetAt]: rows credited before a
     * lifetime reset are not debited on delete. Null for pre-v5 rows, which
     * necessarily predate any reset recorded by v5+.
     */
    @ColumnInfo(name = "recorded_at") val recordedAt: Long? = null
)
