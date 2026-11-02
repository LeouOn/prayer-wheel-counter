package com.prayerwheel.app.data.stats

import com.prayerwheel.app.data.db.dao.TimeStats
import com.prayerwheel.app.data.model.MantraStats
import com.prayerwheel.app.data.model.WheelStats
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigInteger

class SessionAggregatesTest {

    private val trillion = BigInteger("1000000000000")

    private fun row(mantra: String = "om", wheel: String? = null, rotations: Long = 1, mantras: BigInteger = BigInteger.ONE) =
        SessionAggregateRow(mantra, wheel, rotations, mantras)

    @Test
    fun byMantra_groupsAndSumsInInsertionOrder() {
        val rows = listOf(
            row("om", rotations = 2, mantras = BigInteger.valueOf(20)),
            row("tara", rotations = 1, mantras = BigInteger.valueOf(5)),
            row("om", rotations = 3, mantras = BigInteger.valueOf(30))
        )
        assertEquals(
            listOf(
                MantraStats("om", 2, 5, BigInteger.valueOf(50)),
                MantraStats("tara", 1, 1, BigInteger.valueOf(5))
            ),
            SessionAggregates.byMantra(rows)
        )
    }

    @Test
    fun totalsBeyondInt64_doNotOverflow() {
        // A 1T-capacity wheel spun 10M times (1e19 mantras, > Long.MAX_VALUE ≈ 9.22e18)
        // plus a further 5e18 manual log: exactly what made SQL SUM() throw.
        val spun = trillion.multiply(BigInteger.valueOf(10_000_000))
        val logged = BigInteger.valueOf(5_000_000_000_000_000_000L)
        val rows = listOf(row(mantras = spun), row(mantras = logged, wheel = "stupa"))
        val expected = spun.add(logged)
        assertEquals(TimeStats(2, expected), SessionAggregates.totals(rows))
        assertEquals(expected, SessionAggregates.byMantra(rows).single().totalMantras)
        assertEquals(
            listOf(WheelStats(null, 1, 1, spun), WheelStats("stupa", 1, 1, logged)),
            SessionAggregates.byWheel(rows)
        )
    }

    @Test
    fun singleValueBeyondInt64_isExact() {
        val huge = BigInteger("123456789012345678901234567890")
        assertEquals(huge.shiftLeft(1), SessionAggregates.totals(listOf(row(mantras = huge), row(mantras = huge))).totalMantras)
    }

    @Test
    fun rotationSums_saturateInsteadOfWrapping() {
        val rows = listOf(row(rotations = Long.MAX_VALUE - 1), row(rotations = 5))
        assertEquals(Long.MAX_VALUE, SessionAggregates.byMantra(rows).single().totalRotations)
    }

    @Test
    fun empty_isZero() {
        assertEquals(TimeStats(0, BigInteger.ZERO), SessionAggregates.totals(emptyList()))
        assertEquals(emptyList<MantraStats>(), SessionAggregates.byMantra(emptyList()))
    }

    @Test
    fun saturatingAdd() {
        assertEquals(3L, LifetimeStatsMath.saturatingAdd(1, 2))
        assertEquals(Long.MAX_VALUE, LifetimeStatsMath.saturatingAdd(Long.MAX_VALUE, 1))
        assertEquals(Long.MIN_VALUE, LifetimeStatsMath.saturatingAdd(Long.MIN_VALUE, -1))
        assertEquals(-1L, LifetimeStatsMath.saturatingAdd(Long.MAX_VALUE, Long.MIN_VALUE))
    }
}
