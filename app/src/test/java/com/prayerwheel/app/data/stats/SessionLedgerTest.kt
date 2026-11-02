package com.prayerwheel.app.data.stats

import com.prayerwheel.app.data.model.Session
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger

class SessionLedgerTest {

    private val store = FakeLedgerStore()

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

    @Test
    fun record_insertsRowAndCreditsLifetime() = runBlocking {
        val r = SessionLedger.recordSession(store, session(), 60)
        assertEquals(1, store.sessions.size)
        assertEquals(r.session, store.sessions[r.session.id])
        assertEquals(BigInteger.valueOf(1_000), store.stats!!.totalMantras)
        assertEquals(1L, store.stats!!.sessionsCompleted)
        assertEquals(r.stats, store.stats)
    }

    @Test
    fun deleteAndDebit_removesRowAndDebits_onlyOnce() = runBlocking {
        val a = SessionLedger.recordSession(store, session(rotations = 10), 60).session
        SessionLedger.recordSession(store, session(rotations = 5), 60)
        val deleted = SessionLedger.deleteSessionAndDebit(store, a.id)
        assertEquals(a, deleted)
        assertEquals(BigInteger.valueOf(500), store.stats!!.totalMantras)
        // A second delete of the same id must not debit again.
        assertNull(SessionLedger.deleteSessionAndDebit(store, a.id))
        assertEquals(BigInteger.valueOf(500), store.stats!!.totalMantras)
        assertEquals(1L, store.stats!!.sessionsCompleted)
    }

    @Test
    fun restore_isInverseOfDelete_andIdempotent() = runBlocking {
        val a = SessionLedger.recordSession(store, session(), 60).session
        val before = store.stats
        val deleted = SessionLedger.deleteSessionAndDebit(store, a.id)!!
        assertNotNull(SessionLedger.restoreSession(store, deleted))
        assertEquals(before, store.stats)
        assertEquals(a, store.sessions[a.id])
        // Double undo is a no-op.
        assertNull(SessionLedger.restoreSession(store, deleted))
        assertEquals(before, store.stats)
    }

    @Test
    fun replace_updatesInPlace_andCreditsOnlyTheDelta() = runBlocking {
        SessionLedger.recordSession(store, session(rotations = 1), 60)
        val old = SessionLedger.recordSession(store, session(rotations = 10), 60).session
        val replacement = session(rotations = 25, endedAt = old.startedAt + 120_000L)
        val r = SessionLedger.replaceSession(store, old.id, replacement, 120)
        assertEquals(old.id, r.session.id)
        assertEquals(2, store.sessions.size)
        assertEquals(25L, store.sessions[old.id]!!.rotationCount)
        assertEquals(BigInteger.valueOf(2_600), store.stats!!.totalMantras)
        assertEquals(26L, store.stats!!.totalRotations)
        assertEquals(2L, store.stats!!.sessionsCompleted)
        assertEquals(180L, store.stats!!.totalSpinningTimeSeconds)
    }

    @Test
    fun replace_missingOldRow_recordsAsNew() = runBlocking {
        val r = SessionLedger.replaceSession(store, 99L, session(rotations = 3), 10)
        assertEquals(1, store.sessions.size)
        assertEquals(BigInteger.valueOf(300), store.stats!!.totalMantras)
        assertEquals(store.sessions.values.single(), r.session)
    }

    @Test
    fun resetLifetime_keepsSessions_resetAll_wipesEverything() = runBlocking {
        SessionLedger.recordSession(store, session(), 60)
        SessionLedger.resetLifetime(store)
        assertEquals(1, store.sessions.size)
        assertEquals(BigInteger.ZERO, store.stats!!.totalMantras)
        assertEquals(0L, store.stats!!.sessionsCompleted)
        SessionLedger.resetAll(store)
        assertTrue(store.sessions.isEmpty())
        assertNull(store.stats)
    }
}
