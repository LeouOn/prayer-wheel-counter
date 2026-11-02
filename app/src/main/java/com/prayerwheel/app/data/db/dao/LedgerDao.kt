package com.prayerwheel.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.prayerwheel.app.data.model.ActiveSessionCheckpoint
import com.prayerwheel.app.data.model.LifetimeStats
import com.prayerwheel.app.data.model.Session
import com.prayerwheel.app.data.stats.SessionLedger
import com.prayerwheel.app.data.stats.SessionLedgerStore

/**
 * Transactional writes that touch `sessions`, `lifetime_stats` and the
 * `active_session` checkpoint.
 *
 * Every public `@Transaction` method runs the corresponding [SessionLedger]
 * operation inside ONE Room transaction, so the session row and its lifetime
 * contribution are committed (or rolled back) together, and concurrent writers
 * — the ViewModel, [com.prayerwheel.app.work.SessionSaveWorker] and the
 * Settings resets — are serialized by SQLite rather than by an in-memory
 * mutex that only one of them could see.
 *
 * Callers outside this DAO should use only the `@Transaction` methods; the
 * [SessionLedgerStore] primitives are not atomic on their own.
 */
@Dao
abstract class LedgerDao : SessionLedgerStore {

    // ── SessionLedgerStore primitives (not atomic on their own) ────────────

    @Query("SELECT * FROM lifetime_stats WHERE id = 1")
    abstract override suspend fun getStats(): LifetimeStats?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract override suspend fun upsertStats(stats: LifetimeStats)

    @Query("DELETE FROM lifetime_stats")
    abstract override suspend fun deleteStats()

    @Query("SELECT * FROM sessions WHERE id = :id")
    abstract override suspend fun getSessionById(id: Long): Session?

    @Query("SELECT * FROM sessions WHERE session_key = :key")
    abstract override suspend fun getSessionByKey(key: String): Session?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract override suspend fun insertSession(session: Session): Long

    @Update
    abstract override suspend fun updateSession(session: Session)

    @Query("DELETE FROM sessions WHERE id = :id")
    abstract override suspend fun deleteSessionById(id: Long)

    @Query("DELETE FROM sessions")
    abstract override suspend fun deleteAllSessions()

    @Query("SELECT * FROM active_session ORDER BY startedAt")
    abstract override suspend fun getCheckpoints(): List<ActiveSessionCheckpoint>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract override suspend fun upsertCheckpoint(checkpoint: ActiveSessionCheckpoint)

    @Query("DELETE FROM active_session WHERE sessionKey = :key")
    abstract override suspend fun deleteCheckpointByKey(key: String)

    @Query("DELETE FROM active_session")
    abstract override suspend fun deleteAllCheckpoints()

    // ── Atomic operations ──────────────────────────────────────────────────

    @Transaction
    open suspend fun recordSession(session: Session, durationSeconds: Long): SessionLedger.Recorded =
        SessionLedger.recordSession(this, session, durationSeconds, System.currentTimeMillis())

    @Transaction
    open suspend fun deleteSessionAndDebit(id: Long): Session? =
        SessionLedger.deleteSessionAndDebit(this, id)

    @Transaction
    open suspend fun restoreSession(session: Session): SessionLedger.Recorded? =
        SessionLedger.restoreSession(this, session)

    @Transaction
    open suspend fun replaceSession(oldId: Long, replacement: Session, durationSeconds: Long): SessionLedger.Recorded =
        SessionLedger.replaceSession(this, oldId, replacement, durationSeconds, System.currentTimeMillis())

    @Transaction
    open suspend fun ensureSessionKey(id: Long, newKey: String): String? =
        SessionLedger.ensureSessionKey(this, id, newKey)

    @Transaction
    open suspend fun recoverCheckpoints(): List<SessionLedger.Recorded> =
        SessionLedger.recoverCheckpoints(this, System.currentTimeMillis())

    /** Single-statement upsert of the live-session checkpoint (atomic on its own). */
    suspend fun saveCheckpoint(checkpoint: ActiveSessionCheckpoint) = upsertCheckpoint(checkpoint)

    @Transaction
    open suspend fun resetLifetime(): LifetimeStats =
        SessionLedger.resetLifetime(this, System.currentTimeMillis())

    @Transaction
    open suspend fun resetAll() =
        SessionLedger.resetAll(this, System.currentTimeMillis())
}
