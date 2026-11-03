package com.prayerwheel.app.data.stats

import com.prayerwheel.app.data.model.LifetimeStats
import com.prayerwheel.app.data.model.Session
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigInteger

class LifetimeStatsMathTest {

    private fun session(
        rotations: Long = 10,
        mantras: BigInteger = BigInteger.valueOf(rotations * 100),
        startedAt: Long = 1_000_000L,
        endedAt: Long? = startedAt + 60_000L
    ) = Session(
        startedAt = startedAt,
        endedAt = endedAt,
        rotationCount = rotations,
        mantrasPerRotation = 100,
        totalMantras = mantras,
        mantraId = "om",
        dedication = null,
        mode = "MANUAL"
    )

    private fun stats(
        rotations: Long = 20,
        mantras: BigInteger = BigInteger.valueOf(2_000),
        sessions: Long = 2,
        first: Long? = 500L,
        totalSeconds: Long = 100,
        avg: Long = 50
    ) = LifetimeStats(1, rotations, mantras, sessions, first, totalSeconds, avg)

    @Test
    fun plusSession_nullRow_createsSeededRow() {
        val s = session(startedAt = 42L, endedAt = 42L + 30_000)
        val r = LifetimeStatsMath.plusSession(null, s, durationSeconds = 30)
        assertEquals(LifetimeStats(1, 10, BigInteger.valueOf(1_000), 1, 42L, 30, 30), r)
    }

    @Test
    fun plusSession_existing_accumulatesAndAverages() {
        val r = LifetimeStatsMath.plusSession(stats(), session(), durationSeconds = 50)
        assertEquals(30L, r.totalRotations)
        assertEquals(BigInteger.valueOf(3_000), r.totalMantras)
        assertEquals(3L, r.sessionsCompleted)
        assertEquals(150L, r.totalSpinningTimeSeconds)
        assertEquals(50L, r.averageSessionDurationSeconds)
        assertEquals(500L, r.firstSessionAt)
    }

    @Test
    fun plusSession_averageTruncates() {
        val r = LifetimeStatsMath.plusSession(stats(totalSeconds = 100, sessions = 2), session(), durationSeconds = 1)
        assertEquals(33L, r.averageSessionDurationSeconds) // 101 / 3
    }

    @Test
    fun plusSession_usesPassedDurationNotEndMinusStart() {
        // Session spans 60s but the caller (e.g. live timer) says 7s.
        val r = LifetimeStatsMath.plusSession(null, session(), durationSeconds = 7)
        assertEquals(7L, r.totalSpinningTimeSeconds)
    }

    @Test
    fun currentBehavior_plusSession_zeroSessionsRow_averageIgnoresLeftoverTotal() {
        // A row with 0 sessions but leftover spinning time (possible when add/subtract
        // used different durations) sets the average to just this session's duration.
        val r = LifetimeStatsMath.plusSession(stats(sessions = 0, totalSeconds = 500, avg = 0), session(), 40)
        assertEquals(540L, r.totalSpinningTimeSeconds)
        assertEquals(40L, r.averageSessionDurationSeconds)
    }

    @Test
    fun plusSession_backfillsNullFirstSessionAt() {
        // After resetLifetimeStats() the row has firstSessionAt = null; the next session refills it.
        val r = LifetimeStatsMath.plusSession(stats(first = null), session(startedAt = 9_999L), 10)
        assertEquals(9_999L, r.firstSessionAt)
    }

    @Test
    fun plusSession_lowersFirstSessionAtForBackdatedSession() {
        val r = LifetimeStatsMath.plusSession(stats(first = 500L), session(startedAt = 1L), 10)
        assertEquals(1L, r.firstSessionAt)
    }

    @Test
    fun plusSession_keepsEarlierFirstSessionAt() {
        val r = LifetimeStatsMath.plusSession(stats(first = 500L), session(startedAt = 9_999L), 10)
        assertEquals(500L, r.firstSessionAt)
    }

    @Test
    fun plusSession_bigIntegerBeyondLong() {
        val huge = BigInteger.valueOf(Long.MAX_VALUE)
        val sessionMantras = BigInteger("1000000000000").multiply(BigInteger.valueOf(1_000_000))
        val r = LifetimeStatsMath.plusSession(stats(mantras = huge), session(mantras = sessionMantras), 10)
        assertEquals(huge.add(sessionMantras), r.totalMantras)
    }

    @Test
    fun minusSession_floorsAtZero() {
        val r = LifetimeStatsMath.minusSession(
            stats(rotations = 5, mantras = BigInteger.valueOf(50), sessions = 3, totalSeconds = 10),
            session(rotations = 10, mantras = BigInteger.valueOf(1_000)),
            durationSeconds = 60
        )
        assertEquals(0L, r.totalRotations)
        assertEquals(BigInteger.ZERO, r.totalMantras)
        assertEquals(2L, r.sessionsCompleted)
        assertEquals(0L, r.totalSpinningTimeSeconds)
        assertEquals(0L, r.averageSessionDurationSeconds)
    }

    @Test
    fun minusSession_oneToZeroSessions_averageZero() {
        val r = LifetimeStatsMath.minusSession(stats(sessions = 1, totalSeconds = 100, avg = 100), session(), 40)
        assertEquals(0L, r.sessionsCompleted)
        assertEquals(60L, r.totalSpinningTimeSeconds)
        assertEquals(0L, r.averageSessionDurationSeconds)
    }

    @Test
    fun minusSession_zeroSessionsStaysZero() {
        val r = LifetimeStatsMath.minusSession(stats(sessions = 0), session(), 10)
        assertEquals(0L, r.sessionsCompleted)
    }

    @Test
    fun addThenSubtract_roundTripsConsistentRow() {
        val original = stats(rotations = 20, mantras = BigInteger.valueOf(2_000), sessions = 2, totalSeconds = 100, avg = 50)
        val s = session()
        val dur = LifetimeStatsMath.storedDurationSeconds(s)
        val back = LifetimeStatsMath.minusSession(LifetimeStatsMath.plusSession(original, s, dur), s, dur)
        assertEquals(original, back)
    }

    @Test
    fun storedDurationSeconds_truncatesAndHandlesOpenSession() {
        assertEquals(60L, LifetimeStatsMath.storedDurationSeconds(session(startedAt = 0, endedAt = 60_000)))
        assertEquals(1L, LifetimeStatsMath.storedDurationSeconds(session(startedAt = 0, endedAt = 1_999)))
        assertEquals(0L, LifetimeStatsMath.storedDurationSeconds(session(startedAt = 0, endedAt = null)))
    }
}
