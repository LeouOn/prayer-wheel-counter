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
import com.prayerwheel.app.ui.components.CosmicReference
import com.prayerwheel.app.ui.components.NumberFormatter
import com.prayerwheel.app.ui.components.ShortHandGuideCard
import com.prayerwheel.app.ui.components.buildCosmicTimeComparisons
import com.prayerwheel.app.ui.components.buildCrossCulturalTimeComparisons
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.util.Locale

private enum class MonasterySize(val label: String, val count: Int) {
    SMALL("Small (100)", 100),
    MEDIUM("Medium (500)", 500),
    LARGE("Large (1,000)", 1_000),
    GREAT("Great Stupa (10,000)", 10_000),
    MASSIVE("Massive (100,000)", 100_000)
}

private enum class MantraTarget(val label: String, val value: BigInteger) {
    ONE_HUNDRED_MILLION("100M", BigInteger.valueOf(100_000_000L)),
    ONE_BILLION("1B", BigInteger.valueOf(1_000_000_000L)),
    TEN_BILLION("10B", BigInteger.valueOf(10_000_000_000L)),
    ONE_HUNDRED_BILLION("100B", BigInteger.valueOf(100_000_000_000L)),
    ONE_TRILLION("1T", BigInteger.valueOf(1_000_000_000_000L)),
    TEN_TRILLION("10T", BigInteger.valueOf(10_000_000_000_000L)),
    ONE_HUNDRED_TRILLION("100T", BigInteger.valueOf(100_000_000_000_000L)),
    ONE_QUADRILLION("1Qa", BigInteger("1000000000000000")),
    TEN_QUADRILLION("10Qa", BigInteger("10000000000000000")),
    ONE_QUINTILLION("1Qi", BigInteger("1000000000000000000")),
    TEN_QUINTILLION("10Qi", BigInteger("10000000000000000000")),
    ONE_SEXTILLION("1Sx", BigInteger("1000000000000000000000")),
    TEN_SEXTILLION("10Sx", BigInteger("10000000000000000000000")),
    ONE_SEPTILLION("1Sp", BigInteger("1000000000000000000000000")),
    TEN_SEPTILLION("10Sp", BigInteger("10000000000000000000000000")),
    ONE_OCTILLION("1Oc", BigInteger("1000000000000000000000000000")),
    TEN_OCTILLION("10Oc", BigInteger("10000000000000000000000000000")),
    ONE_NONILLION("1No", BigInteger("1000000000000000000000000000000")),
    TEN_NONILLION("10No", BigInteger("10000000000000000000000000000000")),
    ONE_DECILLION("1Dc", BigInteger("1000000000000000000000000000000000"))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MonasteryCalculator(
    defaultRpm: Float,
    defaultMantrasPerRotation: Long
) {
    var rpm by remember { mutableFloatStateOf(defaultRpm.coerceIn(1f, 120f)) }
    var mantrasPerRotation by remember { mutableStateOf(defaultMantrasPerRotation.coerceAtLeast(1L)) }
    var hoursPerDay by remember { mutableFloatStateOf(2f) }
    var recitationRate by remember { mutableIntStateOf(100) }
    var monksCount by remember { mutableIntStateOf(1000) }
    var selectedTarget by remember { mutableStateOf(MantraTarget.ONE_QUADRILLION) }

    val mantrasPerMinute = remember(rpm, mantrasPerRotation) {
        BigInteger.valueOf(rpm.toLong()).multiply(BigInteger.valueOf(mantrasPerRotation))
    }
    val mantrasPerHour = remember(mantrasPerMinute) {
        mantrasPerMinute * BigInteger.valueOf(60)
    }
    val myDailyMantras = remember(mantrasPerHour, hoursPerDay) {
        mantrasPerHour.toBigDecimal()
            .multiply(BigDecimal.valueOf(hoursPerDay.toDouble()))
            .toBigInteger()
    }

    val verbalPerMinute = BigInteger.valueOf(recitationRate.toLong())
    val verbalPerHour = verbalPerMinute * BigInteger.valueOf(60)
    val verbalPerDay8h = verbalPerHour * BigInteger.valueOf(8)

    val verbalDaysForMyDay = remember(myDailyMantras, verbalPerDay8h) {
        if (verbalPerDay8h > BigInteger.ZERO && myDailyMantras > BigInteger.ZERO) {
            myDailyMantras.toBigDecimal().divide(verbalPerDay8h.toBigDecimal(), 1, RoundingMode.HALF_UP)
        } else BigDecimal.ZERO
    }
    val verbalHoursForMyDay = remember(myDailyMantras, verbalPerHour) {
        if (verbalPerHour > BigInteger.ZERO && myDailyMantras > BigInteger.ZERO) {
            myDailyMantras.toBigDecimal().divide(verbalPerHour.toBigDecimal(), 1, RoundingMode.HALF_UP)
        } else BigDecimal.ZERO
    }

    val singleMonkYearsForMyDay = remember(myDailyMantras, verbalPerDay8h) {
        if (verbalPerDay8h > BigInteger.ZERO) {
            val days = myDailyMantras.toBigDecimal().divide(verbalPerDay8h.toBigDecimal(), 1, RoundingMode.HALF_UP)
            days.divide(BigDecimal("365.25"), 2, RoundingMode.HALF_UP)
        } else BigDecimal.ZERO
    }

    val monksForOneDayEquivalent = remember(myDailyMantras, verbalPerDay8h) {
        if (verbalPerDay8h > BigInteger.ZERO && myDailyMantras > BigInteger.ZERO) {
            myDailyMantras.divide(verbalPerDay8h).toFloat().coerceAtLeast(1f)
        } else 0f
    }

    val communityVerbalPerDay = remember(verbalPerDay8h, monksCount) {
        verbalPerDay8h * BigInteger.valueOf(monksCount.toLong())
    }
    val communityWheelPerDay = remember(mantrasPerHour, hoursPerDay, monksCount) {
        mantrasPerHour.toBigDecimal()
            .multiply(BigDecimal.valueOf(hoursPerDay.toDouble()))
            .toBigInteger() * BigInteger.valueOf(monksCount.toLong())
    }
    val wheelVsVerbalRatio = remember(communityWheelPerDay, communityVerbalPerDay) {
        if (communityVerbalPerDay > BigInteger.ZERO) {
            communityWheelPerDay.toBigDecimal().divide(communityVerbalPerDay.toBigDecimal(), 1, RoundingMode.HALF_UP)
        } else BigDecimal.ONE
    }

    val verbalTimeToTarget = remember(communityVerbalPerDay, selectedTarget) {
        if (communityVerbalPerDay > BigInteger.ZERO) {
            val days = selectedTarget.value.toBigDecimal()
                .divide(communityVerbalPerDay.toBigDecimal(), 1, RoundingMode.HALF_UP)
            Triple(days.toFloat(), days.toFloat() / 365.25f, days.toFloat() / 30.44f)
        } else null
    }

    val wheelTimeToTarget = remember(communityWheelPerDay, selectedTarget) {
        if (communityWheelPerDay > BigInteger.ZERO) {
            val days = selectedTarget.value.toBigDecimal()
                .divide(communityWheelPerDay.toBigDecimal(), 1, RoundingMode.HALF_UP)
            Triple(days.toFloat(), days.toFloat() / 365.25f, days.toFloat() / 30.44f)
        } else null
    }

    val yearsToTargetBD = remember(wheelTimeToTarget) {
        wheelTimeToTarget?.let { it.second.toBigDecimal() } ?: BigDecimal.ZERO
    }

    val communityWheelPerYear = remember(communityWheelPerDay) {
        mantrasOverDays(communityWheelPerDay, DAYS_PER_YEAR)
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
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Your Wheel Practice",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Each rotation of your wheel releases the mantra ${NumberFormatter.formatWithCommas(BigInteger.valueOf(mantrasPerRotation))} times. The wheel accompanies your verbal recitation, multiplying the merit of practice you are already doing.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )

                HorizontalDivider()

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Your daily spinning produces",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Text(
                        text = "${NumberFormatter.format(myDailyMantras)} mantras",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "(${NumberFormatter.formatWithCommas(myDailyMantras)})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }

                HorizontalDivider()

                Text(
                    text = "Formula: ${rpm.toInt()} RPM x ${NumberFormatter.formatWithCommas(BigInteger.valueOf(mantrasPerRotation))} mantras x 60 min x ${String.format(Locale.getDefault(), "%.1f", hoursPerDay)} hrs = your daily total",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )

                Text(
                    text = "For context, the same total would take one person reciting verbally at $recitationRate mantras/min:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )

                if (singleMonkYearsForMyDay > BigDecimal.ZERO) {
                    val yearsVal = singleMonkYearsForMyDay.toFloat()
                    StatRow(
                        label = "1 monk, 8 hrs/day",
                        value = when {
                            yearsVal >= 1.0 -> String.format(Locale.getDefault(), "%.1f years", yearsVal)
                            yearsVal * 12 >= 1.0 -> String.format(Locale.getDefault(), "%.1f months", yearsVal * 12)
                            else -> String.format(Locale.getDefault(), "%.0f days", verbalDaysForMyDay.toFloat())
                        }
                    )
                    StatRow(
                        label = "Non-stop recitation",
                        value = formatHoursReadably(verbalHoursForMyDay.toFloat())
                    )
                    StatRow(
                        label = "Monks to match your day",
                        value = String.format(Locale.getDefault(), "%,.0f reciting 8 hrs each", monksForOneDayEquivalent)
                    )
                }
            }
        }

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
                    text = "Parameters",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )

                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Wheel RPM", style = MaterialTheme.typography.bodyMedium)
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
                        Text(text = "Practice Hours per Day", style = MaterialTheme.typography.bodyMedium)
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Verbal Recitation Rate", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = "$recitationRate mantras/min",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Slider(
                        value = recitationRate.toFloat(),
                        onValueChange = { recitationRate = it.toInt().coerceIn(30, 600) },
                        valueRange = 30f..600f,
                        steps = 113,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                HorizontalDivider()

