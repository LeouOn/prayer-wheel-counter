package com.prayerwheel.app.data.model

/**
 * Represents a saved prayer wheel profile.
 * Users can save custom prayer wheel configurations with specific capacities and mantras.
 */
data class SavedWheel(
    val id: String,
    val name: String,
    val capacity: Long,
    val mantraId: String,
    val skinId: String,
    val createdAt: Long
)
