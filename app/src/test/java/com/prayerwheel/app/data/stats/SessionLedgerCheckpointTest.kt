package com.prayerwheel.app.data.stats

import com.prayerwheel.app.data.model.ActiveSessionCheckpoint
import com.prayerwheel.app.data.model.Session
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger

/**
 * Process-death durability: checkpoints, stable session keys, recovery,
 * resume-in-place, and the "never double count" guarantees between the VM
 * save, SessionSaveWorker and checkpoint recovery.
 */
class SessionLedgerCheckpointTest {

    private val store = FakeLedgerStore()
    private val t0 = 1_700_000_000_000L

    private fun checkpoint(
        key: String = "k1",
        rotations: Long = 10,
        updatedAt: Long = t0 + 30_000,
        durationSeconds: Long = 30
    ) = ActiveSessionCheckpoint(
        sessionKey = key,
        startedAt = t0,
        updatedAt = updatedAt,
        rotationCount = rotations,
        mantrasPerRotation = 100,
        totalMantras = BigInteger.valueOf(rotations * 100),
        mantraId = "om",
        mode = "MANUAL",
        averageRpm = 30f,
        peakRpm = 40f,
        intention = null,
        sessionGoal = null,
        wheelId = null,
        label = "morning",
        durationSeconds = durationSeconds
    )

    private fun live(key: String? = "k1", rotations: Long = 10, endedAt: Long = t0 + 30_000, dedication: String? = null) =
        Session(
            startedAt = t0,
            endedAt = endedAt,
            rotationCount = rotations,
            mantrasPerRotation = 100,
            totalMantras = BigInteger.valueOf(rotations * 100),
            mantraId = "om",
            dedication = dedication,
            mode = "MANUAL",
            sessionKey = key
        )

    private fun lifetimeMantras() = store.stats?.totalMantras ?: BigInteger.ZERO

    @Test
    fun sameKeyRecordedTwice_countsOnce() = runBlocking {
        SessionLedger.recordSession(store, live(), 30, TEST_NOW)
        val second = SessionLedger.recordSession(store, live(), 30, TEST_NOW)
        assertFalse(second.changed)
        assertEquals(1, store.sessions.size)
        assertEquals(BigInteger.valueOf(1_000), lifetimeMantras())
        assertEquals(1L, store.stats!!.sessionsCompleted)
    }

    @Test
    fun newerSnapshot_replacesInPlace_olderSnapshot_isIgnored() = runBlocking {
        val first = SessionLedger.recordSession(store, live(rotations = 10), 30, TEST_NOW).session
        SessionLedger.recordSession(store, live(rotations = 15, endedAt = t0 + 60_000), 60, TEST_NOW)
        assertEquals(1, store.sessions.size)
        assertEquals(first.id, store.sessions.values.single().id)
        assertEquals(BigInteger.valueOf(1_500), lifetimeMantras())
        assertEquals(1L, store.stats!!.sessionsCompleted)
        // A stale snapshot arriving late (e.g. an old checkpoint) must not roll back.
        val stale = SessionLedger.recordSession(store, live(rotations = 12, endedAt = t0 + 45_000), 45, TEST_NOW)
        assertFalse(stale.changed)
        assertEquals(BigInteger.valueOf(1_500), lifetimeMantras())
        assertEquals(15L, store.sessions.values.single().rotationCount)
    }

    @Test
    fun record_clearsOnlyItsOwnCheckpoint() = runBlocking {
        store.upsertCheckpoint(checkpoint(key = "k1"))
        store.upsertCheckpoint(checkpoint(key = "k2"))
        SessionLedger.recordSession(store, live(key = "k1"), 30, TEST_NOW)
        assertEquals(setOf("k2"), store.checkpoints.keys)
    }

    @Test
    fun recovery_recordsCheckpointAsCompletedSession_once() = runBlocking {
        store.upsertCheckpoint(checkpoint(rotations = 7, updatedAt = t0 + 99_000, durationSeconds = 99))
        val recovered = SessionLedger.recoverCheckpoints(store, TEST_NOW)
        assertEquals(1, recovered.size)
        val row = store.sessions.values.single()
        assertEquals("k1", row.sessionKey)
        assertEquals(t0 + 99_000, row.endedAt)
        assertNull(row.dedication)
        assertEquals("morning", row.label)
        assertEquals(BigInteger.valueOf(700), lifetimeMantras())
        assertEquals(99L, store.stats!!.totalSpinningTimeSeconds)
        assertTrue(store.checkpoints.isEmpty())
        // Second launch: nothing left to recover, nothing double-counted.
        assertTrue(SessionLedger.recoverCheckpoints(store, TEST_NOW).isEmpty())
        assertEquals(BigInteger.valueOf(700), lifetimeMantras())
    }

