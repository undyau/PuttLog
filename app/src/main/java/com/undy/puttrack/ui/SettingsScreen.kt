package com.undy.puttrack.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.undy.puttrack.data.DistanceRangeConfig
import com.undy.puttrack.data.DistanceUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: PutTrackViewModel = viewModel(),
    onBack: () -> Unit
) {
    val unit by viewModel.distanceUnit.collectAsState()
    val rangeConfig by viewModel.currentRangeConfig.collectAsState()
    var showClearConfirmation by remember { mutableStateOf(false) }

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

        Text("Data", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Permanently deletes every recorded putt across all sessions, months, and years.",
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = { showClearConfirmation = true },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
        ) {
            Text("Clear all data")
        }
    }

    if (showClearConfirmation) {
        AlertDialog(
            onDismissRequest = { showClearConfirmation = false },
            title = { Text("Clear all data?") },
            text = { Text("This permanently deletes every recorded putt. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearAllData()
                    showClearConfirmation = false
                }) {
                    Text("Clear", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmation = false }) {
                    Text("Cancel")
                }
            }
        )
    }
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
