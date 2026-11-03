package com.prayerwheel.app.data.model

import java.math.BigInteger

/**
 * Aggregated statistics for a specific mantra.
 */
data class MantraStats(
    val mantraId: String,
    val sessionCount: Long,
    val totalRotations: Long,
    /** Exact total (BigInteger): sums of BigInteger-as-TEXT can exceed Long. */
    val totalMantras: BigInteger
)
