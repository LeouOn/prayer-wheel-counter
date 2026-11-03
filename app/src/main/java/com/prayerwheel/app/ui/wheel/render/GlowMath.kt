package com.prayerwheel.app.ui.wheel.render

import com.prayerwheel.app.viewmodel.WheelViewModel
import java.math.BigInteger
import kotlin.math.abs

/**
 * Power-of-ten thresholds for milestone animations.
 * 1,000 → 10,000 → 100,000 → 1,000,000 → 10,000,000 → 100,000,000 → 1,000,000,000 → 1,000,000,000,000
 */
internal val MILESTONE_THRESHOLDS = listOf(
    BigInteger("1000"),
    BigInteger("10000"),
    BigInteger("100000"),
    BigInteger("1000000"),
    BigInteger("10000000"),
    BigInteger("100000000"),
    BigInteger("1000000000"),
    BigInteger("1000000000000")
)

/**
 * Tiered glow intensity across the 0–120 RPM devotional speed range.
 *
 * Maps |angularVelocity| (rad/s) to a 0..1 glow intensity through four
 * qualitative tiers — Still, Flowing, Blazing, Dissolving — each smoothly
 * interpolated at its boundaries. The tiers mirror traditional descriptions
 * of deepening samadhi: the visual energy grows in distinct stages rather
 * than along a single linear ramp.
 *
 * Boundary ω values come from RPM × 2π/60:
 *  20 RPM ≈ 2.0944, 50 RPM ≈ 5.2359, 90 RPM ≈ 9.4247, 120 RPM ≈ 12.566.
 */
private fun tieredGlowIntensity(angularVelocity: Float): Float {
    val omega = abs(angularVelocity)
    return when {
        // Tier 1 — Still (0–20 RPM): barely-there meditative glow (0.0–0.15).
        omega <= 2.0944f -> (omega / 2.0944f) * 0.15f
        // Tier 2 — Flowing (20–50 RPM): warm glow building up (0.15–0.5).
        omega <= 5.2359f -> 0.15f + ((omega - 2.0944f) / (5.2359f - 2.0944f)) * (0.5f - 0.15f)
        // Tier 3 — Blazing (50–90 RPM): full glow (0.5–0.85).
        omega <= 9.4247f -> 0.5f + ((omega - 5.2359f) / (9.4247f - 5.2359f)) * (0.85f - 0.5f)
        // Tier 4 — Dissolving (90–120 RPM): ethereal maximum glow (0.85–1.0).
        else -> 0.85f + ((omega - 9.4247f) / (12.566f - 9.4247f)).coerceIn(0f, 1f) * (1.0f - 0.85f)
    }
}

/**
 * Persistent aura floor beneath the tiered glow. A practitioner who has crossed
 * a lifetime milestone always sees at least this much ambient glow on their
 * wheel, even at 0 RPM — the wheel accumulates devotional "warmth" that never
 * fully fades. Maps lifetime tier (0..8, see [WheelViewModel.highestMilestoneTier])
 * to a minimum glow intensity.
 */
private fun milestoneAuraFloor(tier: Int): Float = when {
    tier >= 7 -> 0.15f   // 1B+: radiant
    tier >= 5 -> 0.10f   // 10M+: clearly visible
    tier >= 3 -> 0.06f   // 100K+: subtle
    tier >= 1 -> 0.03f   // 1K+: barely visible warmth
    else -> 0f
}

/**
 * Effective glow intensity: the larger of the velocity-driven tiered glow and
 * the lifetime-milestone aura floor. Used by every wheel renderer so the
 * persistent aura shows uniformly across all view modes.
 */
internal fun effectiveGlowIntensity(angularVelocity: Float, milestoneTier: Int): Float =
    maxOf(tieredGlowIntensity(angularVelocity), milestoneAuraFloor(milestoneTier))
