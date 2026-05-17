package com.prayerwheel.app.ui.wheel

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.prayerwheel.app.data.datastore.SpinMode
import com.prayerwheel.app.data.db.dao.SessionDao
import com.prayerwheel.app.data.model.LifetimeStats
import com.prayerwheel.app.data.model.Mantras
import com.prayerwheel.app.data.model.Session
import com.prayerwheel.app.ui.components.NumberFormatter
import java.io.OutputStreamWriter
import java.math.BigInteger
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Filter options for practice history.
 */
enum class TimeFilter {
    TODAY, LAST_7_DAYS, LAST_30_DAYS, ALL_TIME
}

/**
 * Practice history screen with filters and enhanced session cards.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HistoryScreen(
    sessions: kotlinx.coroutines.flow.Flow<List<Session>>,
    lifetimeStats: kotlinx.coroutines.flow.Flow<LifetimeStats?>,
    sessionDao: SessionDao,
    onNavigateBack: () -> Unit,
    onNavigateToStats: () -> Unit,
    onNavigateToExport: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sessionsList by sessions.collectAsState(initial = emptyList())
    val stats by lifetimeStats.collectAsState(initial = null)
    val context = LocalContext.current

    // Filter states
    var selectedMantraFilter by remember { mutableStateOf<String?>(null) }
    var selectedTimeFilter by remember { mutableStateOf(TimeFilter.ALL_TIME) }
    var selectedModeFilter by remember { mutableStateOf<SpinMode?>(null) }

    // Available mantras for filter chips
    val availableMantras = remember(sessionsList) {
        sessionsList.map { it.mantraId }.distinct().mapNotNull { Mantras.byId(it) }
    }

    // Calculate time-based thresholds
    val now = System.currentTimeMillis()
    val todayStart = remember {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        sdf.parse(sdf.format(Date(now)))?.time ?: now
    }
    val sevenDaysAgo = now - (7 * 24 * 60 * 60 * 1000L)
    val thirtyDaysAgo = now - (30 * 24 * 60 * 60 * 1000L)

    // Filter sessions
    val filteredSessions = remember(sessionsList, selectedMantraFilter, selectedTimeFilter, selectedModeFilter) {
        val currentMode = selectedModeFilter
        sessionsList.filter { session ->
            val matchesMantra = selectedMantraFilter == null || session.mantraId == selectedMantraFilter
            val matchesTime = when (selectedTimeFilter) {
                TimeFilter.TODAY -> session.startedAt >= todayStart
                TimeFilter.LAST_7_DAYS -> session.startedAt >= sevenDaysAgo
                TimeFilter.LAST_30_DAYS -> session.startedAt >= thirtyDaysAgo
                TimeFilter.ALL_TIME -> true
            }
            val matchesMode = currentMode == null || session.mode == currentMode.name
            matchesMantra && matchesTime && matchesMode
        }
    }

    // Calculate filtered stats
    val filteredTotalMantras = remember(filteredSessions) {
        filteredSessions.fold(BigInteger.ZERO) { acc, session ->
            acc.add(session.totalMantras)
        }
    }
    val filteredAvgRpm = remember(filteredSessions) {
        if (filteredSessions.isNotEmpty()) {
            filteredSessions.map { it.averageRpm }.average().toFloat()
        } else {
            0f
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Practice History",
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
                    IconButton(onClick = onNavigateToStats) {
                        Icon(
                            imageVector = Icons.Filled.Share,
                            contentDescription = "Statistics"
                        )
                    }
                    IconButton(onClick = onNavigateToExport) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Export"
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Filter summary card
            item {
                FilterSummaryCard(
                    sessionCount = filteredSessions.size,
                    totalMantras = filteredTotalMantras,
                    avgRpm = filteredAvgRpm
                )
            }

            // Filter chips
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Time filter
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        TimeFilter.entries.forEach { filter ->
                            FilterChip(
                                selected = selectedTimeFilter == filter,
                                onClick = { selectedTimeFilter = filter },
                                label = {
                                    Text(
                                        text = when (filter) {
                                            TimeFilter.TODAY -> "Today"
                                            TimeFilter.LAST_7_DAYS -> "7 Days"
                                            TimeFilter.LAST_30_DAYS -> "30 Days"
                                            TimeFilter.ALL_TIME -> "All Time"
                                        }
                                    )
                                }
                            )
                        }
                    }

                    // Mantra filter
                    if (availableMantras.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            FilterChip(
                                selected = selectedMantraFilter == null,
                                onClick = { selectedMantraFilter = null },
                                label = { Text("All Mantras") }
                            )
                            availableMantras.forEach { mantra ->
                                FilterChip(
                                    selected = selectedMantraFilter == mantra.id,
                                    onClick = { selectedMantraFilter = mantra.id },
                                    label = { Text(mantra.displayName) }
                                )
                            }
                        }
                    }

                    // Mode filter
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        SpinMode.entries.forEach { mode ->
                            FilterChip(
                                selected = selectedModeFilter == mode,
                                onClick = { selectedModeFilter = if (selectedModeFilter == mode) null else mode },
                                label = {
                                    Text(
                                        text = when (mode) {
                                            SpinMode.MANUAL -> "Manual"
                                            SpinMode.TWO_HANDED -> "Two-Handed"
                                            SpinMode.AUTO_SPIN -> "Auto-Spin"
                                            SpinMode.TWO_HANDED_AUTO -> "Two-Handed Auto"
                                        }
                                    )
                                }
                            )
                        }
                    }
                }
            }

            // Lifetime stats summary card
            item {
                LifetimeStatsCard(stats = stats)
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Sessions header
            item {
                Text(
                    text = "Sessions (${filteredSessions.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            // Empty state or sessions list
            if (filteredSessions.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No practice sessions match your filters.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                items(filteredSessions) { session ->
                    SessionCard(session = session)
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

/**
 * Filter summary card showing filtered statistics.
 */
