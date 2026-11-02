package com.prayerwheel.app.ui.wheel.calculator

import com.prayerwheel.app.ui.components.NumberFormatter
import com.prayerwheel.app.ui.components.buildCosmicQuantityComparisons
import com.prayerwheel.app.ui.components.buildCrossGenerational
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.util.Locale
import kotlin.math.pow

internal fun formatHoursReadably(hours: Float): String {
    return when {
        hours >= 8766f -> String.format(Locale.getDefault(), "%,.1f years", hours / 8766f)
        hours >= 48f -> String.format(Locale.getDefault(), "%,.0f days", hours / 24f)
        else -> String.format(Locale.getDefault(), "%,.1f hours", hours)
    }
}

internal fun buildComparisons(totalMantras: BigInteger): List<String> {
    val comparisons = mutableListOf<String>()

    // NEW: Cosmic/scientific quantity comparisons first (only shown when applicable)
    comparisons.addAll(buildCosmicQuantityComparisons(totalMantras))

    // PRESERVED: Original comparisons
    val verbalPerHour = BigInteger.valueOf(6_000L)
    val mala = BigInteger.valueOf(108)
    val hundredM = BigInteger.valueOf(100_000_000L)
    val worldPop = BigInteger.valueOf(8_000_000_000L)

    if (totalMantras >= BigInteger.valueOf(10_000)) {
        comparisons.add(NumberFormatter.formatMalaEquivalent(totalMantras))
    }

    if (totalMantras >= BigInteger.valueOf(100_000)) {
        val hoursBD = totalMantras.toBigDecimal()
            .divide(verbalPerHour.toBigDecimal(), 1, RoundingMode.HALF_UP)
        val yearsBD = hoursBD.divide(BigDecimal(8766), 1, RoundingMode.HALF_UP)
        comparisons.add(
            if (yearsBD >= BigDecimal.ONE) {
                "Same as 1 person reciting non-stop for ${NumberFormatter.humanizeYearsWithCosmicContext(yearsBD)}"
            } else {
                val daysBD = hoursBD.divide(BigDecimal(24), 1, RoundingMode.HALF_UP)
                if (daysBD >= BigDecimal.ONE) {
                    "Same as 1 person reciting non-stop for ${daysBD.stripTrailingZeros().toPlainString()} days"
                } else {
                    "Same as 1 person reciting non-stop for ${hoursBD.stripTrailingZeros().toPlainString()} hours"
                }
            }
        )
    }

    if (totalMantras >= hundredM) {
        val count = totalMantras.divide(hundredM)
        comparisons.add("${NumberFormatter.format(count)}× the traditional 100M milestone (bhumi)")
    }

    if (totalMantras >= worldPop) {
        val count = totalMantras.divide(worldPop)
        comparisons.add("Same as every person on Earth reciting ${NumberFormatter.format(count)} mantras each")
    }

    return comparisons.ifEmpty { listOf("Accumulation in progress") }
}

internal fun buildLifetimeComparisons(mantrasPerYear: BigInteger): List<String> {
    return buildCrossGenerational(mantrasPerYear)
}

