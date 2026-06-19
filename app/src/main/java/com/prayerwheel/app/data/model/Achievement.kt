package com.prayerwheel.app.data.model

import java.math.BigInteger

/**
 * Data model for a practice milestone achievement.
 */
data class Achievement(
    val id: String,
    val title: String,
    val description: String,
    val mantrasRequired: BigInteger,
    val rewardText: String,
    val rewardSkinId: String? = null,
    val quote: String = ""
)

/**
 * Registry of all available achievements.
 */
object Achievements {
    val FIRST_ROTATION = Achievement(
        id = "first_rotation",
        title = "First Rotation",
        description = "Began the wheel of Dharma",
        mantrasRequired = BigInteger.ONE,
        rewardText = "Welcome Animation",
        quote = "The journey of a thousand miles begins with a single step."
    )

    val MALA_COMPLETE = Achievement(
        id = "mala_complete",
        title = "Mala Complete",
        description = "Completed 108 mantras",
        mantrasRequired = BigInteger.valueOf(108),
        rewardText = "Sandalwood Skin",
        rewardSkinId = "sandalwood",
        quote = "Mindfulness is the path to the deathless."
    )

    val THOUSANDFOLD = Achievement(
        id = "thousandfold",
        title = "Thousandfold Merit",
        description = "Completed 1,000 mantras",
        mantrasRequired = BigInteger.valueOf(1000),
        rewardText = "Lotus Petal Particles",
        quote = "Just as a flower blooms, so does the heart of wisdom."
    )

    val TEN_THOUSAND = Achievement(
        id = "ten_thousand",
        title = "Ten Thousand fold",
        description = "Completed 10,000 mantras",
        mantrasRequired = BigInteger.valueOf(10000),
        rewardText = "Lapis Lazuli Skin",
        rewardSkinId = "lapis_lazuli",
        quote = "The mind is everything. What you think you become."
    )

    val HUNDRED_THOUSAND = Achievement(
        id = "hundred_thousand",
        title = "Great Accumulation",
        description = "Completed 100,000 mantras",
        mantrasRequired = BigInteger.valueOf(100000),
        rewardText = "Turquoise Skin",
        rewardSkinId = "turquoise",
        quote = "Radiating peace throughout the ten directions."
    )

    val MILLION = Achievement(
        id = "million",
        title = "Millionfold Compassion",
        description = "Completed 1,000,000 mantras",
        mantrasRequired = BigInteger.valueOf(1000000),
        rewardText = "Golden Glow Environment",
        quote = "All life is interconnected. May all beings be happy."
    )

    val TEN_MILLION = Achievement(
        id = "ten_million",
        title = "Bodhisattva Path",
        description = "Completed 10,000,000 mantras",
        mantrasRequired = BigInteger.valueOf(10000000),
        rewardText = "Ruby Skin",
        rewardSkinId = "ruby",
        quote = "For as long as space endures, may I remain to dispel the misery of the world."
    )

    val HUNDRED_MILLION = Achievement(
        id = "hundred_million",
        title = "Universal Blessing",
        description = "Completed 100,000,000 mantras",
        mantrasRequired = BigInteger.valueOf(100000000),
        rewardText = "Stupa Silhouette on Calendar",
        quote = "The pure mind sees purity everywhere."
    )

    val BILLION = Achievement(
        id = "billion",
        title = "Cosmic Harmony",
        description = "Completed 1,000,000,000 mantras",
        mantrasRequired = BigInteger.valueOf(1000000000),
        rewardText = "Amethyst Skin",
        rewardSkinId = "amethyst",
        quote = "Infinite merit flowing like a limitless ocean."
    )

    val TRILLION = Achievement(
        id = "trillion",
        title = "Ultimate Realization",
        description = "Completed 1,000,000,000,000 mantras",
        mantrasRequired = BigInteger("1000000000000"),
        rewardText = "Rainbow Skin",
        rewardSkinId = "rainbow",
        quote = "Gate gate pāragate pārasaṃgate bodhi svāhā."
    )

    val ALL = listOf(
        FIRST_ROTATION,
        MALA_COMPLETE,
        THOUSANDFOLD,
        TEN_THOUSAND,
        HUNDRED_THOUSAND,
        MILLION,
        TEN_MILLION,
        HUNDRED_MILLION,
        BILLION,
        TRILLION
    )

    fun byId(id: String): Achievement? = ALL.find { it.id == id }
}
