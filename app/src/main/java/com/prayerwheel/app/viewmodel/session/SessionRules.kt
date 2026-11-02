package com.prayerwheel.app.viewmodel.session

import com.prayerwheel.app.data.model.Session
import java.math.BigInteger
import java.time.Instant
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
     * Whether a call to start-session-if-needed should (re)initialize the session.
     * True when no session is active, or when the last *dedicated* session ended
     * more than [mergeThresholdMs] ago.
     *
     * Current behavior (pinned by tests): the second clause does not look at
     * whether a session is currently active, so an active session is reported
     * as needing a fresh start once [lastSessionEndTimeMs] is stale.
     */
    fun needsNewSession(
        sessionStartTimeMs: Long?,
        lastSessionEndTimeMs: Long?,
        nowMs: Long,
        mergeThresholdMs: Long
    ): Boolean =
        sessionStartTimeMs == null ||
            (lastSessionEndTimeMs != null && nowMs - lastSessionEndTimeMs > mergeThresholdMs)

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
     * morning/evening by its *start* hour. Practice time sums `endedAt − startedAt`
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
            val endMs = session.endedAt ?: session.startedAt
            if (endMs >= session.startedAt) {
                practiceMs += (endMs - session.startedAt)
            }
        }
        return TodayProgress(morning, evening, mantras, practiceMs / 1000L)
    }
}