@Composable
private fun FilterSummaryCard(
    sessionCount: Int,
    totalMantras: BigInteger,
    avgRpm: Float
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = sessionCount.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = "Sessions",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = formatBigNumber(totalMantras),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = "Mantras",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (avgRpm > 0) String.format(Locale.getDefault(), "%.1f", avgRpm) else "-",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = "Avg RPM",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                )
            }
        }
    }
}

/**
 * Lifetime stats summary card.
 */
@Composable
private fun LifetimeStatsCard(stats: LifetimeStats?) {
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

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatItem(
                    label = "Sessions",
                    value = (stats?.sessionsCompleted ?: 0L).toString()
                )
                StatItem(
                    label = "Rotations",
                    value = formatNumber(stats?.totalRotations ?: 0L)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

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

            Spacer(modifier = Modifier.height(12.dp))

            // Practice since
            stats?.firstSessionAt?.let { timestamp ->
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
 * Individual stat item in the summary card.
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
 * Enhanced session card showing detailed session information.
 */
@Composable
private fun SessionCard(session: Session) {
    val mantra = Mantras.byId(session.mantraId)
    val displayName = mantra?.displayName ?: session.mantraId
    val tibetan = mantra?.tibetan

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
            // Date and duration row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatDateTime(session.startedAt),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                session.endedAt?.let { endedAt ->
                    val duration = calculateDuration(session.startedAt, endedAt)
                    if (duration.isNotEmpty()) {
                        Text(
                            text = duration,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Mantra name
            Column {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                tibetan?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Stats row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start
            ) {
                Text(
                    text = "${formatNumber(session.rotationCount)} rotations",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "${formatBigNumber(session.totalMantras)} mantras",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // RPM stats row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start
            ) {
                if (session.averageRpm > 0) {
                    Text(
                        text = "Avg: ${String.format(Locale.getDefault(), "%.1f", session.averageRpm)} RPM",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                }
                if (session.peakRpm > 0) {
                    Text(
                        text = "Peak: ${String.format(Locale.getDefault(), "%.1f", session.peakRpm)} RPM",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Mode row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatSpinMode(session.mode),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }

            // Intention (if set)
            session.intention?.let { intention ->
                if (intention.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Intention: $intention",
                        style = MaterialTheme.typography.bodySmall,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Dedication (if present)
            session.dedication?.let { dedication ->
                if (dedication.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = dedication,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * Exports filtered sessions to CSV format and shares via Android share sheet.
 */
private fun exportSessionsToCsv(context: Context, sessions: List<Session>) {
    try {
        val csvContent = buildString {
            // Header
            appendLine("Date,Duration,Mantra,Rotations,Mantras,Avg RPM,Peak RPM,Mode,Intention,Dedication")
            
            // Data rows
            sessions.forEach { session ->
                val mantra = Mantras.byId(session.mantraId)
                val displayName = mantra?.displayName ?: session.mantraId
                val duration = session.endedAt?.let { calculateDuration(session.startedAt, it) } ?: ""
                val intention = session.intention?.replace("\"", "\"\"") ?: ""
                val dedication = session.dedication?.replace("\"", "\"\"") ?: ""
                
                appendLine("\"${formatDateTime(session.startedAt)}\",\"$duration\",\"$displayName\",${session.rotationCount},${session.totalMantras},${session.averageRpm},${session.peakRpm},\"${formatSpinMode(session.mode)}\",\"$intention\",\"$dedication\"")
            }
        }
        
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_SUBJECT, "Prayer Wheel Practice History")
            putExtra(Intent.EXTRA_TEXT, csvContent)
        }
        context.startActivity(Intent.createChooser(intent, "Export Sessions"))
    } catch (e: Exception) {
        // Handle export error silently
    }
}

/**
 * Formats a number with K/M/B suffixes for large values.
 */
private fun formatNumber(number: Long): String {
    return NumberFormatter.formatLong(number)
}

/**
 * Formats a BigInteger for display.
 */
private fun formatBigNumber(mantras: BigInteger): String {
    return NumberFormatter.format(mantras)
}

/**
 * Formats a timestamp to a readable date string.
 */
private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

/**
 * Formats a timestamp to a readable date/time string.
 */
private fun formatDateTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

/**
 * Calculates duration string from start to end time.
 */
private fun calculateDuration(startedAt: Long, endedAt: Long): String {
    val durationMs = endedAt - startedAt
    val seconds = durationMs / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    
    return when {
        hours > 0 -> "${hours}h ${minutes % 60}m"
        minutes > 0 -> "${minutes}m"
        seconds > 0 -> "${seconds}s"
        else -> ""
    }
}

/**
 * Formats spin mode string to display name.
 */
private fun formatSpinMode(mode: String): String {
    return try {
        when (SpinMode.valueOf(mode)) {
            SpinMode.MANUAL -> "Manual"
            SpinMode.TWO_HANDED -> "Two-Handed"
            SpinMode.AUTO_SPIN -> "Auto-Spin"
            SpinMode.TWO_HANDED_AUTO -> "Two-Handed Auto"
        }
    } catch (e: IllegalArgumentException) {
        mode
    }
}
