package com.prayerwheel.app.ui.components

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.math.BigInteger
import java.util.Locale

class NumberFormatterTest {

    private lateinit var originalLocale: Locale

    @Before
    fun setUp() {
        originalLocale = Locale.getDefault()
        Locale.setDefault(Locale.US)
    }

    @After
    fun tearDown() {
        Locale.setDefault(originalLocale)
    }

    private fun big(s: String) = BigInteger(s)

    @Test
    fun format_belowThousand_isPlain() {
        assertEquals("0", NumberFormatter.format(BigInteger.ZERO))
        assertEquals("999", NumberFormatter.format(BigInteger.valueOf(999)))
    }

    @Test
    fun format_suffixBoundaries() {
        assertEquals("1.00K", NumberFormatter.format(BigInteger.valueOf(1_000)))
        assertEquals("12.3K", NumberFormatter.format(BigInteger.valueOf(12_345)))
        assertEquals("999K", NumberFormatter.format(BigInteger.valueOf(999_999)))
        assertEquals("1.00M", NumberFormatter.format(BigInteger.valueOf(1_000_000)))
        assertEquals("1.00T", NumberFormatter.format(big("1000000000000")))
    }

    @Test
    fun format_beyondLongMax() {
        // Long.MAX_VALUE ≈ 9.22e18 → "Qi" (10^18)
        assertEquals("9.22Qi", NumberFormatter.format(BigInteger.valueOf(Long.MAX_VALUE)))
        assertEquals("9.22Qi", NumberFormatter.format(BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE)))
        assertEquals("1.00Sx", NumberFormatter.format(BigInteger.TEN.pow(21)))
    }

    @Test
    fun currentBehavior_format_pastLastSuffix_staysOnVg() {
        // Suffix list ends at Vg (10^63); larger values keep dividing no further,
        // so 10^66 renders as an integer count of Vg.
        assertEquals("1000Vg", NumberFormatter.format(BigInteger.TEN.pow(66)))
    }

    @Test
    fun format_doubleNotation() {
        assertEquals("1.00T", NumberFormatter.format(big("1000000000000"), useDoubleNotation = true))
        assertEquals("1.00aa", NumberFormatter.format(BigInteger.TEN.pow(15), useDoubleNotation = true))
    }

    @Test
    fun formatWithCommas_usesLocale() {
        assertEquals("1,234,567", NumberFormatter.formatWithCommas(BigInteger.valueOf(1_234_567)))
        assertEquals("1,234,567", NumberFormatter.formatWithCommas(1_234_567L))
    }

    @Test
    fun formatDuration_cases() {
        assertEquals("0s", NumberFormatter.formatDuration(0))
        assertEquals("5s", NumberFormatter.formatDuration(5))
        assertEquals("2m 5s", NumberFormatter.formatDuration(125))
        assertEquals("1h 1m", NumberFormatter.formatDuration(3661))
    }

    @Test
    fun formatPercent_zeroTotalOrValue() {
        assertEquals("0%", NumberFormatter.formatPercent(BigInteger.ONE, BigInteger.ZERO))
        assertEquals("0%", NumberFormatter.formatPercent(BigInteger.ZERO, BigInteger.TEN))
    }

    @Test
    fun formatPercent_valueAtOrAboveTotal_is100() {
        assertEquals("100%", NumberFormatter.formatPercent(BigInteger.TEN, BigInteger.TEN))
        assertEquals("100%", NumberFormatter.formatPercent(BigInteger.valueOf(11), BigInteger.TEN))
    }

    @Test
    fun formatPercent_regularAndTiny() {
        assertEquals("33.3333%", NumberFormatter.formatPercent(BigInteger.ONE, BigInteger.valueOf(3)))
        assertEquals("50%", NumberFormatter.formatPercent(BigInteger.ONE, BigInteger.valueOf(2)))
        assertEquals("0.0001%", NumberFormatter.formatPercent(BigInteger.ONE, BigInteger.valueOf(1_000_000)))
        assertEquals("< 0.0001%", NumberFormatter.formatPercent(BigInteger.ONE, BigInteger.valueOf(1_000_000_000)))
    }

    @Test
    fun currentBehavior_formatPercent_roundsHalfUpIntoMinimumBucket() {
        // 1 / 2,000,000 = 0.00005% — rounded HALF_UP to 4 places it becomes 0.0001%,
        // so it is shown as "0.0001%" rather than "< 0.0001%".
        assertEquals("0.0001%", NumberFormatter.formatPercent(BigInteger.ONE, BigInteger.valueOf(2_000_000)))
    }

    @Test
    fun formatWithStyle_notationOnlyChangesSuffixesAboveTrillion() {
        val std = com.prayerwheel.app.data.datastore.NumberFormatStyle.STANDARD
        val ext = com.prayerwheel.app.data.datastore.NumberNotation.EXTENDED
        assertEquals("1.00T", NumberFormatter.formatWithStyle(big("1000000000000"), std, ext))
        assertEquals("1.00Qa", NumberFormatter.formatWithStyle(big("1000000000000000"), std))
        assertEquals("1.00aa", NumberFormatter.formatWithStyle(big("1000000000000000"), std, ext))
        assertEquals("1.00ab", NumberFormatter.formatWithStyle(big("1000000000000000000"), std, ext))
    }

    @Test
    fun formatWithStyle_notationIgnoredForNonStandardStyles() {
        val exact = com.prayerwheel.app.data.datastore.NumberFormatStyle.EXACT
        val ext = com.prayerwheel.app.data.datastore.NumberNotation.EXTENDED
        assertEquals("1,000,000,000,000,000", NumberFormatter.formatWithStyle(big("1000000000000000"), exact, ext))
    }
}
