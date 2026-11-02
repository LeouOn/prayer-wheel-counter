package com.prayerwheel.app.viewmodel.milestones

import com.prayerwheel.app.data.model.Achievement
import com.prayerwheel.app.data.model.Achievements
import java.math.BigInteger

/**
 * Pure lifetime-milestone logic (bowl tones, aura tier, achievement unlocks),
 * extracted from WheelViewModel.
 */
object Milestones {

    /**
     * Lifetime-mantra thresholds (1K → 1T) that trigger singing bowl tones.
     * Index in this list = bowl tier (0 = deepest, 7 = brightest). Each entry
     * mirrors the `mantrasRequired` of [Achievements.THOUSANDFOLD]…[Achievements.TRILLION].
     */
    val MILESTONE_THRESHOLDS: List<BigInteger> = listOf(
        BigInteger.valueOf(1_000L),
        BigInteger.valueOf(10_000L),
        BigInteger.valueOf(100_000L),
        BigInteger.valueOf(1_000_000L),
        BigInteger.valueOf(10_000_000L),
        BigInteger.valueOf(100_000_000L),
        BigInteger.valueOf(1_000_000_000L),
        BigInteger("1000000000000")
    )

    /**
     * Highest milestone tier reached by a lifetime total: the count of
     * [MILESTONE_THRESHOLDS] entries the total has reached or exceeded (1..8),
     * or 0 if below the lowest threshold.
     */
    fun tierFor(lifetimeMantras: BigInteger): Int {
        var tier = 0
        for ((i, threshold) in MILESTONE_THRESHOLDS.withIndex()) {
            if (lifetimeMantras >= threshold) tier = i + 1
        }
        return tier
    }

    /**
     * Bowl-tone index (0..7) of the brightest threshold crossed going from
     * [previousTotal] to [newTotal], or -1 if none was crossed. A single
     * high-capacity rotation that crosses several thresholds yields the
     * brightest one.
     */
    fun highestTierCrossed(previousTotal: BigInteger, newTotal: BigInteger): Int =
        MILESTONE_THRESHOLDS.indexOfLast { threshold ->
            previousTotal < threshold && newTotal >= threshold
        }

    /**
     * Achievements not in [alreadyUnlocked] whose requirement [totalMantras]
     * meets, in [all] order.
     */
    fun newlyUnlocked(
        totalMantras: BigInteger,
        alreadyUnlocked: Set<String>,
        all: List<Achievement> = Achievements.ALL
    ): List<Achievement> =
        all.filter { it.id !in alreadyUnlocked && totalMantras >= it.mantrasRequired }

    /**
     * Index of the bowl/haptic tier matching an achievement's requirement, or
     * -1 when the requirement is not one of [MILESTONE_THRESHOLDS] (e.g. the
     * 1-mantra and 108-mantra achievements).
     */
    fun thresholdIndexOf(mantrasRequired: BigInteger): Int =
        MILESTONE_THRESHOLDS.indexOfFirst { it == mantrasRequired }
}
