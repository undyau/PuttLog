package com.undy.puttlog.ui

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.undy.puttlog.data.DistanceRangeConfig
import com.undy.puttlog.data.DistanceUnit
import com.undy.puttlog.data.Putt
import com.undy.puttlog.data.PuttDatabase
import com.undy.puttlog.data.SettingsRepository
import com.undy.puttlog.domain.CommandParser
import com.undy.puttlog.domain.DistanceStat
import com.undy.puttlog.domain.ParsedCommand
import com.undy.puttlog.domain.PuttCategory
import com.undy.puttlog.domain.PuttStats
import com.undy.puttlog.domain.StatsCalculator
import com.undy.puttlog.domain.categorize
import com.undy.puttlog.domain.computeDistanceBreakdown
import com.undy.puttlog.voice.ListeningService
import com.undy.puttlog.voice.SoundFeedback
import java.util.Calendar
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class StatsPeriod { DAY, MONTH, YEAR }

data class Day(val year: Int, val month: Int, val dayOfMonth: Int)

private const val DAY_MILLIS = 24L * 60 * 60 * 1000

data class YearMonth(val year: Int, val month: Int)

private fun dayStartMillis(day: Day): Long =
    Calendar.getInstance().apply {
        set(day.year, day.month, day.dayOfMonth, 0, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

private fun compareDay(a: Day, b: Day): Int = dayStartMillis(a).compareTo(dayStartMillis(b))

private fun currentDay(): Day {
    val c = Calendar.getInstance()
    return Day(c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH))
}

private fun compareYearMonth(a: YearMonth, b: YearMonth): Int =
    if (a.year != b.year) a.year - b.year else a.month - b.month

private fun currentYearMonth(): YearMonth {
    val c = Calendar.getInstance()
    return YearMonth(c.get(Calendar.YEAR), c.get(Calendar.MONTH))
}

private fun currentYear(): Int = Calendar.getInstance().get(Calendar.YEAR)

class PuttLogViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = PuttDatabase.getInstance(application).puttDao()
    private val settingsRepository = SettingsRepository(application)

    private val allPutts: StateFlow<List<Putt>> = dao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val distanceUnit: StateFlow<DistanceUnit> = settingsRepository.distanceUnit
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DistanceUnit.FEET)

    val audioInputEnabled: StateFlow<Boolean> = settingsRepository.audioInputEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val feetRange: StateFlow<DistanceRangeConfig> = settingsRepository.feetRange
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DistanceRangeConfig.DEFAULT_FEET)

    private val metersRange: StateFlow<DistanceRangeConfig> = settingsRepository.metersRange
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DistanceRangeConfig.DEFAULT_METERS)

    /** The min/max/interval for the currently selected display unit, editable from Settings. */
    val currentRangeConfig: StateFlow<DistanceRangeConfig> =
        combine(distanceUnit, feetRange, metersRange) { unit, feet, meters ->
            if (unit == DistanceUnit.FEET) feet else meters
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DistanceRangeConfig.DEFAULT_FEET)

    /** The quick-entry distance buttons to show, in the currently selected display unit. */
    val quickEntryDistances: StateFlow<List<Double>> = currentRangeConfig
        .map { it.distances() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DistanceRangeConfig.DEFAULT_FEET.distances())

    val isListening = MutableStateFlow(false)
    val lastHeard = MutableStateFlow<String?>(null)
    val lastUnrecognized = MutableStateFlow<String?>(null)

    val selectedPeriod = MutableStateFlow(StatsPeriod.DAY)

    val selectedDay = MutableStateFlow(currentDay())

    /** Distinct calendar days that have at least one recorded putt. */
    private val daysWithData: StateFlow<List<Day>> = allPutts
        .map { putts ->
            putts.map { p ->
                val c = Calendar.getInstance().apply { timeInMillis = p.timestampMillis }
                Day(c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH))
            }.distinct()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val canGoToPreviousDay: StateFlow<Boolean> = combine(selectedDay, daysWithData) { selected, days ->
        days.any { compareDay(it, selected) < 0 }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val canGoToNextDay: StateFlow<Boolean> = selectedDay
        .map { it != currentDay() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val selectedMonth = MutableStateFlow(currentYearMonth())

    val canGoToNextMonth: StateFlow<Boolean> = selectedMonth
        .map { compareYearMonth(it, currentYearMonth()) < 0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val selectedYear = MutableStateFlow(currentYear())

    val canGoToNextYear: StateFlow<Boolean> = selectedYear
        .map { it < currentYear() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val periodPutts: StateFlow<List<Putt>> =
        combine(allPutts, selectedPeriod, selectedDay, selectedMonth, selectedYear) { putts, period, day, month, year ->
            filterForPeriod(putts, period, day, month, year)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val periodStats: StateFlow<PuttStats> = periodPutts
        .map { StatsCalculator.compute(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StatsCalculator.compute(emptyList()))

    /** Whether there is a recorded putt that can be undone. */
    val canUndo: StateFlow<Boolean> = allPutts
        .map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /** The category of the most recently recorded putt, used to decide which breakdown to surface. */
    val activeCategory: StateFlow<PuttCategory?> = allPutts
        .map { putts -> putts.lastOrNull()?.let { categorize(it.distanceFeet) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val distanceBreakdown: StateFlow<List<DistanceStat>> =
        combine(periodPutts, activeCategory, distanceUnit) { putts, category, unit ->
            if (category == null) emptyList() else computeDistanceBreakdown(putts, category, unit)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private fun filterForPeriod(
        putts: List<Putt>,
        period: StatsPeriod,
        day: Day,
        month: YearMonth,
        year: Int
    ): List<Putt> =
        when (period) {
            StatsPeriod.DAY -> putts.filter { p ->
                val c = Calendar.getInstance().apply { timeInMillis = p.timestampMillis }
                c.get(Calendar.YEAR) == day.year &&
                    c.get(Calendar.MONTH) == day.month &&
                    c.get(Calendar.DAY_OF_MONTH) == day.dayOfMonth
            }
            StatsPeriod.MONTH -> putts.filter { p ->
                val c = Calendar.getInstance().apply { timeInMillis = p.timestampMillis }
                c.get(Calendar.YEAR) == month.year && c.get(Calendar.MONTH) == month.month
            }
            StatsPeriod.YEAR -> putts.filter { p ->
                Calendar.getInstance().apply { timeInMillis = p.timestampMillis }.get(Calendar.YEAR) == year
            }
        }

    private val soundFeedback = SoundFeedback()

    private var boundService: ListeningService? = null
    private var bound = false
    private var listeningCollectJob: Job? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val service = (binder as ListeningService.LocalBinder).getService()
            boundService = service
            service.onResult = { candidates -> handleRecognizedCandidates(candidates) }
            service.onUnavailable = {
                lastUnrecognized.value =
                    "On-device speech recognition isn't available on this phone (needs Android 12+)."
            }
            listeningCollectJob = viewModelScope.launch {
                service.isListening.collect { listening -> isListening.value = listening }
            }
            service.startListening()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            boundService = null
            listeningCollectJob?.cancel()
            listeningCollectJob = null
            isListening.value = false
        }
    }

    fun startSession() {
        val context: Context = getApplication()
        val intent = Intent(context, ListeningService::class.java)
        ContextCompat.startForegroundService(context, intent)
        if (!bound) {
            context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
            bound = true
        }
    }

    fun stopSession() {
        boundService?.stopListening()
        unbindListeningService()
        isListening.value = false
    }

    private fun unbindListeningService() {
        if (bound) {
            getApplication<Application>().unbindService(serviceConnection)
            bound = false
        }
        boundService = null
        listeningCollectJob?.cancel()
        listeningCollectJob = null
    }

    fun recordManualPutt(distanceInDisplayUnit: Double, made: Boolean) {
        recordPutt(distanceUnit.value.toFeet(distanceInDisplayUnit), made)
    }

    fun selectPeriod(period: StatsPeriod) {
        selectedPeriod.value = period
    }

    fun shiftDay(delta: Int) {
        val days = daysWithData.value
        val current = selectedDay.value
        if (delta < 0) {
            val previous = days.filter { compareDay(it, current) < 0 }.maxByOrNull { dayStartMillis(it) }
            if (previous != null) selectedDay.value = previous
        } else if (delta > 0) {
            val today = currentDay()
            if (current == today) return
            val next = days.filter { compareDay(it, current) > 0 && compareDay(it, today) <= 0 }
                .minByOrNull { dayStartMillis(it) }
            selectedDay.value = next ?: today
        }
    }

    fun shiftMonth(delta: Int) {
        val current = selectedMonth.value
        val c = Calendar.getInstance().apply {
            set(Calendar.YEAR, current.year)
            set(Calendar.MONTH, current.month)
            set(Calendar.DAY_OF_MONTH, 1)
            add(Calendar.MONTH, delta)
        }
        val candidate = YearMonth(c.get(Calendar.YEAR), c.get(Calendar.MONTH))
        if (compareYearMonth(candidate, currentYearMonth()) <= 0) {
            selectedMonth.value = candidate
        }
    }

    fun shiftYear(delta: Int) {
        val candidate = selectedYear.value + delta
        if (candidate <= currentYear()) {
            selectedYear.value = candidate
        }
    }

    private fun handleRecognizedCandidates(candidates: List<String>) {
        lastHeard.value = candidates.firstOrNull()
        var understood = true
        when (val command = CommandParser.parseBest(candidates)) {
            is ParsedCommand.RecordPutt -> {
                lastUnrecognized.value = null
                recordPutt(distanceUnit.value.toFeet(command.distance), command.made)
            }
            is ParsedCommand.RepeatDistance -> {
                val lastDistanceFeet = allPutts.value.lastOrNull()?.distanceFeet
                if (lastDistanceFeet == null) {
                    lastUnrecognized.value = "No previous putt distance to repeat — say a distance first."
                    understood = false
                } else {
                    lastUnrecognized.value = null
                    recordPutt(lastDistanceFeet, command.made)
                }
            }
            ParsedCommand.Stop -> {
                lastUnrecognized.value = null
                stopSession()
            }
            is ParsedCommand.Unrecognized -> {
                lastUnrecognized.value = "Didn't understand that — try \"<distance> make\" or \"<distance> miss\"."
                understood = false
            }
        }
        if (understood) soundFeedback.playRecognized() else soundFeedback.playUnrecognized()
    }

    private fun recordPutt(distanceFeet: Double, made: Boolean) {
        viewModelScope.launch {
            dao.insert(Putt(distanceFeet = distanceFeet, made = made, timestampMillis = System.currentTimeMillis()))
        }
    }

    fun setDistanceUnit(unit: DistanceUnit) {
        viewModelScope.launch { settingsRepository.setDistanceUnit(unit) }
    }

    fun setAudioInputEnabled(enabled: Boolean) {
        if (!enabled && isListening.value) stopSession()
        viewModelScope.launch { settingsRepository.setAudioInputEnabled(enabled) }
    }

    fun setRangeConfig(config: DistanceRangeConfig) {
        viewModelScope.launch {
            if (distanceUnit.value == DistanceUnit.FEET) {
                settingsRepository.setFeetRange(config)
            } else {
                settingsRepository.setMetersRange(config)
            }
        }
    }

    fun clearDateRange(start: Day, end: Day) {
        viewModelScope.launch {
            val (from, to) = if (compareDay(start, end) <= 0) start to end else end to start
            val startMillis = dayStartMillis(from)
            val endMillis = dayStartMillis(to) + DAY_MILLIS - 1
            dao.deleteBetween(startMillis, endMillis)
        }
    }

    fun undoLastPutt() {
        viewModelScope.launch { dao.deleteLast() }
    }

    override fun onCleared() {
        super.onCleared()
        boundService?.stopListening()
        unbindListeningService()
        soundFeedback.release()
    }
}