internal fun buildTimeToMilestones(mantrasPerMonth: BigInteger): List<String> {
    if (mantrasPerMonth <= BigInteger.ZERO) return listOf("Start spinning to see projections")

    val milestones = mutableListOf<String>()

    val targets = listOf(
        BigInteger.valueOf(100_000_000L) to "100M",
        BigInteger.valueOf(1_000_000_000L) to "1B",
        BigInteger.valueOf(10_000_000_000L) to "10B",
        BigInteger.valueOf(100_000_000_000L) to "100B",
        BigInteger.valueOf(1_000_000_000_000L) to "1T",
        BigInteger.valueOf(10_000_000_000_000L) to "10T",
        BigInteger.valueOf(100_000_000_000_000L) to "100T",
        BigInteger("1000000000000000") to "1Qa",
        BigInteger("10000000000000000") to "10Qa",
        BigInteger("1000000000000000000") to "1Qi",
        BigInteger("10000000000000000000") to "10Qi",
        BigInteger("1000000000000000000000") to "1Sx",
        BigInteger("10000000000000000000000") to "10Sx",
        BigInteger("1000000000000000000000000") to "1Sp",
        BigInteger("10000000000000000000000000") to "10Sp",
        BigInteger("1000000000000000000000000000") to "1Oc",
        BigInteger("10000000000000000000000000000") to "10Oc",
        BigInteger("1000000000000000000000000000000") to "1No",
        BigInteger("10000000000000000000000000000000") to "10No",
        BigInteger("1000000000000000000000000000000000") to "1Dc"
    )

    for ((targetBI, label) in targets) {
        val monthsBD = targetBI.toBigDecimal()
            .divide(mantrasPerMonth.toBigDecimal(), 1, RoundingMode.HALF_UP)
        val yearsBD = monthsBD.divide(BigDecimal(12), 1, RoundingMode.HALF_UP)
        val timeStr = if (yearsBD < BigDecimal.ONE) {
            "${monthsBD.stripTrailingZeros().toPlainString()} months"
        } else {
            NumberFormatter.humanizeYearsWithCosmicContext(yearsBD)
        }
        milestones.add("Reaching $label mantras: $timeStr")
    }

    return milestones
}

internal fun buildMonasteryMilestones(mantrasPerDayTotal: BigInteger): List<String> {
    if (mantrasPerDayTotal <= BigInteger.ZERO) return listOf("Set parameters to see milestones")

    val milestones = mutableListOf<String>()

    val targets = listOf(
        BigInteger.valueOf(100_000_000L) to "100M",
        BigInteger.valueOf(1_000_000_000L) to "1B",
        BigInteger.valueOf(10_000_000_000L) to "10B",
        BigInteger.valueOf(100_000_000_000L) to "100B",
        BigInteger.valueOf(1_000_000_000_000L) to "1T",
        BigInteger.valueOf(10_000_000_000_000L) to "10T",
        BigInteger.valueOf(100_000_000_000_000L) to "100T",
        BigInteger("1000000000000000") to "1Qa",
        BigInteger("10000000000000000") to "10Qa",
        BigInteger("1000000000000000000") to "1Qi",
        BigInteger("10000000000000000000") to "10Qi",
        BigInteger("1000000000000000000000") to "1Sx",
        BigInteger("10000000000000000000000") to "10Sx",
        BigInteger("1000000000000000000000000") to "1Sp",
        BigInteger("10000000000000000000000000") to "10Sp",
        BigInteger("1000000000000000000000000000") to "1Oc",
        BigInteger("10000000000000000000000000000") to "10Oc",
        BigInteger("1000000000000000000000000000000") to "1No",
        BigInteger("10000000000000000000000000000000") to "10No",
        BigInteger("1000000000000000000000000000000000") to "1Dc"
    )

    for ((targetBI, label) in targets) {
        val daysBD = targetBI.toBigDecimal()
            .divide(mantrasPerDayTotal.toBigDecimal(), 1, RoundingMode.HALF_UP)
        val yearsBD = daysBD.divide(BigDecimal("365.25"), 1, RoundingMode.HALF_UP)
        val timeStr = if (yearsBD < BigDecimal.ONE) {
            "${daysBD.stripTrailingZeros().toPlainString()} days"
        } else {
            NumberFormatter.humanizeYearsWithCosmicContext(yearsBD)
        }
        milestones.add("Reaching $label: $timeStr")
    }

    return milestones
}

internal fun longToSliderPosition(value: Long): Float {
    if (value <= 1) return 0f
    if (value >= 1_000_000_000_000_000_000L) return 1f  // Cap at 1Qi (10^18)
    val logMax = 18f  // log10 of 10^18
    val logValue = kotlin.math.ln(value.toFloat()) / kotlin.math.ln(10f)
    return (logValue / logMax).coerceIn(0f, 1f)
}

internal fun sliderPositionToLongValue(position: Float): Long {
    if (position <= 0f) return 1L
    val logMax = 18f  // log10 of 10^18
    val logValue = position * logMax
    val value = 10.0.pow(logValue.toDouble())
    return value.toLong().coerceIn(1L, Long.MAX_VALUE)
}
