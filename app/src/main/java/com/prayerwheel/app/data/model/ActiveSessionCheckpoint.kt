package com.prayerwheel.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.math.BigInteger

/**
 * Durable snapshot of a live (not yet saved) session — one row per session
 * key in `active_session` (normally zero or one row). Keyed by session rather
 * than a fixed singleton id so a new session's checkpoint can never overwrite
 * an earlier session's checkpoint that has not been recovered/saved yet.
 *
 * Written every [CHECKPOINT_INTERVAL_MS] while a session is active and
 * immediately when the app goes to the background, so an OOM kill, crash,
 * force-stop or battery death loses at most one interval of practice.
 * Deleted in the same transaction that records the session. On the next
 * launch every leftover checkpoint is recorded as a completed session (see
 * [com.prayerwheel.app.data.stats.SessionLedger.recoverCheckpoints]).
 *
 * [sessionKey] is the session's stable identity; it is copied to
 * [Session.sessionKey] so the checkpoint recovery, the ViewModel's own save
 * and SessionSaveWorker can never record the same session twice.
 */
@Entity(tableName = "active_session")
data class ActiveSessionCheckpoint(
    @PrimaryKey val sessionKey: String,
    val startedAt: Long,
    /** Wall-clock time of this snapshot; becomes `endedAt` if recovered. */
    val updatedAt: Long,
    val rotationCount: Long,
    val mantrasPerRotation: Long,
    val totalMantras: BigInteger,
    val mantraId: String,
    val mode: String,
    val averageRpm: Float,
    val peakRpm: Float,
    val intention: String?,
    val sessionGoal: Long?,
    val wheelId: String?,
    val label: String?,
    /** Live-timer duration at snapshot time (what lifetime stats are credited with). */
    val durationSeconds: Long
) {
    /** The completed session this checkpoint represents (no dedication: no UI was available). */
    fun toSession(): Session = Session(
        startedAt = startedAt,
        endedAt = updatedAt,
        rotationCount = rotationCount,
        mantrasPerRotation = mantrasPerRotation,
        totalMantras = totalMantras,
        mantraId = mantraId,
        dedication = null,
        mode = mode,
        averageRpm = averageRpm,
        peakRpm = peakRpm,
        totalSpins = rotationCount,
        intention = intention,
        sessionGoal = sessionGoal,
        wheelId = wheelId,
        label = label,
        sessionKey = sessionKey
    )

    companion object {
        const val CHECKPOINT_INTERVAL_MS = 15_000L
    }
}
