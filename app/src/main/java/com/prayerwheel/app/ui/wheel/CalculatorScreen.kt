package com.prayerwheel.app.ui.wheel

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import kotlin.math.pow

private enum class ProjectionPeriod(val label: String, val months: Int) {
    ONE_MONTH("1 Month", 1),
    THREE_MONTHS("3 Months", 3),
    SIX_MONTHS("6 Months", 6),
    ONE_YEAR("1 Year", 12),
    FIVE_YEARS("5 Years", 60),
    TEN_YEARS("10 Years", 120)
}

private enum class CalculatorTab(val label: String) {
    PERSONAL("Personal"),
    MONASTERY("Monastery")
}

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
    ONE_QUADRILLION("1Qa", BigInteger("1000000000000000"))
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CalculatorScreen(
    defaultRpm: Float,
    defaultMantrasPerRotation: Long,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableStateOf(CalculatorTab.PERSONAL) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Practice Calculator",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            TabRow(
                selectedTabIndex = selectedTab.ordinal,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                CalculatorTab.entries.forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        text = { Text(tab.label) }
                    )
                }
            }

            when (selectedTab) {
                CalculatorTab.PERSONAL -> PersonalCalculator(
                    defaultRpm = defaultRpm,
                    defaultMantrasPerRotation = defaultMantrasPerRotation
                )
                CalculatorTab.MONASTERY -> MonasteryCalculator(
                    defaultRpm = defaultRpm,
                    defaultMantrasPerRotation = defaultMantrasPerRotation
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PersonalCalculator(
    defaultRpm: Float,
    defaultMantrasPerRotation: Long
) {
    var rpm by remember { mutableFloatStateOf(defaultRpm.coerceIn(1f, 120f)) }
    var mantrasPerRotation by remember { mutableStateOf(defaultMantrasPerRotation.coerceAtLeast(1L)) }
    var hoursPerDay by remember { mutableFloatStateOf(2f) }
    var daysPerWeek by remember { mutableIntStateOf(7) }
    var selectedPeriod by remember { mutableStateOf(ProjectionPeriod.ONE_YEAR) }

    val mantrasPerMinute = remember(rpm, mantrasPerRotation) {
        BigInteger.valueOf((rpm.toLong() * mantrasPerRotation))
    }
    val mantrasPerHour = remember(mantrasPerMinute) {
        mantrasPerMinute * BigInteger.valueOf(60)
    }
    val mantrasPerDay = remember(mantrasPerHour, hoursPerDay) {
        mantrasPerHour * BigInteger.valueOf(hoursPerDay.toLong())
    }
    val mantrasPerWeek = remember(mantrasPerDay, daysPerWeek) {
        mantrasPerDay * BigInteger.valueOf(daysPerWeek.toLong())
    }
    val mantrasPerMonth = remember(mantrasPerWeek) {
        mantrasPerWeek * BigInteger.valueOf(13).divide(BigInteger.valueOf(3))
    }
    val mantrasPerYear = remember(mantrasPerWeek) {
        mantrasPerWeek * BigInteger.valueOf(52)
    }

    val projectedTotal = remember(mantrasPerMonth, selectedPeriod) {
        mantrasPerMonth * BigInteger.valueOf(selectedPeriod.months.toLong())
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
                            text = String.format("%.1f hrs", hoursPerDay),
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

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MonasteryCalculator(
    defaultRpm: Float,
    defaultMantrasPerRotation: Long
) {
    var rpm by remember { mutableFloatStateOf(defaultRpm.coerceIn(1f, 120f)) }
    var mantrasPerRotation by remember { mutableStateOf(defaultMantrasPerRotation.coerceAtLeast(1L)) }
    var hoursPerDay by remember { mutableFloatStateOf(2f) }
    var recitationRate by remember { mutableIntStateOf(100) }
    var monksCount by remember { mutableIntStateOf(1000) }
    var selectedTarget by remember { mutableStateOf(MantraTarget.ONE_TRILLION) }

    val mantrasPerMinute = remember(rpm, mantrasPerRotation) {
        BigInteger.valueOf((rpm.toLong() * mantrasPerRotation))
    }
    val mantrasPerHour = remember(mantrasPerMinute) {
        mantrasPerMinute * BigInteger.valueOf(60)
    }
    val myDailyMantras = remember(mantrasPerHour, hoursPerDay) {
        mantrasPerHour * BigInteger.valueOf(hoursPerDay.toLong())
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
        mantrasPerHour * BigInteger.valueOf(hoursPerDay.toLong()) * BigInteger.valueOf(monksCount.toLong())
    }
    val wheelVsVerbalRatio = remember(communityWheelPerDay, communityVerbalPerDay) {
        if (communityVerbalPerDay > BigInteger.ZERO) {
            communityWheelPerDay.toBigDecimal().divide(communityVerbalPerDay.toBigDecimal(), 0, RoundingMode.HALF_UP)
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

    val communityWheelPerYear = remember(communityWheelPerDay) {
        communityWheelPerDay * BigInteger.valueOf(365)
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
                    text = "Your Practice vs Recitation",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Each rotation of your wheel multiplies the mantra by the capacity setting. One spin at ${NumberFormatter.formatWithCommas(BigInteger.valueOf(mantrasPerRotation))} mantras/rotation equals the same as reciting it verbally ${NumberFormatter.formatWithCommas(BigInteger.valueOf(mantrasPerRotation))} times.",
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
                    text = "Formula: ${rpm.toInt()} RPM x ${NumberFormatter.formatWithCommas(BigInteger.valueOf(mantrasPerRotation))} mantras x 60 min x ${String.format("%.1f", hoursPerDay)} hrs = your daily total",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )

                Text(
                    text = "To recite the same amount verbally at $recitationRate mantras/min:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )

                if (singleMonkYearsForMyDay > BigDecimal.ZERO) {
                    val yearsVal = singleMonkYearsForMyDay.toFloat()
                    StatRow(
                        label = "1 monk, 8 hrs/day",
                        value = when {
                            yearsVal >= 1.0 -> String.format("%.1f years", yearsVal)
                            yearsVal * 12 >= 1.0 -> String.format("%.1f months", yearsVal * 12)
                            else -> String.format("%.0f days", verbalDaysForMyDay.toFloat())
                        }
                    )
                    StatRow(
                        label = "Non-stop recitation",
                        value = formatHoursReadably(verbalHoursForMyDay.toFloat())
                    )
                    StatRow(
                        label = "Monks to match your day",
                        value = String.format("%,.0f reciting 8 hrs each", monksForOneDayEquivalent)
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
                            text = String.format("%.1f hrs", hoursPerDay),
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
                                years >= 1.0 -> String.format("%.1f years", years)
                                months >= 1.0 -> String.format("%.1f months", months)
                                else -> String.format("%.0f days", days)
                            },
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = String.format("(%,.0f days)", days),
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
                        text = "With prayer wheels at your rate (${String.format("%.1f", hoursPerDay)} hrs/day each)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    wheelTimeToTarget?.let { (days, years, months) ->
                        Text(
                            text = when {
                                years >= 1.0 -> String.format("%.1f years", years)
                                months >= 1.0 -> String.format("%.1f months", months)
                                else -> String.format("%.0f days", days)
                            },
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                        Text(
                            text = String.format("(%,.0f days)", days),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }

                HorizontalDivider()

                StatRow(
                    label = "Acceleration from wheels",
                    value = "${NumberFormatter.formatWithFull(wheelVsVerbalRatio.toBigInteger())}x faster"
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
                    text = "Equivalence (1 year of community verbal recitation)",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                val verbalPerYear = communityVerbalPerDay * BigInteger.valueOf(365)
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

        Spacer(modifier = Modifier.height(32.dp))
    }
}

private fun formatHoursReadably(hours: Float): String {
    return when {
        hours >= 8766f -> String.format("%,.1f years", hours / 8766f)
        hours >= 48f -> String.format("%,.0f days", hours / 24f)
        else -> String.format("%,.1f hours", hours)
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

@Composable
private fun StatRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

private fun buildComparisons(totalMantras: BigInteger): List<String> {
    val comparisons = mutableListOf<String>()
    val verbalPerHour = BigInteger.valueOf(6_000L)
    val mala = BigInteger.valueOf(108)
    val hundredM = BigInteger.valueOf(100_000_000L)
    val worldPop = BigInteger.valueOf(8_000_000_000L)

    if (totalMantras >= BigInteger.valueOf(10_000)) {
        val circuits = totalMantras.divide(mala)
        comparisons.add("${NumberFormatter.format(circuits)} complete mala circuits (108 each)")
    }

    if (totalMantras >= BigInteger.valueOf(100_000)) {
        val hoursBD = totalMantras.toBigDecimal()
            .divide(verbalPerHour.toBigDecimal(), 1, RoundingMode.HALF_UP)
        val yearsBD = hoursBD.divide(BigDecimal(8766), 1, RoundingMode.HALF_UP)
        comparisons.add(
            if (yearsBD >= BigDecimal.ONE) {
                "Same as 1 person reciting non-stop for ${yearsBD.stripTrailingZeros().toPlainString()} years"
            } else {
                val daysBD = hoursBD.divide(BigDecimal(24), 1, RoundingMode.HALF_UP)
                if (daysBD >= BigDecimal.ONE) {
                    "Same as 1 person reciting non-stop for ${daysBD.stripTrailingZeros().toPlainString()} days"
                } else {
                    "Same as 1 person reciting non-stop for ${hoursBD.stripTrailingZeros().toPlainString()} hours"
                }
            }
        )
    }

    if (totalMantras >= hundredM) {
        val count = totalMantras.divide(hundredM)
        comparisons.add("${NumberFormatter.format(count)} × the traditional 100M milestone (bhumi)")
    }

    if (totalMantras >= worldPop) {
        val count = totalMantras.divide(worldPop)
        comparisons.add("Same as every person on Earth reciting ${NumberFormatter.format(count)} mantras each")
    }

    return comparisons.ifEmpty { listOf("Accumulation in progress") }
}

private fun buildLifetimeComparisons(mantrasPerYear: BigInteger): List<String> {
    val comparisons = mutableListOf<String>()

    val tenYears = mantrasPerYear * BigInteger.valueOf(10)
    comparisons.add("10 years: ${NumberFormatter.formatWithFull(tenYears)}")

    val oneLifetime = mantrasPerYear * BigInteger.valueOf(80)
    comparisons.add("1 human lifetime (80 yrs): ${NumberFormatter.formatWithFull(oneLifetime)}")

    val thousandYears = mantrasPerYear * BigInteger.valueOf(1000)
    comparisons.add("1,000 years: ${NumberFormatter.formatWithFull(thousandYears)}")

    return comparisons
}

private fun buildTimeToMilestones(mantrasPerMonth: BigInteger): List<String> {
    if (mantrasPerMonth <= BigInteger.ZERO) return listOf("Start spinning to see projections")

    val milestones = mutableListOf<String>()

    val targets = listOf(
        100_000_000L to "100M",
        1_000_000_000L to "1B",
        10_000_000_000L to "10B",
        1_000_000_000_000L to "1T",
        10_000_000_000_000L to "10T",
        1_000_000_000_000_000L to "1Qa"
    )

    for ((target, label) in targets) {
        val targetBI = BigInteger.valueOf(target)
        val months = targetBI.toBigDecimal()
            .divide(mantrasPerMonth.toBigDecimal(), 1, RoundingMode.HALF_UP)
        val years = months.divide(BigDecimal(12), 1, RoundingMode.HALF_UP)
        milestones.add(
            if (years < BigDecimal.ONE) {
                "Reaching $label mantras: ${months} months"
            } else {
                "Reaching $label mantras: ${years} years"
            }
        )
    }

    return milestones
}

private fun buildMonasteryMilestones(mantrasPerDayTotal: BigInteger): List<String> {
    if (mantrasPerDayTotal <= BigInteger.ZERO) return listOf("Set parameters to see milestones")

    val milestones = mutableListOf<String>()

    val targets = listOf(
        1_000_000_000L to "1B",
        10_000_000_000L to "10B",
        100_000_000_000L to "100B",
        1_000_000_000_000L to "1T",
        10_000_000_000_000L to "10T",
        1_000_000_000_000_000L to "1Qa"
    )

    for ((target, label) in targets) {
        val targetBI = BigInteger.valueOf(target)
        val days = targetBI.toBigDecimal()
            .divide(mantrasPerDayTotal.toBigDecimal(), 1, RoundingMode.HALF_UP)
        val years = days.divide(BigDecimal("365.25"), 1, RoundingMode.HALF_UP)
        milestones.add(
            if (years < BigDecimal.ONE) {
                "Reaching $label: ${days} days"
            } else {
                "Reaching $label: ${years} years"
            }
        )
    }

    return milestones
}

private fun longToSliderPosition(value: Long): Float {
    if (value <= 1) return 0f
    if (value >= 1_000_000_000_000L) return 1f
    val logMax = 12f
    val logValue = kotlin.math.ln(value.toFloat()) / kotlin.math.ln(10f)
    return (logValue / logMax).coerceIn(0f, 1f)
}

private fun sliderPositionToLongValue(position: Float): Long {
    if (position <= 0f) return 1L
    val logMax = 12f
    val logValue = position * logMax
    val value = 10.0.pow(logValue.toDouble())
    return value.toLong().coerceIn(1L, Long.MAX_VALUE)
}
