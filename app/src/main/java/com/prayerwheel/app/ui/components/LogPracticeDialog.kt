package com.prayerwheel.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prayerwheel.app.data.model.SavedWheel
import com.prayerwheel.app.data.model.Mantras
import com.prayerwheel.app.data.model.WheelSkins
import com.prayerwheel.app.data.datastore.UserPreferences
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LogPracticeDialog(
    savedWheels: List<SavedWheel>,
    initialDateTimestamp: Long = System.currentTimeMillis(),
    defaultRpm: Int = 30,
    onSave: (
        wheelId: String?,
        startedAt: Long,
        durationSeconds: Long,
        rotationCount: Long,
        mantraId: String,
        mantrasPerRotation: Long,
        intention: String?,
        dedication: String?
    ) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedWheel by remember { mutableStateOf<SavedWheel?>(savedWheels.firstOrNull()) }
    
    var dateOption by remember { mutableStateOf("Today") } // "Today", "Yesterday", "Custom"
    var selectedDateTimestamp by remember { mutableStateOf(initialDateTimestamp) }
    var showDatePicker by remember { mutableStateOf(false) }

    var hoursText by remember { mutableStateOf("0") }
    var minutesText by remember { mutableStateOf("15") }
    
    var isManualOverride by remember { mutableStateOf(false) }
    var customRotationsText by remember { mutableStateOf("450") }

    var intentionText by remember { mutableStateOf("") }
    var dedicationText by remember { mutableStateOf(UserPreferences.DEFAULT_DEDICATION_TEXT) }

    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }
    val scrollState = rememberScrollState()

    // Initialize custom date text if passed date is not today
    LaunchedEffect(initialDateTimestamp) {
        val today = Calendar.getInstance()
        val param = Calendar.getInstance().apply { timeInMillis = initialDateTimestamp }
        val isToday = today.get(Calendar.YEAR) == param.get(Calendar.YEAR) &&
                today.get(Calendar.DAY_OF_YEAR) == param.get(Calendar.DAY_OF_YEAR)
        
        today.add(Calendar.DAY_OF_YEAR, -1)
        val isYesterday = today.get(Calendar.YEAR) == param.get(Calendar.YEAR) &&
                today.get(Calendar.DAY_OF_YEAR) == param.get(Calendar.DAY_OF_YEAR)
        
        if (isToday) {
            dateOption = "Today"
        } else if (isYesterday) {
            dateOption = "Yesterday"
        } else {
            dateOption = "Custom"
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDateTimestamp
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let {
                            val customCal = Calendar.getInstance().apply { timeInMillis = it }
                            val currentCal = Calendar.getInstance().apply { timeInMillis = selectedDateTimestamp }
                            currentCal.set(Calendar.YEAR, customCal.get(Calendar.YEAR))
                            currentCal.set(Calendar.MONTH, customCal.get(Calendar.MONTH))
                            currentCal.set(Calendar.DAY_OF_MONTH, customCal.get(Calendar.DAY_OF_MONTH))
                            selectedDateTimestamp = currentCal.timeInMillis
                        }
                        showDatePicker = false
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Log Practice Session",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Medium
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. Wheel Inventory Selector
                Text(
                    text = "Select Prayer Wheel",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                
                if (savedWheels.isEmpty()) {
                    Text(
                        text = "No saved prayer wheels available.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        items(savedWheels) { wheel ->
                            val skin = WheelSkins.byId(wheel.skinId) ?: WheelSkins.default()
                            val isSelected = selectedWheel?.id == wheel.id
                            Card(
                                modifier = Modifier
                                    .width(135.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        selectedWheel = wheel
                                    }
                                    .border(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                        shape = RoundedCornerShape(8.dp)
                                    ),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                                    else MaterialTheme.colorScheme.surface
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(16.dp)
                                                .clip(CircleShape)
                                                .background(Color(skin.cylinderColor or 0xFF000000))
                                        )
                                        Text(
                                            text = wheel.name,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1
                                        )
                                    }
                                    val mantra = Mantras.byId(wheel.mantraId)
                                    Text(
                                        text = mantra?.displayName ?: "Mantra",
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                        maxLines = 1
                                    )
                                    Text(
                                        text = NumberFormatter.formatLong(wheel.capacity),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                // 2. Date Selection
                Text(
                    text = "Practice Date",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    listOf("Today", "Yesterday", "Custom Date").forEach { option ->
                        val isSel = (option == "Custom Date" && dateOption == "Custom") || (option == dateOption)
                        FilterChip(
                            selected = isSel,
                            onClick = {
                                if (option == "Custom Date") {
                                    dateOption = "Custom"
                                    showDatePicker = true
                                } else {
                                    dateOption = option
                                    val cal = Calendar.getInstance()
                                    if (option == "Yesterday") {
                                        cal.add(Calendar.DAY_OF_YEAR, -1)
                                    }
                                    selectedDateTimestamp = cal.timeInMillis
                                }
                            },
                            label = {
                                if (option == "Custom Date" && dateOption == "Custom") {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Text(dateFormat.format(Date(selectedDateTimestamp)))
                                    }
                                } else {
                                    Text(option)
                                }
                            }
                        )
                    }
                }

                // 3. Duration Input
                Text(
                    text = "Duration",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = hoursText,
                        onValueChange = { newValue ->
                            if (newValue.all { it.isDigit() } && newValue.length <= 2) {
                                hoursText = newValue
                            }
                        },
                        label = { Text("Hours") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = minutesText,
                        onValueChange = { newValue ->
                            if (newValue.all { it.isDigit() } && newValue.length <= 2) {
                                minutesText = newValue
                            }
                        },
                        label = { Text("Minutes") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }

                // 4. Estimation or Manual Rotation Override
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = isManualOverride,
                        onCheckedChange = { isManualOverride = it }
                    )
                    Text(
                        text = "Enter exact rotation count manually",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                if (isManualOverride) {
                    OutlinedTextField(
                        value = customRotationsText,
                        onValueChange = { newValue ->
                            if (newValue.all { it.isDigit() }) {
                                customRotationsText = newValue
                            }
                        },
                        label = { Text("Exact Rotations") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    selectedWheel?.let { wheel ->
                        val hours = hoursText.toLongOrNull() ?: 0L
                        val minutes = minutesText.toLongOrNull() ?: 0L
                        val totalMinutes = hours * 60 + minutes
                        val rotations = totalMinutes * defaultRpm
                        val totalMantras = java.math.BigInteger.valueOf(rotations)
                            .multiply(java.math.BigInteger.valueOf(wheel.capacity))
                        
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "Practice Estimation (at ~$defaultRpm RPM):",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                                )
                                Text(
                                    text = "${NumberFormatter.formatLong(rotations)} rotations",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "= ${NumberFormatter.format(totalMantras)} mantras",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                // 5. Intention & Dedication
                Column(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = intentionText,
                        onValueChange = { intentionText = it },
                        label = { Text("Intention (optional)") },
                        placeholder = { Text("May all beings be free from suffering") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 3
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    TextButton(
                        onClick = {
                            intentionText = INTENTION_SUGGESTIONS.random()
                        },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Inspire Me ✨")
                    }
                }

                OutlinedTextField(
                    value = dedicationText,
                    onValueChange = { dedicationText = it },
                    label = { Text("Dedication (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 4
                )
            }
        },
        confirmButton = {
            val wheel = selectedWheel
            val hours = hoursText.toLongOrNull() ?: 0L
            val minutes = minutesText.toLongOrNull() ?: 0L
            val durationSeconds = (hours * 3600) + (minutes * 60)
            
            val isValid = wheel != null && durationSeconds > 0 && (!isManualOverride || (customRotationsText.toLongOrNull() ?: 0L) > 0L)
            
            TextButton(
                onClick = {
                    if (isValid && wheel != null) {
                        val rotations = if (isManualOverride) {
                            customRotationsText.toLongOrNull() ?: 0L
                        } else {
                            (durationSeconds / 60) * defaultRpm
                        }
                        
                        onSave(
                            wheel.id,
                            selectedDateTimestamp,
                            durationSeconds,
                            rotations,
                            wheel.mantraId,
                            wheel.capacity,
                            intentionText.ifBlank { null },
                            dedicationText.ifBlank { null }
                        )
                    }
                },
                enabled = isValid,
                modifier = Modifier.bounceClick()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.bounceClick()
            ) {
                Text("Cancel")
            }
        },
        shape = RoundedCornerShape(16.dp)
    )
}
