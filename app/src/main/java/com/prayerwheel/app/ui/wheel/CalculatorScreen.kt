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
import java.math.BigInteger
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.pow

/**
 * Projection period options.
 */
private enum class ProjectionPeriod(val label: String, val months: Int) {
    ONE_MONTH("1 Month", 1),
    THREE_MONTHS("3 Months", 3),
    SIX_MONTHS("6 Months", 6),
    ONE_YEAR("1 Year", 12),
    FIVE_YEARS("5 Years", 60),
    TEN_YEARS("10 Years", 120)
}

/**
 * Capacity presets for comparison.
 */
private object CapacityPresets {
    const val PERSONAL_MALA = 108L
    const val POCKET_WHEEL = 10_000L
    const val HAND_WHEEL = 1_000_000L
    const val STANDING_WHEEL = 100_000_000L
    const val STUPA_CLASS = 1_000_000_000_000L
}

/**
 * Calculator screen for projecting future mantra accumulations.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CalculatorScreen(
    defaultRpm: Float,
    defaultMantrasPerRotation: Long,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Input states
    var rpm by remember { mutableFloatStateOf(defaultRpm.coerceIn(1f, 120f)) }
    var mantrasPerRotation by remember { mutableStateOf(defaultMantrasPerRotation.coerceAtLeast(1L)) }
    var hoursPerDay by remember { mutableFloatStateOf(2f) }
    var daysPerWeek by remember { mutableIntStateOf(7) }
    var selectedPeriod by remember { mutableStateOf(ProjectionPeriod.ONE_YEAR) }

    // Derived calculations
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
        mantrasPerWeek * BigInteger.valueOf(13).divide(BigInteger.valueOf(3)) // 4.33 average
    }
    val mantrasPerYear = remember(mantrasPerWeek) {
        mantrasPerWeek * BigInteger.valueOf(52)
    }

    // Projected total for selected period
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Practice Projections",
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // INPUTS SECTION
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
                        text = "Inputs",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )

                    // RPM input
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Average RPM",
                                style = MaterialTheme.typography.bodyMedium
                            )
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

                    // Mantras per rotation
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Mantras per Rotation",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = formatNumber(BigInteger.valueOf(mantrasPerRotation)),
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

                    // Hours per day
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Hours per Day",
                                style = MaterialTheme.typography.bodyMedium
                            )
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

                    // Days per week
                    Column {
                        Text(
                            text = "Days per Week",
                            style = MaterialTheme.typography.bodyMedium
                        )
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

                    // Projection period
                    Column {
                        Text(
                            text = "Projection Period",
                            style = MaterialTheme.typography.bodyMedium
                        )
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

            // RESULTS SECTION
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
                        text = "Projected Results",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )

                    HorizontalDivider()

                    // Prominent projected total
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Total Mantras",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                        Text(
                            text = formatNumber(projectedTotal),
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "(${formatWithCommas(projectedTotal)})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                        Text(
                            text = "over ${selectedPeriod.label.lowercase()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }

                    HorizontalDivider()

                    // Supporting stats
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        StatItem(
                            label = "Total Rotations",
                            value = formatNumber(totalRotations)
                        )
                        StatItem(
                            label = "Spinning Hours",
                            value = formatNumber(totalHours)
                        )
                    }

                    HorizontalDivider()

                    // Rate breakdowns
                    Text(
                        text = "Accumulation Rates",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )

                    StatRow(label = "Per minute", value = formatNumber(mantrasPerMinute))
                    StatRow(label = "Per hour", value = formatNumber(mantrasPerHour))
                    StatRow(label = "Per day", value = formatNumber(mantrasPerDay))
                    StatRow(label = "Per week", value = formatNumber(mantrasPerWeek))
                    StatRow(label = "Per month", value = formatNumber(mantrasPerMonth))
                    StatRow(label = "Per year", value = formatNumber(mantrasPerYear))
                }
            }

            // MILESTONE COMPARISONS
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
                        Text(
                            text = comparison,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
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

/**
 * Builds milestone comparison strings.
 */
