package com.prayerwheel.app.data.stats

import com.prayerwheel.app.data.db.dao.TimeStats
import com.prayerwheel.app.data.model.MantraStats
import com.prayerwheel.app.data.model.WheelStats
import java.math.BigInteger

/**
 * The per-session columns the Stats screen aggregates. Selected as plain rows
 * and summed here instead of with SQL `SUM()`.
 *
 * `totalMantras` is a BigInteger stored as TEXT. SQLite's `SUM()` coerces it
 * to INT64 and throws "integer overflow" once a group passes ~9.2e18 (one
 * stupa-class wheel plus a large manual log is enough), or silently switches
 * to a lossy REAL when a single value no longer fits — crashing or corrupting
 * the Stats screen. BigInteger sums cannot overflow.
 */
data class SessionAggregateRow(
    val mantraId: String,
    val wheelId: String?,
    val rotationCount: Long,
    val totalMantras: BigInteger
)

object SessionAggregates {

    private class Acc {
        var count = 0L
        var rotations = 0L
        var mantras: BigInteger = BigInteger.ZERO
        fun add(row: SessionAggregateRow) {
            count++
            rotations = LifetimeStatsMath.saturatingAdd(rotations, row.rotationCount)
            mantras = mantras.add(row.totalMantras)
        }
    }

    private fun <K> group(rows: List<SessionAggregateRow>, key: (SessionAggregateRow) -> K): Map<K, Acc> {
        val out = LinkedHashMap<K, Acc>()
        for (row in rows) out.getOrPut(key(row)) { Acc() }.add(row)
        return out
    }

    /** Equivalent of `GROUP BY mantraId` with exact sums. */
    fun byMantra(rows: List<SessionAggregateRow>): List<MantraStats> =
        group(rows) { it.mantraId }.map { (id, a) -> MantraStats(id, a.count, a.rotations, a.mantras) }

    /** Equivalent of `GROUP BY wheel_id` (null wheel = manual configuration) with exact sums. */
    fun byWheel(rows: List<SessionAggregateRow>): List<WheelStats> =
        group(rows) { it.wheelId }.map { (id, a) -> WheelStats(id, a.count, a.rotations, a.mantras) }

    /** Session count and exact mantra total over all [rows]. */
    fun totals(rows: List<SessionAggregateRow>): TimeStats {
        val a = Acc()
        rows.forEach(a::add)
        return TimeStats(sessionCount = a.count, totalMantras = a.mantras)
    }
}
