package com.prayerwheel.app.data.stats

import com.prayerwheel.app.data.model.ActiveSessionCheckpoint
import com.prayerwheel.app.data.model.Session
import com.prayerwheel.app.viewmodel.session.SessionRules
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger

/** Resume must restore from the row's current state, never from a stale offered copy. */
class SessionLedgerResumeTest {

    private val store = FakeLedgerStore()
    private val t0 = 1_700_000_000_000L

    private fun session(rotations: Long, endedAt: Long, key: String? = "K") = Session(
        startedAt = t0,
        endedAt = endedAt,
        rotationCount = rotations,
        mantrasPerRotation = 100,
        totalMantras = BigInteger.valueOf(rotations * 100),
        mantraId = "om",
        dedication = null,
        mode = "MANUAL",
        sessionKey = key
    )

    private fun checkpoint(rotations: Long, updatedAt: Long) = ActiveSessionCheckpoint(
        sessionKey = "K", startedAt = t0, updatedAt = updatedAt, rotationCount = rotations,
        mantrasPerRotation = 100, totalMantras = BigInteger.valueOf(rotations * 100), mantraId = "om",
        mode = "MANUAL", averageRpm = 0f, peakRpm = 0f, intention = null, sessionGoal = null,
        wheelId = null, label = null, durationSeconds = (updatedAt - t0) / 1000
    )

    @Test
    fun staleOfferedCopy_isIgnored_resumeRestoresCurrentRow() = runBlocking {
        // Offered copy R has N = 10; the session was resumed, spun M = 5 and saved meanwhile.
        val offered = SessionLedger.recordSession(store, session(10, t0 + 60_000), 60, TEST_NOW).session
        SessionLedger.recordSession(store, session(15, t0 + 120_000), 120, TEST_NOW)

        val row = SessionLedger.prepareResume(store, offered.id, "unused", TEST_NOW)!!
        assertEquals(15L, row.rotationCount) // not the offered 10
        assertEquals("K", row.sessionKey)

        // Resuming from it and spinning M' = 3 more ends at 18: nothing lost.
        SessionLedger.recordSession(store, session(18, t0 + 200_000), 200, TEST_NOW)
        assertEquals(BigInteger.valueOf(1_800), store.stats!!.totalMantras)
        assertEquals(1, store.sessions.size)
    }

    @Test
    fun newerCheckpointForKey_isFoldedInBeforeResuming() = runBlocking {
        // Recovery recorded checkpoint C (10); a newer snapshot W (14) is still a checkpoint.
        val row0 = SessionLedger.recordSession(store, session(10, t0 + 60_000), 60, TEST_NOW).session
        store.upsertCheckpoint(checkpoint(14, t0 + 90_000))

        val row = SessionLedger.prepareResume(store, row0.id, "unused", TEST_NOW)!!
        assertEquals(14L, row.rotationCount)
        assertEquals(BigInteger.valueOf(1_400), store.stats!!.totalMantras)
        assertTrue(store.checkpoints.isEmpty())
    }

    @Test
    fun olderCheckpointForKey_doesNotRollBack() = runBlocking {
        val row0 = SessionLedger.recordSession(store, session(14, t0 + 90_000), 90, TEST_NOW).session
        store.upsertCheckpoint(checkpoint(10, t0 + 60_000))
        assertEquals(14L, SessionLedger.prepareResume(store, row0.id, "unused", TEST_NOW)!!.rotationCount)
        assertTrue(store.checkpoints.isEmpty())
    }

    @Test
    fun legacyRow_getsAKey_missingRow_isNull() = runBlocking {
        val legacy = SessionLedger.recordSession(store, session(3, t0 + 10_000, key = null), 10, TEST_NOW).session
        assertEquals("new-key", SessionLedger.prepareResume(store, legacy.id, "new-key", TEST_NOW)!!.sessionKey)
        assertNull(SessionLedger.prepareResume(store, 999L, "x", TEST_NOW))
    }

    @Test
    fun deletingTheLiveResumedRow_mustDiscardTheLiveSession() = runBlocking {
        // Resumed R (key K, 10 rot); user deletes R in History while still spinning.
        val r = SessionLedger.recordSession(store, session(10, t0 + 60_000), 60, TEST_NOW).session
        assertTrue(SessionRules.isDeletingLiveSession(r.sessionKey, liveKey = "K"))
        SessionLedger.deleteSessionAndDebit(store, r.id)
        assertEquals(BigInteger.ZERO, store.stats!!.totalMantras)
        // Had the live session been kept, its next save (10 + 5) would re-insert
        // and re-credit the deleted 10 rotations:
        SessionLedger.recordSession(store, session(15, t0 + 120_000), 120, TEST_NOW)
        assertEquals(BigInteger.valueOf(1_500), store.stats!!.totalMantras)
        // ...which is why the VM discards it. Other rows are unaffected by the rule:
        assertFalse(SessionRules.isDeletingLiveSession("other", liveKey = "K"))
        assertFalse(SessionRules.isDeletingLiveSession(null, liveKey = null))
    }

    @Test
    fun offerGuard_neverWhileLive_neverForTheLiveKey() {
        assertTrue(SessionRules.canOfferResume("K", null, hasLiveSession = false))
        assertTrue(SessionRules.canOfferResume(null, null, hasLiveSession = false))
        assertFalse(SessionRules.canOfferResume("K", "K", hasLiveSession = false))
        assertFalse(SessionRules.canOfferResume("K", "other", hasLiveSession = true))
    }
}
