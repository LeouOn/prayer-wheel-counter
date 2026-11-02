package com.prayerwheel.app.data.stats

import com.prayerwheel.app.data.model.LifetimeStats
import com.prayerwheel.app.data.model.Session
import java.math.BigInteger

/**
 * Pure arithmetic for folding a [Session] into (or out of) the singleton
 * [LifetimeStats] row. Callers own the read-modify-write locking/transaction
 * and decide how the session's duration is measured — each call site passes
 * [durationSeconds] exactly as it computed it before this extraction
 * (live timer, endedAt − startedAt, or a user-entered value).
 */
object LifetimeStatsMath {

    /**
     * Adds one session to [existing]. When there is no row yet, creates the
     * singleton row (id = 1) seeded from the session.
     *
     * Current behavior (pinned by tests): when a row already exists,
     * `firstSessionAt` is carried over unchanged — even if it is null (e.g.
     * after a lifetime reset) or later than the session's start.
     */
    fun plusSession(existing: LifetimeStats?, session: Session, durationSeconds: Long): LifetimeStats =
        existing?.copy(
            totalRotations = existing.totalRotations + session.rotationCount,
            totalMantras = existing.totalMantras + session.totalMantras,
            sessionsCompleted = existing.sessionsCompleted + 1,
            totalSpinningTimeSeconds = existing.totalSpinningTimeSeconds + durationSeconds,
            averageSessionDurationSeconds = if (existing.sessionsCompleted > 0) {
                (existing.totalSpinningTimeSeconds + durationSeconds) / (existing.sessionsCompleted + 1)
            } else durationSeconds
        ) ?: LifetimeStats(
            id = 1,
            totalRotations = session.rotationCount,
            totalMantras = session.totalMantras,
            sessionsCompleted = 1,
            firstSessionAt = session.startedAt,
            totalSpinningTimeSeconds = durationSeconds,
            averageSessionDurationSeconds = durationSeconds
        )

    /**
     * Removes one session from [existing]. Every counter floors at zero and
     * the average is recomputed from the floored total (0 when no sessions remain).
     */
    fun minusSession(existing: LifetimeStats, session: Session, durationSeconds: Long): LifetimeStats {
        val newCount = (existing.sessionsCompleted - 1).coerceAtLeast(0)
        return existing.copy(
            totalRotations = (existing.totalRotations - session.rotationCount).coerceAtLeast(0),
            totalMantras = (existing.totalMantras - session.totalMantras).coerceAtLeast(BigInteger.ZERO),
            sessionsCompleted = newCount,
            totalSpinningTimeSeconds = (existing.totalSpinningTimeSeconds - durationSeconds).coerceAtLeast(0),
            averageSessionDurationSeconds = if (newCount > 0) {
                (existing.totalSpinningTimeSeconds - durationSeconds).coerceAtLeast(0) / newCount
            } else 0L
        )
    }

    /** Session duration as `(endedAt ?: startedAt) − startedAt`, in whole seconds (truncated). */
    fun storedDurationSeconds(session: Session): Long =
        ((session.endedAt ?: session.startedAt) - session.startedAt) / 1000
}
