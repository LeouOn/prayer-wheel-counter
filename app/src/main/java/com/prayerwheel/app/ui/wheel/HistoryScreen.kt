package com.prayerwheel.app.ui.wheel


import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prayerwheel.app.data.datastore.SpinMode
import com.prayerwheel.app.ui.components.StatItem
import com.prayerwheel.app.ui.components.bounceClick
import com.prayerwheel.app.data.db.dao.SessionDao
import com.prayerwheel.app.data.model.LifetimeStats
import com.prayerwheel.app.data.model.Mantras
import com.prayerwheel.app.data.model.SavedWheel
import com.prayerwheel.app.data.model.Session
import com.prayerwheel.app.ui.components.LogPracticeDialog
import com.prayerwheel.app.ui.components.NumberFormatter
import com.prayerwheel.app.ui.components.calculateDuration
import com.prayerwheel.app.ui.wheel.history.SessionCard
import com.prayerwheel.app.ui.wheel.history.formatBigNumber
import com.prayerwheel.app.ui.wheel.history.formatNumber
import kotlinx.coroutines.launch

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
    viewModel: com.prayerwheel.app.viewmodel.WheelViewModel,
    sessions: kotlinx.coroutines.flow.Flow<List<Session>>,
    lifetimeStats: kotlinx.coroutines.flow.Flow<LifetimeStats?>,
    sessionDao: SessionDao,
    savedWheels: List<SavedWheel>,
    onNavigateBack: () -> Unit,
    onNavigateToStats: () -> Unit,
    onNavigateToExport: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sessionsList by sessions.collectAsState(initial = emptyList())
    val stats by lifetimeStats.collectAsState(initial = null)
    var showLogDialog by remember { mutableStateOf(false) }
    var sessionToDelete by remember { mutableStateOf<Session?>(null) }
    var lastDeletedSession by remember { mutableStateOf<Session?>(null) }
    val defaultRpm by viewModel.autoSpinRpm.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    // Filter states
    var isFiltersExpanded by remember { mutableStateOf(false) }
    var selectedMantraFilter by remember { mutableStateOf<String?>(null) }
    var selectedWheelFilter by remember { mutableStateOf<String?>(null) }
    var selectedTimeFilter by remember { mutableStateOf(TimeFilter.ALL_TIME) }
    var selectedModeFilter by remember { mutableStateOf<SpinMode?>(null) }

    // Available mantras and wheels for filter chips
    val availableMantras = remember(sessionsList) {
        sessionsList.map { it.mantraId }.distinct().mapNotNull { Mantras.byId(it) }
    }
    val availableWheels = remember(sessionsList, savedWheels) {
        sessionsList.mapNotNull { it.wheelId }.distinct().mapNotNull { id ->
            savedWheels.find { it.id == id } ?: SavedWheel(id, "Deleted Wheel", 1L, "", "", 0L)
        }
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
            val matchesWheel = selectedWheelFilter == null || session.wheelId == selectedWheelFilter
            val matchesTime = when (selectedTimeFilter) {
                TimeFilter.TODAY -> session.startedAt >= todayStart
                TimeFilter.LAST_7_DAYS -> session.startedAt >= sevenDaysAgo
                TimeFilter.LAST_30_DAYS -> session.startedAt >= thirtyDaysAgo
                TimeFilter.ALL_TIME -> true
            }
            val matchesMode = currentMode == null || session.mode == currentMode.name
            matchesMantra && matchesWheel && matchesTime && matchesMode
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showLogDialog = true },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.bounceClick()
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Log practice"
                )
            }
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

            // Filters Header
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .bounceClick()
                        .clickable { isFiltersExpanded = !isFiltersExpanded }
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Filters",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Icon(
                        imageVector = if (isFiltersExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = "Toggle Filters"
                    )
                }
            }

            // Filter chips
            item {
                AnimatedVisibility(visible = isFiltersExpanded) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
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

                    // Wheel filter
                    if (availableWheels.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            FilterChip(
                                selected = selectedWheelFilter == null,
                                onClick = { selectedWheelFilter = null },
                                label = { Text("All Wheels") }
                            )
                            availableWheels.forEach { wheel ->
                                FilterChip(
                                    selected = selectedWheelFilter == wheel.id,
                                    onClick = { selectedWheelFilter = wheel.id },
                                    label = { Text(wheel.name) }
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
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 64.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "🪷",
                            fontSize = 48.sp,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        Text(
                            text = "The wheel is at rest.",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
                        )
                        Text(
                            text = "No practice sessions match your filters.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            } else {
                items(filteredSessions) { session ->
                    SessionCard(
                        session = session,
                        savedWheels = savedWheels,
                        onDeleteClick = { sessionToDelete = session },
                        onLabelChange = { newLabel ->
                            viewModel.updateSessionLabel(session, newLabel)
                        }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    if (showLogDialog) {
        LogPracticeDialog(
            savedWheels = savedWheels,
            defaultRpm = defaultRpm,
            onSave = { wheelId, startedAt, durationSeconds, rotations, mantraId, capacity, intention, dedication ->
                viewModel.logManualSession(wheelId, startedAt, durationSeconds, rotations, mantraId, capacity, intention, dedication)
                showLogDialog = false
            },
            onDismiss = { showLogDialog = false }
        )
    }

    if (sessionToDelete != null) {
        AlertDialog(
            onDismissRequest = { sessionToDelete = null },
            title = { Text("Delete Practice Session?") },
            text = {
                val session = sessionToDelete!!
                val wheelName = session.wheelId?.let { id -> savedWheels.find { it.id == id }?.name ?: "Deleted Wheel" } ?: "Unspecified Wheel"
                val durationStr = calculateDuration(session.startedAt, session.endedAt ?: session.startedAt)
                Text(
                    "Are you sure you want to delete this session?\n\n" +
                    "• Wheel: $wheelName\n" +
                    "• Date: ${formatDate(session.startedAt)}\n" +
                    "• Duration: $durationStr\n" +
                    "• Mantras: ${NumberFormatter.format(session.totalMantras)}\n\n" +
                    "This will subtract the merit and duration from your lifetime statistics. You can undo this briefly after deletion."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val deleted = sessionToDelete
                        sessionToDelete = null
                        deleted?.let { session ->
                            viewModel.deleteSession(session)
                            lastDeletedSession = session
                            coroutineScope.launch {
                                val result = snackbarHostState.showSnackbar(
                                    message = "Session deleted",
                                    actionLabel = "Undo",
                                    duration = SnackbarDuration.Short
                                )
                                if (result == SnackbarResult.ActionPerformed) {
                                    lastDeletedSession?.let { viewModel.undoDeleteSession(it) }
                                }
                                lastDeletedSession = null
                            }
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { sessionToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
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
 * Formats a timestamp to a readable date string.
 */
private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
