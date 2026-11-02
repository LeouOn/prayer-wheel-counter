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
     * `firstSessionAt` is the earliest start among credited sessions: it is
     * refilled after a lifetime reset (null) and lowered for backdated logs.
     */
    fun plusSession(existing: LifetimeStats?, session: Session, durationSeconds: Long): LifetimeStats =
        existing?.copy(
            // Long counters saturate instead of wrapping negative (manual logs
            // accept arbitrarily large rotation counts / durations).
            totalRotations = saturatingAdd(existing.totalRotations, session.rotationCount),
            totalMantras = existing.totalMantras + session.totalMantras,
            sessionsCompleted = existing.sessionsCompleted + 1,
            firstSessionAt = minOf(existing.firstSessionAt ?: session.startedAt, session.startedAt),
            totalSpinningTimeSeconds = saturatingAdd(existing.totalSpinningTimeSeconds, durationSeconds),
            averageSessionDurationSeconds = if (existing.sessionsCompleted > 0) {
                saturatingAdd(existing.totalSpinningTimeSeconds, durationSeconds) / (existing.sessionsCompleted + 1)
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

    /** Long addition that clamps at [Long.MAX_VALUE]/[Long.MIN_VALUE] instead of wrapping. */
    fun saturatingAdd(a: Long, b: Long): Long {
        val r = a + b
        // Overflow iff both operands have the same sign and the result's sign differs.
        return if (((a xor r) and (b xor r)) < 0) {
            if (a < 0) Long.MIN_VALUE else Long.MAX_VALUE
        } else r
    }

    /** Session duration as `(endedAt ?: startedAt) − startedAt`, in whole seconds (truncated). */
    fun storedDurationSeconds(session: Session): Long =
        ((session.endedAt ?: session.startedAt) - session.startedAt) / 1000
}
