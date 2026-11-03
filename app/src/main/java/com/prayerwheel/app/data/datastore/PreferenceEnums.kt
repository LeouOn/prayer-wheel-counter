package com.prayerwheel.app.data.datastore

/**
 * Theme options for the app.
 */
enum class AppTheme {
    LIGHT,
    DARK,
    SEPIA,
    DAWN_DUSK,
    SYSTEM
}

/**
 * Spin modes for the prayer wheel.
 */
enum class SpinMode {
    MANUAL,        // drag-to-spin (current behavior)
    TWO_HANDED,    // two simultaneous touches required
    AUTO_SPIN,     // continuous at configurable RPM
    TWO_HANDED_AUTO // auto-spin combined with two-handed friction
}

/**
 * View modes for the prayer wheel display.
 */
enum class ViewMode {
    SIDE_VIEW,     // side view with stem, cylinder, caps, crystal
    TOP_DOWN,      // top-down circular view with mantra around rim
    ABSTRACT,      // minimalist mandala/sacred geometry representation
    TABLE_TOP,     // table top view with a wider base
    GLOBE          // spherical wheel rotating on an axis
}

/**
 * Number formatting styles for the mantra counter.
 */
enum class NumberFormatStyle {
    STANDARD,      // 1.2M, 5B
    LONG_FORM,     // 1 Million 200 Thousand
    SCIENTIFIC,    // 1.2 x 10^6
    EXACT          // 1,200,000
}

/**
 * Number notation style for displaying large numbers.
 */
enum class NumberNotation {
    STANDARD,      // K, M, B, T, Qa, Qi, etc.
    EXTENDED       // aa, ab, ac, ... az, ba, bb, etc.
}
