package com.prayerwheel.app.viewmodel.physics

import com.prayerwheel.app.data.datastore.SpinMode
import com.prayerwheel.app.viewmodel.physics.SpinPhysics.DragSample
import com.prayerwheel.app.viewmodel.physics.SpinPhysics.MAX_ANGULAR_VELOCITY
import com.prayerwheel.app.viewmodel.physics.SpinPhysics.TWO_PI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

class SpinPhysicsTest {

    private val eps = 1e-4f
    private val pi = PI.toFloat()

    // ── revolutionsCrossed ─────────────────────────────────────────────

    @Test
    fun revolutionsCrossed_justUnderTwoPi_isZero() {
        assertEquals(0L, SpinPhysics.revolutionsCrossed(0f, TWO_PI - 0.001f))
    }

    @Test
    fun revolutionsCrossed_crossingTwoPi_isOne() {
        assertEquals(1L, SpinPhysics.revolutionsCrossed(TWO_PI - 0.01f, TWO_PI + 0.01f))
    }

    @Test
    fun revolutionsCrossed_largeStep_countsMany() {
        assertEquals(10L, SpinPhysics.revolutionsCrossed(0f, 10 * TWO_PI + 0.1f))
    }

    @Test
    fun currentBehavior_revolutionsCrossed_negativeBandAroundZeroIsOneRevolution() {
        // Truncation toward zero makes (-2π, 2π) a single "revolution 0", so
        // nearly two full clockwise turns starting from a negative angle count as 0.
        assertEquals(0L, SpinPhysics.revolutionsCrossed(-TWO_PI + 0.01f, TWO_PI - 0.01f))
        assertEquals(0L, SpinPhysics.revolutionsCrossed(-0.1f, 0.1f))
    }

    @Test
    fun currentBehavior_revolutionsCrossed_clockwiseCrossingNegativeTwoPi_counts() {
        // -1.0x → -0.98: index goes -1 → 0, so this counts as a revolution.
        assertEquals(1L, SpinPhysics.revolutionsCrossed(-TWO_PI - 0.1f, -TWO_PI + 0.1f))
    }

    @Test
    fun currentBehavior_revolutionsCrossed_counterClockwiseNeverCounts() {
        // Decreasing angle never yields a positive count, even with CCW enabled by the user.
        assertEquals(0L, SpinPhysics.revolutionsCrossed(TWO_PI + 0.1f, TWO_PI - 0.1f))
        assertEquals(0L, SpinPhysics.revolutionsCrossed(-0.1f, -TWO_PI - 0.1f))
        assertEquals(0L, SpinPhysics.revolutionsCrossed(0f, -10 * TWO_PI))
    }

    // ── normalizeAngle ─────────────────────────────────────────────────

    @Test
    fun normalizeAngle_wrapsIntoRange() {
        assertEquals(0.5f, SpinPhysics.normalizeAngle(0.5f), eps)
        assertEquals(-pi + 0.5f, SpinPhysics.normalizeAngle(pi + 0.5f), eps)
        assertEquals(pi - 0.5f, SpinPhysics.normalizeAngle(-pi - 0.5f), eps)
        assertEquals(0.5f, SpinPhysics.normalizeAngle(0.5f + 3 * TWO_PI), 1e-3f)
    }

    @Test
    fun currentBehavior_normalizeAngle_atPlusMinusPi_bothMapToPlusPi() {
        // PI.toFloat() rounds up past Double PI, so +π_f is "> π" and wraps to -π_f,
        // which is then "< -π" and wraps back to +π_f. -π_f likewise ends at +π_f.
        assertEquals(pi, SpinPhysics.normalizeAngle(pi), 0f)
        assertEquals(pi, SpinPhysics.normalizeAngle(-pi), 0f)
    }

    // ── flickVelocity ──────────────────────────────────────────────────

    @Test
    fun flickVelocity_fewerThanTwoSamples_isNull() {
        assertNull(SpinPhysics.flickVelocity(emptyList(), counterClockwiseEnabled = false))
        assertNull(SpinPhysics.flickVelocity(listOf(DragSample(0f, 0L)), counterClockwiseEnabled = false))
    }

    @Test
    fun flickVelocity_simpleClockwise() {
        val r = SpinPhysics.flickVelocity(listOf(DragSample(0f, 0L), DragSample(0.1f, 100L)), false)!!
        assertEquals(1.0f, r.velocity, eps)
        assertFalse(r.showCounterClockwiseReminder)
    }

    @Test
    fun flickVelocity_isRecencyWeighted() {
        // Interval speeds 1 rad/s (weight 2) and 2 rad/s (weight 3) → 8/5 = 1.6.
        val samples = listOf(DragSample(0f, 0L), DragSample(0.1f, 100L), DragSample(0.3f, 200L))
        assertEquals(1.6f, SpinPhysics.flickVelocity(samples, false)!!.velocity, eps)
    }

