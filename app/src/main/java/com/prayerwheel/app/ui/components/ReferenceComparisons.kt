package com.prayerwheel.app.ui.components

import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

/**
 * Pure functions that translate BigInteger mantra counts and BigDecimal year-spans
 * into human-readable comparison strings. Used by CalculatorScreen to render cosmic,
 * cultural, cross-generational, and fiction reference cards.
 *
 * Each function returns a List<String> of comparison lines, ordered roughly by
 * increasing magnitude. Empty lists are valid (caller should handle).
 */

// ===== Quantity comparisons =====

/**
 * Compares a total mantra count against cosmic/scientific quantities (stars, grains
 * of sand, neurons, etc.). Only includes comparisons where totalMantras >= the
 * reference quantity, so small totals produce few lines and huge totals produce many.
 *
 * Example output:
 *   "11.6× neurons in the human brain"
 *   "133.3× grains of sand on Earth"
 *   "1.0× stars in the observable universe"
 */
fun buildCosmicQuantityComparisons(totalMantras: BigInteger): List<String> {
    val comparisons = mutableListOf<Pair<BigInteger, String>>()
    comparisons.add(CosmicReference.NEURONS_HUMAN_BRAIN to "neurons in the human brain (86B)")
    comparisons.add(CosmicReference.STARS_IN_MILKY_WAY to "stars in the Milky Way (200B)")
    comparisons.add(CosmicReference.GRAINS_OF_SAND_EARTH to "grains of sand on Earth (7.5Qi)")
    comparisons.add(CosmicReference.WATER_MOLECULES_DROP to "water molecules in a drop (1.5Sx)")
    comparisons.add(CosmicReference.ATOMS_HUMAN_BODY to "atoms in a human body (7Oc)")
    comparisons.add(CosmicReference.STARS_IN_OBSERVABLE_UNIVERSE to "stars in the observable universe (1Sp)")
    comparisons.add(CosmicReference.SECONDS_SINCE_BIG_BANG to "seconds since the Big Bang (4.35×10^17)")

    return comparisons
        .filter { (ref, _) -> totalMantras >= ref }
        .map { (ref, label) ->
            val ratio = totalMantras.toBigDecimal()
                .divide(ref.toBigDecimal(), 1, RoundingMode.HALF_UP)
                .stripTrailingZeros()
            "${ratio.toPlainString()}× $label"
        }
}

/**
 * Compares a year-span against cosmic/historical/evolutionary timescales. Only includes
 * comparisons where years >= the reference timescale.
 *
 * Example output:
 *   "13.8× the age of the Pyramids (4,500 years)"
 *   "2.0× the age of Tibetan Buddhism (1,300 years)"
 *   "1.0× the time since the last dinosaur extinction (66M years)"
 *   "1.5× the age of Earth (4.54B years)"
 */
fun buildCosmicTimeComparisons(years: BigDecimal): List<String> {
    data class Ref(val value: BigDecimal, val label: String)
    val refs = listOf(
        Ref(CosmicReference.TIME_SINCE_PYRAMIDS, "the age of the Pyramids (4,500 years)"),
        Ref(CosmicReference.TIME_SINCE_TIBETAN_BUDDHISM, "the age of Tibetan Buddhism (1,300 years)"),
        Ref(CosmicReference.TIME_SINCE_HOMO_SAPIENS, "the age of Homo sapiens (300k years)"),
        Ref(CosmicReference.TIME_SINCE_DINOSAURS, "the time since the last dinosaur extinction (66M years)"),
        Ref(CosmicReference.TIME_SINCE_PANGAEA, "the time since Pangaea broke apart (200M years)"),
        Ref(CosmicReference.GALACTIC_YEAR, "one galactic year (225M years)"),
        Ref(CosmicReference.AGE_OF_EARTH, "the age of Earth (4.54B years)"),
        Ref(CosmicReference.TIME_SUN_REMAINING, "the remaining lifetime of the Sun (5B years)"),
        Ref(CosmicReference.AGE_OF_MILKY_WAY, "the age of the Milky Way (13.51B years)"),
        Ref(CosmicReference.AGE_OF_UNIVERSE, "the age of the universe (13.8B years)")
    )
    return refs
        .filter { years >= it.value }
        .map {
            val ratio = years.divide(it.value, 1, RoundingMode.HALF_UP).stripTrailingZeros()
            "${ratio.toPlainString()}× ${it.label}"
        }
}

