package com.prayerwheel.app.data.model

/**
 * Represents a prayer mantra with display information and optional Tibetan text.
 *
 * @param id Unique identifier for the mantra
 * @param displayName Human-readable name (e.g., "Om Mani Padme Hum")
 * @param tibetan Tibetan Unicode text, or null if romanized only
 * @param romanized Romanized version of the mantra
 * @param meaning English meaning, or null if not available
 * @param isCustom Whether this is a user-defined custom mantra
 */
data class Mantra(
    val id: String,
    val displayName: String,
    val tibetan: String? = null,
    val romanized: String,
    val meaning: String? = null,
    val isCustom: Boolean = false
)