    @Test
    fun flickVelocity_usesOnlyLastFiveSamples() {
        val wild = listOf(DragSample(0f, 0L), DragSample(-3f, 1L))
        val steady = (0..4).map { DragSample(it * 0.1f, 1000L + it * 100L) }
        assertEquals(1.0f, SpinPhysics.flickVelocity(wild + steady, false)!!.velocity, eps)
    }

    @Test
    fun flickVelocity_wrapsAcrossPi() {
        // atan2 jumps from +3.0 to -3.0 while moving clockwise through π: true delta ≈ +0.2832.
        val r = SpinPhysics.flickVelocity(listOf(DragSample(3.0f, 0L), DragSample(-3.0f, 1000L)), false)!!
        assertEquals(TWO_PI - 6.0f, r.velocity, eps)
    }

    @Test
    fun flickVelocity_capsAtMax() {
        val r = SpinPhysics.flickVelocity(listOf(DragSample(0f, 0L), DragSample(0.5f, 10L)), false)!!
        assertEquals(MAX_ANGULAR_VELOCITY, r.velocity, 0f)
    }

    @Test
    fun currentBehavior_flickVelocity_zeroTimeDeltaTreatedAsOneMs() {
        val r = SpinPhysics.flickVelocity(listOf(DragSample(0f, 5L), DragSample(0.001f, 5L)), false)!!
        assertEquals(1.0f, r.velocity, eps)
    }

    @Test
    fun flickVelocity_counterClockwise_showsReminderOnlyWhenDisabled() {
        val samples = listOf(DragSample(0f, 0L), DragSample(-0.05f, 10L)) // -5 rad/s
        val disabled = SpinPhysics.flickVelocity(samples, counterClockwiseEnabled = false)!!
        assertEquals(-5f, disabled.velocity, eps)
        assertTrue(disabled.showCounterClockwiseReminder)

        val enabled = SpinPhysics.flickVelocity(samples, counterClockwiseEnabled = true)!!
        assertEquals(-5f, enabled.velocity, eps)
        assertFalse(enabled.showCounterClockwiseReminder)
    }

    @Test
    fun flickVelocity_counterClockwise_clampedToNegativeMax() {
        val r = SpinPhysics.flickVelocity(listOf(DragSample(0f, 0L), DragSample(-1f, 10L)), false)!!
        assertEquals(-MAX_ANGULAR_VELOCITY, r.velocity, 0f)
    }

    @Test
    fun flickVelocity_slowCounterClockwise_clampedToZero_noReminder() {
        // Exactly -3 rad/s is not "< -3", so it falls in the clockwise branch → coerced to 0.
        val atThreshold = SpinPhysics.flickVelocity(listOf(DragSample(0f, 0L), DragSample(-3f, 1000L)), false)!!
        assertEquals(0f, atThreshold.velocity, 0f)
        assertFalse(atThreshold.showCounterClockwiseReminder)

        val slow = SpinPhysics.flickVelocity(listOf(DragSample(0f, 0L), DragSample(-2f, 1000L)), false)!!
        assertEquals(0f, slow.velocity, 0f)
        assertFalse(slow.showCounterClockwiseReminder)
    }

    // ── isTwoHandedMode ────────────────────────────────────────────────

    @Test
    fun isTwoHandedMode_cases() {
        assertTrue(SpinPhysics.isTwoHandedMode(SpinMode.TWO_HANDED, false, false))
        assertTrue(SpinPhysics.isTwoHandedMode(SpinMode.TWO_HANDED_AUTO, false, false))
        assertTrue(SpinPhysics.isTwoHandedMode(SpinMode.MANUAL, true, true))
        assertFalse(SpinPhysics.isTwoHandedMode(SpinMode.MANUAL, true, false))
        assertFalse(SpinPhysics.isTwoHandedMode(SpinMode.AUTO_SPIN, false, true))
    }

    // ── stepVelocity ───────────────────────────────────────────────────

    private fun step(
        omega: Float,
        dt: Float = 1f / 60f,
        auto: Boolean = false,
        twoHandedMode: Boolean = false,
        engaged: Boolean = false,
        rpm: Int = 30,
        friction: Float = SpinPhysics.FRICTION_COEFFICIENT,
        weightDeg: Float = 0f,
        breath: Float = 0f
    ) = SpinPhysics.stepVelocity(omega, dt, auto, twoHandedMode, engaged, rpm, friction, weightDeg, breath)

    @Test
    fun stepVelocity_frictionDecay() {
        assertEquals(10f * (1f - 0.008f), step(10f, dt = 1f), eps)
        assertEquals(-10f * (1f - 0.008f), step(-10f, dt = 1f), eps)
    }

