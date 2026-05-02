package com.prayerwheel.app.ui.wheel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.prayerwheel.app.data.db.dao.TimeStats
import com.prayerwheel.app.data.model.LifetimeStats
import com.prayerwheel.app.data.model.Mantra
import com.prayerwheel.app.data.model.MantraStats
import com.prayerwheel.app.data.model.Mantras
import com.prayerwheel.app.data.model.Session
import java.math.BigInteger
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Practice statistics screen showing lifetime stats and per-mantra breakdown.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    lifetimeStats: LifetimeStats?,
    mantraStats: List<MantraStats>,
    last7DaysStats: TimeStats?,
    last30DaysStats: TimeStats?,
    allTimeStats: TimeStats?,
    recentSessions: List<Session>,
    onNavigateBack: () -> Unit,
    onNavigateToCalendar: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Practice Statistics",
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
                actions = {
                    IconButton(onClick = onNavigateToCalendar) {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = "Calendar"
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Lifetime Summary Card
            item {
                LifetimeSummaryCard(stats = lifetimeStats)
            }

            // Time Period Summary
            item {
                TimePeriodSummaryCard(
                    last7DaysStats = last7DaysStats,
                    last30DaysStats = last30DaysStats,
                    allTimeStats = allTimeStats
                )
            }

            // Per-Mantra Breakdown Header
            item {
                Text(
                    text = "Per-Mantra Breakdown",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            // Per-mantra cards
            if (mantraStats.isEmpty()) {
                item {
                    Text(
                        text = "No practice sessions recorded yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                }
            } else {
                items(mantraStats) { stats ->
                    MantraStatsCard(
                        stats = stats,
                        lifetimeStats = lifetimeStats
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

/**
 * Lifetime summary card showing overall practice statistics.
 */
@Composable
private fun LifetimeSummaryCard(stats: LifetimeStats?) {
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
                .padding(16.dp)
        ) {
            Text(
                text = "Lifetime Practice",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Sessions and spinning time
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatItem(
                    label = "Sessions",
                    value = (stats?.sessionsCompleted ?: 0L).toString()
                )
                StatItem(
                    label = "Spinning Time",
                    value = formatDuration(stats?.totalSpinningTimeSeconds ?: 0L)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Average session duration
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                StatItem(
                    label = "Avg Session",
                    value = formatDuration(stats?.averageSessionDurationSeconds ?: 0L)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Total mantras (prominent)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Total Mantras",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
                Text(
                    text = formatBigNumber(stats?.totalMantras ?: BigInteger.ZERO),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            // Practice since
            stats?.firstSessionAt?.let { timestamp ->
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Practicing since ${formatDate(timestamp)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                )
            }
        }
    }
}

/**
 * Time period summary card showing recent activity.
 */
@Composable
private fun TimePeriodSummaryCard(
    last7DaysStats: TimeStats?,
    last30DaysStats: TimeStats?,
    allTimeStats: TimeStats?
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
                .padding(16.dp)
        ) {
            Text(
                text = "Recent Activity",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Last 7 days
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Last 7 Days",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Text(
                        text = "${last7DaysStats?.sessionCount ?: 0} sessions, ${formatNumber(last7DaysStats?.totalMantras ?: 0L)} mantras",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Last 30 days
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Last 30 Days",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Text(
                        text = "${last30DaysStats?.sessionCount ?: 0} sessions, ${formatNumber(last30DaysStats?.totalMantras ?: 0L)} mantras",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // All time (from sessions)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "All Time",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Text(
                        text = "${allTimeStats?.sessionCount ?: 0} sessions, ${formatNumber(allTimeStats?.totalMantras ?: 0L)} mantras",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

/**
 * Card showing statistics for a specific mantra.
 */
@Composable
private fun MantraStatsCard(
    stats: MantraStats,
    lifetimeStats: LifetimeStats?
) {
    val mantra = Mantras.byId(stats.mantraId)
    val displayName = mantra?.displayName ?: stats.mantraId
    val tibetan = mantra?.tibetan
    val percentage = if (lifetimeStats != null && lifetimeStats.totalRotations > 0) {
        (stats.totalRotations.toFloat() / lifetimeStats.totalRotations * 100)
    } else {
        0f
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
                .padding(16.dp)
        ) {
            // Mantra name
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    tibetan?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
                Text(
                    text = "${String.format(Locale.getDefault(), "%.1f", percentage)}%",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Stats row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${stats.sessionCount} sessions",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Text(
                    text = "${formatNumber(stats.totalRotations)} rotations",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Text(
                    text = "${formatNumber(stats.totalMantras)} mantras",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        }
    }
}

/**
 * Individual stat item.
 */
@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

/**
 * Formats a number with K/M/B suffixes for large values.
 */
private fun formatNumber(number: Long): String {
    return when {
        number >= 1_000_000_000 -> "${number / 1_000_000_000}B"
        number >= 1_000_000 -> "${number / 1_000_000}M"
        number >= 1_000 -> "${number / 1_000}K"
        else -> number.toString()
    }
}

/**
 * Formats a BigInteger for display.
 */
private fun formatBigNumber(mantras: BigInteger): String {
    return when {
        mantras >= BigInteger("1000000000") -> "${mantras.divide(BigInteger("1000000000"))}B"
        mantras >= BigInteger("1000000") -> "${mantras.divide(BigInteger("1000000"))}M"
        mantras >= BigInteger("1000") -> "${mantras.divide(BigInteger("1000"))}K"
        else -> mantras.toString()
    }
}

/**
 * Formats a duration in seconds to a readable string.
 */
private fun formatDuration(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m"
        seconds > 0 -> "${seconds}s"
        else -> "0m"
    }
}

/**
 * Formats a timestamp to a readable date string.
 */
private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
