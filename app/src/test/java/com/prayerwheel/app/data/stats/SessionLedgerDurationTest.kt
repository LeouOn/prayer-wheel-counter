package com.prayerwheel.app.data.stats

import com.prayerwheel.app.data.model.Session
import com.prayerwheel.app.viewmodel.session.SessionRules
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigInteger

/** The credited duration is stored per row, so debits match credits and resumes skip the idle gap. */
class SessionLedgerDurationTest {

    private val store = FakeLedgerStore()
    private val t0 = 1_700_000_000_000L

    private fun session(rotations: Long, endedAt: Long, key: String? = "k") = Session(
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

    @Test
    fun recordedRow_carriesCreditedDuration_andDeleteDebitsExactlyIt() = runBlocking {
        // A manual log: user-entered 1 h, though endedAt - startedAt says 10 min.
        val row = SessionLedger.recordSession(store, session(5, t0 + 600_000, key = null), 3_600, TEST_NOW).session
        assertEquals(3_600L, row.durationSeconds)
        assertEquals(3_600L, store.stats!!.totalSpinningTimeSeconds)
        SessionLedger.deleteSessionAndDebit(store, row.id)
        assertEquals(0L, store.stats!!.totalSpinningTimeSeconds)
    }

    @Test
    fun resumeAfterIdleGap_creditsOnlyPracticeTime() = runBlocking {
        // 10 min of practice, then the app was killed; resumed 2 h later for 5 more min.
        val original = SessionLedger.recordSession(store, session(10, t0 + 600_000), 600, TEST_NOW).session
        val resumeAt = t0 + 600_000 + 7_200_000
        val base = SessionRules.resumeTimerBase(resumeAt, LifetimeStatsMath.storedDurationSeconds(original))
        val endAt = resumeAt + 300_000
        val duration = SessionRules.endedSessionDurationSeconds(base, endAt)
        assertEquals(900L, duration) // not 2 h 15 min

        SessionLedger.recordSession(store, session(15, endAt), duration, TEST_NOW)
        assertEquals(900L, store.stats!!.totalSpinningTimeSeconds)
        assertEquals(900L, store.sessions.values.single().durationSeconds)
        // Deleting debits exactly what was credited (the old endedAt-startedAt would be 8 100 s).
        SessionLedger.deleteSessionAndDebit(store, original.id)
        assertEquals(0L, store.stats!!.totalSpinningTimeSeconds)
    }

    @Test
    fun legacyRowWithoutDuration_fallsBackToEndMinusStart() {
        assertEquals(600L, LifetimeStatsMath.storedDurationSeconds(session(1, t0 + 600_000)))
        assertEquals(42L, LifetimeStatsMath.storedDurationSeconds(session(1, t0 + 600_000).copy(durationSeconds = 42)))
    }
}
