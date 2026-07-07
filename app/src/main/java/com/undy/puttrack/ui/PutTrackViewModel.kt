package com.undy.puttrack.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.undy.puttrack.data.DistanceRangeConfig
import com.undy.puttrack.data.DistanceUnit
import com.undy.puttrack.data.Putt
import com.undy.puttrack.data.PuttDatabase
import com.undy.puttrack.data.SettingsRepository
import com.undy.puttrack.domain.CommandParser
import com.undy.puttrack.domain.DistanceStat
import com.undy.puttrack.domain.ParsedCommand
import com.undy.puttrack.domain.PuttCategory
import com.undy.puttrack.domain.PuttStats
import com.undy.puttrack.domain.StatsCalculator
import com.undy.puttrack.domain.categorize
import com.undy.puttrack.domain.computeDistanceBreakdown
import com.undy.puttrack.voice.ContinuousSpeechRecognizer
import com.undy.puttrack.voice.SoundFeedback
import java.util.Calendar
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class StatsPeriod { SESSION, MONTH, YEAR }

class PutTrackViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = PuttDatabase.getInstance(application).puttDao()
    private val settingsRepository = SettingsRepository(application)

    private val sessionStartTime = MutableStateFlow(System.currentTimeMillis())

    private val allPutts: StateFlow<List<Putt>> = dao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val distanceUnit: StateFlow<DistanceUnit> = settingsRepository.distanceUnit
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DistanceUnit.FEET)

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

    val selectedPeriod = MutableStateFlow(StatsPeriod.SESSION)

    private val periodPutts: StateFlow<List<Putt>> =
        combine(allPutts, sessionStartTime, selectedPeriod) { putts, sessionStart, period ->
            filterForPeriod(putts, period, sessionStart)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val periodStats: StateFlow<PuttStats> = periodPutts
        .map { StatsCalculator.compute(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StatsCalculator.compute(emptyList()))

    /** The category of the most recently recorded putt, used to decide which breakdown to surface. */
    val activeCategory: StateFlow<PuttCategory?> = allPutts
        .map { putts -> putts.lastOrNull()?.let { categorize(it.distanceFeet) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val distanceBreakdown: StateFlow<List<DistanceStat>> =
        combine(periodPutts, activeCategory, distanceUnit) { putts, category, unit ->
            if (category == null) emptyList() else computeDistanceBreakdown(putts, category, unit)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private fun filterForPeriod(putts: List<Putt>, period: StatsPeriod, sessionStart: Long): List<Putt> =
        when (period) {
            StatsPeriod.SESSION -> putts.filter { it.timestampMillis >= sessionStart }
            StatsPeriod.MONTH -> {
                val now = Calendar.getInstance()
                val year = now.get(Calendar.YEAR)
                val month = now.get(Calendar.MONTH)
                putts.filter { p ->
                    val c = Calendar.getInstance().apply { timeInMillis = p.timestampMillis }
                    c.get(Calendar.YEAR) == year && c.get(Calendar.MONTH) == month
                }
            }
            StatsPeriod.YEAR -> {
                val year = Calendar.getInstance().get(Calendar.YEAR)
                putts.filter { p ->
                    Calendar.getInstance().apply { timeInMillis = p.timestampMillis }.get(Calendar.YEAR) == year
                }
            }
        }

    private var speechRecognizer: ContinuousSpeechRecognizer? = null
    private val soundFeedback = SoundFeedback()

    private fun recognizer(): ContinuousSpeechRecognizer =
        speechRecognizer ?: ContinuousSpeechRecognizer(
            context = getApplication(),
            onResult = { candidates -> handleRecognizedCandidates(candidates) },
            onListeningChanged = { listening -> isListening.value = listening },
            onUnavailable = {
                lastUnrecognized.value =
                    "On-device speech recognition isn't available on this phone (needs Android 12+)."
            }
        ).also { speechRecognizer = it }

    fun startSession() {
        sessionStartTime.value = System.currentTimeMillis()
        recognizer().start()
    }

    fun stopSession() {
        speechRecognizer?.stop()
    }

    fun recordManualPutt(distanceInDisplayUnit: Double, made: Boolean) {
        recordPutt(distanceUnit.value.toFeet(distanceInDisplayUnit), made)
    }

    fun selectPeriod(period: StatsPeriod) {
        selectedPeriod.value = period
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

    fun setRangeConfig(config: DistanceRangeConfig) {
        viewModelScope.launch {
            if (distanceUnit.value == DistanceUnit.FEET) {
                settingsRepository.setFeetRange(config)
            } else {
                settingsRepository.setMetersRange(config)
            }
        }
    }

    fun clearAllData() {
        sessionStartTime.value = System.currentTimeMillis()
        viewModelScope.launch { dao.deleteAll() }
    }

    override fun onCleared() {
        super.onCleared()
        speechRecognizer?.destroy()
        soundFeedback.release()
    }
}
