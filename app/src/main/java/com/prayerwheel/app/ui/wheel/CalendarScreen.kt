package com.prayerwheel.app.ui.wheel

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.prayerwheel.app.data.db.dao.SessionDao
import com.prayerwheel.app.data.model.Mantras
import com.prayerwheel.app.data.model.SavedWheel
import com.prayerwheel.app.data.model.Session
import com.prayerwheel.app.ui.components.bounceClick
import com.prayerwheel.app.ui.components.LogPracticeDialog
import com.prayerwheel.app.ui.components.NumberFormatter
import com.prayerwheel.app.ui.wheel.calendar.CalendarDayCell
import com.prayerwheel.app.ui.wheel.calendar.DayDetailCard
import com.prayerwheel.app.ui.wheel.calendar.DayStats
import com.prayerwheel.app.ui.wheel.calendar.HeatMapLegend
import com.prayerwheel.app.ui.wheel.calendar.MonthlyTotals
import com.prayerwheel.app.ui.wheel.calendar.SummaryCard
import com.prayerwheel.app.ui.wheel.calendar.computeStreaks
import com.prayerwheel.app.ui.wheel.calendar.practiceDays
import com.prayerwheel.app.ui.wheel.calendar.formatDurationShort
import kotlinx.coroutines.launch
import java.math.BigInteger
import java.text.SimpleDateFormat
import java.time.ZoneId
import java.util.Calendar
import java.util.Date
import java.util.Locale

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

    val todayDate = rememberToday()
    val today = remember(todayDate) {
        // Calendar months are 0-based; LocalDate months are 1-based.
        Triple(todayDate.year, todayDate.monthValue - 1, todayDate.dayOfMonth)
    }

    // Streaks span all history, not just the displayed month, so a run that
    // crosses a month or year boundary (Dec 31 -> Jan 1) keeps counting.
    val allSessionsFlow = remember(sessionDao) { sessionDao.getAllSessions() }
    val allSessions by allSessionsFlow.collectAsState(initial = emptyList())
    val allPracticeDays = remember(allSessions, selectedMantraFilter, selectedWheelFilter) {
        val zone = ZoneId.systemDefault()
        practiceDays(
            allSessions.asSequence()
                .filter { selectedMantraFilter == null || it.mantraId == selectedMantraFilter }
                .filter { selectedWheelFilter == null || it.wheelId == selectedWheelFilter }
                .map { it.startedAt }
                .asIterable(),
            zone
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

    val streakInfo = remember(allPracticeDays, todayDate) {
        val streaks = computeStreaks(allPracticeDays, todayDate)
        Pair(streaks.current, streaks.best)
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
