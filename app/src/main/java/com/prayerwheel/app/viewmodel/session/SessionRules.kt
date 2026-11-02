package com.prayerwheel.app.viewmodel.session

import com.prayerwheel.app.data.model.Session
import java.math.BigInteger
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Pure session-lifecycle rules extracted from WheelViewModel. Time ("now") and
 * the time zone are always parameters so the rules are deterministic in tests;
 * the ViewModel passes `System.currentTimeMillis()` and `ZoneId.systemDefault()`.
 */
object SessionRules {

    const val LABEL_MORNING = "morning"
    const val LABEL_EVENING = "evening"

    /**
     * T17: whether the most-recent persisted [session] is conservatively resumable.
     * All three conditions must hold:
     *  - recent: `endedAt == null` OR `startedAt` within [recentWindowMs] of [nowMs]
     *  - had motion: `rotationCount > 0`
     *  - interrupted: `dedication == null`
     *
     * Current behavior (pinned by tests): recency is measured from `startedAt`,
     * not `endedAt`, and a session whose dedication was skipped (saved with
     * `dedication == null`) counts as "interrupted".
     */
    fun isResumable(session: Session, nowMs: Long, recentWindowMs: Long): Boolean {
        val isRecent = session.endedAt == null ||
            (nowMs - session.startedAt) <= recentWindowMs
        val hasRotations = session.rotationCount > 0L
        val isInterrupted = session.dedication == null
        return isRecent && hasRotations && isInterrupted
    }

    /**
     * Whether a call to start-session-if-needed should (re)initialize the session:
     * true only when no session is active.
     *
     * An active session is NEVER reset here. Every drag/flick/auto-spin start
     * calls this, so returning true for an in-progress session silently zeroed
     * its rotations, mantras and start time without saving them. (The original
     * rule also returned true whenever the last *dedicated* session had ended
     * more than the merge threshold ago, and that timestamp was never cleared —
     * so after the first dedicated session every subsequent flick wiped the
     * live session.)
     *
     * The "session merge threshold" setting is described in Settings as "how
     * long after a session ends before a new spin counts as a new session", i.e.
     * it may only decide what happens when there is *no* active session. Since
     * an ended session is always persisted and cleared through the dedication
     * flow, there is currently no ended-but-mergeable session to continue, so
     * the threshold has no effect on this decision.
     */
    fun needsNewSession(sessionStartTimeMs: Long?): Boolean = sessionStartTimeMs == null

    /**
     * Duration credited to lifetime stats for a session that ended at
     * [endedAtMs]: whole seconds of `endedAt − startedAt`, never negative.
     * Fixed when the session ends (wheel stopped / End Session), so time spent
     * on the dedication prompt afterwards is never counted as practice.
     */
    fun endedSessionDurationSeconds(startedAtMs: Long, endedAtMs: Long): Long =
        (endedAtMs - startedAtMs).coerceAtLeast(0L) / 1000L

    /** A local calendar day as a half-open `[startMs, endMs)` epoch-millis window. */
    data class DayWindow(val date: LocalDate, val zone: ZoneId, val startMs: Long, val endMs: Long)

    /**
     * Bounds of the local calendar day containing [nowMs] in [zone]. Computed
     * from the current date every time (never by adding 24 h), so DST days
     * are 23/25 h long and a time-zone change yields the new zone's day.
     */
    fun dayWindow(nowMs: Long, zone: ZoneId): DayWindow {
        val date = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        return DayWindow(
            date = date,
            zone = zone,
            startMs = date.atStartOfDay(zone).toInstant().toEpochMilli(),
            endMs = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        )
    }

    /**
     * Base instant for a resumed session's timer: the timer shows
     * `now − base`, so it continues from the [priorDurationSeconds] already
     * practiced instead of counting the idle gap since the original start.
     */
    fun resumeTimerBase(nowMs: Long, priorDurationSeconds: Long): Long =
        nowMs - priorDurationSeconds.coerceAtLeast(0L) * 1000L

    /** Local hour-of-day (0..23) of [epochMillis] in [zone]. */
    fun hourOfDay(epochMillis: Long, zone: ZoneId): Int =
        Instant.ofEpochMilli(epochMillis).atZone(zone).hour

    /**
     * Morning/evening attribution: "morning" iff the local hour is strictly less
     * than [eveningHour]. Shared by the auto-label and Today's Progress rules.
     */
    fun isMorning(epochMillis: Long, eveningHour: Int, zone: ZoneId): Boolean =
        hourOfDay(epochMillis, zone) < eveningHour

    /** T15: auto-suggested session label, or null when auto-labelling is disabled. */
    fun autoLabel(nowMs: Long, eveningHour: Int, zone: ZoneId, enabled: Boolean): String? =
        if (enabled) {
            if (isMorning(nowMs, eveningHour, zone)) LABEL_MORNING else LABEL_EVENING
        } else {
            null
        }

    data class TodayProgress(
        val morningCompleted: Boolean,
        val eveningCompleted: Boolean,
        val mantras: BigInteger,
        val practiceSeconds: Long
    )

    /**
     * Reduces today's sessions into Today's Progress. A session is attributed to
     * morning/evening by its *start* hour. Practice time sums the recorded
     * `durationSeconds` when present (v5+; excludes a resume's idle gap), else `endedAt − startedAt`
     * (open sessions contribute 0; negative spans are ignored) and is truncated
     * to whole seconds once, after summing milliseconds.
     */
    fun todayProgress(sessions: List<Session>, eveningHour: Int, zone: ZoneId): TodayProgress {
        var morning = false
        var evening = false
        var mantras = BigInteger.ZERO
        var practiceMs = 0L
        for (session in sessions) {
            if (isMorning(session.startedAt, eveningHour, zone)) morning = true else evening = true
            mantras = mantras.add(session.totalMantras)
            val recorded = session.durationSeconds
            if (recorded != null) {
                practiceMs += recorded.coerceAtLeast(0L) * 1000L
            } else {
                val endMs = session.endedAt ?: session.startedAt
                if (endMs >= session.startedAt) {
                    practiceMs += (endMs - session.startedAt)
                }
            }
        }
        return TodayProgress(morning, evening, mantras, practiceMs / 1000L)
    }
}
