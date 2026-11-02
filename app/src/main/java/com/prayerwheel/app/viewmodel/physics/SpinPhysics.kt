package com.prayerwheel.app.viewmodel.physics

import com.prayerwheel.app.data.datastore.SpinMode
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * Pure prayer-wheel spin physics, extracted from WheelViewModel so it can be
 * unit-tested on the JVM. No Android, coroutine or StateFlow dependencies —
 * every input is passed explicitly and every output is returned.
 */
object SpinPhysics {

    const val FRICTION_COEFFICIENT = 0.008f
    const val TWO_HANDED_FRICTION = 0.0001f
    const val STOP_THRESHOLD = 0.001f
    const val TWO_PI = 2f * PI.toFloat()

    // Reduced from 1.5f so flicks don't slam into the lowered velocity cap (12.566 rad/s).
    // At 1.0f, a typical drag of ~8 rad/s reaches the cap cleanly without an invisible wall.
    const val FLICK_VELOCITY_MULTIPLIER = 1.0f

    // 120 RPM × 2π/60 ≈ 12.566 rad/s — vigorous but meditative devotional cap.
    // Real prayer wheels spin at 30-100 RPM; the previous 75f (~716 RPM) was frantic.
    const val MAX_ANGULAR_VELOCITY = 12.566f

    const val MIN_COUNTER_CLOCKWISE_VELOCITY = 3.0f // rad/s — below this, accidental flicks are ignored

    // Static friction: wheel halts cleanly below this angular velocity (rad/s) when no torque is applied.
    const val STATIC_FRICTION_THRESHOLD = 0.05f

    // Weight→wheel coupling: restoring torque coefficient from sin(weightAngle); acts as extra friction at low speed.
    const val WEIGHT_COUPLING_COEFFICIENT = 0.03f

    // Auto-spin ramp: max rad/s change per frame at the 60fps baseline (~0.5-1.5s ramp to a typical target RPM).
    const val AUTO_SPIN_MAX_ACCEL_PER_FRAME = 0.1f

    // Breath mode: angular acceleration (rad/s²) per unit of normalized mic
    // amplitude. At steady breath (amp ≈ 0.3–0.5) the wheel settles around
    // 30–60 RPM; a strong blow (amp ≈ 1.0) approaches the 120 RPM devotional
    // cap. Tuned against FRICTION_COEFFICIENT so the steady-state velocity
    // (BREATH_TORQUE × amp / μ) lands in that band.
    const val BREATH_TORQUE = 0.1f

    // Below this normalized amplitude the breath input is treated as zero
    // for the purposes of (a) keeping the physics loop alive and
    // (b) bypassing static-friction snap-to-zero.
    const val BREATH_ACTIVE_THRESHOLD = 0.01f

    /** Max number of drag samples retained for flick-velocity estimation. */
    const val DRAG_HISTORY_SIZE = 5

    /** A single drag sample: pointer angle around the wheel centre (radians) and its timestamp (ms). */
    data class DragSample(
        val angle: Float,
        val timestamp: Long
    )

    /**
     * Outcome of a flick: the clamped angular velocity to apply and whether the
     * gentle counter-clockwise reminder should be shown.
     */
    data class FlickResult(
        val velocity: Float,
        val showCounterClockwiseReminder: Boolean
    )

    /**
     * Normalizes an angular difference into roughly [-π, π] by repeated ±2π steps.
     *
     * Note: comparisons are Float-vs-Double [PI]. Because `PI.toFloat()` rounds
     * *up*, an input of exactly `PI.toFloat()` is treated as "> π" and wraps to
     * just above -π (and symmetrically for `-PI.toFloat()`).
     */
    fun normalizeAngle(diff: Float): Float {
        var d = diff
        while (d > PI) d -= TWO_PI
        while (d < -PI) d += TWO_PI
        return d
    }

