package com.prayerwheel.app.ui.wheel.calendar

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class StreaksTest {

    private fun run(endInclusive: LocalDate, length: Int): Set<LocalDate> =
        (0 until length).map { endInclusive.minusDays(it.toLong()) }.toSet()

    @Test
    fun empty_hasNoStreaks() {
        assertEquals(Streaks(0, 0), computeStreaks(emptySet(), LocalDate.of(2026, 11, 1)))
    }

    @Test
    fun sixtyStraightDaysEndingToday_isCurrentAndBest() {
        val today = LocalDate.of(2026, 11, 1)
        assertEquals(Streaks(60, 60), computeStreaks(run(today, 60), today))
    }

    @Test
    fun todayNotYetPracticed_doesNotBreakStreak() {
        val today = LocalDate.of(2026, 11, 1)
        val days = run(today.minusDays(1), 5)
        assertEquals(Streaks(5, 5), computeStreaks(days, today))
    }

    @Test
    fun missedYesterdayAndToday_currentIsZero() {
        val today = LocalDate.of(2026, 11, 1)
        val days = run(today.minusDays(2), 4)
        assertEquals(Streaks(0, 4), computeStreaks(days, today))
    }

    @Test
    fun gap_splitsRuns_andBestIsLongest() {
        val today = LocalDate.of(2026, 11, 1)
        val older = run(LocalDate.of(2026, 10, 20), 7) // Oct 14..20
        val recent = run(today, 3)                      // Oct 30..Nov 1
        assertEquals(Streaks(3, 7), computeStreaks(older + recent, today))
    }

    @Test
    fun crossingNewYear_countsContinuously() {
        val today = LocalDate.of(2027, 1, 2)
        val days = run(today, 6) // Dec 28 2026 .. Jan 2 2027
        assertEquals(Streaks(6, 6), computeStreaks(days, today))
    }

    @Test
    fun crossingNewYear_onJan1BeforePracticing() {
        val today = LocalDate.of(2027, 1, 1)
        val days = run(LocalDate.of(2026, 12, 31), 10)
        assertEquals(Streaks(10, 10), computeStreaks(days, today))
    }

    @Test
    fun leapDay_isPartOfRun() {
        val today = LocalDate.of(2028, 3, 1)
        val days = setOf(
            LocalDate.of(2028, 2, 27),
            LocalDate.of(2028, 2, 28),
            LocalDate.of(2028, 2, 29),
            LocalDate.of(2028, 3, 1)
        )
        assertEquals(Streaks(4, 4), computeStreaks(days, today))
    }

    @Test
    fun missingLeapDay_breaksRun() {
        val today = LocalDate.of(2028, 3, 1)
        val days = setOf(
            LocalDate.of(2028, 2, 27),
            LocalDate.of(2028, 2, 28),
            LocalDate.of(2028, 3, 1)
        )
        assertEquals(Streaks(1, 2), computeStreaks(days, today))
    }

    @Test
    fun practiceDays_usesLocalZone() {
        val zone = ZoneId.of("America/Los_Angeles")
        // 23:30 local on Dec 31 is already Jan 1 in UTC; it must count as Dec 31.
        val lateNewYearsEve = ZonedDateTime.of(2026, 12, 31, 23, 30, 0, 0, zone).toInstant().toEpochMilli()
        val earlyNewYear = ZonedDateTime.of(2027, 1, 1, 0, 15, 0, 0, zone).toInstant().toEpochMilli()
        val days = practiceDays(listOf(lateNewYearsEve, earlyNewYear), zone)
        assertEquals(setOf(LocalDate.of(2026, 12, 31), LocalDate.of(2027, 1, 1)), days)
    }
}
