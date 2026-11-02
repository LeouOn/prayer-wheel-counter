package com.prayerwheel.app.viewmodel.milestones

import com.prayerwheel.app.data.model.Achievements
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger

class MilestonesTest {

    private fun b(v: Long) = BigInteger.valueOf(v)
    private val trillion = BigInteger("1000000000000")

    @Test
    fun tierFor_boundaries() {
        assertEquals(0, Milestones.tierFor(BigInteger.ZERO))
        assertEquals(0, Milestones.tierFor(b(999)))
        assertEquals(1, Milestones.tierFor(b(1_000)))
        assertEquals(7, Milestones.tierFor(trillion - BigInteger.ONE))
        assertEquals(8, Milestones.tierFor(trillion))
        assertEquals(8, Milestones.tierFor(BigInteger.TEN.pow(30)))
    }

    @Test
    fun highestTierCrossed_singleAndNone() {
        assertEquals(0, Milestones.highestTierCrossed(b(999), b(1_000)))
        assertEquals(-1, Milestones.highestTierCrossed(b(1_000), b(1_000)))
        assertEquals(-1, Milestones.highestTierCrossed(b(1_001), b(9_999)))
    }

    @Test
    fun highestTierCrossed_multiThresholdJump_returnsBrightest() {
        // 999 → 1,000,000 crosses 1K, 10K, 100K and 1M → index 3.
        assertEquals(3, Milestones.highestTierCrossed(b(999), b(1_000_000)))
        assertEquals(7, Milestones.highestTierCrossed(BigInteger.ZERO, trillion))
    }

    @Test
    fun newlyUnlocked_returnsEligibleInOrder() {
        val ids = Milestones.newlyUnlocked(b(1_000), emptySet()).map { it.id }
        assertEquals(
            listOf(Achievements.FIRST_ROTATION.id, Achievements.MALA_COMPLETE.id, Achievements.THOUSANDFOLD.id),
            ids
        )
    }

    @Test
    fun newlyUnlocked_excludesAlreadyUnlocked() {
        val unlocked = setOf(Achievements.FIRST_ROTATION.id, Achievements.MALA_COMPLETE.id)
        val ids = Milestones.newlyUnlocked(b(1_000), unlocked).map { it.id }
        assertEquals(listOf(Achievements.THOUSANDFOLD.id), ids)
        assertTrue(Milestones.newlyUnlocked(BigInteger.ZERO, emptySet()).isEmpty())
    }

    @Test
    fun newlyUnlocked_jumpToTrillion_unlocksAll() {
        assertEquals(Achievements.ALL, Milestones.newlyUnlocked(trillion, emptySet()))
    }

    @Test
    fun thresholdIndexOf_matchesAchievementRequirements() {
        assertEquals(-1, Milestones.thresholdIndexOf(Achievements.FIRST_ROTATION.mantrasRequired))
        assertEquals(-1, Milestones.thresholdIndexOf(Achievements.MALA_COMPLETE.mantrasRequired))
        assertEquals(0, Milestones.thresholdIndexOf(Achievements.THOUSANDFOLD.mantrasRequired))
        assertEquals(7, Milestones.thresholdIndexOf(Achievements.TRILLION.mantrasRequired))
        // Every threshold mirrors some achievement's requirement.
        val required = Achievements.ALL.map { it.mantrasRequired }.toSet()
        assertTrue(Milestones.MILESTONE_THRESHOLDS.all { it in required })
    }
}