// ===== Cross-cultural time comparisons =====

/**
 * Compares a year-span against cross-cultural / mythological / sci-fi cycles.
 * Includes only comparisons where years >= the reference cycle length.
 *
 * Example output:
 *   "5,127.0× the elapsed Kali Yuga (5,127 years)"
 *   "10.0× Platonic Great Years (25,920 years each)"
 *   "1.0× Foundation's Seldon Plan (1,000 sci-fi years)"
 */
fun buildCrossCulturalTimeComparisons(years: BigDecimal): List<String> {
    data class Ref(val value: BigDecimal, val label: String)
    val refs = listOf(
        Ref(CosmicReference.KALI_YUGA_ELAPSED, "the elapsed Kali Yuga (5,127 years)"),
        Ref(CosmicReference.RAGNAROK_CYCLE_NORSE, "Ragnarok cycles (Norse, ~7,000 years each)"),
        Ref(CosmicReference.MUNDANE_ERA_BUDDHIST, "Buddhist Mundane Eras (2,562 years each)"),
        Ref(CosmicReference.GREAT_YEAR_GREEK, "Platonic Great Years (precession, 25,920 years each)"),
        Ref(CosmicReference.KALI_YUGA_HINDU, "Kali Yugas (Hindu, 432,000 years each)"),
        Ref(CosmicReference.MAHA_YUGA_HINDU, "Mahayugas (Hindu, 4.32M years each)"),
        Ref(CosmicReference.HITCHHIKERS_DEEP_THOUGHT, "Deep Thought computations (Hitchhiker's Guide, 7.5M years each)"),
        Ref(CosmicReference.MANVANTARA_HINDU, "Manvantaras (Hindu, 306.72M years each)"),
        Ref(CosmicReference.KALPA_HINDU, "Kalpas (Hindu, 4.32B years each = 1 day of Brahma)")
    )
    return refs
        .filter { years >= it.value }
        .map {
            val ratio = years.divide(it.value, 1, RoundingMode.HALF_UP).stripTrailingZeros()
            "${ratio.toPlainString()}× ${it.label}"
        }
}

/**
 * Fiction / non-fiction pop-culture comparisons for a mantra total.
 * Includes only comparisons where totalMantras >= the reference value.
 *
 * Tone: respectful, devotional. Avoids mocking references.
 */
fun buildFictionComparisons(totalMantras: BigInteger): List<String> {
    data class Ref(val value: BigInteger, val label: String)
    val refs = listOf(
        Ref(BigInteger("10000000000"), "the estimated pages in all Hogwarts library books (~10B)"),
        Ref(BigInteger("30000000000000"), "the souls Dante placed across all circles of the Inferno (~3 × 10^13)"),
        Ref(BigInteger("1000000000000000000"), "the storage of Foundation's Encyclopedia Galactica (Asimov, ~1Qi entries)"),
        Ref(CosmicReference.GRAINS_OF_SAND_EARTH, "the grains of sand Slartibartfast shaped into Norwegian fjords (Hitchhiker's Guide)")
    )
    return refs
        .filter { totalMantras >= it.value }
        .map {
            val ratio = totalMantras.toBigDecimal()
                .divide(it.value.toBigDecimal(), 1, RoundingMode.HALF_UP)
                .stripTrailingZeros()
            "${ratio.toPlainString()}× ${it.label}"
        }
}

// ===== Rate comparisons =====

/**
 * Rate comparisons for an hourly mantra output. Shows what one hour of spinning
 * produces in terms of human-scale recitation practice.
 *
 * Always returns at least 2 entries.
 *
 * Example output:
 *   "Each hour = 1.1× years of 1 person reciting 24/7 at 100 mantras/min"
 *   "Each hour = 1,900,000 monks reciting 8 hrs/day for 1 hour"
 *   "Each hour = 60× the traditional 100M Om Mani Padme Hum milestone"
 */
