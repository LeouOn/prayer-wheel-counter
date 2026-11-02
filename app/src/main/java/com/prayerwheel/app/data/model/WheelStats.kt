package com.prayerwheel.app.data.model

import java.math.BigInteger

/**
 * Aggregated statistics for a specific prayer wheel.
 */
data class WheelStats(
    val wheelId: String?,
    val sessionCount: Long,
    val totalRotations: Long,
    /** Exact total (BigInteger): sums of BigInteger-as-TEXT can exceed Long. */
    val totalMantras: BigInteger
)
