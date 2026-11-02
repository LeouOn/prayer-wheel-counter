package com.prayerwheel.app.ui.wheel.calculator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.math.abs

class CalculatorMathTest {

    private fun bi(v: Long) = BigInteger.valueOf(v)

    @Test
    fun month_isNotTruncatedToFourWeeks() {
        // Old code: week * (13 / 3) == week * 4.
        val week = bi(3_000_000)
        val month = mantrasPerMonthFromWeekly(week)
        assertEquals(bi(13_044_642), month) // 3,000,000 * 30.4375 / 7, floored
        assertTrue(month > week * bi(13) / bi(3))
        assertTrue(month > week * bi(4))
    }

    @Test
    fun perDayRate_givesCalendarAverages() {
        val week = bi(7_000) // 1,000 a day, every day
        assertEquals(bi(30_437), mantrasPerMonthFromWeekly(week))
        assertEquals(bi(365_250), mantrasPerYearFromWeekly(week))
        assertEquals(bi(365_250), mantrasOverDays(bi(1_000), DAYS_PER_YEAR))
    }

    @Test
    fun oneYearProjection_matchesPerYear() {
        for (week in listOf(bi(1), bi(7), bi(12_345), bi(9_876_543_210), BigInteger.TEN.pow(40))) {
            assertEquals(mantrasPerYearFromWeekly(week), mantrasOverMonthsFromWeekly(week, 12))
        }
    }

    @Test
    fun projections_scaleWithMonths() {
        val week = bi(70_000)
        assertEquals(mantrasPerMonthFromWeekly(week), mantrasOverMonthsFromWeekly(week, 1))
        assertEquals(mantrasPerYearFromWeekly(week) * bi(10), mantrasOverMonthsFromWeekly(week, 120))
    }

    @Test
    fun hugeRates_stayExactBigInteger() {
        val week = BigInteger.TEN.pow(30)
        assertEquals(BigInteger("52178571428571428571428571428571"), mantrasPerYearFromWeekly(week))
    }

    @Test
    fun zeroRate_isZero() {
        assertEquals(BigInteger.ZERO, mantrasPerMonthFromWeekly(BigInteger.ZERO))
        assertEquals(BigInteger.ZERO, mantrasOverDays(BigInteger.ZERO, DAYS_PER_YEAR))
    }

    @Test
    fun monthAndYearConstants_agree() {
        assertEquals(0, DAYS_PER_MONTH.multiply(BigDecimal(12)).compareTo(DAYS_PER_YEAR))
    }

    @Test
    fun timeToMilestones_noRate() {
        assertEquals(listOf("Start spinning to see projections"), buildTimeToMilestones(BigInteger.ZERO))
    }

    @Test
    fun timeToMilestones_months() {
        val lines = buildTimeToMilestones(bi(50_000_000))
        assertEquals("Reaching 100M mantras: 2 months", lines.first())
    }

    @Test
    fun timeToMilestones_singularMonth() {
        assertEquals("Reaching 100M mantras: 1 month", buildTimeToMilestones(bi(100_000_000)).first())
    }

    @Test
    fun timeToMilestones_fastRate_neverShowsZero() {
        val lines = buildTimeToMilestones(BigInteger.TEN.pow(15))
        assertEquals("Reaching 100M mantras: less than a day", lines.first())
        assertTrue(lines.none { it.endsWith(": 0 months") || it.endsWith(": 0 days") })
    }

    @Test
    fun timeToMilestones_days() {
        // 1B per month: 100M takes a tenth of a month, 3.04375 days.
        assertEquals("Reaching 100M mantras: 3 days", buildTimeToMilestones(bi(1_000_000_000)).first())
    }

    @Test
    fun monasteryMilestones_days() {
        assertEquals("Reaching 100M: 10 days", buildMonasteryMilestones(bi(10_000_000)).first())
        assertEquals(listOf("Set parameters to see milestones"), buildMonasteryMilestones(BigInteger.ZERO))
    }

    @Test
    fun monasteryMilestones_fastRate_neverShowsZero() {
        assertEquals("Reaching 100M: less than a day", buildMonasteryMilestones(BigInteger.TEN.pow(12)).first())
    }

    @Test
    fun describeDays_yearsUseYearBasis() {
        // 730.5 days == 2 mean years.
        assertTrue(describeDays(BigDecimal("730.5")).contains("2"))
    }

    @Test
    fun slider_endpoints() {
        assertEquals(1L, sliderPositionToLongValue(0f))
        assertEquals(SLIDER_MAX_VALUE, sliderPositionToLongValue(1f))
        assertEquals(0f, longToSliderPosition(1L))
        assertEquals(1f, longToSliderPosition(SLIDER_MAX_VALUE))
        assertEquals(1f, longToSliderPosition(Long.MAX_VALUE))
    }

    @Test
    fun slider_smallPowersOfTen_roundTripExactly() {
        var value = 1L
        repeat(5) {
            assertEquals(value, sliderPositionToLongValue(longToSliderPosition(value)))
            value *= 10
        }
    }

    @Test
    fun slider_largeValues_roundTripWithinFloatPrecision() {
        var value = 10L
        repeat(17) {
            val back = sliderPositionToLongValue(longToSliderPosition(value))
            val relErr = abs(back.toDouble() - value.toDouble()) / value.toDouble()
            assertTrue("value=$value back=$back", relErr < 1e-4)
            value *= 10
        }
    }

    @Test
    fun slider_isMonotonic() {
        var previous = 0L
        for (step in 0..1000) {
            val v = sliderPositionToLongValue(step / 1000f)
            assertTrue("step=$step", v >= previous)
            previous = v
        }
    }
}
