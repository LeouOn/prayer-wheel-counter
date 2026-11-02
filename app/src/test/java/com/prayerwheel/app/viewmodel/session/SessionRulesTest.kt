package com.prayerwheel.app.viewmodel.session

import com.prayerwheel.app.data.model.Session
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger
import java.time.ZoneId
import java.time.ZonedDateTime

class SessionRulesTest {

    private val utc: ZoneId = ZoneId.of("UTC")
    private val window = 300_000L
    private val now = 10_000_000L

    private fun session(
        startedAt: Long = now - 60_000L,
        endedAt: Long? = now - 1_000L,
        rotations: Long = 5,
        dedication: String? = null,
        mantras: Long = 50
    ) = Session(
        startedAt = startedAt,
        endedAt = endedAt,
        rotationCount = rotations,
        mantrasPerRotation = 10,
        totalMantras = BigInteger.valueOf(mantras),
        mantraId = "om",
        dedication = dedication,
        mode = "MANUAL"
    )

    private fun utcMillis(hour: Int, minute: Int = 0, second: Int = 0, nano: Int = 0): Long =
        ZonedDateTime.of(2026, 3, 10, hour, minute, second, nano, utc).toInstant().toEpochMilli()

    // ── isResumable ────────────────────────────────────────────────────

    @Test
    fun isResumable_truthTable() {
        val old = now - window - 1
        for (recentByOpen in listOf(true, false)) {
            for (recentByStart in listOf(true, false)) {
                for (hasRotations in listOf(true, false)) {
                    for (interrupted in listOf(true, false)) {
                        val s = session(
                            startedAt = if (recentByStart) now - 1_000 else old,
                            endedAt = if (recentByOpen) null else now,
                            rotations = if (hasRotations) 1 else 0,
                            dedication = if (interrupted) null else "for all beings"
                        )
                        val expected = (recentByOpen || recentByStart) && hasRotations && interrupted
                        assertEquals(
                            "open=$recentByOpen start=$recentByStart rot=$hasRotations int=$interrupted",
                            expected,
                            SessionRules.isResumable(s, now, window)
                        )
                    }
                }
            }
        }
    }

    @Test
    fun isResumable_windowEdgeIsInclusive() {
        assertTrue(SessionRules.isResumable(session(startedAt = now - window), now, window))
        assertFalse(SessionRules.isResumable(session(startedAt = now - window - 1), now, window))
    }

    @Test
    fun currentBehavior_isResumable_measuresRecencyFromStartNotEnd() {
        // Killed 1s ago after a 10-minute session: not resumable because startedAt is old.
        val s = session(startedAt = now - 600_000L, endedAt = now - 1_000L)
        assertFalse(SessionRules.isResumable(s, now, window))
    }

    @Test
    fun currentBehavior_isResumable_skippedDedicationLooksInterrupted() {
        // skipDedication() saves a normally-ended session with dedication = null.
        assertTrue(SessionRules.isResumable(session(dedication = null), now, window))
    }

    // ── needsNewSession ────────────────────────────────────────────────

    @Test
    fun needsNewSession_noActiveSession_isTrue() {
        assertTrue(SessionRules.needsNewSession(null))
    }

    @Test
    fun needsNewSession_activeSession_isFalse() {
        assertFalse(SessionRules.needsNewSession(now - 1_000_000))
        assertFalse(SessionRules.needsNewSession(now))
    }

    @Test
    fun needsNewSession_activeSessionNeverResetAfterEarlierDedicatedSession() {
        // Regression: session A was dedicated at T0; session B started at T0+1min and
        // is still active. Long after T0 (beyond any merge threshold) further flicks
        // must keep accumulating into B instead of zeroing it.
        val t0 = now - 6 * 60_000L
        val activeStart = t0 + 60_000L
        assertFalse(SessionRules.needsNewSession(activeStart))
    }

    // ── endedSessionDurationSeconds ─────────────────────────────────────

    @Test
    fun endedSessionDuration_isFixedAtSessionEnd_truncatedAndNonNegative() {
        val start = now - 600_000L
        assertEquals(600L, SessionRules.endedSessionDurationSeconds(start, now))
        assertEquals(0L, SessionRules.endedSessionDurationSeconds(start, start + 999))
        assertEquals(0L, SessionRules.endedSessionDurationSeconds(start, start - 5_000))
    }

    // ── dayWindow ──────────────────────────────────────────────────────

    @Test
    fun dayWindow_isLocalMidnightToMidnight() {
        val w = SessionRules.dayWindow(utcMillis(15, 30), utc)
        assertEquals(java.time.LocalDate.of(2026, 3, 10), w.date)
        assertEquals(utcMillis(0), w.startMs)
        assertEquals(utcMillis(0) + 86_400_000L, w.endMs)
    }

