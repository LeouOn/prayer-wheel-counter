package com.prayerwheel.app.ui.wheel

import android.content.Context
import android.content.Intent
import android.os.Environment
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.prayerwheel.app.data.db.dao.SessionDao
import com.prayerwheel.app.data.model.LifetimeStats
import com.prayerwheel.app.data.model.Mantras
import com.prayerwheel.app.data.model.Session
import com.prayerwheel.app.ui.components.NumberFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileWriter
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
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Export & Backup") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Session count summary
            val sessionCount = remember { mutableIntStateOf(0) }
            LaunchedEffect(Unit) {
                sessionCount.intValue = sessionDao.getSessionCount()
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Practice Data",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "${sessionCount.intValue} practice sessions available for export.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Text(
                text = "Share or save your practice history. CSV works with any spreadsheet. JSON is best for backups and data portability.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )

            // Share options
            Text(
                text = "Share",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            isExporting = true
                            val sessions = sessionDao.getAllSessions().first()
                            shareData(context, sessions, "csv")
                            isExporting = false
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !isExporting
                ) {
                    Text("Share CSV")
                }

                Button(
                    onClick = {
                        coroutineScope.launch {
                            isExporting = true
                            val sessions = sessionDao.getAllSessions().first()
                            shareData(context, sessions, "json")
                            isExporting = false
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !isExporting
                ) {
                    Text("Share JSON")
                }
            }

            // Save backup to device
            HorizontalDivider()
            Text(
                text = "Backup",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )

            OutlinedButton(
                onClick = {
                    coroutineScope.launch {
                        isExporting = true
                        try {
                            val sessions = sessionDao.getAllSessions().first()
                            val stats = sessionDao.getSessionCount()
                            val result = saveBackupToDevice(context, sessions, stats)
                            snackbarHostState.showSnackbar(result)
                        } catch (e: Exception) {
                            snackbarHostState.showSnackbar("Backup failed: ${e.message}")
                        }
                        isExporting = false
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isExporting
            ) {
                Text("Save Backup to Device")
            }

            Text(
                text = "Backups are saved to this app's private storage (Android/data/com.prayerwheel.app/files/Download/) as prayer-wheel-backup.json. You can copy this file to keep your practice data safe.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )

            if (isExporting) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            }
        }
    }
}

private fun shareData(context: Context, sessions: List<Session>, format: String) {
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
        context.startActivity(Intent.createChooser(intent, "Share Practice Data"))
    } catch (e: Exception) {
        Toast.makeText(context, "Share failed: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

private suspend fun saveBackupToDevice(context: Context, sessions: List<Session>, sessionCount: Int): String {
    return withContext(Dispatchers.IO) {
        try {
            val json = buildJsonContent(sessions)
            // Use app-private external storage (no MANAGE_EXTERNAL_STORAGE permission required on API 29+).
            // Path: Android/data/com.prayerwheel.app/files/Download/
            val downloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: context.filesDir
            if (!downloadsDir.exists()) downloadsDir.mkdirs()

            val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val file = File(downloadsDir, "prayer-wheel-backup-$dateStr.json")
            FileWriter(file).use { it.write(json) }

            "Backup saved: ${file.absolutePath} (${sessionCount} sessions)"
        } catch (e: Exception) {
            throw Exception("Could not save backup: ${e.message}")
        }
    }
}

private fun buildCsvContent(sessions: List<Session>): String {
    return buildString {
        appendLine("Date,Duration,Mantra,Rotations,Mantras,Avg RPM,Peak RPM,Mode,Intention,Dedication,Wheel")
        sessions.forEach { session ->
            val mantra = Mantras.byId(session.mantraId)
            val displayName = mantra?.displayName ?: session.mantraId
            val duration = session.endedAt?.let { calculateDuration(session.startedAt, it) } ?: ""
            val intention = session.intention?.replace("\"", "\"\"") ?: ""
            val dedication = session.dedication?.replace("\"", "\"\"") ?: ""
            val dateStr = SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.getDefault()).format(Date(session.startedAt))
            val wheel = session.wheelId ?: ""
            appendLine("\"$dateStr\",\"$duration\",\"$displayName\",${session.rotationCount},${session.totalMantras},${session.averageRpm},${session.peakRpm},\"${session.mode}\",\"$intention\",\"$dedication\",\"$wheel\"")
        }
    }
}

private fun buildJsonContent(sessions: List<Session>): String {
    return buildString {
        appendLine("{")
        appendLine("  \"exportedAt\": \"${SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault()).format(Date())}\",")
        appendLine("  \"sessionCount\": ${sessions.size},")
        appendLine("  \"sessions\": [")
        sessions.forEachIndexed { index, session ->
            appendLine("    {")
            appendLine("      \"id\": ${session.id},")
            appendLine("      \"startedAt\": ${session.startedAt},")
            appendLine("      \"endedAt\": ${session.endedAt ?: "null"},")
            appendLine("      \"rotationCount\": ${session.rotationCount},")
            appendLine("      \"mantrasPerRotation\": ${session.mantrasPerRotation},")
            appendLine("      \"totalMantras\": ${session.totalMantras},")
            appendLine("      \"mantraId\": \"${session.mantraId}\",")
            appendLine("      \"mode\": \"${session.mode}\",")
            appendLine("      \"averageRpm\": ${session.averageRpm},")
            appendLine("      \"peakRpm\": ${session.peakRpm},")
            appendLine("      \"totalSpins\": ${session.totalSpins},")
            appendLine("      \"wheelId\": ${if (session.wheelId != null) "\"${session.wheelId}\"" else "null"},")
            session.intention?.let {
                appendLine("      \"intention\": \"${it.replace("\"", "\\\"")}\",")
            } ?: appendLine("      \"intention\": null,")
            session.dedication?.let {
                appendLine("      \"dedication\": \"${it.replace("\"", "\\\"")}\"")
            } ?: appendLine("      \"dedication\": null")
            if (index < sessions.size - 1) {
                appendLine("    },")
            } else {
                appendLine("    }")
            }
        }
        appendLine("  ]")
        appendLine("}")
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


