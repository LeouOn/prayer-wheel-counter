package com.prayerwheel.app.ui.wheel

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.prayerwheel.app.data.db.dao.SessionDao
import com.prayerwheel.app.data.model.Mantras
import com.prayerwheel.app.data.model.Session
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(
    sessionDao: SessionDao,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isExporting by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Export Data") },
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Export your practice history. You can save it as a CSV file for spreadsheets or JSON for backup purposes.", style = MaterialTheme.typography.bodyMedium)
            
            Button(
                onClick = { 
                    coroutineScope.launch {
                        isExporting = true
                        val sessions = sessionDao.getAllSessions().first()
                        exportData(context, sessions, "csv")
                        isExporting = false
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isExporting
            ) {
                Text("Export as CSV")
            }

            Button(
                onClick = { 
                    coroutineScope.launch {
                        isExporting = true
                        val sessions = sessionDao.getAllSessions().first()
                        exportData(context, sessions, "json")
                        isExporting = false
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isExporting
            ) {
                Text("Export as JSON")
            }
            
            if (isExporting) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            }
        }
    }
}

private fun exportData(context: Context, sessions: List<Session>, format: String) {
    try {
        val content = if (format == "csv") {
            buildCsvContent(sessions)
        } else {
            buildJsonContent(sessions)
        }
        
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = if (format == "csv") "text/csv" else "application/json"
            putExtra(Intent.EXTRA_SUBJECT, "Prayer Wheel Practice History")
            putExtra(Intent.EXTRA_TEXT, content)
        }
        context.startActivity(Intent.createChooser(intent, "Export Sessions"))
    } catch (e: Exception) {
        // Handle export error silently
    }
}

private fun buildCsvContent(sessions: List<Session>): String {
    return buildString {
        appendLine("Date,Duration,Mantra,Rotations,Mantras,Avg RPM,Peak RPM,Mode,Intention,Dedication")
        sessions.forEach { session ->
            val mantra = Mantras.byId(session.mantraId)
            val displayName = mantra?.displayName ?: session.mantraId
            val duration = session.endedAt?.let { calculateDuration(session.startedAt, it) } ?: ""
            val intention = session.intention?.replace("\"", "\"\"") ?: ""
            val dedication = session.dedication?.replace("\"", "\"\"") ?: ""
            val dateStr = SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.getDefault()).format(Date(session.startedAt))
            appendLine("\"$dateStr\",\"$duration\",\"$displayName\",${session.rotationCount},${session.totalMantras},${session.averageRpm},${session.peakRpm},\"${session.mode}\",\"$intention\",\"$dedication\"")
        }
    }
}

private fun buildJsonContent(sessions: List<Session>): String {
    return buildString {
        appendLine("[")
        sessions.forEachIndexed { index, session ->
            appendLine("  {")
            appendLine("    \"startedAt\": ${session.startedAt},")
            appendLine("    \"endedAt\": ${session.endedAt ?: "null"},")
            appendLine("    \"rotationCount\": ${session.rotationCount},")
            appendLine("    \"totalMantras\": ${session.totalMantras},")
            appendLine("    \"mantraId\": \"${session.mantraId}\",")
            appendLine("    \"mode\": \"${session.mode}\",")
            appendLine("    \"averageRpm\": ${session.averageRpm},")
            appendLine("    \"peakRpm\": ${session.peakRpm},")
            appendLine("    \"intention\": \"${session.intention?.replace("\"", "\\\"") ?: ""}\",")
            appendLine("    \"dedication\": \"${session.dedication?.replace("\"", "\\\"") ?: ""}\"")
            if (index < sessions.size - 1) {
                appendLine("  },")
            } else {
                appendLine("  }")
            }
        }
        appendLine("]")
    }
}

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
