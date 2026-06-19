package com.prayerwheel.app.data.model

/**
 * Wheel skin data model for customizing prayer wheel appearance.
 *
 * Each skin defines colors for different parts of the prayer wheel:
 * - cylinderColor: Main cylinder body
 * - capColor: Top and bottom caps/bevels
 * - stemColor: Stem/handle
 * - crystalColor: Crystal/gem finial
 * - weightColor: String weight/bead
 * - rayColor: Light rays when spinning
 */
data class WheelSkin(
    val id: String,
    val name: String,
    val cylinderColor: Long,      // Main cylinder color
    val capColor: Long,           // Cap/bevel color
    val stemColor: Long,          // Stem/handle color
    val crystalColor: Long,       // Crystal/gem color
    val weightColor: Long,        // Weight/bead color
    val rayColor: Long            // Light ray color
)

/**
 * Predefined wheel skins available for selection.
 */
object WheelSkins {
    val TRADITIONAL_GOLD = WheelSkin(
        id = "traditional_gold",
        name = "Traditional Gold",
        cylinderColor = 0xFFD4A030,  // Golden bronze
        capColor = 0xFFF5DC80,       // Light gold
        stemColor = 0xFF5D4037,       // Dark wood brown
        crystalColor = 0xFF81D4FA,    // Light blue crystal
        weightColor = 0xFF8D6E63,     // Brown bead
        rayColor = 0xFFFFD700        // Gold rays
    )

    val SILVER = WheelSkin(
        id = "silver",
        name = "Silver",
        cylinderColor = 0xFFC0C0C0,  // Silver
        capColor = 0xFFE8E8E8,        // Light silver
        stemColor = 0xFF4A4A4A,      // Dark metal
        crystalColor = 0xFFE1F5FE,    // Light blue crystal
        weightColor = 0xFF9E9E9E,    // Gray bead
        rayColor = 0xFFE0E0E0        // Silver rays
    )

    val COPPER = WheelSkin(
        id = "copper",
        name = "Copper",
        cylinderColor = 0xFFB87333,  // Copper
        capColor = 0xFFDA8A67,        // Light copper
        stemColor = 0xFF4A3728,       // Dark wood
        crystalColor = 0xFFB3E5FC,    // Light blue crystal
        weightColor = 0xFF8B6914,     // Bronze bead
        rayColor = 0xFFFFB900        // Copper rays
    )

    val IVORY = WheelSkin(
        id = "ivory",
        name = "Ivory",
        cylinderColor = 0xFFFFFFF0,   // Ivory
        capColor = 0xFFFFF8DC,        // Cream
        stemColor = 0xFF8B7355,       // Antique brown
        crystalColor = 0xFFFFF9C4,   // Pale yellow crystal
        weightColor = 0xFFD4A030,    // Amber bead
        rayColor = 0xFFFFF8DC        // Cream rays
    )

    val JADE = WheelSkin(
        id = "jade",
        name = "Jade",
        cylinderColor = 0xFF00A86B,  // Jade green
        capColor = 0xFF7FD8A6,        // Light jade
        stemColor = 0xFF2D4A3E,       // Dark green wood
        crystalColor = 0xFFADFF2F,    // Yellow-green crystal
        weightColor = 0xFF556B2F,     // Dark olive bead
        rayColor = 0xFF90EE90         // Light green rays
    )

    val OBSIDIAN = WheelSkin(
        id = "obsidian",
        name = "Obsidian",
        cylinderColor = 0xFF1C1C1C,   // Black
        capColor = 0xFF3D3D3D,        // Dark gray
        stemColor = 0xFF0D0D0D,       // Near black
        crystalColor = 0xFF4A4A4A,    // Gray crystal
        weightColor = 0xFF2F2F2F,     // Charcoal bead
        rayColor = 0xFF6A6A6A         // Gray rays
    )

    val SANDALWOOD = WheelSkin(
        id = "sandalwood",
        name = "Sandalwood",
        cylinderColor = 0xFF8C6239,  // Warm sandalwood brown
        capColor = 0xFFA67C52,       // Light wood
        stemColor = 0xFF5C3A21,      // Dark wood
        crystalColor = 0xFFFFD700,    // Golden crystal
        weightColor = 0xFF704214,     // Dark bead
        rayColor = 0xFFD2B48C        // Sandalwood tan rays
    )

    val LAPIS_LAZULI = WheelSkin(
        id = "lapis_lazuli",
        name = "Lapis Lazuli",
        cylinderColor = 0xFF264395,  // Deep lapis blue
        capColor = 0xFF4A68B0,       // Medium lapis
        stemColor = 0xFF1B2A4A,      // Dark blue-black handle
        crystalColor = 0xFFE0E0E0,    // White quartz crystal
        weightColor = 0xFF5072A7,     // Lapis bead
        rayColor = 0xFF3F51B5        // Blue rays
    )

    val TURQUOISE = WheelSkin(
        id = "turquoise",
        name = "Turquoise",
        cylinderColor = 0xFF30D5C8,  // Turquoise teal
        capColor = 0xFF4DEEEA,       // Bright teal
        stemColor = 0xFF2F4F4F,      // Slate handle
        crystalColor = 0xFFE0F7FA,    // Cyan crystal
        weightColor = 0xFF00ACC1,     // Teal bead
        rayColor = 0xFF00E5FF        // Teal rays
    )

    val RUBY = WheelSkin(
        id = "ruby",
        name = "Ruby",
        cylinderColor = 0xFFE0115F,  // Deep ruby red
        capColor = 0xFFFF4081,       // Ruby rose
        stemColor = 0xFF4A0E17,      // Crimson handle
        crystalColor = 0xFFFFD700,    // Golden finial
        weightColor = 0xFFC2185B,     // Red bead
        rayColor = 0xFFFF2A6D        // Ruby rays
    )

    val AMETHYST = WheelSkin(
        id = "amethyst",
        name = "Amethyst",
        cylinderColor = 0xFF9966CC,  // Amethyst purple
        capColor = 0xFFBA55D3,       // Violet cap
        stemColor = 0xFF483D8B,      // Dark slate purple
        crystalColor = 0xFFE8D7F1,    // Light lavender crystal
        weightColor = 0xFF8A2BE2,     // Purple bead
        rayColor = 0xFFD8BFD8        // Purple rays
    )

    val RAINBOW = WheelSkin(
        id = "rainbow",
        name = "Rainbow",
        cylinderColor = 0xFFFF007F,  // Pink/magenta base
        capColor = 0xFF9400D3,       // Violet cap
        stemColor = 0xFF00FF00,      // Green handle
        crystalColor = 0xFF00FFFF,    // Cyan crystal
        weightColor = 0xFFFFFF00,     // Yellow bead
        rayColor = 0xFFFF8C00        // Orange rays
    )

    val ALL = listOf(
        TRADITIONAL_GOLD, SILVER, COPPER, IVORY, JADE, OBSIDIAN,
        SANDALWOOD, LAPIS_LAZULI, TURQUOISE, RUBY, AMETHYST, RAINBOW
    )

    fun byId(id: String): WheelSkin? = ALL.find { it.id == id }

    fun default(): WheelSkin = TRADITIONAL_GOLD
}
