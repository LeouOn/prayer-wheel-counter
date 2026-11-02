package com.prayerwheel.app.ui.wheel.calendar

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Practice streaks measured in whole local calendar days.
 *
 * @property current consecutive practice days ending today, or ending yesterday
 *   when today has no session yet (an unpracticed today does not break the streak).
 * @property best longest run of consecutive practice days across all history.
 */
data class Streaks(val current: Int, val best: Int)

/**
 * Converts session start timestamps (epoch millis) into the set of local
 * calendar days on which practice happened.
 */
fun practiceDays(startTimesMillis: Iterable<Long>, zone: ZoneId): Set<LocalDate> =
    startTimesMillis.mapTo(HashSet()) { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }

/**
 * Computes current and best streaks from the full set of practice days.
 *
 * Works on [LocalDate] so month, year and leap-day boundaries need no special
 * handling: Dec 31 is followed by Jan 1 and Feb 28 by Feb 29 when it exists.
 */
fun computeStreaks(days: Set<LocalDate>, today: LocalDate): Streaks {
    if (days.isEmpty()) return Streaks(current = 0, best = 0)

    var current = 0
    var cursor = if (today in days) today else today.minusDays(1)
    while (cursor in days) {
        current++
        cursor = cursor.minusDays(1)
    }

    var best = 0
    var run = 0
    var previous: LocalDate? = null
    for (day in days.sorted()) {
        run = if (previous != null && previous.plusDays(1) == day) run + 1 else 1
        if (run > best) best = run
        previous = day
    }

    return Streaks(current = current, best = maxOf(best, current))
}
