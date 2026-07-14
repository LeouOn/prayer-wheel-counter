package com.prayerwheel.app.ui.wheel

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.prayerwheel.app.data.datastore.SpinMode
import com.prayerwheel.app.data.datastore.NumberFormatStyle
import com.prayerwheel.app.data.datastore.UserPreferences
import com.prayerwheel.app.data.db.dao.LifetimeStatsDao
import com.prayerwheel.app.data.db.dao.SessionDao
import com.prayerwheel.app.data.model.LifetimeStats
import com.prayerwheel.app.notification.EndOfDayScheduler
import com.prayerwheel.app.notification.ReminderScheduler
import com.prayerwheel.app.ui.components.CapacitySlider
import com.prayerwheel.app.ui.components.NumberFormatter
import com.prayerwheel.app.viewmodel.WheelViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.util.Locale
import kotlin.math.log10

/**
 * Settings screen with all preference groups.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    userPreferences: UserPreferences,
    viewModel: WheelViewModel,
    lifetimeStatsDao: LifetimeStatsDao,
    sessionDao: SessionDao,
    onNavigateBack: () -> Unit,
    onNavigateToCalculator: () -> Unit,
    onNavigateToExport: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    
    val theme by userPreferences.theme.collectAsState(initial = "system")
    val spinMode by userPreferences.spinMode.collectAsState(initial = SpinMode.MANUAL)
    val hapticEnabled by userPreferences.hapticEnabled.collectAsState(initial = true)
    val mantrasPerRotation by userPreferences.mantrasPerRotation.collectAsState(initial = 1)
    val autoSpinRpm by userPreferences.autoSpinRpm.collectAsState(initial = 30)
    val friction by userPreferences.friction.collectAsState(initial = 0.97f)
    val clockwiseDirection by userPreferences.clockwiseDirection.collectAsState(initial = true)
    val recitationEnabled by userPreferences.recitationEnabled.collectAsState(initial = false)
    val recitationVolume by userPreferences.recitationVolume.collectAsState(initial = 0.5f)
    val bellAtMilestones by userPreferences.bellAtMilestones.collectAsState(initial = false)
    val ambientEnabled by userPreferences.ambientEnabled.collectAsState(initial = false)
    val ambientVolume by userPreferences.ambientVolume.collectAsState(initial = 0.5f)
    val masterVolume by userPreferences.masterVolume.collectAsState(initial = 1.0f)
    val showCounter by userPreferences.showCounter.collectAsState(initial = true)
    val milestoneNotifications by userPreferences.milestoneNotifications.collectAsState(initial = true)
    val keepScreenOn by userPreferences.keepScreenOn.collectAsState(initial = false)
    val counterClockwiseEnabled by userPreferences.counterClockwiseEnabled.collectAsState(initial = false)
    val customDedication by userPreferences.customDedication.collectAsState(initial = null)
    val sessionTimeGoalSeconds by userPreferences.sessionTimeGoalSeconds.collectAsState(initial = 0L)
    val dailyTimeGoalSeconds by userPreferences.dailyTimeGoalSeconds.collectAsState(initial = 0L)
    val dailyMantraGoal by userPreferences.dailyMantraGoal.collectAsState(initial = 0L)
    val backgroundVibrationEnabled by userPreferences.backgroundVibrationEnabled.collectAsState(initial = false)
    val vibrationIntensity by userPreferences.vibrationIntensity.collectAsState(initial = 1.0f)

    // Practice Rhythm bindings (reminders, merge threshold, labels, wheel mass)
    val reminderMorningEnabled by userPreferences.reminderMorningEnabled.collectAsState(initial = false)
    val reminderMorningHour by userPreferences.reminderMorningHour.collectAsState(initial = 7)
    val reminderMorningMinute by userPreferences.reminderMorningMinute.collectAsState(initial = 0)
    val reminderEveningEnabled by userPreferences.reminderEveningEnabled.collectAsState(initial = false)
    val reminderEveningHour by userPreferences.reminderEveningHour.collectAsState(initial = 19)
    val reminderEveningMinute by userPreferences.reminderEveningMinute.collectAsState(initial = 0)
    val reminderEndOfDayEnabled by userPreferences.reminderEndOfDayEnabled.collectAsState(initial = false)
    val reminderEndOfDayHour by userPreferences.reminderEndOfDayHour.collectAsState(initial = 21)
    val reminderEndOfDayMinute by userPreferences.reminderEndOfDayMinute.collectAsState(initial = 30)
    val sessionMergeThresholdMs by userPreferences.sessionMergeThresholdMs.collectAsState(initial = 300000L)
    val autoLabelSessions by userPreferences.autoLabelSessions.collectAsState(initial = true)
    val wheelMass by userPreferences.wheelMass.collectAsState(initial = 1.0f)

    // Lifetime stats
    val lifetimeStats by lifetimeStatsDao.observeStats().collectAsState(initial = null)

    // Expandable section states
    var wheelExpanded by remember { mutableStateOf(true) }
    var interactionExpanded by remember { mutableStateOf(true) }
    var audioExpanded by remember { mutableStateOf(false) }
    var practiceExpanded by remember { mutableStateOf(false) }
    var practiceRhythmExpanded by remember { mutableStateOf(false) }
    var displayExpanded by remember { mutableStateOf(true) }
    var dataExpanded by remember { mutableStateOf(false) }
    var aboutExpanded by remember { mutableStateOf(false) }

    val context = LocalContext.current

    var timePickerSlot by remember { mutableStateOf<ReminderSlot?>(null) }

    var showExactAlarmRationale by remember { mutableStateOf(false) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        // Empty on purpose: permission state is re-checked on the next toggle,
        // so there is nothing to do with the result here.
    }

    // Reset confirmation dialog
    var showResetDialog by remember { mutableStateOf(false) }

    var showLifetimeResetDialog by remember { mutableStateOf(false) }

    // Dedication editor dialog
    var showDedicationEditor by remember { mutableStateOf(false) }
    var dedicationEditorText by remember { mutableStateOf(customDedication ?: UserPreferences.DEFAULT_DEDICATION_TEXT) }

    // Selected mantra
    val selectedMantraId by userPreferences.selectedMantra.collectAsState(initial = "om_mani_padme_hum")
    val currentMantra = com.prayerwheel.app.data.model.Mantras.byId(selectedMantraId) ?: com.prayerwheel.app.data.model.Mantras.OM_MANI_PADME_HUM

    // Dropdown states
    var showThemeDropdown by remember { mutableStateOf(false) }
    var showNumberFormatDropdown by remember { mutableStateOf(false) }
    var showMantraDropdown by remember { mutableStateOf(false) }

    // Spin mode dropdown
    var showSpinModeDropdown by remember { mutableStateOf(false) }

    fun ensureReminderPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                showExactAlarmRationale = true
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
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
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // WHEEL SECTION
            SettingsSection(
                title = "Wheel",
                expanded = wheelExpanded,
                onToggle = { wheelExpanded = !wheelExpanded }
            ) {
                // Mantra selector
                ListItem(
                    headlineContent = { Text("Mantra") },
                    supportingContent = {
                        Column {
                            Text(currentMantra.displayName)
                            currentMantra.tibetan?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        }
                    },
                    modifier = Modifier.clickable { showMantraDropdown = true }
                )
                DropdownMenu(
                    expanded = showMantraDropdown,
                    onDismissRequest = { showMantraDropdown = false }
                ) {
                    com.prayerwheel.app.data.model.Mantras.ALL.forEach { mantra ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(mantra.displayName)
                                    mantra.tibetan?.let {
                                        Text(
                                            text = it,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                        )
                                    }
                                }
                            },
                            onClick = {
                                scope.launch {
                                    userPreferences.setSelectedMantra(mantra.id)
                                    viewModel.setSelectedMantra(mantra.id)
                                }
                                showMantraDropdown = false
                            }
                        )
                    }
                }
                
                ListItem(
                    headlineContent = { Text("Mantras per Rotation") },
                    supportingContent = {
                        Text(
                            NumberFormatter.formatWithFull(
                                BigInteger.valueOf(mantrasPerRotation)
                            )
                        )
                    }
                )
                CapacitySlider(
                    currentValue = mantrasPerRotation,
                    onValueChange = {
                        scope.launch { userPreferences.setMantrasPerRotation(it) }
                    },
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                // Direction
                ListItem(
                    headlineContent = { Text("Direction") },
                    supportingContent = { Text(if (clockwiseDirection) "Clockwise" else "Counter-clockwise") },
                    trailingContent = {
                        Switch(
                            checked = clockwiseDirection,
                            onCheckedChange = {
                                scope.launch { userPreferences.setClockwiseDirection(it) }
                            }
                        )
                    }
                )
            }

            // INTERACTION SECTION
            SettingsSection(
                title = "Interaction",
                expanded = interactionExpanded,
                onToggle = { interactionExpanded = !interactionExpanded }
            ) {
                // Spin Mode
                ListItem(
                    headlineContent = { Text("Spin Mode") },
                    supportingContent = { Text(spinMode.name.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }) },
                    modifier = Modifier.clickable { showSpinModeDropdown = true }
                )
                DropdownMenu(
                    expanded = showSpinModeDropdown,
                    onDismissRequest = { showSpinModeDropdown = false }
                ) {
                    SpinMode.entries.forEach { mode ->
                        DropdownMenuItem(
                            text = { Text(mode.name.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }) },
                            onClick = {
                                scope.launch { userPreferences.setSpinMode(mode) }
                                showSpinModeDropdown = false
                            }
                        )
                    }
                }

                // Friction
                ListItem(
                    headlineContent = { Text("Friction") },
                    supportingContent = { Text("Low - High") }
                )
                Slider(
                    value = friction,
                    onValueChange = {
                        scope.launch { userPreferences.setFriction(it) }
                    },
                    valueRange = 0.9f..0.99f,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                )

                // Max RPM (for auto-spin)
                if (spinMode == SpinMode.AUTO_SPIN) {
                    ListItem(
                        headlineContent = { Text("Max RPM") },
                        supportingContent = { Text("$autoSpinRpm RPM") }
                    )
                    Slider(
                        value = autoSpinRpm.toFloat(),
                        onValueChange = {
                            scope.launch { userPreferences.setAutoSpinRpm(it.toInt()) }
                        },
                        valueRange = 1f..60f,
                        steps = 58,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    )
                }

                // Haptics
                ListItem(
                    headlineContent = { Text("Haptics") },
                    trailingContent = {
                        Switch(
                            checked = hapticEnabled,
                            onCheckedChange = {
                                scope.launch { userPreferences.setHapticEnabled(it) }
                            }
                        )
                    }
                )
                if (hapticEnabled) {
                    ListItem(
                        headlineContent = { Text("Vibration Intensity") },
                        supportingContent = { Text("${(vibrationIntensity * 100).toInt()}%") }
                    )
                    Slider(
                        value = vibrationIntensity,
                        onValueChange = {
                            viewModel.setVibrationIntensity(it)
                        },
                        valueRange = 0.1f..1.0f,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    )
                    ListItem(
                        headlineContent = { Text("Background Vibration") },
                        supportingContent = { Text("Vibrate when app is in background") },
                        trailingContent = {
                            Switch(
                                checked = backgroundVibrationEnabled,
                                onCheckedChange = {
                                    viewModel.setBackgroundVibrationEnabled(it)
                                }
                            )
                        }
                    )
                }

                // Counter-clockwise
                ListItem(
                    headlineContent = { Text("Counter-clockwise rotations") },
                    supportingContent = { Text("Count anti-clockwise spins") },
                    trailingContent = {
                        Switch(
                            checked = counterClockwiseEnabled,
                            onCheckedChange = {
                                scope.launch { userPreferences.setCounterClockwiseEnabled(it) }
                            }
                        )
                    }
                )
            }

            // AUDIO SECTION
            SettingsSection(
                title = "Audio",
                expanded = audioExpanded,
                onToggle = { audioExpanded = !audioExpanded }
            ) {
                // Recitation
                ListItem(
                    headlineContent = { Text("Recitation") },
                    trailingContent = {
                        Switch(
                            checked = recitationEnabled,
                            onCheckedChange = {
                                scope.launch { userPreferences.setRecitationEnabled(it) }
                            }
                        )
                    }
                )
                if (recitationEnabled) {
                    ListItem(
                        headlineContent = { Text("Recitation Volume") },
                        supportingContent = { Text("${(recitationVolume * 100).toInt()}%") }
                    )
                    Slider(
                        value = recitationVolume,
                        onValueChange = {
                            scope.launch { userPreferences.setRecitationVolume(it) }
                        },
                        valueRange = 0f..1f,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    )
                }

                // Bell at milestones
                ListItem(
                    headlineContent = { Text("Bell at milestones") },
                    trailingContent = {
                        Switch(
                            checked = bellAtMilestones,
                            onCheckedChange = {
                                scope.launch { userPreferences.setBellAtMilestones(it) }
                            }
                        )
                    }
                )

                // Ambient sounds
                ListItem(
                    headlineContent = { Text("Ambient sounds") },
                    trailingContent = {
                        Switch(
                            checked = ambientEnabled,
                            onCheckedChange = {
                                scope.launch { userPreferences.setAmbientEnabled(it) }
                            }
                        )
                    }
                )
                if (ambientEnabled) {
                    ListItem(
                        headlineContent = { Text("Ambient Volume") },
                        supportingContent = { Text("${(ambientVolume * 100).toInt()}%") }
                    )
                    Slider(
                        value = ambientVolume,
                        onValueChange = {
                            scope.launch { userPreferences.setAmbientVolume(it) }
                        },
                        valueRange = 0f..1f,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    )
                }

                // Master volume
                ListItem(
                    headlineContent = { Text("Master Volume") },
                    supportingContent = { Text("${(masterVolume * 100).toInt()}%") }
                )
                Slider(
                    value = masterVolume,
                    onValueChange = {
                        scope.launch { userPreferences.setMasterVolume(it) }
                    },
                    valueRange = 0f..1f,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                )
            }

            // PRACTICE SECTION
            SettingsSection(
                title = "Practice",
                expanded = practiceExpanded,
                onToggle = { practiceExpanded = !practiceExpanded }
            ) {
                // Calculator
                ListItem(
                    headlineContent = { Text("Practice Calculator") },
                    supportingContent = { Text("Project future mantra accumulations") },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                    },
                    modifier = Modifier.clickable { onNavigateToCalculator() }
                )

                // Dedication Text
                ListItem(
                    headlineContent = { Text("Default Dedication") },
                    supportingContent = { Text(if (customDedication != null) "Custom" else "Traditional") },
                    modifier = Modifier.clickable { 
                        dedicationEditorText = customDedication ?: UserPreferences.DEFAULT_DEDICATION_TEXT
                        showDedicationEditor = true 
                    }
                )

                // Show counter
                ListItem(
                    headlineContent = { Text("Show counter") },
                    trailingContent = {
                        Switch(
                            checked = showCounter,
                            onCheckedChange = {
                                scope.launch { userPreferences.setShowCounter(it) }
                            }
                        )
                    }
                )

                // Time Goals
                ListItem(
                    headlineContent = { Text("Session Time Goal") },
                    supportingContent = { Text(if (sessionTimeGoalSeconds > 0) "${sessionTimeGoalSeconds / 60} minutes" else "None") }
                )
                Slider(
                    value = (sessionTimeGoalSeconds / 60f).coerceIn(0f, 60f),
                    onValueChange = {
                        scope.launch { viewModel.setSessionTimeGoal(it.toLong() * 60) }
                    },
                    valueRange = 0f..60f,
                    steps = 59,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                )

                // Milestone notifications
                ListItem(
                    headlineContent = { Text("Milestone notifications") },
                    trailingContent = {
                        Switch(
                            checked = milestoneNotifications,
                            onCheckedChange = {
                                scope.launch { userPreferences.setMilestoneNotifications(it) }
                            }
                        )
                    }
                )
            }

            // PRACTICE RHYTHM SECTION
            SettingsSection(
                title = "Practice Rhythm",
                expanded = practiceRhythmExpanded,
                onToggle = { practiceRhythmExpanded = !practiceRhythmExpanded }
            ) {
                // Daily mantra goal
                ListItem(
                    headlineContent = { Text("Daily mantra goal") },
                    supportingContent = {
                        Text(
                            if (dailyMantraGoal > 0)
                                NumberFormatter.formatWithStyle(dailyMantraGoal, NumberFormatStyle.STANDARD)
                            else "None"
                        )
                    }
                )
                Slider(
                    value = dailyMantraGoal.toFloat().coerceIn(0f, 10000f),
                    onValueChange = {
                        scope.launch { viewModel.setDailyGoal(it.toLong()) }
                    },
                    valueRange = 0f..10000f,
                    steps = 99,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                )

                // Daily time goal
                ListItem(
                    headlineContent = { Text("Daily time goal") },
                    supportingContent = { Text(if (dailyTimeGoalSeconds > 0) "${dailyTimeGoalSeconds / 60} minutes" else "None") }
                )
                Slider(
                    value = (dailyTimeGoalSeconds / 60f).coerceIn(0f, 120f),
                    onValueChange = {
                        scope.launch { viewModel.setDailyTimeGoal(it.toLong() * 60) }
                    },
                    valueRange = 0f..120f,
                    steps = 119,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                )

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))

                // Morning reminder: toggle + tappable time
                ListItem(
                    headlineContent = { Text("Morning reminder") },
                    supportingContent = { Text(formatClockTime(reminderMorningHour, reminderMorningMinute)) },
                    trailingContent = {
                        Switch(
                            checked = reminderMorningEnabled,
                            onCheckedChange = { enabled ->
                                scope.launch { userPreferences.setReminderMorningEnabled(enabled) }
                                if (enabled) {
                                    ensureReminderPermissions()
                                    ReminderScheduler.scheduleMorningReminder(
                                        context, reminderMorningHour, reminderMorningMinute
                                    )
                                } else {
                                    ReminderScheduler.cancelMorningReminder(context)
                                }
                            }
                        )
                    },
                    modifier = Modifier.clickable { timePickerSlot = ReminderSlot.MORNING }
                )

                // Evening reminder: toggle + tappable time
                ListItem(
                    headlineContent = { Text("Evening reminder") },
                    supportingContent = { Text(formatClockTime(reminderEveningHour, reminderEveningMinute)) },
                    trailingContent = {
                        Switch(
                            checked = reminderEveningEnabled,
                            onCheckedChange = { enabled ->
                                scope.launch { userPreferences.setReminderEveningEnabled(enabled) }
                                if (enabled) {
                                    ensureReminderPermissions()
                                    ReminderScheduler.scheduleEveningReminder(
                                        context, reminderEveningHour, reminderEveningMinute
                                    )
                                } else {
                                    ReminderScheduler.cancelEveningReminder(context)
                                }
                            }
                        )
                    },
                    modifier = Modifier.clickable { timePickerSlot = ReminderSlot.EVENING }
                )

                // End-of-day summary: toggle + tappable time
                ListItem(
                    headlineContent = { Text("End-of-day summary") },
                    supportingContent = {
                        Text(
                            if (reminderEndOfDayEnabled) formatClockTime(reminderEndOfDayHour, reminderEndOfDayMinute)
                            else "A quiet recap if you practiced today"
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = reminderEndOfDayEnabled,
                            onCheckedChange = { enabled ->
                                scope.launch { userPreferences.setReminderEndOfDayEnabled(enabled) }
                                if (enabled) {
                                    ensureReminderPermissions()
                                    EndOfDayScheduler.scheduleEndOfDaySummary(
                                        context, reminderEndOfDayHour, reminderEndOfDayMinute
                                    )
                                } else {
                                    EndOfDayScheduler.cancelEndOfDaySummary(context)
                                }
                            }
                        )
                    },
                    modifier = Modifier.clickable { timePickerSlot = ReminderSlot.END_OF_DAY }
                )

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))

                // Session merge threshold (log scale, 1s..10min)
                ListItem(
                    headlineContent = { Text("Session merge threshold") },
                    supportingContent = { Text(formatMergeThreshold(sessionMergeThresholdMs)) }
                )
                Slider(
                    value = thresholdMsToSlider(sessionMergeThresholdMs),
                    onValueChange = { sliderPos ->
                        scope.launch {
                            userPreferences.setSessionMergeThresholdMs(sliderToThresholdMs(sliderPos))
                        }
                    },
                    valueRange = 0f..1f,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                )
                Text(
                    text = "How long after a session ends before a new spin counts as a new session.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))

                // Auto-label sessions toggle
                ListItem(
                    headlineContent = { Text("Auto-label sessions") },
                    supportingContent = { Text("Suggest morning / evening labels for new sessions") },
                    trailingContent = {
                        Switch(
                            checked = autoLabelSessions,
                            onCheckedChange = {
                                scope.launch { userPreferences.setAutoLabelSessions(it) }
                            }
                        )
                    }
                )

                // Wheel mass (physics feel): 0.5..3.0
                ListItem(
                    headlineContent = { Text("Wheel mass") },
                    supportingContent = { Text(String.format(Locale.getDefault(), "%.1f", wheelMass)) }
                )
                Slider(
                    value = wheelMass,
                    onValueChange = {
                        scope.launch { userPreferences.setWheelMass(it) }
                    },
                    valueRange = 0.5f..3.0f,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                )
            }

            // DISPLAY SECTION
            SettingsSection(
                title = "Display",
                expanded = displayExpanded,
                onToggle = { displayExpanded = !displayExpanded }
            ) {
                // Theme
                ListItem(
                    headlineContent = { Text("Theme") },
                    supportingContent = { Text(theme.replaceFirstChar { it.uppercase() }) },
                    modifier = Modifier.clickable { showThemeDropdown = true }
                )
                DropdownMenu(
                    expanded = showThemeDropdown,
                    onDismissRequest = { showThemeDropdown = false }
                ) {
                    listOf("system", "light", "dark", "sepia", "dawn_dusk").forEach { themeOption ->
                        DropdownMenuItem(
                            text = { Text(themeOption.replace("_", " ").replaceFirstChar { it.uppercase() }) },
                            onClick = {
                                scope.launch { userPreferences.setTheme(themeOption) }
                                showThemeDropdown = false
                            }
                        )
                    }
                }

                // Number Format
                val numberFormatStyle by userPreferences.numberFormatStyle.collectAsState(initial = com.prayerwheel.app.data.datastore.NumberFormatStyle.STANDARD)
                ListItem(
                    headlineContent = { Text("Number Format") },
                    supportingContent = { Text(numberFormatStyle.name.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }) },
                    modifier = Modifier.clickable { showNumberFormatDropdown = true }
                )
                DropdownMenu(
                    expanded = showNumberFormatDropdown,
                    onDismissRequest = { showNumberFormatDropdown = false }
                ) {
                    com.prayerwheel.app.data.datastore.NumberFormatStyle.entries.forEach { style ->
                        DropdownMenuItem(
                            text = { Text(style.name.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }) },
                            onClick = {
                                viewModel.setNumberFormatStyle(style)
                                showNumberFormatDropdown = false
                            }
                        )
                    }
                }

                // Number Notation
                val numberNotation by userPreferences.numberNotation.collectAsState(initial = com.prayerwheel.app.data.datastore.NumberNotation.STANDARD)
                ListItem(
                    headlineContent = { Text("Number Notation") },
                    supportingContent = { 
                        Text(
                            when (numberNotation) {
                                com.prayerwheel.app.data.datastore.NumberNotation.STANDARD -> "Standard (K/M/B/T)"
                                com.prayerwheel.app.data.datastore.NumberNotation.EXTENDED -> "Extended (aa/ab/ac...)"
                            }
                        )
                    },
                    modifier = Modifier.clickable { 
                        // Toggle between STANDARD and EXTENDED
                        val newNotation = when (numberNotation) {
                            com.prayerwheel.app.data.datastore.NumberNotation.STANDARD -> com.prayerwheel.app.data.datastore.NumberNotation.EXTENDED
                            com.prayerwheel.app.data.datastore.NumberNotation.EXTENDED -> com.prayerwheel.app.data.datastore.NumberNotation.STANDARD
                        }
                        scope.launch { userPreferences.setNumberNotation(newNotation) }
                    }
                )

                // Keep screen on
                ListItem(
                    headlineContent = { Text("Keep screen on") },
                    trailingContent = {
                        Switch(
                            checked = keepScreenOn,
                            onCheckedChange = {
                                scope.launch { userPreferences.setKeepScreenOn(it) }
                            }
                        )
                    }
                )
            }

            // DATA SECTION
            SettingsSection(
                title = "Data & Statistics",
                expanded = dataExpanded,
                onToggle = { dataExpanded = !dataExpanded }
            ) {
                lifetimeStats?.let { stats ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Lifetime Statistics",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Medium
                            )
                            HorizontalDivider()
                            StatRow(
                                label = "Total Mantras",
                                value = NumberFormatter.formatWithStyle(stats.totalMantras, com.prayerwheel.app.data.datastore.NumberFormatStyle.STANDARD)
                            )
                            StatRow(
                                label = "Exact Count",
                                value = NumberFormatter.formatWithCommas(stats.totalMantras)
                            )
                            StatRow(
                                label = "Total Rotations",
                                value = NumberFormatter.formatLong(stats.totalRotations)
                            )
                            StatRow(
                                label = "Sessions Completed",
                                value = NumberFormatter.formatLong(stats.sessionsCompleted)
                            )
                            if (stats.totalSpinningTimeSeconds > 0) {
                                StatRow(
                                    label = "Total Practice Time",
                                    value = formatDuration(stats.totalSpinningTimeSeconds)
                                )
                            }
                            if (stats.averageSessionDurationSeconds > 0) {
                                StatRow(
                                    label = "Average Session",
                                    value = formatDuration(stats.averageSessionDurationSeconds)
                                )
                            }
                            if (stats.totalRotations > 0 && stats.totalSpinningTimeSeconds > 0) {
                                val avgRpm = stats.totalRotations.toFloat() / (stats.totalSpinningTimeSeconds.toFloat() / 60f)
                                StatRow(
                                    label = "Lifetime Average RPM",
                                    value = String.format(Locale.getDefault(), "%.1f", avgRpm)
                                )
                            }
                            stats.firstSessionAt?.let { firstMs ->
                                val daysSinceStart = (System.currentTimeMillis() - firstMs) / (1000 * 60 * 60 * 24)
                                if (daysSinceStart > 0) {
                                    StatRow(
                                        label = "Practicing For",
                                        value = if (daysSinceStart >= 365) {
                                            String.format(Locale.getDefault(), "%d days (%.1f years)", daysSinceStart, daysSinceStart / 365.25)
                                        } else {
                                            "$daysSinceStart days"
                                        }
                                    )
                                    val mantrasPerDay = stats.totalMantras.toBigDecimal()
                                        .divide(BigDecimal(daysSinceStart), 0, java.math.RoundingMode.HALF_UP)
                                    StatRow(
                                        label = "Average Mantras/Day",
                                        value = NumberFormatter.format(mantrasPerDay.toBigInteger())
                                    )
                                }
                            }
                        }
                    }
                } ?: run {
                    ListItem(
                        headlineContent = { Text("No practice data yet") },
                        supportingContent = { Text("Complete a session to see your statistics here") }
                    )
                }

                // Export CSV
                ListItem(
                    headlineContent = { Text("Export (CSV)") },
                    modifier = Modifier.clickable { onNavigateToExport() }
                )

                // Export JSON
                ListItem(
                    headlineContent = { Text("Export (JSON)") },
                    modifier = Modifier.clickable { onNavigateToExport() }
                )

                // Backup
                ListItem(
                    headlineContent = { Text("Backup") },
                    modifier = Modifier.clickable { onNavigateToExport() }
                )

                // Reset all data
                ListItem(
                    headlineContent = { Text("Reset all data") },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                    },
                    modifier = Modifier.clickable { showResetDialog = true }
                )
            }

            // ABOUT SECTION
            SettingsSection(
                title = "About",
                expanded = aboutExpanded,
                onToggle = { aboutExpanded = !aboutExpanded }
            ) {
                // Version
                ListItem(
                    headlineContent = { Text("Version") },
                    supportingContent = { Text("1.0.0") }
                )

                // Privacy
                ListItem(
                    headlineContent = { Text("Privacy") },
                    supportingContent = { Text("No data is collected or shared") }
                )
            }

            // DANGER ZONE
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Danger Zone",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = "Reset your lifetime accumulation counters. Your individual session records are preserved, but the totals shown in stats will reset to zero. Use this to start fresh after a data issue.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Button(
                        onClick = { showLifetimeResetDialog = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        )
                    ) {
                        Text("Reset Lifetime Stats")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Reset confirmation dialog
        if (showResetDialog) {
            AlertDialog(
                onDismissRequest = { showResetDialog = false },
                title = { Text("Reset All Data") },
                text = { Text("This will permanently delete all your practice history and preferences. This action cannot be undone.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            scope.launch {
                                userPreferences.clearAllData()
                                sessionDao.deleteAll()
                                lifetimeStatsDao.deleteAll()
                            }
                            showResetDialog = false
                        }
                    ) {
                        Text("Reset", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showResetDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Lifetime-stats reset confirmation dialog
        if (showLifetimeResetDialog) {
            AlertDialog(
                onDismissRequest = { showLifetimeResetDialog = false },
                title = { Text("Reset Lifetime Stats") },
                text = {
                    Text(
                        "This will zero out your lifetime rotation count, total mantras, and total practice time. " +
                            "Your individual session records will be preserved. This cannot be undone."
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showLifetimeResetDialog = false
                            viewModel.resetLifetimeStats()
                        }
                    ) {
                        Text("Reset", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showLifetimeResetDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
        
        // Dedication editor dialog
        if (showDedicationEditor) {
            AlertDialog(
                onDismissRequest = { showDedicationEditor = false },
                title = { Text("Default Dedication") },
                text = {
                    OutlinedTextField(
                        value = dedicationEditorText,
                        onValueChange = { dedicationEditorText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp),
                        maxLines = 5,
                        placeholder = { Text("Enter your dedication text...") }
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            scope.launch {
                                userPreferences.setCustomDedication(dedicationEditorText.ifBlank { null })
                            }
                            showDedicationEditor = false
                        }
                    ) {
                        Text("Save")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDedicationEditor = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Reminder TimePicker dialog
        timePickerSlot?.let { slot ->
            val initialHour = when (slot) {
                ReminderSlot.MORNING -> reminderMorningHour
                ReminderSlot.EVENING -> reminderEveningHour
                ReminderSlot.END_OF_DAY -> reminderEndOfDayHour
            }
            val initialMinute = when (slot) {
                ReminderSlot.MORNING -> reminderMorningMinute
                ReminderSlot.EVENING -> reminderEveningMinute
                ReminderSlot.END_OF_DAY -> reminderEndOfDayMinute
            }
            val timePickerState = rememberTimePickerState(
                initialHour = initialHour,
                initialMinute = initialMinute,
                is24Hour = false
            )
            AlertDialog(
                onDismissRequest = { timePickerSlot = null },
                title = {
                    Text(
                        when (slot) {
                            ReminderSlot.MORNING -> "Morning reminder time"
                            ReminderSlot.EVENING -> "Evening reminder time"
                            ReminderSlot.END_OF_DAY -> "End-of-day summary time"
                        }
                    )
                },
                text = { TimePicker(state = timePickerState) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            scope.launch {
                                when (slot) {
                                    ReminderSlot.MORNING -> {
                                        userPreferences.setReminderMorningHour(timePickerState.hour)
                                        userPreferences.setReminderMorningMinute(timePickerState.minute)
                                    }
                                    ReminderSlot.EVENING -> {
                                        userPreferences.setReminderEveningHour(timePickerState.hour)
                                        userPreferences.setReminderEveningMinute(timePickerState.minute)
                                    }
                                    ReminderSlot.END_OF_DAY -> {
                                        userPreferences.setReminderEndOfDayHour(timePickerState.hour)
                                        userPreferences.setReminderEndOfDayMinute(timePickerState.minute)
                                    }
                                }
                            }
                            when (slot) {
                                ReminderSlot.MORNING -> {
                                    if (reminderMorningEnabled) {
                                        ReminderScheduler.scheduleMorningReminder(
                                            context, timePickerState.hour, timePickerState.minute
                                        )
                                    }
                                }
                                ReminderSlot.EVENING -> {
                                    if (reminderEveningEnabled) {
                                        ReminderScheduler.scheduleEveningReminder(
                                            context, timePickerState.hour, timePickerState.minute
                                        )
                                    }
                                }
                                ReminderSlot.END_OF_DAY -> {
                                    if (reminderEndOfDayEnabled) {
                                        EndOfDayScheduler.scheduleEndOfDaySummary(
                                            context, timePickerState.hour, timePickerState.minute
                                        )
                                    }
                                }
                            }
                            timePickerSlot = null
                        }
                    ) { Text("Set") }
                },
                dismissButton = {
                    TextButton(onClick = { timePickerSlot = null }) { Text("Cancel") }
                }
            )
        }

        // Exact-alarm permission rationale dialog (API 31+)
        if (showExactAlarmRationale) {
            AlertDialog(
                onDismissRequest = { showExactAlarmRationale = false },
                title = { Text("Exact alarm permission") },
                text = {
                    Text(
                        "To fire practice reminders at the precise time you choose, " +
                            "allow this app to schedule exact alarms in Android settings. " +
                            "Without it, reminders may be delayed by several minutes."
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showExactAlarmRationale = false
                            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                data = Uri.parse("package:${context.packageName}")
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            runCatching { context.startActivity(intent) }
                        }
                    ) { Text("Open settings") }
                },
                dismissButton = {
                    TextButton(onClick = { showExactAlarmRationale = false }) { Text("Not now") }
                }
            )
        }
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
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium
        )
    }
}

private fun formatDuration(seconds: Long): String {
    return when {
        seconds >= 86400 -> {
            val days = seconds / 86400
            val hrs = (seconds % 86400) / 3600
            if (hrs > 0) String.format(Locale.getDefault(), "%dd %dh", days, hrs) else "${days}d"
        }
        seconds >= 3600 -> {
            val hrs = seconds / 3600
            val mins = (seconds % 3600) / 60
            if (mins > 0) String.format(Locale.getDefault(), "%dh %dm", hrs, mins) else "${hrs}h"
        }
        seconds >= 60 -> {
            val mins = seconds / 60
            val secs = seconds % 60
            if (secs > 0) String.format(Locale.getDefault(), "%dm %ds", mins, secs) else "${mins}m"
        }
        else -> "${seconds}s"
    }
}

private enum class ReminderSlot { MORNING, EVENING, END_OF_DAY }

private fun formatClockTime(hour: Int, minute: Int): String {
    val amPm = if (hour < 12) "AM" else "PM"
    val displayHour = when {
        hour == 0 -> 12
        hour > 12 -> hour - 12
        else -> hour
    }
    return String.format(Locale.getDefault(), "%d:%02d %s", displayHour, minute, amPm)
}

private fun formatMergeThreshold(ms: Long): String {
    val seconds = (ms / 1000).coerceAtLeast(1L)
    return formatDuration(seconds)
}

private val MERGE_THRESHOLD_MIN_MS = 1_000L
private val MERGE_THRESHOLD_MAX_MS = 600_000L

private fun thresholdMsToSlider(ms: Long): Float {
    val lMin = log10(MERGE_THRESHOLD_MIN_MS.toDouble())
    val lMax = log10(MERGE_THRESHOLD_MAX_MS.toDouble())
    val lVal = log10(ms.coerceIn(MERGE_THRESHOLD_MIN_MS, MERGE_THRESHOLD_MAX_MS).toDouble())
    return ((lVal - lMin) / (lMax - lMin)).toFloat()
}

private fun sliderToThresholdMs(sliderPos: Float): Long {
    val lMin = log10(MERGE_THRESHOLD_MIN_MS.toDouble())
    val lMax = log10(MERGE_THRESHOLD_MAX_MS.toDouble())
    val lVal = lMin + sliderPos.coerceIn(0f, 1f) * (lMax - lMin)
    return Math.pow(10.0, lVal).toLong()
}

/**
 * Expandable settings section.
 */
@Composable
private fun SettingsSection(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column {
            // Section header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggle() }
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse" else "Expand"
                )
            }

            // Section content
            if (expanded) {
                Column {
                    content()
                }
            }
        }
    }
}
