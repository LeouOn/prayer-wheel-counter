package com.prayerwheel.app.data.stats

import com.prayerwheel.app.data.model.Session
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.math.BigInteger

/** "Reset lifetime stats" (sessions kept) followed by deletes, undos, logs and resumes. */
class SessionLedgerResetTest {

    private val store = FakeLedgerStore()
    private val day = 86_400_000L
    private val t0 = 1_700_000_000_000L

    private fun session(rotations: Long, startedAt: Long, key: String? = null) = Session(
        startedAt = startedAt,
        endedAt = startedAt + 60_000L,
        rotationCount = rotations,
        mantrasPerRotation = 100,
        totalMantras = BigInteger.valueOf(rotations * 100),
        mantraId = "om",
        dedication = null,
        mode = "MANUAL",
        sessionKey = key
    )

    private fun mantras() = store.stats!!.totalMantras

    @Test
    fun deletingPreResetSession_doesNotDebitNewTotals() = runBlocking {
        val old = SessionLedger.recordSession(store, session(50, t0), 60, nowMs = t0 + 60_000).session
        SessionLedger.resetLifetime(store, nowMs = t0 + day)
        SessionLedger.recordSession(store, session(3, t0 + 2 * day), 60, nowMs = t0 + 2 * day)
        assertEquals(BigInteger.valueOf(300), mantras())

        SessionLedger.deleteSessionAndDebit(store, old.id)
        // Previously: 300 - 5000 clamped to 0, erasing the new post-reset practice.
        assertEquals(BigInteger.valueOf(300), mantras())
        assertEquals(1L, store.stats!!.sessionsCompleted)
        assertEquals(60L, store.stats!!.totalSpinningTimeSeconds)
    }

    @Test
    fun undoOfPreResetDelete_doesNotCreditEither() = runBlocking {
        val old = SessionLedger.recordSession(store, session(50, t0), 60, nowMs = t0).session
        SessionLedger.resetLifetime(store, nowMs = t0 + day)
        val deleted = SessionLedger.deleteSessionAndDebit(store, old.id)!!
        assertNotNull(SessionLedger.restoreSession(store, deleted))
        assertEquals(BigInteger.ZERO, mantras())
        assertEquals(0L, store.stats!!.sessionsCompleted)
    }

    @Test
    fun postResetSession_isDebitedNormally_andUndoCreditsAgain() = runBlocking {
        SessionLedger.resetLifetime(store, nowMs = t0)
        val s = SessionLedger.recordSession(store, session(7, t0 + day), 60, nowMs = t0 + day).session
        val deleted = SessionLedger.deleteSessionAndDebit(store, s.id)!!
        assertEquals(BigInteger.ZERO, mantras())
        SessionLedger.restoreSession(store, deleted)
        assertEquals(BigInteger.valueOf(700), mantras())
    }

    @Test
    fun backdatedLogAfterReset_isCountedAndDebitedNormally() = runBlocking {
        // Logged after the reset, but for a practice day before it: it was credited
        // to the new totals, so deleting it must debit them (keyed on recordedAt,
        // not startedAt).
        SessionLedger.resetLifetime(store, nowMs = t0 + day)
        val log = SessionLedger.recordSession(store, session(4, t0), 60, nowMs = t0 + 2 * day).session
        assertEquals(BigInteger.valueOf(400), mantras())
        assertEquals(t0, store.stats!!.firstSessionAt)
        SessionLedger.deleteSessionAndDebit(store, log.id)
        assertEquals(BigInteger.ZERO, mantras())
    }

    @Test
    fun legacyRowsWithoutRecordedAt_countUntilAReset() = runBlocking {
        val legacy = session(9, t0).copy(id = 5)
        store.sessions[5] = legacy // pre-v5 row: recordedAt == null
        store.stats = LifetimeStatsMath.plusSession(null, legacy, 60)
        SessionLedger.deleteSessionAndDebit(store, 5)
        assertEquals(BigInteger.ZERO, mantras())

        store.sessions[5] = legacy
        SessionLedger.resetLifetime(store, nowMs = t0 + day)
        SessionLedger.recordSession(store, session(2, t0 + 2 * day), 60, nowMs = t0 + 2 * day)
        SessionLedger.deleteSessionAndDebit(store, 5)
        assertEquals(BigInteger.valueOf(200), mantras())
    }

    @Test
    fun resumingPreResetSession_creditsReplacementInFull_thenDebitsExactlyThat() = runBlocking {
        val old = SessionLedger.recordSession(store, session(10, t0, key = "k"), 60, nowMs = t0).session
        SessionLedger.resetLifetime(store, nowMs = t0 + 1_000)
        val r = SessionLedger.recordSession(store, session(12, t0, key = "k"), 90, nowMs = t0 + 2_000)
        assertEquals(old.id, r.session.id)
        assertEquals(BigInteger.valueOf(1_200), mantras())
        assertEquals(t0 + 2_000, r.session.recordedAt)
        SessionLedger.deleteSessionAndDebit(store, old.id)
        assertEquals(BigInteger.ZERO, mantras())
    }

    @Test
    fun firstSessionAt_isRefilledAfterReset() = runBlocking {
        SessionLedger.recordSession(store, session(1, t0), 60, nowMs = t0)
        SessionLedger.resetLifetime(store, nowMs = t0 + day)
        assertEquals(null, store.stats!!.firstSessionAt)
        SessionLedger.recordSession(store, session(1, t0 + 2 * day), 60, nowMs = t0 + 2 * day)
        assertEquals(t0 + 2 * day, store.stats!!.firstSessionAt)
        assertEquals(t0 + day, store.stats!!.resetAt)
    }
}
