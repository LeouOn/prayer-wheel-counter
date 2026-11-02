package com.prayerwheel.app.data.stats

import com.prayerwheel.app.data.model.Session
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger

/** "Reset All Data" racing with late session snapshots (in-flight SessionSaveWorker). */
class SessionLedgerWipeTest {

    private val store = FakeLedgerStore()
    private val t0 = 1_700_000_000_000L
    private val wipe = t0 + 600_000L

    private fun session(startedAt: Long, key: String?, rotations: Long = 10) = Session(
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

    @Test
    fun workerSnapshotFromBeforeWipe_landingAfterIt_isDropped() = runBlocking {
        SessionLedger.recordSession(store, session(t0, "a"), 60, nowMs = t0 + 60_000)
        SessionLedger.resetAll(store, nowMs = wipe)
        // The worker for session "b" (started before the wipe) commits after it.
        val late = SessionLedger.recordSession(store, session(t0 + 120_000, "b"), 60, nowMs = wipe + 5)
        assertFalse(late.changed)
        assertTrue(store.sessions.isEmpty())
        assertEquals(BigInteger.ZERO, store.stats!!.totalMantras)
        assertTrue(store.checkpoints.isEmpty())
    }

    @Test
    fun sessionsAfterWipe_andBackdatedManualLogs_areRecorded() = runBlocking {
        SessionLedger.resetAll(store, nowMs = wipe)
        SessionLedger.recordSession(store, session(wipe + 1_000, "c"), 60, nowMs = wipe + 61_000)
        // A manual log (no session key) may legitimately be backdated before the wipe.
        SessionLedger.recordSession(store, session(t0, key = null, rotations = 2), 60, nowMs = wipe + 70_000)
        assertEquals(2, store.sessions.size)
        assertEquals(BigInteger.valueOf(1_200), store.stats!!.totalMantras)
    }

    @Test
    fun lifetimeResetAfterWipe_keepsWipeMarker() = runBlocking {
        SessionLedger.resetAll(store, nowMs = wipe)
        SessionLedger.resetLifetime(store, nowMs = wipe + 1_000)
        assertEquals(wipe, store.stats!!.wipedAt)
        assertFalse(SessionLedger.recordSession(store, session(t0, "late"), 60, nowMs = wipe + 2_000).changed)
    }
}
