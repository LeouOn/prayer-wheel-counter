package com.prayerwheel.app.ui.components

import java.math.BigDecimal
import java.math.BigInteger

/**
 * Cosmic, historical, scientific, and cross-cultural reference constants used by the
 * Practice Calculator to translate abstract BigInteger mantra counts into comprehensible
 * cosmic / human / cultural scales. Pure data — no functions.
 *
 * All time-in-years values are BigDecimal; all integer quantities are BigInteger.
 */
object CosmicReference {

    // ===== Time constants (years, as BigDecimal) — source: NASA / Wikipedia =====

    /** 13.8 billion years (source: NASA) */
    val AGE_OF_UNIVERSE: BigDecimal = BigDecimal("13800000000")

    /** 4.54 billion years (source: USGS) */
    val AGE_OF_EARTH: BigDecimal = BigDecimal("4540000000")

    /** 13.51 billion years (source: ESA / Hubble) */
    val AGE_OF_MILKY_WAY: BigDecimal = BigDecimal("13510000000")

    /** 5 billion years until the Sun enters red giant phase (source: NASA) */
    val TIME_SUN_REMAINING: BigDecimal = BigDecimal("5000000000")

    /** 66 million years since the K-Pg extinction (source: geological record) */
    val TIME_SINCE_DINOSAURS: BigDecimal = BigDecimal("66000000")

    /** ~300,000 years since anatomically modern Homo sapiens emerged (source: anthropology) */
    val TIME_SINCE_HOMO_SAPIENS: BigDecimal = BigDecimal("300000")

    /** ~1,300 years since Tibetan Buddhism took root (7th century CE) */
    val TIME_SINCE_TIBETAN_BUDDHISM: BigDecimal = BigDecimal("1300")

    /** ~4,500 years since the Great Pyramid of Giza was built */
    val TIME_SINCE_PYRAMIDS: BigDecimal = BigDecimal("4500")

    /** 225 million years for the Solar System to orbit the galactic center */
    val GALACTIC_YEAR: BigDecimal = BigDecimal("225000000")

    /** 200 million years since Pangaea began to break apart */
    val TIME_SINCE_PANGAEA: BigDecimal = BigDecimal("200000000")

    // ===== Quantity constants (as BigInteger) =====

    /** ~4.35 × 10^17 seconds elapsed since the Big Bang (13.8B yrs × 31.5M sec/yr) */
    val SECONDS_SINCE_BIG_BANG: BigInteger = BigInteger("435000000000000000")

    /** ~200 billion stars in the Milky Way (mid estimate) */
    val STARS_IN_MILKY_WAY: BigInteger = BigInteger("200000000000")

    /** ~10^24 stars in the observable universe (source: astronomy estimate) */
    val STARS_IN_OBSERVABLE_UNIVERSE: BigInteger = BigInteger("1000000000000000000000000")

    /** ~7.5 × 10^18 grains of sand on Earth's beaches and deserts (source: estimate) */
    val GRAINS_OF_SAND_EARTH: BigInteger = BigInteger("7500000000000000000")

    /** ~86 billion neurons in the human brain */
    val NEURONS_HUMAN_BRAIN: BigInteger = BigInteger("86000000000")

    /** ~7 × 10^27 atoms in the human body */
    val ATOMS_HUMAN_BODY: BigInteger = BigInteger("7000000000000000000000000000")

    /** ~1.5 × 10^21 water molecules in a single drop (0.05 mL) */
    val WATER_MOLECULES_DROP: BigInteger = BigInteger("1500000000000000000")

    /** 8 billion — approximate world population (2024) */
    val WORLD_POPULATION: BigInteger = BigInteger("8000000000")

    /** Traditional Buddhist/Hindu mala bead count */
    val MALA_BEADS: BigInteger = BigInteger("108")

    /** Traditional 100-million mantra milestone ("bhumi" or "bum") */
    val TRADITIONAL_100M_MILESTONE: BigInteger = BigInteger("100000000")

    // ===== Cross-cultural / historical time constants (years, as BigDecimal) =====

    /** Hindu: 1 Kalpa = 4.32B years = 1 day of Brahma */
    val KALPA_HINDU: BigDecimal = BigDecimal("4320000000")

    /** Hindu: 1 Manvantara = 306.72M years = 71 Mahayugas */
    val MANVANTARA_HINDU: BigDecimal = BigDecimal("306720000")

    /** Hindu: 1 Mahayuga = 4.32M years (Satya+Treta+Dvapara+Kali) */
    val MAHA_YUGA_HINDU: BigDecimal = BigDecimal("4320000")

    /** Hindu: 1 Kali Yuga = 432,000 years (current yuga; started 3102 BCE) */
    val KALI_YUGA_HINDU: BigDecimal = BigDecimal("432000")

    /** Hindu: years elapsed in Kali Yuga (2025 - 3102 BCE start) */
    val KALI_YUGA_ELAPSED: BigDecimal = BigDecimal("5127")

    /** Greek: Platonic Great Year (precession of equinoxes) = 25,920 years */
    val GREAT_YEAR_GREEK: BigDecimal = BigDecimal("25920")

    /** Norse: approximate mythological Ragnarok cycle */
    val RAGNAROK_CYCLE_NORSE: BigDecimal = BigDecimal("7000")

    /** Buddhist: Mundane Era (BE calendar from 543 BCE) — current year 2562 */
    val MUNDANE_ERA_BUDDHIST: BigDecimal = BigDecimal("2562")

    /** Sci-fi: Deep Thought's computation time (Hitchhiker's Guide) = 7.5M years */
    val HITCHHIKERS_DEEP_THOUGHT: BigDecimal = BigDecimal("7500000")
}
