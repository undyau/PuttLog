package com.undy.puttrack.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.undy.puttrack.data.DistanceUnit
import com.undy.puttrack.domain.CategoryStats
import com.undy.puttrack.domain.DistanceStat
import com.undy.puttrack.domain.PuttCategory
import com.undy.puttrack.domain.PuttStats
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt

private fun hasPermission(context: Context, permission: String) =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

@Composable
fun MainScreen(modifier: Modifier = Modifier, viewModel: PutTrackViewModel = viewModel()) {
    var showSettings by rememberSaveable { mutableStateOf(false) }

    if (showSettings) {
        SettingsScreen(modifier = modifier, viewModel = viewModel, onBack = { showSettings = false })
    } else {
        TrackingScreen(modifier = modifier, viewModel = viewModel, onOpenSettings = { showSettings = true })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrackingScreen(
    modifier: Modifier,
    viewModel: PutTrackViewModel,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val isListening by viewModel.isListening.collectAsState()
    val lastHeard by viewModel.lastHeard.collectAsState()
    val lastUnrecognized by viewModel.lastUnrecognized.collectAsState()
    val unit by viewModel.distanceUnit.collectAsState()
    val stats by viewModel.periodStats.collectAsState()
    val selectedPeriod by viewModel.selectedPeriod.collectAsState()
    val selectedDay by viewModel.selectedDay.collectAsState()
    val canGoToPreviousDay by viewModel.canGoToPreviousDay.collectAsState()
    val canGoToNextDay by viewModel.canGoToNextDay.collectAsState()
    val selectedMonth by viewModel.selectedMonth.collectAsState()
    val canGoToNextMonth by viewModel.canGoToNextMonth.collectAsState()
    val selectedYear by viewModel.selectedYear.collectAsState()
    val canGoToNextYear by viewModel.canGoToNextYear.collectAsState()
    val activeCategory by viewModel.activeCategory.collectAsState()
    val distanceBreakdown by viewModel.distanceBreakdown.collectAsState()
    val quickEntryDistances by viewModel.quickEntryDistances.collectAsState()
    val canUndo by viewModel.canUndo.collectAsState()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val recordGranted = results[Manifest.permission.RECORD_AUDIO]
            ?: hasPermission(context, Manifest.permission.RECORD_AUDIO)
        if (recordGranted) viewModel.startSession()
    }

    fun onMicClick() {
        if (isListening) {
            viewModel.stopSession()
            return
        }
        val missing = buildList {
            if (!hasPermission(context, Manifest.permission.RECORD_AUDIO)) {
                add(Manifest.permission.RECORD_AUDIO)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                !hasPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
            ) {
                add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                !hasPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            ) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (missing.isEmpty()) {
            viewModel.startSession()
        } else {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(modifier = Modifier.width(72.dp))
            Text(
                text = "PutTrack",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            TextButton(onClick = onOpenSettings) {
                Text("Settings")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = { onMicClick() },
            colors = if (isListening) {
                ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            } else {
                ButtonDefaults.buttonColors()
            }
        ) {
            Text(if (isListening) "Listening… tap to stop" else "Tap to start voice input")
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Say a distance and \"make\" or \"miss\" (e.g. \"20 make\"), or \"stop\" to end.",
            style = MaterialTheme.typography.bodySmall,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        lastHeard?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "Heard: \"$it\"", style = MaterialTheme.typography.bodySmall)
        }
        lastUnrecognized?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        QuickEntryGrid(
            distances = quickEntryDistances,
            unit = unit,
            onRecord = { distance, made -> viewModel.recordManualPutt(distance, made) }
        )

        Spacer(modifier = Modifier.height(4.dp))

        TextButton(onClick = { viewModel.undoLastPutt() }, enabled = canUndo) {
            Text("Undo last putt")
        }

        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(10.dp))

        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            StatsPeriod.entries.forEachIndexed { index, period ->
                SegmentedButton(
                    selected = selectedPeriod == period,
                    onClick = { viewModel.selectPeriod(period) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = StatsPeriod.entries.size)
                ) {
                    Text(
                        when (period) {
                            StatsPeriod.DAY -> "Day"
                            StatsPeriod.MONTH -> "Month"
                            StatsPeriod.YEAR -> "Year"
                        }
                    )
                }
            }
        }

        if (selectedPeriod == StatsPeriod.DAY) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { viewModel.shiftDay(-1) }, enabled = canGoToPreviousDay) {
                    Text("◀")
                }
                Text(
                    text = dayLabel(selectedDay),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                TextButton(onClick = { viewModel.shiftDay(1) }, enabled = canGoToNextDay) {
                    Text("▶")
                }
            }
        }

        if (selectedPeriod == StatsPeriod.MONTH) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { viewModel.shiftMonth(-1) }) {
                    Text("◀")
                }
                Text(
                    text = monthLabel(selectedMonth),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                TextButton(onClick = { viewModel.shiftMonth(1) }, enabled = canGoToNextMonth) {
                    Text("▶")
                }
            }
        }

        if (selectedPeriod == StatsPeriod.YEAR) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { viewModel.shiftYear(-1) }) {
                    Text("◀")
                }
                Text(
                    text = selectedYear.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                TextButton(onClick = { viewModel.shiftYear(1) }, enabled = canGoToNextYear) {
                    Text("▶")
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        StatsSection(stats = stats, unit = unit)

        if (activeCategory != null && distanceBreakdown.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            BreakdownSection(category = activeCategory!!, unit = unit, breakdown = distanceBreakdown)
        }
    }
}

