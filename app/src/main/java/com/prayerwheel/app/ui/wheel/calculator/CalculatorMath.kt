package com.prayerwheel.app.ui.wheel.calculator

import com.prayerwheel.app.ui.components.NumberFormatter
import com.prayerwheel.app.ui.components.buildCosmicQuantityComparisons
import com.prayerwheel.app.ui.components.buildCrossGenerational
import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext
import java.math.RoundingMode
import java.util.Locale
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToLong

/**
 * Calendar basis for every calculator projection: the mean Gregorian year
 * (365.25 days, leap years included) and its twelfth (30.4375 days).
 * Using one basis keeps "per month" x 12 equal to "per year", and the
 * "1 Year" projection equal to the "per year" figure.
 */
internal val DAYS_PER_YEAR: BigDecimal = BigDecimal("365.25")
internal val DAYS_PER_MONTH: BigDecimal = BigDecimal("30.4375")
private val DAYS_PER_WEEK: BigDecimal = BigDecimal(7)

/** Mantras accumulated over [days] calendar days at [mantrasPerDay], rounded down. */
internal fun mantrasOverDays(mantrasPerDay: BigInteger, days: BigDecimal): BigInteger =
    mantrasPerDay.toBigDecimal().multiply(days).setScale(0, RoundingMode.DOWN).toBigInteger()

/**
 * Mantras accumulated over [days] calendar days given a weekly total
 * (which already reflects practice days per week). Multiplies before
 * dividing so nothing is lost to integer division; rounded down.
 */
internal fun mantrasOverDaysFromWeekly(mantrasPerWeek: BigInteger, days: BigDecimal): BigInteger =
    mantrasPerWeek.toBigDecimal().multiply(days)
        .divide(DAYS_PER_WEEK, 0, RoundingMode.DOWN)
        .toBigInteger()

internal fun mantrasPerMonthFromWeekly(mantrasPerWeek: BigInteger): BigInteger =
    mantrasOverDaysFromWeekly(mantrasPerWeek, DAYS_PER_MONTH)

internal fun mantrasPerYearFromWeekly(mantrasPerWeek: BigInteger): BigInteger =
    mantrasOverDaysFromWeekly(mantrasPerWeek, DAYS_PER_YEAR)

/** Projection over a whole number of months, computed from the weekly rate in one step. */
internal fun mantrasOverMonthsFromWeekly(mantrasPerWeek: BigInteger, months: Int): BigInteger =
    mantrasOverDaysFromWeekly(mantrasPerWeek, DAYS_PER_MONTH.multiply(BigDecimal(months)))

/**
 * Human-readable duration for a fractional number of days. Never shows
 * "0 days" for a non-zero time: very short spans read "less than a day".
 */
internal fun describeDays(days: BigDecimal): String {
    val years = days.divide(DAYS_PER_YEAR, 1, RoundingMode.HALF_UP)
    if (years >= BigDecimal.ONE) return NumberFormatter.humanizeYearsWithCosmicContext(years)
    val months = days.divide(DAYS_PER_MONTH, 1, RoundingMode.HALF_UP)
    if (months >= BigDecimal.ONE) return quantity(months, "month", "months")
    val roundedDays = days.setScale(1, RoundingMode.HALF_UP)
    if (roundedDays >= BigDecimal.ONE) return quantity(roundedDays, "day", "days")
    return "less than a day"
}

private fun quantity(value: BigDecimal, singular: String, plural: String): String {
    val text = value.stripTrailingZeros().toPlainString()
    return if (value.compareTo(BigDecimal.ONE) == 0) "$text $singular" else "$text $plural"
}

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
        // Full precision until display; rounding the intermediate month count
        // to one decimal turned fast rates into "0 months".
        val daysBD = targetBI.toBigDecimal()
            .multiply(DAYS_PER_MONTH)
            .divide(mantrasPerMonth.toBigDecimal(), MathContext.DECIMAL128)
        milestones.add("Reaching $label mantras: ${describeDays(daysBD)}")
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
            .divide(mantrasPerDayTotal.toBigDecimal(), MathContext.DECIMAL128)
        milestones.add("Reaching $label: ${describeDays(daysBD)}")
    }

    return milestones
}

/** Upper bound of the log-scale "mantras per rotation" slider: 10^18 (1Qi). */
internal const val SLIDER_MAX_VALUE: Long = 1_000_000_000_000_000_000L
private const val SLIDER_LOG_MAX = 18.0 // log10(SLIDER_MAX_VALUE)

/** Maps 1..10^18 onto 0..1 on a log10 scale (double precision). */
internal fun longToSliderPosition(value: Long): Float {
    if (value <= 1) return 0f
    if (value >= SLIDER_MAX_VALUE) return 1f
    return (log10(value.toDouble()) / SLIDER_LOG_MAX).toFloat().coerceIn(0f, 1f)
}

/**
 * Inverse of [longToSliderPosition]. Rounds to the nearest whole value rather
 * than truncating, so a position that lands a hair under a power of ten
 * (e.g. 999,999.9998) reads 1,000,000, not 999,999.
 */
internal fun sliderPositionToLongValue(position: Float): Long {
    if (position <= 0f) return 1L
    if (position >= 1f) return SLIDER_MAX_VALUE
    val value = 10.0.pow(position.toDouble() * SLIDER_LOG_MAX)
    return value.roundToLong().coerceIn(1L, SLIDER_MAX_VALUE)
}
