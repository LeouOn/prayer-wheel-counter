package com.prayerwheel.app.data.stats

import com.prayerwheel.app.data.model.ActiveSessionCheckpoint
import com.prayerwheel.app.data.model.LifetimeStats
import com.prayerwheel.app.data.model.Session
import java.math.BigInteger

/**
 * Storage primitives the [SessionLedger] needs. Implemented by
 * [com.prayerwheel.app.data.db.dao.LedgerDao] (Room) and by an in-memory fake
 * in JVM tests. Implementations do NOT provide atomicity themselves: every
 * multi-step [SessionLedger] operation must run inside one DB transaction
 * (see the `@Transaction` wrappers on `LedgerDao`).
 */
interface SessionLedgerStore {
    suspend fun getStats(): LifetimeStats?
    suspend fun upsertStats(stats: LifetimeStats)
    suspend fun deleteStats()

    suspend fun getSessionById(id: Long): Session?
    suspend fun getSessionByKey(key: String): Session?
    suspend fun insertSession(session: Session): Long
    suspend fun updateSession(session: Session)
    suspend fun deleteSessionById(id: Long)
    suspend fun deleteAllSessions()

    suspend fun getCheckpoints(): List<ActiveSessionCheckpoint>
    suspend fun upsertCheckpoint(checkpoint: ActiveSessionCheckpoint)
    suspend fun deleteCheckpointByKey(key: String)
    suspend fun deleteAllCheckpoints()
}

/**
 * The only code that writes the `sessions` table together with the singleton
 * `lifetime_stats` row (and the `active_session` checkpoint). Each operation
 * is a read-modify-write across tables; callers MUST invoke these through a
 * single database transaction so a crash, a concurrent writer (VM,
 * SessionSaveWorker, Settings reset) or a cancelled coroutine can never leave
 * a session row without its lifetime contribution (or vice versa).
 */
object SessionLedger {

    /**
     * Result of a write: the persisted session (with its row id) and the
     * lifetime row after the write. [changed] is false when the write was a
     * no-op (an older/equal snapshot of an already-recorded session).
     */
    data class Recorded(val session: Session, val stats: LifetimeStats, val changed: Boolean = true)

    private val EMPTY_STATS = LifetimeStats(
        id = 1,
        totalRotations = 0L,
        totalMantras = BigInteger.ZERO,
        sessionsCompleted = 0L,
        firstSessionAt = null,
        totalSpinningTimeSeconds = 0L,
        averageSessionDurationSeconds = 0L
    )

    /**
     * Records a completed session and credits it to lifetime stats.
     *
     * Sessions with a [Session.sessionKey] are upserted by key, so the same
     * live session arriving from several writers (VM save, SessionSaveWorker
     * after `onCleared`, checkpoint recovery, a resumed session being saved
     * again) is counted exactly once:
     *  - no row with that key → insert + credit;
     *  - a row exists and [session] is a newer snapshot (more rotations, or
     *    same rotations and later end) → replace the row in place: debit the
     *    old row, credit the new one (net effect: only the delta is added);
     *  - otherwise (older or identical snapshot) → no-op.
     *
     * Always clears the `active_session` checkpoint for that key.
     */
    suspend fun recordSession(
        store: SessionLedgerStore,
        session: Session,
        durationSeconds: Long,
        nowMs: Long
    ): Recorded {
        val key = session.sessionKey
        if (key != null && isStaleAfterWipe(session, store.getStats())) {
            // A snapshot of a session discarded by "Reset All Data" (e.g. a worker
            // that was already running when the wipe happened): drop it.
            store.deleteCheckpointByKey(key)
            return Recorded(session, store.getStats() ?: EMPTY_STATS, changed = false)
        }
        val existing = key?.let { store.getSessionByKey(it) }
        val result = when {
            existing == null -> insertAndCredit(store, session, durationSeconds, nowMs)
            isNewerSnapshot(session, existing) -> replaceInPlace(store, existing, session, durationSeconds, nowMs)
            else -> Recorded(existing, store.getStats() ?: EMPTY_STATS, changed = false)
        }
        if (key != null) store.deleteCheckpointByKey(key)
        return result
    }

