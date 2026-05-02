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

    val ALL = listOf(TRADITIONAL_GOLD, SILVER, COPPER, IVORY, JADE, OBSIDIAN)

    fun byId(id: String): WheelSkin? = ALL.find { it.id == id }

    fun default(): WheelSkin = TRADITIONAL_GOLD
}