private val MakeGreen = Color(0xFF2E7D32)
private val MissRed = Color(0xFFC62828)

@Composable
private fun QuickEntryGrid(distances: List<Double>, unit: DistanceUnit, onRecord: (Double, Boolean) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("Make (${unit.label})", style = MaterialTheme.typography.labelMedium, color = MakeGreen)
        Spacer(modifier = Modifier.height(4.dp))
        DistanceButtonRow(distances = distances, color = MakeGreen, onClick = { distance -> onRecord(distance, true) })

        Spacer(modifier = Modifier.height(12.dp))

        Text("Miss (${unit.label})", style = MaterialTheme.typography.labelMedium, color = MissRed)
        Spacer(modifier = Modifier.height(4.dp))
        DistanceButtonRow(distances = distances, color = MissRed, onClick = { distance -> onRecord(distance, false) })
    }
}

@Composable
private fun DistanceButtonRow(distances: List<Double>, color: Color, onClick: (Double) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        distances.forEach { distance ->
            Button(
                onClick = { onClick(distance) },
                colors = ButtonDefaults.buttonColors(containerColor = color),
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier.size(width = 34.dp, height = 32.dp)
            ) {
                Text(formatDistance(distance), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

private fun formatDistance(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()

private fun dayLabel(day: Day): String {
    val today = Calendar.getInstance()
    if (day.year == today.get(Calendar.YEAR) &&
        day.month == today.get(Calendar.MONTH) &&
        day.dayOfMonth == today.get(Calendar.DAY_OF_MONTH)
    ) {
        return "Today"
    }
    val calendar = Calendar.getInstance().apply {
        set(day.year, day.month, day.dayOfMonth, 0, 0, 0)
    }
    return SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(calendar.time)
}

private fun monthLabel(month: YearMonth): String {
    val calendar = Calendar.getInstance().apply {
        set(Calendar.YEAR, month.year)
        set(Calendar.MONTH, month.month)
        set(Calendar.DAY_OF_MONTH, 1)
    }
    return SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(calendar.time)
}

@Composable
private fun StatsSection(stats: PuttStats, unit: DistanceUnit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            OverallRow(stats.overall)
            Spacer(modifier = Modifier.height(8.dp))
            CategoryRow("C1X", rangeLabel(PuttCategory.C1X, unit), stats.c1x)
            if (stats.c2.attempts > 0) {
                CategoryRow("C2", rangeLabel(PuttCategory.C2, unit), stats.c2)
            }
            if (stats.c3.attempts > 0) {
                CategoryRow("C3", rangeLabel(PuttCategory.C3, unit), stats.c3)
            }
            if (stats.short.attempts > 0) {
                CategoryRow("Tap-in", rangeLabel(PuttCategory.SHORT, unit), stats.short)
            }
        }
    }
}

@Composable
private fun BreakdownSection(category: PuttCategory, unit: DistanceUnit, breakdown: List<DistanceStat>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "${categoryDisplayName(category)} breakdown",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "Based on your most recent putt's range",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.height(8.dp))
            breakdown.forEach { stat ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("${stat.distance} ${unit.label}", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "${stat.made}/${stat.attempts} (${stat.percent.roundToInt()}%)",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
    }
}

private fun categoryDisplayName(category: PuttCategory): String = when (category) {
    PuttCategory.SHORT -> "Tap-in"
    PuttCategory.C1X -> "C1X"
    PuttCategory.C2 -> "C2"
    PuttCategory.C3 -> "C3"
}

@Composable
private fun OverallRow(stats: CategoryStats) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text("Overall", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        Text(
            "${stats.made}/${stats.attempts} (${stats.percent.roundToInt()}%)",
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium
        )
    }
}

@Composable
private fun CategoryRow(name: String, range: String, stats: CategoryStats) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(name, style = MaterialTheme.typography.bodyLarge)
            Text(range, style = MaterialTheme.typography.bodySmall)
        }
        Text(
            "${stats.made}/${stats.attempts} (${stats.percent.roundToInt()}%)",
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

private fun rangeLabel(category: PuttCategory, unit: DistanceUnit): String = when (unit) {
    DistanceUnit.FEET -> when (category) {
        PuttCategory.SHORT -> "<11 ft"
        PuttCategory.C1X -> "11-33 ft"
        PuttCategory.C2 -> "34-66 ft"
        PuttCategory.C3 -> "67+ ft"
    }
    DistanceUnit.METERS -> when (category) {
        PuttCategory.SHORT -> "<3 m"
        PuttCategory.C1X -> "3-10 m"
        PuttCategory.C2 -> "10-20 m"
        PuttCategory.C3 -> "20+ m"
    }
}