    /**
     * Number of whole revolutions gained moving from [oldAngle] to [newAngle].
     * Uses truncation toward zero (`toLong()`) of angle / 2π, and returns 0 when
     * the revolution index did not increase.
     *
     * Current behavior (pinned by tests): because of truncation toward zero the
     * band (-2π, 2π) is a single "revolution 0", and counter-clockwise motion
     * (decreasing angle) never yields a positive count.
     */
    fun revolutionsCrossed(oldAngle: Float, newAngle: Float): Long {
        val oldRevolutions = (oldAngle / TWO_PI).toLong()
        val newRevolutions = (newAngle / TWO_PI).toLong()
        return if (newRevolutions > oldRevolutions) newRevolutions - oldRevolutions else 0L
    }

    /**
     * Estimates flick velocity from the most recent drag samples using a
     * recency-weighted average of per-interval angular speeds.
     *
     * @return null when fewer than 2 samples are available (no velocity change).
     */
    fun flickVelocity(
        samples: List<DragSample>,
        counterClockwiseEnabled: Boolean
    ): FlickResult? {
        if (samples.size < 2) return null
        // Use weighted average of last 3-5 events for smoother velocity
        val eventsToConsider = samples.takeLast(DRAG_HISTORY_SIZE)
        var totalWeightedAngularDiff = 0f
        var totalWeight = 0f

        eventsToConsider.forEachIndexed { index, event ->
            val weight = (index + 1).toFloat() // More recent events have higher weight
            if (index > 0) {
                val prevEvent = eventsToConsider[index - 1]
                val timeDelta = (event.timestamp - prevEvent.timestamp).coerceAtLeast(1L) / 1000f
                if (timeDelta > 0) {
                    var angularDiff = event.angle - prevEvent.angle
                    // Handle angle wrap-around for clockwise detection
                    if (angularDiff > PI) angularDiff -= TWO_PI
                    if (angularDiff < -PI) angularDiff += TWO_PI
                    totalWeightedAngularDiff += (angularDiff / timeDelta) * weight
                    totalWeight += weight
                }
            }
        }

        if (totalWeight <= 0) return null

        var velocity = totalWeightedAngularDiff / totalWeight

        // Apply flick velocity multiplier for stronger spinning
        velocity *= FLICK_VELOCITY_MULTIPLIER

        var showReminder = false
        // Check for counter-clockwise motion (only trigger for intentional CCW flicks)
        if (velocity < -MIN_COUNTER_CLOCKWISE_VELOCITY) {
            // Significant counter-clockwise detected; show gentle reminder but still allow the motion
            if (!counterClockwiseEnabled) showReminder = true
            velocity = velocity.coerceIn(-MAX_ANGULAR_VELOCITY, 0f)
        } else {
            // Clockwise - apply higher cap
            velocity = velocity.coerceIn(0f, MAX_ANGULAR_VELOCITY)
        }
        return FlickResult(velocity, showReminder)
    }

    /** Whether the reduced two-handed friction regime applies. */
    fun isTwoHandedMode(spinMode: SpinMode, twoHandedEnabled: Boolean, autoSpinEnabled: Boolean): Boolean =
        spinMode == SpinMode.TWO_HANDED ||
            spinMode == SpinMode.TWO_HANDED_AUTO ||
            (twoHandedEnabled && autoSpinEnabled)