private fun buildComparisons(totalMantras: BigInteger): List<String> {
    val comparisons = mutableListOf<String>()
    val total = totalMantras

    if (total >= BigInteger.valueOf(CapacityPresets.STUPA_CLASS)) {
        val count = total.divide(BigInteger.valueOf(CapacityPresets.STUPA_CLASS))
        comparisons.add("Equivalent to ${formatNumber(count)} stupa-class wheels")
    }
    if (total >= BigInteger.valueOf(CapacityPresets.STANDING_WHEEL)) {
        val count = total.divide(BigInteger.valueOf(CapacityPresets.STANDING_WHEEL))
        comparisons.add("Equivalent to ${formatNumber(count)} standing wheels")
    }
    if (total >= BigInteger.valueOf(CapacityPresets.HAND_WHEEL)) {
        val count = total.divide(BigInteger.valueOf(CapacityPresets.HAND_WHEEL))
        comparisons.add("Equivalent to ${formatNumber(count)} hand-held wheels")
    }
    if (total >= BigInteger.valueOf(CapacityPresets.POCKET_WHEEL)) {
        val count = total.divide(BigInteger.valueOf(CapacityPresets.POCKET_WHEEL))
        comparisons.add("Equivalent to ${formatNumber(count)} pocket wheels")
    }
    if (total >= BigInteger.valueOf(CapacityPresets.PERSONAL_MALA)) {
        val count = total.divide(BigInteger.valueOf(CapacityPresets.PERSONAL_MALA))
        comparisons.add("Equivalent to ${formatNumber(count)} personal malas")
    }

    return comparisons.ifEmpty { listOf("Accumulation in progress") }
}

/**
 * Formats a BigInteger for display with proper notation (no scientific notation).
 * Shows abbreviated form (e.g., 1.5M, 12.3B, 1.2T) with full number below.
 */
private fun formatNumber(number: BigInteger): String {
    return when {
        number >= BigInteger.valueOf(1_000_000_000_000) -> {
            val trillions = number.divide(BigInteger.valueOf(1_000_000_000_000))
            val remainder = number.mod(BigInteger.valueOf(1_000_000_000_000))
            if (remainder == BigInteger.ZERO) {
                "${formatWithCommas(trillions)} T"
            } else {
                val decimal = (remainder.toDouble() / 1_000_000_000_000.0 * 100).toInt()
                "${formatWithCommas(trillions)}.${String.format("%02d", decimal)} T"
            }
        }
        number >= BigInteger.valueOf(1_000_000_000) -> {
            val billions = number.divide(BigInteger.valueOf(1_000_000_000))
            val remainder = number.mod(BigInteger.valueOf(1_000_000_000))
            if (remainder == BigInteger.ZERO) {
                "${formatWithCommas(billions)} B"
            } else {
                val decimal = (remainder.toDouble() / 1_000_000_000.0 * 100).toInt()
                "${formatWithCommas(billions)}.${String.format("%02d", decimal)} B"
            }
        }
        number >= BigInteger.valueOf(1_000_000) -> {
            val millions = number.divide(BigInteger.valueOf(1_000_000))
            val remainder = number.mod(BigInteger.valueOf(1_000_000))
            if (remainder == BigInteger.ZERO) {
                "${formatWithCommas(millions)} M"
            } else {
                val decimal = (remainder.toDouble() / 1_000_000.0 * 100).toInt()
                "${formatWithCommas(millions)}.${String.format("%02d", decimal)} M"
            }
        }
        number >= BigInteger.valueOf(1_000) -> {
            val thousands = number.divide(BigInteger.valueOf(1_000))
            val remainder = number.mod(BigInteger.valueOf(1_000))
            if (remainder == BigInteger.ZERO) {
                "${formatWithCommas(thousands)} K"
            } else {
                val decimal = (remainder.toDouble() / 1_000.0 * 100).toInt()
                "${formatWithCommas(thousands)}.${String.format("%02d", decimal)} K"
            }
        }
        else -> formatWithCommas(number)
    }
}

/**
 * Formats a BigInteger with locale-aware comma separators.
 */
private fun formatWithCommas(number: BigInteger): String {
    return NumberFormat.getNumberInstance(Locale.getDefault()).format(number)
}

/**
 * Converts a long value to a slider position (0..1) using logarithmic scale.
 */
private fun longToSliderPosition(value: Long): Float {
    if (value <= 1) return 0f
    if (value >= 1_000_000_000_000L) return 1f
    val logMax = 12f
    val logValue = kotlin.math.ln(value.toFloat()) / kotlin.math.ln(10f)
    return (logValue / logMax).coerceIn(0f, 1f)
}

/**
 * Converts a slider position (0..1) to a long value using logarithmic scale.
 */
private fun sliderPositionToLongValue(position: Float): Long {
    if (position <= 0f) return 1L
    val logMax = 12f
    val logValue = position * logMax
    val value = 10.0.pow(logValue.toDouble())
    return value.toLong().coerceIn(1L, Long.MAX_VALUE)
}
