package com.prayerwheel.app.ui.components

import java.math.BigInteger
import java.text.NumberFormat
import java.util.Locale

/**
 * Number formatter with extended notation for very large numbers.
 * Supports both standard metric suffixes (K, M, B, T) and extended
 * double-character notation (aa, ab, ac, etc.) like in idle games.
 */
object NumberFormatter {
    
    // Standard metric suffixes
    private val suffixes = listOf(
        "", "K", "M", "B", "T",           // thousand, million, billion, trillion
        "Qa", "Qi", "Sx", "Sp", "Oc",     // quadrillion, quintillion, sextillion, septillion, octillion
        "No", "Dc", "UDc", "DDc", "TDc",  // nonillion, decillion, undecillion, duodecillion, tredecillion
        "QaDc", "QiDc", "SxDc", "SpDc", "OcDc", // quattuordecillion through octodecillion
        "NoDc", "Vg"                       // novemdecillion, vigintillion
    )
    
    // Double-character notation (like antimatter dimensions)
    private val doubleSuffixes = listOf(
        "", "K", "M", "B", "T",
        "aa", "ab", "ac", "ad", "ae", "af", "ag", "ah", "ai", "aj",
        "ak", "al", "am", "an", "ao", "ap", "aq", "ar", "as", "at",
        "au", "av", "aw", "ax", "ay", "az",
        "ba", "bb", "bc", "bd", "be", "bf", "bg", "bh", "bi", "bj",
        "bk", "bl", "bm", "bn", "bo", "bp", "bq", "br", "bs", "bt",
        "bu", "bv", "bw", "bx", "by", "bz"
    )
    
    /**
     * Formats a BigInteger using the specified notation style.
     * @param number The number to format
     * @param useDoubleNotation If true, use extended double-character suffixes (aa, ab, etc.)
     * @return Formatted string like "1.5M" or "1.5aa"
     */
    fun format(number: BigInteger, useDoubleNotation: Boolean = false): String {
        if (number < BigInteger.valueOf(1000)) return number.toString()
        
        val suffixList = if (useDoubleNotation) doubleSuffixes else suffixes
        var value = number.toDouble()
        var suffixIndex = 0
        
        while (value >= 1000.0 && suffixIndex < suffixList.size - 1) {
            value /= 1000.0
            suffixIndex++
        }
        
        val suffix = suffixList[suffixIndex]
        return if (value >= 100.0) {
            "${value.toInt()}$suffix"
        } else if (value >= 10.0) {
            "${"%.1f".format(value)}$suffix"
        } else {
            "${"%.2f".format(value)}$suffix"
        }
    }
    
    /**
     * Formats a number showing both abbreviated and full forms.
     * @param number The number to format
     * @param useDoubleNotation If true, use extended double-character suffixes
     * @return String like "1.5M (1,500,000)"
     */
    fun formatWithFull(number: BigInteger, useDoubleNotation: Boolean = false): String {
        val abbreviated = format(number, useDoubleNotation)
        val full = formatWithCommas(number)
        return "$abbreviated ($full)"
    }
    
    /**
     * Formats a number with locale-aware comma separators.
     * @param number The number to format
     * @return String like "1,500,000"
     */
    fun formatWithCommas(number: BigInteger): String {
        return NumberFormat.getNumberInstance(Locale.getDefault()).format(number)
    }
    
    /**
     * Formats a Long value (for simpler cases).
     */
    fun formatLong(number: Long, useDoubleNotation: Boolean = false): String {
        return format(number.toBigInteger(), useDoubleNotation)
    }
}