    /**
     * Whether [incoming] supersedes [existing] for the same session key. Counts
     * only ever grow within one session, so more rotations wins; with equal
     * rotations a later end wins (it may carry a dedication or longer time).
     */
    fun isNewerSnapshot(incoming: Session, existing: Session): Boolean =
        incoming.rotationCount > existing.rotationCount ||
            (incoming.rotationCount == existing.rotationCount &&
                (incoming.endedAt ?: incoming.startedAt) > (existing.endedAt ?: existing.startedAt))

    /**
     * Whether [session] is a live-session snapshot (it has a session key) that
     * started before the last "Reset All Data" and therefore must not be
     * recorded. Manual logs have no key and are never affected (they may be
     * backdated legitimately).
     */
    fun isStaleAfterWipe(session: Session, stats: LifetimeStats?): Boolean {
        val wipedAt = stats?.wipedAt ?: return false
        return session.sessionKey != null && session.startedAt < wipedAt
    }

    /**
     * Whether [session] is currently included in the lifetime totals: always,
     * unless the totals were reset after the session was credited. Rows from
     * before v5 (no `recordedAt`) predate any reset that v5+ recorded.
     */
    fun countsTowardLifetime(session: Session, stats: LifetimeStats?): Boolean {
        val resetAt = stats?.resetAt ?: return true
        val recordedAt = session.recordedAt ?: return false
        return recordedAt >= resetAt
    }

    private suspend fun insertAndCredit(
        store: SessionLedgerStore,
        session: Session,
        durationSeconds: Long,
        nowMs: Long
    ): Recorded {
        val row = session.copy(id = 0, recordedAt = nowMs, durationSeconds = durationSeconds)
        val id = store.insertSession(row)
        val stats = LifetimeStatsMath.plusSession(store.getStats(), row, durationSeconds)
        store.upsertStats(stats)
        return Recorded(row.copy(id = id), stats)
    }

    /**
     * Replaces [old] with [replacement] (same row id). If [old] still counts
     * toward the lifetime totals it is debited first; if it was credited
     * before a lifetime reset, the replacement is credited in full and
     * re-stamped as recorded now (so a later delete debits exactly that).
     */
    private suspend fun replaceInPlace(
        store: SessionLedgerStore,
        old: Session,
        replacement: Session,
        durationSeconds: Long,
        nowMs: Long
    ): Recorded {
        val current = store.getStats()
        val oldCounts = countsTowardLifetime(old, current)
        val debited = if (oldCounts && current != null) {
            LifetimeStatsMath.minusSession(current, old, LifetimeStatsMath.storedDurationSeconds(old))
        } else current
        val stats = LifetimeStatsMath.plusSession(debited, replacement, durationSeconds)
        // User-entered fields edited on the stored row survive a snapshot
        // that doesn't carry them.
        val updated = replacement.copy(
            id = old.id,
            sessionKey = old.sessionKey ?: replacement.sessionKey,
            dedication = replacement.dedication ?: old.dedication,
            label = replacement.label ?: old.label,
            recordedAt = if (oldCounts) old.recordedAt ?: nowMs else nowMs,
            durationSeconds = durationSeconds
        )
        store.updateSession(updated)
        store.upsertStats(stats)
        return Recorded(updated, stats)
    }

    /**
     * Deletes the session row [id] and debits it from lifetime stats — unless it
     * was credited before the last lifetime reset (its counts are no longer in
     * the totals; debiting would subtract pre-reset practice from the new total).
     * Returns the deleted row (for undo), or null if it no longer exists —
     * in which case nothing is debited, so a double delete cannot subtract twice.
     * Also drops any checkpoint for the same key so a stale checkpoint can't
     * resurrect the deleted session on the next launch.
     */
    suspend fun deleteSessionAndDebit(store: SessionLedgerStore, id: Long): Session? {
        val existing = store.getSessionById(id) ?: return null
        store.deleteSessionById(id)
        existing.sessionKey?.let { store.deleteCheckpointByKey(it) }
        val stats = store.getStats()
        if (stats != null && countsTowardLifetime(existing, stats)) {
            store.upsertStats(
                LifetimeStatsMath.minusSession(stats, existing, LifetimeStatsMath.storedDurationSeconds(existing))
            )
        }
        return existing
    }