                Text(
                    text = "Assumptions",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Text(
                    text = "Verbal recitation: $recitationRate mantras/min is a typical pace for Om Mani Padme Hum. Monks practice 8 hrs/day (traditional schedule). Prayer wheel output = RPM x capacity x 60 min/hr x practice hours.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        }

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
                    text = "Community Recitation",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "How long for a community of monks reciting verbally to reach ${selectedTarget.label} mantras",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )

                Column {
                    Text(text = "Community Size", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        MonasterySize.entries.forEach { size ->
                            FilterChip(
                                selected = monksCount == size.count,
                                onClick = { monksCount = size.count },
                                label = { Text(size.label, style = MaterialTheme.typography.labelSmall) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                                )
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Practitioners", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = NumberFormatter.formatLong(monksCount.toLong()),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Slider(
                        value = monksCount.toFloat().coerceIn(1f, 100000f),
                        onValueChange = { monksCount = it.toInt().coerceAtLeast(1) },
                        valueRange = 1f..100000f,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Column {
                    Text(text = "Mantra Target", style = MaterialTheme.typography.bodyMedium)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        MantraTarget.entries.forEach { target ->
                            FilterChip(
                                selected = selectedTarget == target,
                                onClick = { selectedTarget = target },
                                label = { Text(target.label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                                )
                            )
                        }
                    }
                }

                HorizontalDivider()

                Text(
                    text = "Formula: $recitationRate mantras/min x 60 min x 8 hrs/day x ${NumberFormatter.formatLong(monksCount.toLong())} monks = ${NumberFormatter.formatWithFull(communityVerbalPerDay)} mantras/day",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
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
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Time to Reach ${selectedTarget.label} Mantras",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                HorizontalDivider()

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Verbal recitation by ${NumberFormatter.formatLong(monksCount.toLong())} monks, 8 hrs/day",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    verbalTimeToTarget?.let { (days, years, months) ->
                        Text(
                            text = when {
                                years >= 1.0 -> String.format(Locale.getDefault(), "%.1f years", years)
                                months >= 1.0 -> String.format(Locale.getDefault(), "%.1f months", months)
                                else -> String.format(Locale.getDefault(), "%.0f days", days)
                            },
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = String.format(Locale.getDefault(), "(%,.0f days)", days),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }

                HorizontalDivider()

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "With prayer wheels at your rate (${String.format(Locale.getDefault(), "%.1f", hoursPerDay)} hrs/day each)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    wheelTimeToTarget?.let { (days, years, months) ->
                        Text(
                            text = when {
                                years >= 1.0 -> String.format(Locale.getDefault(), "%.1f years", years)
                                months >= 1.0 -> String.format(Locale.getDefault(), "%.1f months", months)
                                else -> String.format(Locale.getDefault(), "%.0f days", days)
                            },
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                        Text(
                            text = String.format(Locale.getDefault(), "(%,.0f days)", days),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }

                HorizontalDivider()

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Both together (wheels + verbal recitation)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    val combinedPerDay = communityWheelPerDay + communityVerbalPerDay
                    if (combinedPerDay > BigInteger.ZERO) {
                        val combinedDays = selectedTarget.value.toBigDecimal()
                            .divide(combinedPerDay.toBigDecimal(), 1, RoundingMode.HALF_UP)
                        val combinedYears = combinedDays.divide(BigDecimal("365.25"), 1, RoundingMode.HALF_UP)
                        Text(
                            text = when {
                                combinedYears >= BigDecimal.ONE -> "${combinedYears.stripTrailingZeros().toPlainString()} years"
                                combinedDays >= BigDecimal.ONE -> "${combinedDays.stripTrailingZeros().toPlainString()} days"
                                else -> "Less than 1 day"
                            },
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                HorizontalDivider()

                StatRow(
                    label = "Wheel amplification",
                    value = "${wheelVsVerbalRatio.stripTrailingZeros().toPlainString()}× the verbal-only rate"
                )
                StatRow(
                    label = "Community verbal per day",
                    value = NumberFormatter.formatWithFull(communityVerbalPerDay)
                )
                StatRow(
                    label = "Community with wheels per day",
                    value = NumberFormatter.formatWithFull(communityWheelPerDay)
                )
                StatRow(
                    label = "Community with wheels per year",
                    value = "${NumberFormatter.format(communityWheelPerYear)} (${NumberFormatter.formatWithCommas(communityWheelPerYear)})"
                )
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
                    text = "Community Milestones",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Time for ${NumberFormatter.formatLong(monksCount.toLong())} monks reciting 8 hrs/day to reach each milestone",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                val milestones = buildMonasteryMilestones(communityVerbalPerDay)
                milestones.forEach { milestone ->
                    Text(text = milestone, style = MaterialTheme.typography.bodyMedium)
                }

                HorizontalDivider()

                Text(
                    text = "For context, 1 year of community verbal recitation produces:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                val verbalPerYear = mantrasOverDays(communityVerbalPerDay, DAYS_PER_YEAR)
                val comparisons = buildComparisons(verbalPerYear)
                comparisons.forEach { comparison ->
                    Text(text = comparison, style = MaterialTheme.typography.bodyMedium)
                }

                val lifetimes = buildLifetimeComparisons(verbalPerYear)
                lifetimes.forEach { comparison ->
                    Text(text = comparison, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        // ===== NEW: Wheel-vs-World card =====
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
                    text = "Community Practice in Context",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Your community's output in human terms",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                val verbalEquivalentMonks = if (communityVerbalPerDay > BigInteger.ZERO) {
                    communityWheelPerDay.toBigDecimal()
                        .divide(communityVerbalPerDay.toBigDecimal(), 0, RoundingMode.HALF_UP)
                        .toBigInteger()
                } else BigInteger.ONE
                StatRow(
                    label = "Equivalent verbal community size",
                    value = "${NumberFormatter.format(verbalEquivalentMonks)}× larger"
                )
                val worldPopPerYear = communityWheelPerYear.divide(CosmicReference.WORLD_POPULATION)
                StatRow(
                    label = "Per year vs world population",
                    value = "${NumberFormatter.format(worldPopPerYear)} mantras per person on Earth"
                )
                val oneB = BigInteger.valueOf(1_000_000_000L)
                if (communityWheelPerDay >= oneB) {
                    val ratio = communityWheelPerDay.divide(oneB)
                    StatRow(
                        label = "Daily output vs 1B milestone",
                        value = "${NumberFormatter.format(ratio)}× the 1B target"
                    )
                } else {
                    val percent = NumberFormatter.formatPercent(communityWheelPerDay, oneB)
                    StatRow(
                        label = "Daily output as % of 1B milestone",
                        value = percent
                    )
                }
            }
        }

        // ===== NEW: Cosmic Time to target card =====
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
                    text = "Cosmic Time to ${selectedTarget.label}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Time to reach target in cosmic context",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                if (yearsToTargetBD > BigDecimal.ZERO) {
                    Text(
                        text = NumberFormatter.humanizeYearsWithCosmicContext(yearsToTargetBD),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    val cosmicTimeLines = buildCosmicTimeComparisons(yearsToTargetBD)
                    if (cosmicTimeLines.isNotEmpty()) {
                        HorizontalDivider()
                        cosmicTimeLines.forEach { line ->
                            Text(text = line, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }

        // ===== NEW: Cross-Cultural Time Scales card =====
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
                    text = "Cross-Cultural Time Scales",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "How this compares to cosmic cycles across traditions",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                val crossCulturalLines = buildCrossCulturalTimeComparisons(yearsToTargetBD)
                if (crossCulturalLines.isEmpty()) {
                    Text(
                        text = "Choose a larger target to see cross-cultural time comparisons.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                } else {
                    crossCulturalLines.forEach { line ->
                        Text(text = line, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        // ===== NEW: Shorthand Guide card =====
        ShortHandGuideCard()

        Spacer(modifier = Modifier.height(32.dp))
    }
}
