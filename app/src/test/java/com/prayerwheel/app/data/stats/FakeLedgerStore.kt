package com.prayerwheel.app.data.stats

import com.prayerwheel.app.data.model.LifetimeStats
import com.prayerwheel.app.data.model.Session

/**
 * In-memory [SessionLedgerStore] mirroring the Room DAO semantics used by
 * [com.prayerwheel.app.data.db.dao.LedgerDao] (autoincrement ids, ABORT on a
 * duplicate primary key).
 */
open class FakeLedgerStore : SessionLedgerStore {
    var stats: LifetimeStats? = null
    val sessions = linkedMapOf<Long, Session>()
    private var nextId = 1L

    override suspend fun getStats(): LifetimeStats? = stats
    override suspend fun upsertStats(stats: LifetimeStats) { this.stats = stats }
    override suspend fun deleteStats() { stats = null }

    override suspend fun getSessionById(id: Long): Session? = sessions[id]

    override suspend fun insertSession(session: Session): Long {
        val id = if (session.id == 0L) nextId else session.id
        check(id !in sessions) { "UNIQUE constraint failed: sessions.id" }
        nextId = maxOf(nextId, id + 1)
        sessions[id] = session.copy(id = id)
        return id
    }

    override suspend fun updateSession(session: Session) {
        if (session.id in sessions) sessions[session.id] = session
    }

    override suspend fun deleteSessionById(id: Long) { sessions.remove(id) }
    override suspend fun deleteAllSessions() { sessions.clear() }
}