    @Test
    fun dayWindow_newYearAfterOvernight_isTheNewDay() {
        val ny = ZoneId.of("America/New_York")
        val jan1 = ZonedDateTime.of(2027, 1, 1, 7, 0, 0, 0, ny).toInstant().toEpochMilli()
        val w = SessionRules.dayWindow(jan1, ny)
        assertEquals(java.time.LocalDate.of(2027, 1, 1), w.date)
        assertEquals(ZonedDateTime.of(2027, 1, 1, 0, 0, 0, 0, ny).toInstant().toEpochMilli(), w.startMs)
    }

    @Test
    fun dayWindow_dstDaysAre23And25Hours() {
        val ny = ZoneId.of("America/New_York")
        val spring = ZonedDateTime.of(2026, 3, 8, 12, 0, 0, 0, ny).toInstant().toEpochMilli()
        val fall = ZonedDateTime.of(2026, 11, 1, 12, 0, 0, 0, ny).toInstant().toEpochMilli()
        val sw = SessionRules.dayWindow(spring, ny)
        val fw = SessionRules.dayWindow(fall, ny)
        assertEquals(23 * 3_600_000L, sw.endMs - sw.startMs)
        assertEquals(25 * 3_600_000L, fw.endMs - fw.startMs)
    }

    @Test
    fun dayWindow_followsZoneChange() {
        val instant = utcMillis(22) // 22:00 UTC on Mar 10 = Mar 11 in Tokyo
        assertEquals(java.time.LocalDate.of(2026, 3, 10), SessionRules.dayWindow(instant, utc).date)
        assertEquals(java.time.LocalDate.of(2026, 3, 11), SessionRules.dayWindow(instant, ZoneId.of("Asia/Tokyo")).date)
    }

    // ── autoLabel / isMorning ──────────────────────────────────────────

    @Test
    fun autoLabel_eveningHourBoundary() {
        assertEquals("morning", SessionRules.autoLabel(utcMillis(18, 59, 59, 999_000_000), 19, utc, true))
        assertEquals("evening", SessionRules.autoLabel(utcMillis(19), 19, utc, true))
        assertEquals("morning", SessionRules.autoLabel(utcMillis(0), 19, utc, true))
        assertEquals("evening", SessionRules.autoLabel(utcMillis(23, 59), 19, utc, true))
    }

    @Test
    fun autoLabel_disabled_isNull() {
        assertNull(SessionRules.autoLabel(utcMillis(8), 19, utc, false))
    }

    @Test
    fun autoLabel_eveningHourZero_isAlwaysEvening() {
        assertEquals("evening", SessionRules.autoLabel(utcMillis(0), 0, utc, true))
        assertEquals("evening", SessionRules.autoLabel(utcMillis(12), 0, utc, true))
    }

    @Test
    fun autoLabel_usesGivenZone() {
        val instant = utcMillis(13, 30) // 19:00 in Asia/Kolkata (+05:30)
        assertEquals("morning", SessionRules.autoLabel(instant, 19, utc, true))
        assertEquals("evening", SessionRules.autoLabel(instant, 19, ZoneId.of("Asia/Kolkata"), true))
    }

    // ── todayProgress ──────────────────────────────────────────────────

    @Test
    fun todayProgress_empty() {
        assertEquals(
            SessionRules.TodayProgress(false, false, BigInteger.ZERO, 0L),
            SessionRules.todayProgress(emptyList(), 19, utc)
        )
    }

    @Test
    fun todayProgress_attributesByStartHourAndSums() {
        val morning = session(startedAt = utcMillis(8), endedAt = utcMillis(8, 10), mantras = 100)
        // Starts 18:55, ends 19:20 → attributed to morning by its start hour.
        val spanning = session(startedAt = utcMillis(18, 55), endedAt = utcMillis(19, 20), mantras = 5)
        val p = SessionRules.todayProgress(listOf(morning, spanning), 19, utc)
        assertTrue(p.morningCompleted)
        assertFalse(p.eveningCompleted)
        assertEquals(BigInteger.valueOf(105), p.mantras)
        assertEquals(35 * 60L, p.practiceSeconds)
    }

    @Test
    fun todayProgress_eveningOpenAndNegativeSessions() {
        val open = session(startedAt = utcMillis(20), endedAt = null, mantras = 1)
        val negative = session(startedAt = utcMillis(21), endedAt = utcMillis(20), mantras = 1)
        val p = SessionRules.todayProgress(listOf(open, negative), 19, utc)
        assertFalse(p.morningCompleted)
        assertTrue(p.eveningCompleted)
        assertEquals(BigInteger.valueOf(2), p.mantras)
        assertEquals(0L, p.practiceSeconds)
    }

    @Test
    fun todayProgress_truncatesOnlyAfterSummingMillis() {
        val a = session(startedAt = utcMillis(9), endedAt = utcMillis(9) + 1_500)
        val b = session(startedAt = utcMillis(10), endedAt = utcMillis(10) + 1_500)
        assertEquals(3L, SessionRules.todayProgress(listOf(a, b), 19, utc).practiceSeconds)
    }
}
