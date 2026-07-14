package com.prayerwheel.app.ui.wheel

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prayerwheel.app.data.db.dao.SessionDao
import com.prayerwheel.app.data.model.Mantras
import com.prayerwheel.app.data.model.SavedWheel
import com.prayerwheel.app.data.model.Session
import com.prayerwheel.app.ui.components.bounceClick
import com.prayerwheel.app.ui.components.LogPracticeDialog
import com.prayerwheel.app.ui.components.NumberFormatter
import kotlinx.coroutines.launch
import java.math.BigInteger
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private data class DayStats(
    val dayOfMonth: Int,
    val totalMantras: BigInteger,
    val totalRotations: Long,
    val sessionCount: Int,
    val totalDurationMs: Long
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CalendarScreen(
    viewModel: com.prayerwheel.app.viewmodel.WheelViewModel,
    sessionDao: SessionDao,
    savedWheels: List<SavedWheel>,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var currentMonth by remember { mutableStateOf(Calendar.getInstance()) }
    val monthFormat = remember { SimpleDateFormat("MMMM yyyy", Locale.getDefault()) }
    var selectedDay by remember { mutableStateOf<DayStats?>(null) }
    
    var showLogDialog by remember { mutableStateOf(false) }
    var sessionToDelete by remember { mutableStateOf<Session?>(null) }
    var lastDeletedSession by remember { mutableStateOf<Session?>(null) }
    val defaultRpm by viewModel.autoSpinRpm.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    val startOfMonth = remember(currentMonth) {
        val cal = currentMonth.clone() as Calendar
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        cal.timeInMillis
    }

    val endOfMonth = remember(currentMonth) {
        val cal = currentMonth.clone() as Calendar
        cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.timeInMillis
    }

    val sessions by sessionDao.getSessionsBetween(startOfMonth, endOfMonth).collectAsState(initial = emptyList())

    var isFiltersExpanded by remember { mutableStateOf(false) }
    var selectedMantraFilter by remember { mutableStateOf<String?>(null) }
    var selectedWheelFilter by remember { mutableStateOf<String?>(null) }

    val availableMantras = remember(sessions) {
        sessions.map { it.mantraId }.distinct().mapNotNull { Mantras.byId(it) }
    }
    val availableWheels = remember(sessions, savedWheels) {
        sessions.mapNotNull { it.wheelId }.distinct().mapNotNull { id ->
            savedWheels.find { it.id == id } ?: SavedWheel(id, "Deleted Wheel", 1L, "", "", 0L)
        }
    }

    val filteredSessions = remember(sessions, selectedMantraFilter, selectedWheelFilter) {
        sessions.filter { session ->
            val matchesMantra = selectedMantraFilter == null || session.mantraId == selectedMantraFilter
            val matchesWheel = selectedWheelFilter == null || session.wheelId == selectedWheelFilter
            matchesMantra && matchesWheel
        }
    }

    val today = remember {
        val cal = Calendar.getInstance()
        Triple(
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        )
    }

    val sessionsByDay = remember(filteredSessions, currentMonth) {
        val grouped = mutableMapOf<Int, DayStats>()
        val cal = Calendar.getInstance()
        filteredSessions.forEach { session ->
            cal.timeInMillis = session.startedAt
            val day = cal.get(Calendar.DAY_OF_MONTH)
            val existing = grouped[day]
            val sessionDuration = if (session.endedAt != null) session.endedAt - session.startedAt else 0L
            grouped[day] = DayStats(
                dayOfMonth = day,
                totalMantras = (existing?.totalMantras ?: BigInteger.ZERO) + session.totalMantras,
                totalRotations = (existing?.totalRotations ?: 0L) + session.rotationCount,
                sessionCount = (existing?.sessionCount ?: 0) + 1,
                totalDurationMs = (existing?.totalDurationMs ?: 0L) + sessionDuration
            )
        }
        grouped
    }

    val maxMantrasInMonth = remember(sessionsByDay) {
        sessionsByDay.values.maxOfOrNull { it.totalMantras } ?: BigInteger.ZERO
    }

    val streakInfo = remember(sessionsByDay, currentMonth) {
        val cal = Calendar.getInstance()
        var currentStreak = 0
        var bestStreak = 0
        var tempStreak = 0

        val daysInMonth = currentMonth.getActualMaximum(Calendar.DAY_OF_MONTH)
        for (day in 1..daysInMonth) {
            if (sessionsByDay.containsKey(day)) {
                tempStreak++
                bestStreak = maxOf(bestStreak, tempStreak)
            } else {
                tempStreak = 0
            }
        }

        cal.set(Calendar.DAY_OF_MONTH, daysInMonth)
        for (day in daysInMonth downTo 1) {
            if (sessionsByDay.containsKey(day)) {
                currentStreak++
            } else if (day != today.third || currentMonth.get(Calendar.MONTH) != today.second || currentMonth.get(Calendar.YEAR) != today.first) {
                break
            }
        }

        Pair(currentStreak, bestStreak)
    }

    val monthlyTotals = remember(sessionsByDay) {
        var mantras = BigInteger.ZERO
        var rotations = 0L
        var sessions = 0
        var duration = 0L
        sessionsByDay.values.forEach { day ->
            mantras += day.totalMantras
            rotations += day.totalRotations
            sessions += day.sessionCount
            duration += day.totalDurationMs
        }
        MonthlyTotals(mantras, rotations, sessions, duration)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Practice Calendar") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SummaryCard(monthlyTotals, streakInfo)

            // Filters Header
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

            // Filter chips
            AnimatedVisibility(visible = isFiltersExpanded) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
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
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = {
                    val cal = currentMonth.clone() as Calendar
                    cal.add(Calendar.MONTH, -1)
                    currentMonth = cal
                    selectedDay = null
                }) {
                    Text("< Prev")
                }

                Text(
                    text = monthFormat.format(currentMonth.time),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                TextButton(
                    onClick = {
                        val cal = currentMonth.clone() as Calendar
                        cal.add(Calendar.MONTH, 1)
                        currentMonth = cal
                        selectedDay = null
                    },
                    enabled = currentMonth.get(Calendar.YEAR) < today.first ||
                        (currentMonth.get(Calendar.YEAR) == today.first && currentMonth.get(Calendar.MONTH) < today.second)
                ) {
                    Text("Next >")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                TextButton(onClick = {
                    currentMonth = Calendar.getInstance()
                    selectedDay = null
                }) {
                    Text("Today")
                }
            }

            HeatMapLegend()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat").forEach { day ->
                    Text(
                        text = day,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }

            val daysInMonth = currentMonth.getActualMaximum(Calendar.DAY_OF_MONTH)
            val firstDayOfWeek = run {
                val cal = currentMonth.clone() as Calendar
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.get(Calendar.DAY_OF_WEEK) - 1
            }
            val totalCells = daysInMonth + firstDayOfWeek
            val rows = Math.ceil(totalCells / 7.0).toInt()

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                for (row in 0 until rows) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        for (col in 0..6) {
                            val index = row * 7 + col
                            val dayOfMonth = index - firstDayOfWeek + 1
                            if (index >= firstDayOfWeek && dayOfMonth <= daysInMonth) {
                                val dayStats = sessionsByDay[dayOfMonth]
                                val isToday = currentMonth.get(Calendar.YEAR) == today.first &&
                                        currentMonth.get(Calendar.MONTH) == today.second &&
                                        dayOfMonth == today.third
                                Box(modifier = Modifier.weight(1f)) {
                                    CalendarDayCell(
                                        day = dayOfMonth,
                                        dayStats = dayStats,
                                        maxMantras = maxMantrasInMonth,
                                        isToday = isToday,
                                        isSelected = selectedDay?.dayOfMonth == dayOfMonth,
                                        onClick = { selectedDay = dayStats ?: DayStats(dayOfMonth, BigInteger.ZERO, 0L, 0, 0L) }
                                    )
                                }
                            } else {
                                Box(modifier = Modifier.weight(1f).padding(2.dp).height(44.dp))
                            }
                        }
                    }
                }
            }

            selectedDay?.let { day ->
                val daySessions = remember(filteredSessions, day, currentMonth) {
                    val cal = Calendar.getInstance()
                    filteredSessions.filter { session ->
                        cal.timeInMillis = session.startedAt
                        cal.get(Calendar.DAY_OF_MONTH) == day.dayOfMonth &&
                        cal.get(Calendar.MONTH) == currentMonth.get(Calendar.MONTH) &&
                        cal.get(Calendar.YEAR) == currentMonth.get(Calendar.YEAR)
                    }
                }
                DayDetailCard(
                    day = day,
                    sessions = daySessions,
                    savedWheels = savedWheels,
                    onDeleteClick = { sessionToDelete = it },
                    onLogPracticeClick = { showLogDialog = true }
                )
            }
        }
    }

    val selectedDayTimestamp = remember(selectedDay, currentMonth) {
        val cal = currentMonth.clone() as Calendar
        selectedDay?.let {
            cal.set(Calendar.DAY_OF_MONTH, it.dayOfMonth)
        }
        cal.timeInMillis
    }

    if (showLogDialog) {
        LogPracticeDialog(
            savedWheels = savedWheels,
            initialDateTimestamp = selectedDayTimestamp,
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
                val duration = (session.endedAt ?: session.startedAt) - session.startedAt
                val durationStr = formatDurationShort(duration)
                Text(
                    "Are you sure you want to delete this session?\n\n" +
                    "• Wheel: $wheelName\n" +
                    "• Date: ${SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(session.startedAt))}\n" +
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
                            selectedDay = null
                            coroutineScope.launch {
                                val result = snackbarHostState.showSnackbar(
                                    message = "Session deleted",
                                    actionLabel = "Undo",
                                    duration = SnackbarDuration.Short
                                )
                                if (result == SnackbarResult.ActionPerformed) {
                                    lastDeletedSession?.let { viewModel.undoDeleteSession(it) }
                                    lastDeletedSession = null
                                } else {
                                    lastDeletedSession = null
                                }
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

@Composable
private fun SummaryCard(totals: MonthlyTotals, streakInfo: Pair<Int, Int>) {
    val (currentStreak, bestStreak) = streakInfo
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
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "This Month",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = NumberFormatter.format(totals.mantras),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "mantras",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${totals.sessions}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "sessions",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = formatDurationShort(totals.duration),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "time",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
            if (currentStreak > 0 || bestStreak > 0) {
                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Text(
                        text = "Current streak: $currentStreak days",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                    Text(
                        text = "Best: $bestStreak days",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

@Composable
private fun HeatMapLegend() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Less",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            modifier = Modifier.padding(end = 4.dp)
        )
        repeat(5) { i ->
            val alpha = (0.1f + i * 0.2f).coerceAtMost(0.9f)
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = alpha))
            )
            Spacer(modifier = Modifier.size(2.dp))
        }
        Text(
            text = "More",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            modifier = Modifier.padding(start = 4.dp)
        )
    }
}

@Composable
private fun CalendarDayCell(
    day: Int,
    dayStats: DayStats?,
    maxMantras: BigInteger,
    isToday: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val intensity = if (dayStats != null && maxMantras > BigInteger.ZERO) {
        val ratio = dayStats.totalMantras.toBigDecimal()
            .divide(maxMantras.toBigDecimal(), 2, java.math.RoundingMode.HALF_UP)
            .toFloat()
        (ratio * 0.8f + 0.1f).coerceIn(0.1f, 0.9f)
    } else 0f

    val backgroundColor = when {
        isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        dayStats != null -> MaterialTheme.colorScheme.primary.copy(alpha = intensity)
        isToday -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
    }

    val borderColor = when {
        isToday -> MaterialTheme.colorScheme.primary
        else -> Color.Transparent
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(2.dp)
            .height(44.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(backgroundColor)
            .then(
                if (isToday) Modifier.background(borderColor, RoundedCornerShape(6.dp))
                else Modifier
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = day.toString(),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (dayStats != null) FontWeight.Bold else FontWeight.Normal,
                color = if (dayStats != null) {
                    if (intensity > 0.5f) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                }
            )
            if (dayStats != null) {
                Text(
                    text = NumberFormatter.format(dayStats.totalMantras),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                    color = if (intensity > 0.5f) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun DayDetailCard(
    day: DayStats,
    sessions: List<Session>,
    savedWheels: List<SavedWheel>,
    onDeleteClick: (Session) -> Unit,
    onLogPracticeClick: () -> Unit
) {
    val timeFormat = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }

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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (day.sessionCount > 0) "Day ${day.dayOfMonth} Details" else "Day ${day.dayOfMonth}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (day.sessionCount > 0) {
                    Surface(
                           shape = RoundedCornerShape(4.dp),
                           color = MaterialTheme.colorScheme.primaryContainer
                       ) {
                           Text(
                               text = "${day.sessionCount} session${if (day.sessionCount > 1) "s" else ""}",
                               style = MaterialTheme.typography.labelSmall,
                               modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                           )
                       }
                }
            }

            if (day.sessionCount > 0) {
                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                        Text(
                            text = NumberFormatter.format(day.totalMantras),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "mantras",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                        Text(
                            text = NumberFormatter.formatLong(day.totalRotations),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "rotations",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                        Text(
                            text = formatDurationShort(day.totalDurationMs),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "time",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }

                HorizontalDivider()

                Text(
                    text = "Sessions List",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    sessions.forEach { session ->
                        val wheelName = session.wheelId?.let { id -> savedWheels.find { it.id == id }?.name ?: "Deleted Wheel" } ?: "Unspecified Wheel"
                        val durationMs = if (session.endedAt != null) session.endedAt - session.startedAt else 0L
                        
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = timeFormat.format(Date(session.startedAt)),
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = formatDurationShort(durationMs),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        IconButton(
                                            onClick = { onDeleteClick(session) },
                                            modifier = Modifier.minimumInteractiveComponentSize()
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Delete",
                                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                                
                                Spacer(modifier = Modifier.height(4.dp))
                                
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = wheelName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "${NumberFormatter.format(session.totalMantras)} mantras",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                session.intention?.let { intention ->
                                    if (intention.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Intention: $intention",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                Text(
                    text = "No practice sessions this day",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }

            TextButton(
                onClick = onLogPracticeClick,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .fillMaxWidth()
                    .bounceClick()
            ) {
                Text("+ Log Practice for this Day")
            }
        }
    }
}

private data class MonthlyTotals(
    val mantras: BigInteger,
    val rotations: Long,
    val sessions: Int,
    val duration: Long
)

private fun formatDurationShort(ms: Long): String {
    val hours = ms / 3_600_000
    val minutes = (ms % 3_600_000) / 60_000
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m"
        ms > 0 -> "${ms / 1000}s"
        else -> "-"
    }
}
