package com.prayerwheel.app.ui.wheel

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.prayerwheel.app.data.db.dao.SessionDao
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    sessionDao: SessionDao,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var currentMonth by remember { mutableStateOf(Calendar.getInstance()) }
    val monthFormat = remember { SimpleDateFormat("MMMM yyyy", Locale.getDefault()) }
    
    // Get start and end of month to query sessions
    val startOfMonth = remember(currentMonth) {
        val cal = currentMonth.clone() as Calendar
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
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

    // Group sessions by day of month
    val sessionsByDay = remember(sessions) {
        val grouped = mutableMapOf<Int, Int>() // Day -> Session Count
        val cal = Calendar.getInstance()
        sessions.forEach { session ->
            cal.timeInMillis = session.startedAt
            val day = cal.get(Calendar.DAY_OF_MONTH)
            grouped[day] = (grouped[day] ?: 0) + 1
        }
        grouped
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Practice Calendar") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            // Month navigation
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { 
                    val cal = currentMonth.clone() as Calendar
                    cal.add(Calendar.MONTH, -1)
                    currentMonth = cal
                }) {
                    Text("< Prev")
                }
                
                Text(
                    text = monthFormat.format(currentMonth.time),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                TextButton(onClick = { 
                    val cal = currentMonth.clone() as Calendar
                    cal.add(Calendar.MONTH, 1)
                    currentMonth = cal
                }) {
                    Text("Next >")
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))

            // Days of week header
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat").forEach { day ->
                    Text(
                        text = day,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))

            // Calendar grid
            val daysInMonth = currentMonth.getActualMaximum(Calendar.DAY_OF_MONTH)
            val firstDayOfWeek = run {
                val cal = currentMonth.clone() as Calendar
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.get(Calendar.DAY_OF_WEEK) - 1 // 0-indexed (Sun=0)
            }
            
            val totalCells = daysInMonth + firstDayOfWeek
            val rows = Math.ceil(totalCells / 7.0).toInt()
            
            LazyVerticalGrid(
                columns = GridCells.Fixed(7),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(rows * 7) { index ->
                    val dayOfMonth = index - firstDayOfWeek + 1
                    if (index >= firstDayOfWeek && dayOfMonth <= daysInMonth) {
                        val sessionCount = sessionsByDay[dayOfMonth] ?: 0
                        CalendarDayCell(day = dayOfMonth, sessionCount = sessionCount)
                    } else {
                        Spacer(modifier = Modifier.padding(4.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun CalendarDayCell(day: Int, sessionCount: Int) {
    val backgroundColor = if (sessionCount > 0) {
        MaterialTheme.colorScheme.primary.copy(alpha = (0.2f + (sessionCount * 0.1f)).coerceAtMost(0.8f))
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    }

    Box(
        modifier = Modifier
            .padding(4.dp)
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = day.toString(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (sessionCount > 0) FontWeight.Bold else FontWeight.Normal,
            color = if (sessionCount > 0) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
        )
    }
}
