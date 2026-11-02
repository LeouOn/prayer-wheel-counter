package com.prayerwheel.app.viewmodel.haptics

import com.prayerwheel.app.viewmodel.haptics.HapticPatterns.Pattern
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HapticPatternsTest {

    @Test
    fun tickDuration_rpmBoundaries() {
        assertEquals(8L, HapticPatterns.tickDurationMs(0f))
        assertEquals(8L, HapticPatterns.tickDurationMs(29.99f))
        assertEquals(5L, HapticPatterns.tickDurationMs(30f))
        assertEquals(5L, HapticPatterns.tickDurationMs(59.99f))
        assertEquals(3L, HapticPatterns.tickDurationMs(60f))
    }

    @Test
    fun tickAmplitudeScale_rpmBoundaries() {
        assertEquals(1.0f, HapticPatterns.tickAmplitudeScale(29.99f), 0f)
        assertEquals(0.7f, HapticPatterns.tickAmplitudeScale(30f), 0f)
        assertEquals(0.45f, HapticPatterns.tickAmplitudeScale(60f), 0f)
    }

    @Test
    fun scaledTickAmplitude_clampsTo1to255() {
        assertEquals(255, HapticPatterns.scaledTickAmplitude(1f, 1f))
        assertEquals(255, HapticPatterns.scaledTickAmplitude(1f, 2f))
        assertEquals(1, HapticPatterns.scaledTickAmplitude(0.45f, 0f))
        assertEquals(114, HapticPatterns.scaledTickAmplitude(0.45f, 1f)) // 114.75 truncated
    }

    @Test
    fun currentBehavior_scaledAmplitude_defaultAmplitudeAlwaysMinimum() {
        // VibrationEffect.DEFAULT_AMPLITUDE is -1; the pause/slider haptic scales it by
        // intensity and clamps, so it always vibrates at amplitude 1 (barely perceptible).
        val defaultAmplitude = -1
        assertEquals(1, HapticPatterns.scaledAmplitude(defaultAmplitude, 1f))
        assertEquals(1, HapticPatterns.scaledAmplitude(defaultAmplitude, 2f))
    }

    @Test
    fun milestonePattern_tierGroups() {
        assertEquals(Pattern.OneShot(20L, 200), HapticPatterns.milestonePattern(0, 1f))
        assertEquals(Pattern.OneShot(20L, 200), HapticPatterns.milestonePattern(1, 1f))

        val double = HapticPatterns.milestonePattern(2, 1f) as Pattern.Waveform
        assertArrayEquals(longArrayOf(0, 20, 50, 20), double.timings)
        assertArrayEquals(intArrayOf(0, 210, 0, 210), double.amplitudes)
        assertEquals(double, HapticPatterns.milestonePattern(3, 1f))

        val triple = HapticPatterns.milestonePattern(5, 1f) as Pattern.Waveform
        assertArrayEquals(longArrayOf(0, 20, 40, 20, 40, 30), triple.timings)
        assertArrayEquals(intArrayOf(0, 210, 0, 225, 0, 245), triple.amplitudes)

        val cosmic = HapticPatterns.milestonePattern(7, 1f) as Pattern.Waveform
        assertArrayEquals(longArrayOf(0, 30, 60, 40, 60, 50, 80, 80), cosmic.timings)
        assertArrayEquals(intArrayOf(0, 150, 0, 200, 0, 230, 0, 255), cosmic.amplitudes)
        assertTrue(HapticPatterns.milestonePattern(6, 1f) is Pattern.Waveform)
    }

    @Test
    fun milestonePattern_scalesAndClampsByIntensity() {
        assertEquals(Pattern.OneShot(20L, 100), HapticPatterns.milestonePattern(0, 0.5f))
        assertEquals(Pattern.OneShot(20L, 1), HapticPatterns.milestonePattern(0, 0f))
        val loud = HapticPatterns.milestonePattern(7, 2f) as Pattern.Waveform
        assertArrayEquals(intArrayOf(0, 255, 0, 255, 0, 255, 0, 255), loud.amplitudes)
    }

    @Test
    fun legacyMilestoneDuration_byTierGroup() {
        assertEquals(20L, HapticPatterns.legacyMilestoneDurationMs(0))
        assertEquals(90L, HapticPatterns.legacyMilestoneDurationMs(2))
        assertEquals(130L, HapticPatterns.legacyMilestoneDurationMs(4))
        assertEquals(400L, HapticPatterns.legacyMilestoneDurationMs(7))
    }
}
