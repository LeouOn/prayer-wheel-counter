package com.prayerwheel.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * One entry in the shorthand → longhand reference guide shown in the Practice Calculator.
 * - shorthand: e.g. "Qa" for Quadrillion
 * - longForm: full English name
 * - power: power of 10 as a string, e.g. "10^15"
 * - realWorld: a relatable real-world reference for the scale
 * - example: a practice-relevant example (mantra/mala context)
 */
data class ShortHandEntry(
    val shorthand: String,
    val longForm: String,
    val power: String,
    val realWorld: String,
    val example: String
)

/**
 * The 16-entry reference list covering the suffix ladder used by NumberFormatter:
 * K (10^3) through QiDc (10^48).
 */
val shortHandGuide: List<ShortHandEntry> = listOf(
    ShortHandEntry("K", "Thousand", "10^3", "A small village", "1K = 1,000 = ~9 mala circuits"),
    ShortHandEntry("M", "Million", "10^6", "Population of a small country", "1M = 1,000,000 = ~9,259 malas"),
    ShortHandEntry("B", "Billion", "10^9", "Population of India", "1B = 1,000,000,000 = ~9.26M malas"),
    ShortHandEntry("T", "Trillion", "10^12", "1,000 billions", "1T = 1,000B = ~1 year of 1,000 monks spinning"),
    ShortHandEntry("Qa", "Quadrillion", "10^15", "1,000 trillions", "1Qa = 1,000T = traditional great monastery lifetime"),
    ShortHandEntry("Qi", "Quintillion", "10^18", "1,000 quadrillions = grains of sand on Earth", "1Qi = 1,000Qa"),
    ShortHandEntry("Sx", "Sextillion", "10^21", "1,000 quintillions", "1Sx = 1,000Qi = more mantras than a monastery in 1M years"),
    ShortHandEntry("Sp", "Septillion", "10^24", "1,000 sextillions = stars in observable universe", "1Sp = 1,000Sx"),
    ShortHandEntry("Oc", "Octillion", "10^27", "1,000 septillions", "1Oc = 1,000Sp = atoms in 1 person ÷ 26"),
    ShortHandEntry("No", "Nonillion", "10^30", "1,000 octillions", "1No = 1,000Oc"),
    ShortHandEntry("Dc", "Decillion", "10^33", "1,000 nonillions", "1Dc = 1,000No"),
    ShortHandEntry("UDc", "Undecillion", "10^36", "1,000 decillions", "1UDc = 1,000Dc"),
    ShortHandEntry("DDc", "Duodecillion", "10^39", "1,000 undecillions", "1DDc = 1,000UDc"),
    ShortHandEntry("TDc", "Tredecillion", "10^42", "1,000 duodecillions", "1TDc = 1,000DDc"),
    ShortHandEntry("QaDc", "Quattuordecillion", "10^45", "1,000 tredecillions", "1QaDc = 1,000TDc"),
    ShortHandEntry("QiDc", "Quindecillion", "10^48", "1,000 quattuordecillions", "1QiDc = 1,000QaDc")
)

/**
 * Reference card showing what each NumberFormatter suffix abbreviation means.
 * Embedded inside the Personal and Monastery tabs of the Practice Calculator.
 */
@Composable
fun ShortHandGuideCard(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Shorthand Guide",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "What the suffix abbreviations mean",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )

            shortHandGuide.forEach { entry ->
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = entry.shorthand,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "= ${entry.longForm} (${entry.power})",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Text(
                        text = "${entry.realWorld} · ${entry.example}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}
