package com.prayerwheel.app.ui.wheel.calculator

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.prayerwheel.app.ui.components.NumberFormatter
import com.prayerwheel.app.ui.components.ShortHandGuideCard
import com.prayerwheel.app.ui.components.buildCosmicQuantityComparisons
import com.prayerwheel.app.ui.components.buildCrossGenerational
import com.prayerwheel.app.ui.components.buildRateComparison
import java.math.BigDecimal
import java.math.BigInteger
import java.util.Locale

private enum class ProjectionPeriod(val label: String, val months: Int) {
    ONE_MONTH("1 Month", 1),
    THREE_MONTHS("3 Months", 3),
    SIX_MONTHS("6 Months", 6),
    ONE_YEAR("1 Year", 12),
    FIVE_YEARS("5 Years", 60),
    TEN_YEARS("10 Years", 120)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PersonalCalculator(
    defaultRpm: Float,
    defaultMantrasPerRotation: Long
) {
    var rpm by remember { mutableFloatStateOf(defaultRpm.coerceIn(1f, 120f)) }
    var mantrasPerRotation by remember { mutableStateOf(defaultMantrasPerRotation.coerceAtLeast(1L)) }
    var hoursPerDay by remember { mutableFloatStateOf(2f) }
    var daysPerWeek by remember { mutableIntStateOf(7) }
    var personalRecitationRate by remember { mutableIntStateOf(100) }
    var selectedPeriod by remember { mutableStateOf(ProjectionPeriod.ONE_YEAR) }

    val mantrasPerMinute = remember(rpm, mantrasPerRotation) {
        BigInteger.valueOf(rpm.toLong()).multiply(BigInteger.valueOf(mantrasPerRotation))
    }
    val mantrasPerHour = remember(mantrasPerMinute) {
        mantrasPerMinute * BigInteger.valueOf(60)
    }
    val mantrasPerDay = remember(mantrasPerHour, hoursPerDay) {
        mantrasPerHour.toBigDecimal()
            .multiply(BigDecimal.valueOf(hoursPerDay.toDouble()))
            .toBigInteger()
    }
    val mantrasPerWeek = remember(mantrasPerDay, daysPerWeek) {
        mantrasPerDay * BigInteger.valueOf(daysPerWeek.toLong())
    }
    // All periods share one calendar basis (365.25-day year, see CalculatorMath),
    // so 12 x per month == per year == the "1 Year" projection.
    val mantrasPerMonth = remember(mantrasPerWeek) {
        mantrasPerMonthFromWeekly(mantrasPerWeek)
    }
    val mantrasPerYear = remember(mantrasPerWeek) {
        mantrasPerYearFromWeekly(mantrasPerWeek)
    }

    val projectedTotal = remember(mantrasPerWeek, selectedPeriod) {
        mantrasOverMonthsFromWeekly(mantrasPerWeek, selectedPeriod.months)
    }
    val totalRotations = remember(projectedTotal, mantrasPerRotation) {
        if (mantrasPerRotation > 0) {
            projectedTotal.divide(BigInteger.valueOf(mantrasPerRotation))
        } else BigInteger.ZERO
    }
    val totalHours = remember(projectedTotal, mantrasPerHour) {
        if (mantrasPerHour > BigInteger.ZERO) {
            projectedTotal.divide(mantrasPerHour)
        } else BigInteger.ZERO
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Input Parameters",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )

                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Average RPM", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = "${rpm.toInt()} RPM",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Slider(
                        value = rpm,
                        onValueChange = { rpm = it.coerceIn(1f, 120f) },
                        valueRange = 1f..120f,
                        steps = 118,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Mantras per Rotation", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = NumberFormatter.formatWithCommas(BigInteger.valueOf(mantrasPerRotation)),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Slider(
                        value = longToSliderPosition(mantrasPerRotation),
                        onValueChange = {
                            mantrasPerRotation = sliderPositionToLongValue(it).coerceAtLeast(1L)
                        },
                        valueRange = 0f..1f,
                        steps = 999,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Hours per Day", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = String.format(Locale.getDefault(), "%.1f hrs", hoursPerDay),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Slider(
                        value = hoursPerDay,
                        onValueChange = { hoursPerDay = it.coerceIn(0.5f, 24f) },
                        valueRange = 0.5f..24f,
                        steps = 46,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Column {
                    Text(text = "Days per Week", style = MaterialTheme.typography.bodyMedium)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        (1..7).forEach { day ->
                            FilterChip(
                                selected = daysPerWeek == day,
                                onClick = { daysPerWeek = day },
                                label = { Text("$day") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                                )
                            )
                        }
                    }
                }

                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Verbal recitation while spinning", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = "$personalRecitationRate mantras/min",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Slider(
                        value = personalRecitationRate.toFloat(),
                        onValueChange = { personalRecitationRate = it.toInt().coerceIn(0, 300) },
                        valueRange = 0f..300f,
                        steps = 59,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Column {
                    Text(text = "Projection Period", style = MaterialTheme.typography.bodyMedium)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        ProjectionPeriod.entries.forEach { period ->
                            FilterChip(
                                selected = selectedPeriod == period,
                                onClick = { selectedPeriod = period },
                                label = { Text(period.label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                                )
                            )
                        }
                    }
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Projections",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                HorizontalDivider()
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Projected for ${selectedPeriod.label.lowercase()}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Text(
                        text = "${NumberFormatter.format(projectedTotal)} mantras",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "(${NumberFormatter.formatWithCommas(projectedTotal)})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    StatItem(label = "Total Rotations", value = NumberFormatter.format(totalRotations))
                    StatItem(label = "Spinning Hours", value = NumberFormatter.format(totalHours))
                }
                HorizontalDivider()
                Text(
                    text = "Accumulation Rates",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                StatRow(label = "Per minute", value = NumberFormatter.formatWithFull(mantrasPerMinute))
                StatRow(label = "Per hour", value = NumberFormatter.formatWithFull(mantrasPerHour))
                StatRow(label = "Per day", value = "${NumberFormatter.format(mantrasPerDay)} (${NumberFormatter.formatWithCommas(mantrasPerDay)})")
                StatRow(label = "Per week", value = "${NumberFormatter.format(mantrasPerWeek)} (${NumberFormatter.formatWithCommas(mantrasPerWeek)})")
                StatRow(label = "Per month", value = "${NumberFormatter.format(mantrasPerMonth)} (${NumberFormatter.formatWithCommas(mantrasPerMonth)})")
                StatRow(label = "Per year", value = "${NumberFormatter.format(mantrasPerYear)} (${NumberFormatter.formatWithCommas(mantrasPerYear)})")
            }
        }

        // ===== Dual Practice card =====
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            )
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Dual Practice: Wheel + Recitation",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Traditional Tibetan practice combines wheel and verbal recitation — body, speech, and mind together. The wheel amplifies practice; it does not replace the voice.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                HorizontalDivider()

