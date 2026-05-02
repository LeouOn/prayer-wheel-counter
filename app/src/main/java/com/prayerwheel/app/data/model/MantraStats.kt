package com.prayerwheel.app.data.model

/**
 * Aggregated statistics for a specific mantra.
 */
data class MantraStats(
    val mantraId: String,
    val sessionCount: Long,
    val totalRotations: Long,
    val totalMantras: Long
)
