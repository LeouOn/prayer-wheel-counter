package com.prayerwheel.app.ui.components

import java.math.BigInteger
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale

object NumberFormatter {

    private val suffixes = listOf(
        "", "K", "M", "B", "T",
        "Qa", "Qi", "Sx", "Sp", "Oc",
        "No", "Dc", "UDc", "DDc", "TDc",
        "QaDc", "QiDc", "SxDc", "SpDc", "OcDc",
        "NoDc", "Vg"
    )

    private val doubleSuffixes = listOf(
        "", "K", "M", "B", "T",
        "aa", "ab", "ac", "ad", "ae", "af", "ag", "ah", "ai", "aj",
        "ak", "al", "am", "an", "ao", "ap", "aq", "ar", "as", "at",
        "au", "av", "aw", "ax", "ay", "az",
        "ba", "bb", "bc", "bd", "be", "bf", "bg", "bh", "bi", "bj",
        "bk", "bl", "bm", "bn", "bo", "bp", "bq", "br", "bs", "bt",
        "bu", "bv", "bw", "bx", "by", "bz"
    )

    fun format(number: BigInteger, useDoubleNotation: Boolean = false): String {
        if (number < BigInteger.valueOf(1000)) return number.toString()

        val suffixList = if (useDoubleNotation) doubleSuffixes else suffixes
        var value = number.toBigDecimal()
        var suffixIndex = 0
        val thousand = BigDecimal.valueOf(1000)

        while (value >= thousand && suffixIndex < suffixList.size - 1) {
            value = value.divide(thousand, 10, RoundingMode.HALF_UP)
            suffixIndex++
        }

        val suffix = suffixList[suffixIndex]
        val doubleVal = value.toDouble()
        return if (doubleVal >= 100.0) {
            "${doubleVal.toInt()}$suffix"
        } else if (doubleVal >= 10.0) {
            "${"%.1f".format(doubleVal)}$suffix"
        } else {
            "${"%.2f".format(doubleVal)}$suffix"
        }
    }

    fun formatLong(number: Long, useDoubleNotation: Boolean = false): String {
        return format(number.toBigInteger(), useDoubleNotation)
    }

    fun formatWithFull(number: BigInteger, useDoubleNotation: Boolean = false): String {
        val abbreviated = format(number, useDoubleNotation)
        val full = formatWithCommas(number)
        return "$abbreviated ($full)"
    }

    fun formatWithCommas(number: BigInteger): String {
        return NumberFormat.getNumberInstance(Locale.getDefault()).format(number)
    }

    fun formatWithCommas(number: Long): String {
        return NumberFormat.getNumberInstance(Locale.getDefault()).format(number)
    }

    private val longFormNames = listOf(
        "", "Thousand", "Million", "Billion", "Trillion",
        "Quadrillion", "Quintillion", "Sextillion", "Septillion", "Octillion",
        "Nonillion", "Decillion", "Undecillion", "Duodecillion", "Tredecillion",
        "Quattuordecillion", "Quindecillion", "Sexdecillion", "Septendecillion",
        "Octodecillion", "Novemdecillion", "Vigintillion"
    )

    fun formatWithStyle(number: BigInteger, style: com.prayerwheel.app.data.datastore.NumberFormatStyle): String {
        return when (style) {
            com.prayerwheel.app.data.datastore.NumberFormatStyle.STANDARD -> format(number)
            com.prayerwheel.app.data.datastore.NumberFormatStyle.EXACT -> formatWithCommas(number)
            com.prayerwheel.app.data.datastore.NumberFormatStyle.SCIENTIFIC -> formatScientific(number)
            com.prayerwheel.app.data.datastore.NumberFormatStyle.LONG_FORM -> formatLongForm(number)
        }
    }

    fun formatWithStyle(number: Long, style: com.prayerwheel.app.data.datastore.NumberFormatStyle): String {
        return formatWithStyle(number.toBigInteger(), style)
    }

    private fun formatScientific(number: BigInteger): String {
        if (number < BigInteger.valueOf(1_000_000L)) return formatWithCommas(number)
        val str = number.toString()
        val exponent = str.length - 1
        val mantissa = "${str[0]}.${str.substring(1, 3)}"
        return "$mantissa x 10^$exponent"
    }

    fun formatLongForm(number: BigInteger): String {
        if (number < BigInteger.valueOf(1_000L)) return formatWithCommas(number)
        var value = number.toBigDecimal()
        var suffixIndex = 0
        val thousand = BigDecimal.valueOf(1000)
        while (value >= thousand && suffixIndex < longFormNames.size - 1) {
            value = value.divide(thousand, 10, RoundingMode.HALF_UP)
            suffixIndex++
        }
        val name = longFormNames.getOrElse(suffixIndex) { suffixes.getOrElse(suffixIndex) { "" } }
        val doubleVal = value.toDouble()
        return if (doubleVal >= 100.0) {
            "${doubleVal.toInt()} $name"
        } else if (doubleVal >= 10.0) {
            "${"%.1f".format(doubleVal)} $name"
        } else {
            "${"%.2f".format(doubleVal)} $name"
        }
    }

