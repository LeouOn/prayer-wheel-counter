package com.prayerwheel.app.data.stats

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
    suspend fun insertSession(session: Session): Long
    suspend fun updateSession(session: Session)
    suspend fun deleteSessionById(id: Long)
    suspend fun deleteAllSessions()
}

/**
 * The only code that writes the `sessions` table together with the singleton
 * `lifetime_stats` row. Each operation is a read-modify-write across both
 * tables; callers MUST invoke these through a single database transaction so
 * a crash, a concurrent writer (VM, SessionSaveWorker, Settings reset) or a
 * cancelled coroutine can never leave a session row without its lifetime
 * contribution (or vice versa).
 */
object SessionLedger {

    /** Result of a write: the persisted session (with its row id) and the new lifetime row. */
    data class Recorded(val session: Session, val stats: LifetimeStats)

    /** Inserts a new completed session and credits it to lifetime stats. */
    suspend fun recordSession(store: SessionLedgerStore, session: Session, durationSeconds: Long): Recorded {
        val id = store.insertSession(session.copy(id = 0))
        val stats = LifetimeStatsMath.plusSession(store.getStats(), session, durationSeconds)
        store.upsertStats(stats)
        return Recorded(session.copy(id = id), stats)
    }

    /**
     * Deletes the session row [id] and debits it from lifetime stats.
     * Returns the deleted row (for undo), or null if it no longer exists —
     * in which case nothing is debited, so a double delete cannot subtract twice.
     */
    suspend fun deleteSessionAndDebit(store: SessionLedgerStore, id: Long): Session? {
        val existing = store.getSessionById(id) ?: return null
        store.deleteSessionById(id)
        store.getStats()?.let { stats ->
            store.upsertStats(
                LifetimeStatsMath.minusSession(stats, existing, LifetimeStatsMath.storedDurationSeconds(existing))
            )
        }
        return existing
    }

    /**
     * Undo of [deleteSessionAndDebit]: re-inserts [session] under its original
     * id and credits it again. No-op (returns null) if a row with that id
     * already exists, so a double undo cannot credit twice.
     */
    suspend fun restoreSession(store: SessionLedgerStore, session: Session): Recorded? {
        if (session.id != 0L && store.getSessionById(session.id) != null) return null
        val id = store.insertSession(session)
        val stats = LifetimeStatsMath.plusSession(
            store.getStats(), session, LifetimeStatsMath.storedDurationSeconds(session)
        )
        store.upsertStats(stats)
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
        durationSeconds: Long
    ): Recorded {
        val old = store.getSessionById(oldId)
            ?: return recordSession(store, replacement, durationSeconds)
        val debited = store.getStats()?.let {
            LifetimeStatsMath.minusSession(it, old, LifetimeStatsMath.storedDurationSeconds(old))
        }
        val stats = LifetimeStatsMath.plusSession(debited, replacement, durationSeconds)
        val updated = replacement.copy(id = old.id)
        store.updateSession(updated)
        store.upsertStats(stats)
        return Recorded(updated, stats)
    }

    /** Zeroes lifetime counters, keeping every session row. */
    suspend fun resetLifetime(store: SessionLedgerStore): LifetimeStats {
        val zero = LifetimeStats(
            id = 1,
            totalRotations = 0L,
            totalMantras = BigInteger.ZERO,
            sessionsCompleted = 0L,
            firstSessionAt = null,
            totalSpinningTimeSeconds = 0L,
            averageSessionDurationSeconds = 0L
        )
        store.upsertStats(zero)
        return zero
    }

    /** Deletes every session and the lifetime row. */
    suspend fun resetAll(store: SessionLedgerStore) {
        store.deleteAllSessions()
        store.deleteStats()
    }
}