    @Test
    fun stepVelocity_staticFrictionSnapsBelowThreshold() {
        assertEquals(0f, step(0.04f), 0f)
        assertEquals(0f, step(-0.04f), 0f)
        assertTrue(step(0.06f) > 0.05f)
    }

    @Test
    fun stepVelocity_breathAboveThreshold_skipsStaticFriction() {
        val v = step(0f, dt = 1f / 60f, breath = 0.5f)
        assertEquals(0.5f * SpinPhysics.BREATH_TORQUE / 60f, v, 1e-6f)
        assertTrue(v > 0f)
    }

    @Test
    fun stepVelocity_breathBelowThreshold_stillSnaps() {
        assertEquals(0f, step(0f, breath = 0.005f), 0f)
    }

    @Test
    fun stepVelocity_autoSpinRampsTowardTarget() {
        // 30 RPM target = π rad/s; per-frame step at 60 fps is 0.1.
        assertEquals(0.1f, step(0f, auto = true, rpm = 30), eps)
        assertEquals(pi, step(pi - 0.05f, auto = true, rpm = 30), eps)
        assertEquals(9.9f, step(10f, auto = true, rpm = 30), eps)
        // Larger frame → proportionally larger allowed step.
        assertEquals(0.2f, step(0f, dt = 2f / 60f, auto = true, rpm = 30), eps)
    }

    @Test
    fun stepVelocity_autoSpinInTwoHandedMode_usesFrictionInstead() {
        assertEquals(0f, step(0f, auto = true, twoHandedMode = true), 0f)
    }

    @Test
    fun stepVelocity_weightCouplingOpposesMotionWithoutReversing() {
        // 90° weight → sin = 1 → extra 0.03·dt decel.
        assertEquals(0.992f - 0.03f, step(1f, dt = 1f, weightDeg = 90f), eps)
        assertEquals(-0.992f + 0.03f, step(-1f, dt = 1f, weightDeg = 90f), eps)
        // Coupling larger than speed clamps to 0 (and then static friction holds it).
        assertEquals(0f, step(0.02f, dt = 1f, weightDeg = 90f), 0f)
        assertEquals(0f, step(-0.02f, dt = 1f, weightDeg = 90f), 0f)
    }

    @Test
    fun stepVelocity_twoHandedEngagedUsesLowFriction() {
        assertEquals(10f * (1f - SpinPhysics.TWO_HANDED_FRICTION), step(10f, dt = 1f, twoHandedMode = true, engaged = true), eps)
        assertEquals(10f * (1f - 0.008f), step(10f, dt = 1f, twoHandedMode = true, engaged = false), eps)
        assertEquals(10f * (1f - 0.008f), step(10f, dt = 1f, twoHandedMode = false, engaged = true), eps)
    }

    @Test
    fun snapToStop_belowThresholdIsZero() {
        assertEquals(0f, SpinPhysics.snapToStop(0.0009f), 0f)
        assertEquals(0f, SpinPhysics.snapToStop(-0.0009f), 0f)
        assertEquals(0.002f, SpinPhysics.snapToStop(0.002f), 0f)
    }

    // ── RPM / weight / particles ───────────────────────────────────────

    @Test
    fun rpm_isAbsoluteAndScaled() {
        assertEquals(60f, SpinPhysics.rpm(TWO_PI), eps)
        assertEquals(60f, SpinPhysics.rpm(-TWO_PI), eps)
        assertEquals(0f, SpinPhysics.rpm(0f), 0f)
    }

    @Test
    fun weightAngle_targetAndStep() {
        assertEquals(0f, SpinPhysics.targetWeightAngle(0f), eps)
        assertEquals(45f, SpinPhysics.targetWeightAngle(30f), eps)
        assertEquals(80f, SpinPhysics.targetWeightAngle(10_000f), 0f)
        assertEquals(4.5f, SpinPhysics.stepWeightAngle(0f, 30f), eps)
        assertEquals(45f, SpinPhysics.stepWeightAngle(45f, 30f), eps)
    }

    @Test
    fun particleSpawnProbability_tiers() {
        assertEquals(0f, SpinPhysics.particleSpawnProbability(0f), 0f)
        assertEquals(0f, SpinPhysics.particleSpawnProbability(2.0944f), 0f)
        assertEquals(0.15f, SpinPhysics.particleSpawnProbability(5.2359f), eps)
        assertEquals(0.35f, SpinPhysics.particleSpawnProbability(9.4247f), eps)
        assertEquals(0.6f, SpinPhysics.particleSpawnProbability(MAX_ANGULAR_VELOCITY), eps)
        assertEquals(0.6f, SpinPhysics.particleSpawnProbability(100f), eps)
        assertEquals(0.15f, SpinPhysics.particleSpawnProbability(-5.2359f), eps)
    }

}
