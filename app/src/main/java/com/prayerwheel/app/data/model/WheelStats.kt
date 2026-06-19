package com.prayerwheel.app.data.model

/**
 * Aggregated statistics for a specific prayer wheel.
 */
data class WheelStats(
    val wheelId: String?,
    val sessionCount: Long,
    val totalRotations: Long,
    val totalMantras: Long
)
