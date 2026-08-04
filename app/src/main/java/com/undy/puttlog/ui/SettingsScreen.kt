package com.undy.puttlog.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.undy.puttlog.data.DistanceRangeConfig
import com.undy.puttlog.data.DistanceUnit
import java.util.Calendar
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: PuttLogViewModel = viewModel(),
    onBack: () -> Unit
) {
    val unit by viewModel.distanceUnit.collectAsState()
    val rangeConfig by viewModel.currentRangeConfig.collectAsState()
    val audioInputEnabled by viewModel.audioInputEnabled.collectAsState()

    Column(modifier = modifier.fillMaxWidth().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) {
                Text("← Back")
            }
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text("Distance unit", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            DistanceUnit.entries.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = unit == option,
                    onClick = { viewModel.setDistanceUnit(option) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = DistanceUnit.entries.size)
                ) {
                    Text(if (option == DistanceUnit.FEET) "Feet" else "Meters")
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(32.dp))

        Text("Quick-entry range", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Distances shown as Make/Miss buttons on the main screen, in ${unit.label}.",
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(modifier = Modifier.height(8.dp))
        RangeEditor(unit = unit, range = rangeConfig, onSave = { viewModel.setRangeConfig(it) })

        Spacer(modifier = Modifier.height(32.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(32.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Audio input", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "Lets you speak distances instead of tapping. When off, the mic " +
                        "button and voice hints are hidden to leave more room on screen.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Switch(
                checked = audioInputEnabled,
                onCheckedChange = { viewModel.setAudioInputEnabled(it) }
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(32.dp))

        Text("Data", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Permanently deletes recorded putts within a date range.",
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(modifier = Modifier.height(12.dp))
        DeleteRangeSection(onDelete = { start, end -> viewModel.clearDateRange(start, end) })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeleteRangeSection(onDelete: (Day, Day) -> Unit) {
    var startDay by remember { mutableStateOf<Day?>(null) }
    var endDay by remember { mutableStateOf<Day?>(null) }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedButton(onClick = { showStartPicker = true }, modifier = Modifier.weight(1f)) {
            Text(startDay?.let { dayLabel(it) } ?: "Start date")
        }
        OutlinedButton(onClick = { showEndPicker = true }, modifier = Modifier.weight(1f)) {
            Text(endDay?.let { dayLabel(it) } ?: "End date")
        }
    }

    Spacer(modifier = Modifier.height(12.dp))

    Button(
        onClick = { showDeleteConfirmation = true },
        enabled = startDay != null && endDay != null,
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
    ) {
        Text("Delete data in range")
    }

    if (showStartPicker) {
        DayPickerDialog(
            initialDay = startDay,
            onDismiss = { showStartPicker = false },
            onConfirm = { startDay = it }
        )
    }

    if (showEndPicker) {
        DayPickerDialog(
            initialDay = endDay,
            onDismiss = { showEndPicker = false },
            onConfirm = { endDay = it }
        )
    }

    val start = startDay
    val end = endDay
    if (showDeleteConfirmation && start != null && end != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Delete this range?") },
            text = {
                Text(
                    "This permanently deletes every putt recorded between " +
                        "${dayLabel(start)} and ${dayLabel(end)}. This can't be undone."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(start, end)
                    showDeleteConfirmation = false
                    startDay = null
                    endDay = null
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayPickerDialog(initialDay: Day?, onDismiss: () -> Unit, onConfirm: (Day) -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initialDay?.let { utcMidnightMillis(it) })
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onConfirm(dayFromUtcMillis(it)) }
                onDismiss()
            }) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    ) {
        DatePicker(state = state)
    }
}

private fun utcMidnightMillis(day: Day): Long =
    Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(day.year, day.month, day.dayOfMonth)
    }.timeInMillis

private fun dayFromUtcMillis(millis: Long): Day {
    val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = millis }
    return Day(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH))
}

@Composable
private fun RangeEditor(unit: DistanceUnit, range: DistanceRangeConfig, onSave: (DistanceRangeConfig) -> Unit) {
    var minText by remember(unit, range) { mutableStateOf(formatNumber(range.min)) }
    var maxText by remember(unit, range) { mutableStateOf(formatNumber(range.max)) }
    var intervalText by remember(unit, range) { mutableStateOf(formatNumber(range.interval)) }
    var error by remember(unit, range) { mutableStateOf<String?>(null) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = minText,
            onValueChange = { minText = it },
            label = { Text("Min") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f)
        )
        OutlinedTextField(
            value = maxText,
            onValueChange = { maxText = it },
            label = { Text("Max") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f)
        )
        OutlinedTextField(
            value = intervalText,
            onValueChange = { intervalText = it },
            label = { Text("Interval") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f)
        )
    }

    Spacer(modifier = Modifier.height(8.dp))

    error?.let {
        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        Spacer(modifier = Modifier.height(8.dp))
    }

    Button(onClick = {
        val min = minText.toDoubleOrNull()
        val max = maxText.toDoubleOrNull()
        val interval = intervalText.toDoubleOrNull()
        error = when {
            min == null || max == null || interval == null -> "Enter valid numbers."
            interval <= 0.0 -> "Interval must be greater than zero."
            min > max -> "Min must not be greater than max."
            else -> null
        }
        if (error == null && min != null && max != null && interval != null) {
            onSave(DistanceRangeConfig(min, max, interval))
        }
    }) {
        Text("Save range")
    }
}

private fun formatNumber(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