    @Test
    fun workerThenRecovery_sameSession_countedOnceWithNewestCounts() = runBlocking {
        // onCleared enqueued the worker with the newest snapshot; the checkpoint
        // (15 s older) survived because the process died before recovery ran.
        store.upsertCheckpoint(checkpoint(rotations = 10, updatedAt = t0 + 30_000))
        SessionLedger.recordSession(store, live(rotations = 12, endedAt = t0 + 40_000), 40, TEST_NOW) // worker
        SessionLedger.recoverCheckpoints(store, TEST_NOW)
        assertEquals(1, store.sessions.size)
        assertEquals(BigInteger.valueOf(1_200), lifetimeMantras())
    }

    @Test
    fun recoveryThenWorker_sameSession_countedOnceWithNewestCounts() = runBlocking {
        store.upsertCheckpoint(checkpoint(rotations = 10, updatedAt = t0 + 30_000))
        SessionLedger.recoverCheckpoints(store, TEST_NOW)
        SessionLedger.recordSession(store, live(rotations = 12, endedAt = t0 + 40_000), 40, TEST_NOW) // worker
        assertEquals(1, store.sessions.size)
        assertEquals(BigInteger.valueOf(1_200), lifetimeMantras())
        assertEquals(1L, store.stats!!.sessionsCompleted)
        assertEquals(40L, store.stats!!.totalSpinningTimeSeconds)
    }

    @Test
    fun zeroRotationCheckpoint_isDiscarded() = runBlocking {
        store.upsertCheckpoint(checkpoint(rotations = 0))
        assertTrue(SessionLedger.recoverCheckpoints(store, TEST_NOW).isEmpty())
        assertTrue(store.sessions.isEmpty())
        assertTrue(store.checkpoints.isEmpty())
    }

    @Test
    fun resume_keepsRow_andNextSaveReplacesInPlace() = runBlocking {
        // A legacy (pre-v5, keyless) interrupted session.
        val original = SessionLedger.recordSession(store, live(key = null, rotations = 10), 30, TEST_NOW).session
        val before = lifetimeMantras()

        val key = SessionLedger.ensureSessionKey(store, original.id, "resume-key")!!
        assertEquals("resume-key", key)
        // Resuming neither deletes the row nor debits it: a process death now loses nothing.
        assertEquals(before, lifetimeMantras())
        assertEquals(1, store.sessions.size)
        // Idempotent: an existing key is kept.
        assertEquals("resume-key", SessionLedger.ensureSessionKey(store, original.id, "other"))

        // The process dies mid-resume; the checkpoint carries 14 rotations.
        store.upsertCheckpoint(checkpoint(key = key, rotations = 14, updatedAt = t0 + 120_000, durationSeconds = 120))
        SessionLedger.recoverCheckpoints(store, TEST_NOW)
        assertEquals(1, store.sessions.size)
        assertEquals(original.id, store.sessions.values.single().id)
        assertEquals(BigInteger.valueOf(1_400), lifetimeMantras())
        assertEquals(1L, store.stats!!.sessionsCompleted)
    }

    @Test
    fun ensureSessionKey_missingRow_isNull() = runBlocking {
        assertNull(SessionLedger.ensureSessionKey(store, 42L, "k"))
    }

    @Test
    fun dedicationAndLabel_onStoredRow_surviveNewerSnapshotWithoutThem() = runBlocking {
        SessionLedger.recordSession(store, live(rotations = 10, dedication = "all beings").copy(label = "evening"), 30, TEST_NOW)
        SessionLedger.recordSession(store, live(rotations = 11, endedAt = t0 + 50_000), 50, TEST_NOW)
        val row = store.sessions.values.single()
        assertEquals("all beings", row.dedication)
        assertEquals("evening", row.label)
        assertEquals(11L, row.rotationCount)
    }

    @Test
    fun deletingSession_dropsItsCheckpoint_soRecoveryCannotResurrectIt() = runBlocking {
        val row = SessionLedger.recordSession(store, live(), 30, TEST_NOW).session
        store.upsertCheckpoint(checkpoint()) // a late/stale checkpoint write
        SessionLedger.deleteSessionAndDebit(store, row.id)
        assertTrue(SessionLedger.recoverCheckpoints(store, TEST_NOW).isEmpty())
        assertTrue(store.sessions.isEmpty())
        assertEquals(BigInteger.ZERO, lifetimeMantras())
    }

    @Test
    fun resetAll_clearsCheckpoints() = runBlocking {
        store.upsertCheckpoint(checkpoint())
        SessionLedger.resetAll(store)
        assertTrue(store.checkpoints.isEmpty())
        assertTrue(SessionLedger.recoverCheckpoints(store, TEST_NOW).isEmpty())
    }
}