                val verbalMantrasPerHour = BigInteger.valueOf(personalRecitationRate.toLong() * 60L)
                val verbalMantrasPerDay = verbalMantrasPerHour.toBigDecimal()
                    .multiply(BigDecimal.valueOf(hoursPerDay.toDouble()))
                    .toBigInteger()
                val verbalMantrasPerWeek = verbalMantrasPerDay * BigInteger.valueOf(daysPerWeek.toLong())
                val verbalMantrasPerYear = mantrasPerYearFromWeekly(verbalMantrasPerWeek)
                val combinedPerHour = mantrasPerHour + verbalMantrasPerHour
                val combinedPerYear = mantrasPerYear + verbalMantrasPerYear

                StatRow(label = "Wheel output per hour", value = NumberFormatter.format(mantrasPerHour))
                StatRow(label = "Verbal recitation per hour", value = NumberFormatter.format(verbalMantrasPerHour))
                StatRow(label = "Combined per hour", value = "${NumberFormatter.format(combinedPerHour)} mantras")
                HorizontalDivider()
                StatRow(label = "Wheel output per year", value = NumberFormatter.format(mantrasPerYear))
                StatRow(label = "Verbal recitation per year", value = NumberFormatter.format(verbalMantrasPerYear))
                StatRow(
                    label = "Combined per year",
                    value = NumberFormatter.formatWithFull(combinedPerYear)
                )

                if (personalRecitationRate == 0) {
                    Text(
                        text = "Set the verbal recitation slider above 0 to see your combined practice output.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Time to Milestones",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "At this rate:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                val milestones = buildTimeToMilestones(mantrasPerMonth)
                milestones.forEach { milestone ->
                    Text(text = milestone, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Milestone Comparisons",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Equivalent to:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                val comparisons = buildComparisons(projectedTotal)
                comparisons.forEach { comparison ->
                    Text(text = comparison, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        // ===== NEW: Rate Comparison card =====
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
            )
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Rate Comparison",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "What your spinning rate produces",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                buildRateComparison(mantrasPerHour).forEach { line ->
                    Text(text = line, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        // ===== NEW: Cross-Generational card =====
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Cross-Generational Practice",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "How this rate scales across lifetimes and eras",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                buildCrossGenerational(mantrasPerYear).forEach { line ->
                    Text(text = line, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        // ===== NEW: Cosmic Comparisons card =====
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Cosmic Comparisons",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Your ${selectedPeriod.label.lowercase()} total in cosmic context",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                val cosmicLines = buildCosmicQuantityComparisons(projectedTotal)
                if (cosmicLines.isEmpty()) {
                    Text(
                        text = "Increase your rate or projection period to see cosmic-scale comparisons.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                } else {
                    cosmicLines.forEach { line ->
                        Text(text = line, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        // ===== NEW: Exponential Ladder card (static reference) =====
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            )
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Exponential Ladder",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "What each order of magnitude means",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                val ladder = listOf(
                    "1,000 mantras ≈ 9 mala circuits",
                    "1M mantras ≈ 9,259 malas (~21 days of verbal recitation at 8hrs/day)",
                    "1B mantras ≈ 9.26M malas (~57 years of verbal recitation at 8hrs/day)",
                    "1T mantras ≈ 9.26B malas (~57,000 years of 1 practitioner at 8hrs/day)",
                    "1Qa mantras ≈ 9.26T malas (~57 million years of 1 practitioner)",
                    "1Qi mantras ≈ 9.26Qa malas (~57 billion years, ~4× the age of the universe)",
                    "1Sx mantras ≈ 9.26Qi malas (~57 trillion years of verbal practice)",
                    "1Sp mantras ≈ stars in the observable universe"
                )
                ladder.forEach { line ->
                    Text(text = line, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        // ===== NEW: Shorthand Guide card =====
        ShortHandGuideCard()

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun StatItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium
        )
    }
}