    // ===== Cosmic Comparison Helpers =====

    fun formatMalaEquivalent(mantras: BigInteger): String {
        val mala = BigInteger("108")
        if (mantras < mala) return "< 1 mala circuit"
        val circuits = mantras.divide(mala)
        return "${format(circuits)} mala circuits (108 each)"
    }

    fun humanizeYears(years: BigDecimal): String {
        if (years <= BigDecimal.ZERO) return "0 years"
        val oneMonth = BigDecimal("0.083333")  // 1/12
        return when {
            years < oneMonth -> {
                val days = years.multiply(BigDecimal("365.25")).setScale(0, RoundingMode.HALF_UP)
                "$days days"
            }
            years < BigDecimal.ONE -> {
                val months = years.multiply(BigDecimal("12")).setScale(1, RoundingMode.HALF_UP)
                "$months months"
            }
            years < BigDecimal.valueOf(1000) -> {
                if (years >= BigDecimal.valueOf(10)) {
                    "${years.setScale(0, RoundingMode.HALF_UP)} years"
                } else {
                    "${years.setScale(1, RoundingMode.HALF_UP)} years"
                }
            }
            years < BigDecimal.valueOf(1_000_000) -> {
                val v = years.divide(BigDecimal.valueOf(1000), 1, RoundingMode.HALF_UP)
                "$v thousand years"
            }
            years < BigDecimal.valueOf(1_000_000_000) -> {
                val v = years.divide(BigDecimal.valueOf(1_000_000), 1, RoundingMode.HALF_UP)
                "$v million years"
            }
            years < BigDecimal.valueOf(1_000_000_000_000L) -> {
                val v = years.divide(BigDecimal.valueOf(1_000_000_000), 1, RoundingMode.HALF_UP)
                "$v billion years"
            }
            years < BigDecimal("1000000000000000") -> {
                val v = years.divide(BigDecimal.valueOf(1_000_000_000_000L), 1, RoundingMode.HALF_UP)
                "$v trillion years"
            }
            else -> {
                // Use scientific notation for >= 1 quadrillion years
                val s = years.toBigInteger().toString()
                val exponent = s.length - 1
                val mantissa = "${s[0]}.${s.substring(1, minOf(3, s.length))}"
                "$mantissa × 10^$exponent years"
            }
        }
    }

    fun humanizeYearsWithCosmicContext(years: BigDecimal): String {
        val base = humanizeYears(years)
        return when {
            years >= CosmicReference.AGE_OF_UNIVERSE -> {
                val ratio = years.divide(CosmicReference.AGE_OF_UNIVERSE, 1, RoundingMode.HALF_UP)
                "$base ($ratio× age of universe)"
            }
            years >= CosmicReference.AGE_OF_EARTH -> {
                val ratio = years.divide(CosmicReference.AGE_OF_EARTH, 1, RoundingMode.HALF_UP)
                "$base ($ratio× age of Earth)"
            }
            years >= CosmicReference.TIME_SINCE_DINOSAURS -> {
                val ratio = years.divide(CosmicReference.TIME_SINCE_DINOSAURS, 1, RoundingMode.HALF_UP)
                "$base ($ratio× time since dinosaurs)"
            }
            years >= CosmicReference.TIME_SINCE_HOMO_SAPIENS -> {
                val ratio = years.divide(CosmicReference.TIME_SINCE_HOMO_SAPIENS, 1, RoundingMode.HALF_UP)
                "$base ($ratio× age of Homo sapiens)"
            }
            years >= CosmicReference.TIME_SINCE_PYRAMIDS -> {
                val ratio = years.divide(CosmicReference.TIME_SINCE_PYRAMIDS, 1, RoundingMode.HALF_UP)
                "$base ($ratio× age of Pyramids)"
            }
            else -> base
        }
    }

    fun formatPercent(value: BigInteger, total: BigInteger): String {
        if (total == BigInteger.ZERO) return "0%"
        if (value == BigInteger.ZERO) return "0%"
        if (value >= total) return "100%"
        val percent = value.toBigDecimal()
            .divide(total.toBigDecimal(), 8, RoundingMode.HALF_UP)
            .multiply(BigDecimal("100"))
            .setScale(4, RoundingMode.HALF_UP)
            .stripTrailingZeros()
        val plain = percent.toPlainString()
        return if (percent < BigDecimal("0.0001")) "< 0.0001%" else "$plain%"
    }
}