    /**
     * One physics-frame velocity update (before the [STOP_THRESHOLD] snap, which
     * the caller applies when publishing; the unsnapped value drives the angle step).
     *
     * @param weightAngleDegrees current pendulum weight angle (degrees).
     * @param breathAmplitude normalized microphone amplitude; pass 0 when breath mode is off.
     */
    fun stepVelocity(
        currentOmega: Float,
        deltaTime: Float,
        autoSpinEnabled: Boolean,
        isTwoHandedMode: Boolean,
        twoHandedEngaged: Boolean,
        autoSpinRpm: Int,
        baseFriction: Float,
        weightAngleDegrees: Float,
        breathAmplitude: Float
    ): Float {
        return if (autoSpinEnabled && !isTwoHandedMode) {
            // Auto-spin: torque-limited ramp toward target RPM (~0.5-1.5s to reach set speed)
            val targetOmega = autoSpinRpm * 2 * PI.toFloat() / 60f
            val gap = targetOmega - currentOmega
            val maxStepThisFrame = AUTO_SPIN_MAX_ACCEL_PER_FRAME * (deltaTime * 60f)
            currentOmega + gap.coerceIn(-maxStepThisFrame, maxStepThisFrame)
        } else {
            // Manual / two-handed / coast / breath: friction decay ω_{t+1} = ω_t × (1 - μ × Δt)
            val frictionCoefficient = when {
                isTwoHandedMode && twoHandedEngaged -> TWO_HANDED_FRICTION
                else -> baseFriction
            }
            val frictionDecayed = currentOmega * (1f - frictionCoefficient * deltaTime)

            // Weight→wheel coupling: the chain weight acts as a pendulum restoring force.
            // Applied as additional friction opposing motion, capped to never reverse direction.
            val weightAngleRad = weightAngleDegrees * PI.toFloat() / 180f
            val couplingDecel = WEIGHT_COUPLING_COEFFICIENT * sin(weightAngleRad) * deltaTime
            val withCoupling = when {
                frictionDecayed > 0f -> (frictionDecayed - couplingDecel).coerceAtLeast(0f)
                frictionDecayed < 0f -> (frictionDecayed + couplingDecel).coerceAtMost(0f)
                else -> frictionDecayed
            }

            // Breath mode: microphone amplitude applies clockwise torque proportional
            // to loudness, alongside the existing friction/weight forces.
            val withBreath = if (breathAmplitude > 0f) {
                withCoupling + breathAmplitude * BREATH_TORQUE * deltaTime
            } else withCoupling

            // Static friction: clean halt when very slow and no torque applied this frame.
            // Breath is allowed to accumulate from rest (otherwise tiny per-frame breath
            // torque could never break through the 0.05 rad/s threshold).
            if (breathAmplitude > BREATH_ACTIVE_THRESHOLD) withBreath
            else if (abs(withBreath) < STATIC_FRICTION_THRESHOLD) 0f else withBreath
        }
    }

    /** Zeroes velocities whose magnitude is below [STOP_THRESHOLD]. */
    fun snapToStop(velocity: Float): Float =
        if (abs(velocity) < STOP_THRESHOLD) 0f else velocity

    /** RPM = |ω| · 60 / 2π. */
    fun rpm(angularVelocity: Float): Float = abs(angularVelocity) * 60f / TWO_PI

    /** Target weight angle (degrees) from centrifugal force: atan(rpm / 30), clamped to 0..80°. */
    fun targetWeightAngle(rpm: Float): Float =
        (kotlin.math.atan(rpm.toDouble() / 30.0) * 180.0 / PI).toFloat().coerceIn(0f, 80f)

    /** One frame of smooth interpolation of the weight angle toward its RPM-driven target. */
    fun stepWeightAngle(currentWeightAngle: Float, rpm: Float): Float =
        currentWeightAngle + (targetWeightAngle(rpm) - currentWeightAngle) * 0.1f

    /**
     * Per-frame star-particle spawn probability, tiered across the RPM range.
     * Boundary ω values are 20/50/90/120 RPM × 2π/60 ≈ 2.0944/5.2359/9.4247/12.566.
     * Tier 4 (Dissolving) caps at the [MAX_ANGULAR_VELOCITY] devotional ceiling.
     */
    fun particleSpawnProbability(angularVelocity: Float): Float {
        val omega = abs(angularVelocity)
        return when {
            omega <= 2.0944f -> 0f
            omega <= 5.2359f -> ((omega - 2.0944f) / (5.2359f - 2.0944f)) * 0.15f
            omega <= 9.4247f -> 0.15f + ((omega - 5.2359f) / (9.4247f - 5.2359f)) * (0.35f - 0.15f)
            else -> 0.35f + ((omega - 9.4247f) / (MAX_ANGULAR_VELOCITY - 9.4247f)).coerceIn(0f, 1f) * (0.6f - 0.35f)
        }
    }
}