fun buildRateComparison(mantrasPerHour: BigInteger): List<String> {
    if (mantrasPerHour <= BigInteger.ZERO) return listOf("Begin spinning to see rate comparisons")

    val result = mutableListOf<String>()

    // 1 person reciting 24/7 at 100/min for 1 year = 100 × 60 × 24 × 365.25 = 52,596,000 mantras/year
    val onePersonOneYear = BigDecimal("52596000")
    val yearsPerHour = mantrasPerHour.toBigDecimal().divide(onePersonOneYear, 1, RoundingMode.HALF_UP)
    if (yearsPerHour >= BigDecimal("0.1")) {
        result.add("Each hour = ${yearsPerHour.stripTrailingZeros().toPlainString()} years of 1 person reciting 24/7 at 100 mantras/min")
    }

    // 1 monk reciting 8 hrs/day produces: 100 × 60 × 8 = 48,000 mantras/day
    val oneMonkOneDay = BigInteger("48000")
    if (mantrasPerHour >= oneMonkOneDay) {
        val monks = mantrasPerHour.divide(oneMonkOneDay)
        result.add("Each hour = ${NumberFormatter.format(monks)} monks reciting 8 hrs/day for 1 hour")
    }

    // The traditional 100M milestone
    val hundredM = CosmicReference.TRADITIONAL_100M_MILESTONE
    if (mantrasPerHour >= hundredM) {
        val multiples = mantrasPerHour.toBigDecimal()
            .divide(hundredM.toBigDecimal(), 1, RoundingMode.HALF_UP)
            .stripTrailingZeros()
        result.add("Each hour = ${multiples.toPlainString()}× the traditional 100M Om Mani Padme Hum milestone")
    }

    // Mala circuits
    val mala = CosmicReference.MALA_BEADS
    if (mantrasPerHour >= mala.multiply(BigInteger("1000"))) {
        val circuits = mantrasPerHour.divide(mala)
        result.add("Each hour = ${NumberFormatter.format(circuits)} complete mala circuits (108 each)")
    }

    return if (result.isEmpty()) listOf("Each hour = ${NumberFormatter.format(mantrasPerHour)} mantras") else result
}

// ===== Cross-generational comparisons =====

/**
 * Scales a per-year mantra rate across increasing timescales, from 1 human lifetime
 * to deep cosmic time. Always returns 10 entries.
 *
 * Each line includes a cosmic-context suffix for very long spans.
 *
 * Example output:
 *   "10 years: 100T mantras"
 *   "80 years (1 lifetime): 800T mantras"
 *   "1,000 years (~30 generations): 100Qa mantras"
 *   "100M years (since T-rex): 1Sp mantras (1.5× time since dinosaurs)"
 */
fun buildCrossGenerational(mantrasPerYear: BigInteger): List<String> {
    if (mantrasPerYear <= BigInteger.ZERO) return listOf("Set your rate to see cross-generational projections")

    data class Span(val years: Long, val label: String, val cosmicSuffix: String?)
    val spans = listOf(
        Span(10L, "10 years", null),
        Span(80L, "80 years (1 lifetime)", null),
        Span(1_000L, "1,000 years (~30 generations)", null),
        Span(10_000L, "10,000 years (~400 generations)", null),
        Span(100_000L, "100,000 years (~4,000 generations)", null),
        Span(1_000_000L, "1 million years (~40,000 generations)", null),
        Span(10_000_000L, "10 million years (~400,000 generations, early mammals)", "1.5× time since dinosaurs"),
        Span(100_000_000L, "100 million years (~4M generations, since T-rex)", "1.5× time since dinosaurs"),
        Span(1_000_000_000L, "1 billion years (~40M generations)", "1.2× time until Sun dies"),
        Span(10_000_000_000L, "10 billion years (older than the universe)", "5× remaining lifetime of the Sun")
    )

    return spans.map { span ->
        val total = mantrasPerYear.multiply(BigInteger.valueOf(span.years))
        val base = "${span.label}: ${NumberFormatter.formatWithFull(total)}"
        if (span.cosmicSuffix != null) "$base ($span.cosmicSuffix)" else base
    }
}
