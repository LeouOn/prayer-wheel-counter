package com.prayerwheel.app.viewmodel.haptics

/**
 * Pure haptic pattern selection extracted from WheelViewModel. The ViewModel
 * still owns the Vibrator and turns these descriptions into VibrationEffects.
 */
object HapticPatterns {

    const val MILESTONE_DURATION_MS = 20L

    /** A vibration shape for API 26+: either a one-shot pulse or a non-repeating waveform. */
    sealed interface Pattern {
        data class OneShot(val durationMs: Long, val amplitude: Int) : Pattern

        class Waveform(val timings: LongArray, val amplitudes: IntArray) : Pattern {
            override fun equals(other: Any?): Boolean =
                other is Waveform && timings.contentEquals(other.timings) && amplitudes.contentEquals(other.amplitudes)

            override fun hashCode(): Int = 31 * timings.contentHashCode() + amplitudes.contentHashCode()

            override fun toString(): String =
                "Waveform(timings=${timings.contentToString()}, amplitudes=${amplitudes.contentToString()})"
        }
    }

    /**
     * Rotation-tick duration scales inversely with RPM: slow turns produce long,
     * distinct mechanical clicks (prayer beads through fingers); fast turns
     * dissolve into a smooth hum as individual clicks merge.
     */
    fun tickDurationMs(rpm: Float): Long = when {
        rpm < 30f -> 8L
        rpm < 60f -> 5L
        else -> 3L
    }

    fun tickAmplitudeScale(rpm: Float): Float = when {
        rpm < 30f -> 1.0f
        rpm < 60f -> 0.7f
        else -> 0.45f
    }

    /** Tick amplitude: 255 × scale × user intensity, clamped to the valid 1..255 range. */
    fun scaledTickAmplitude(amplitudeScale: Float, intensity: Float): Int =
        (255 * amplitudeScale * intensity).toInt().coerceIn(1, 255)

    /**
     * Scales a base amplitude by user intensity and clamps to 1..255.
     *
     * Current behavior (pinned by tests): the ViewModel's pause/slider haptic
     * passes `VibrationEffect.DEFAULT_AMPLITUDE` (-1) as [base], which always
     * clamps to the minimum amplitude 1 regardless of intensity.
     */
    fun scaledAmplitude(base: Int, intensity: Float): Int =
        (base * intensity).toInt().coerceIn(1, 255)

    /**
     * Milestone haptic patterns scale with milestone magnitude (tiers 0–7,
     * matching the milestone thresholds 1K, 10K, 100K, 1M, 10M, 100M, 1B, 1T).
     *
     *  tiers 0–1 — acknowledgment : single pulse
     *  tiers 2–3 — celebration    : double pulse
     *  tiers 4–5 — resonance      : triple pulse, building
     *  tiers 6–7 — cosmic moment  : sustained rising waveform
     */
    fun milestonePattern(tier: Int, intensity: Float): Pattern {
        fun amp(base: Int) = scaledAmplitude(base, intensity)
        return when (tier / 2) {
            0 -> Pattern.OneShot(MILESTONE_DURATION_MS, amp(200))
            1 -> Pattern.Waveform(
                longArrayOf(0, MILESTONE_DURATION_MS, 50, MILESTONE_DURATION_MS),
                intArrayOf(0, amp(210), 0, amp(210))
            )
            2 -> Pattern.Waveform(
                longArrayOf(0, MILESTONE_DURATION_MS, 40, MILESTONE_DURATION_MS, 40, 30),
                intArrayOf(0, amp(210), 0, amp(225), 0, amp(245))
            )
            else -> Pattern.Waveform(
                longArrayOf(0, 30, 60, 40, 60, 50, 80, 80),
                intArrayOf(0, amp(150), 0, amp(200), 0, amp(230), 0, amp(255))
            )
        }
    }

    /** Pre-API-26 fallback: a single vibration whose length grows with the tier. */
    fun legacyMilestoneDurationMs(tier: Int): Long = when (tier / 2) {
        0 -> MILESTONE_DURATION_MS
        1 -> 90L
        2 -> 130L
        else -> 400L
    }
}