    /**
     * Undo of [deleteSessionAndDebit]: re-inserts [session] under its original
     * id (and original recordedAt) and credits it again — only if it counts
     * toward the current lifetime totals, mirroring the delete. No-op (returns
     * null) if a row with that id or session key already exists, so a double
     * undo cannot credit twice.
     */
    suspend fun restoreSession(store: SessionLedgerStore, session: Session): Recorded? {
        if (session.id != 0L && store.getSessionById(session.id) != null) return null
        if (session.sessionKey != null && store.getSessionByKey(session.sessionKey) != null) return null
        val id = store.insertSession(session)
        val current = store.getStats()
        val stats = if (countsTowardLifetime(session, current)) {
            LifetimeStatsMath.plusSession(current, session, LifetimeStatsMath.storedDurationSeconds(session))
                .also { store.upsertStats(it) }
        } else current ?: EMPTY_STATS
        return Recorded(session.copy(id = id), stats)
    }

    /**
     * Replaces the persisted session [oldId] with [replacement] in place (same
     * row id): debits the old row, credits the replacement, updates the row.
     * If the old row is gone (e.g. deleted in History meanwhile) the
     * replacement is recorded as a new session.
     */
    suspend fun replaceSession(
        store: SessionLedgerStore,
        oldId: Long,
        replacement: Session,
        durationSeconds: Long,
        nowMs: Long
    ): Recorded {
        val old = store.getSessionById(oldId)
            ?: return insertAndCredit(store, replacement, durationSeconds, nowMs)
        return replaceInPlace(store, old, replacement, durationSeconds, nowMs)
    }

    /**
     * Returns the stable session key of row [id], assigning [newKey] first if
     * the row predates session keys. Null if the row no longer exists.
     * Used when resuming a saved session so its next save replaces the row.
     */
    suspend fun ensureSessionKey(store: SessionLedgerStore, id: Long, newKey: String): String? {
        val row = store.getSessionById(id) ?: return null
        row.sessionKey?.let { return it }
        store.updateSession(row.copy(sessionKey = newKey))
        return newKey
    }

    /**
     * Records every leftover `active_session` checkpoint (the app died while a
     * session was live) as a completed session with no dedication, crediting
     * the live-timer duration it carried, and clears them. Upserting by key
     * makes this safe to race with SessionSaveWorker or to run twice.
     * Returns only the writes that changed something.
     */
    suspend fun recoverCheckpoints(store: SessionLedgerStore, nowMs: Long): List<Recorded> =
        store.getCheckpoints().mapNotNull { checkpoint ->
            if (checkpoint.rotationCount <= 0L) {
                store.deleteCheckpointByKey(checkpoint.sessionKey)
                null
            } else {
                recordSession(store, checkpoint.toSession(), checkpoint.durationSeconds, nowMs)
                    .takeIf { it.changed }
            }
        }

    /**
     * Zeroes lifetime counters, keeping every session row, and stamps
     * [LifetimeStats.resetAt] so sessions credited before now are no longer
     * debited/credited by delete/undo.
     */
    suspend fun resetLifetime(store: SessionLedgerStore, nowMs: Long): LifetimeStats {
        val zero = EMPTY_STATS.copy(resetAt = nowMs, wipedAt = store.getStats()?.wipedAt)
        store.upsertStats(zero)
        return zero
    }

    /**
     * Deletes every session and live-session checkpoint and zeroes the
     * lifetime row, stamping [LifetimeStats.wipedAt] so a session snapshot
     * from before the wipe that arrives later (in-flight SessionSaveWorker)
     * is dropped instead of resurrected.
     */
    suspend fun resetAll(store: SessionLedgerStore, nowMs: Long) {
        store.deleteAllSessions()
        store.deleteAllCheckpoints()
        store.upsertStats(EMPTY_STATS.copy(resetAt = nowMs, wipedAt = nowMs))
    }
}
