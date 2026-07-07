package com.undy.puttrack.data

import android.content.Context
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    val distanceUnit: Flow<DistanceUnit> = context.dataStore.data.map { prefs ->
        prefs[UNIT_KEY]?.let { runCatching { DistanceUnit.valueOf(it) }.getOrNull() } ?: DistanceUnit.FEET
    }

    suspend fun setDistanceUnit(unit: DistanceUnit) {
        context.dataStore.edit { it[UNIT_KEY] = unit.name }
    }

    val feetRange: Flow<DistanceRangeConfig> = context.dataStore.data.map { prefs ->
        DistanceRangeConfig(
            min = prefs[FEET_MIN_KEY] ?: DistanceRangeConfig.DEFAULT_FEET.min,
            max = prefs[FEET_MAX_KEY] ?: DistanceRangeConfig.DEFAULT_FEET.max,
            interval = prefs[FEET_INTERVAL_KEY] ?: DistanceRangeConfig.DEFAULT_FEET.interval
        )
    }

    val metersRange: Flow<DistanceRangeConfig> = context.dataStore.data.map { prefs ->
        DistanceRangeConfig(
            min = prefs[METERS_MIN_KEY] ?: DistanceRangeConfig.DEFAULT_METERS.min,
            max = prefs[METERS_MAX_KEY] ?: DistanceRangeConfig.DEFAULT_METERS.max,
            interval = prefs[METERS_INTERVAL_KEY] ?: DistanceRangeConfig.DEFAULT_METERS.interval
        )
    }

    suspend fun setFeetRange(config: DistanceRangeConfig) {
        context.dataStore.edit {
            it[FEET_MIN_KEY] = config.min
            it[FEET_MAX_KEY] = config.max
            it[FEET_INTERVAL_KEY] = config.interval
        }
    }

    suspend fun setMetersRange(config: DistanceRangeConfig) {
        context.dataStore.edit {
            it[METERS_MIN_KEY] = config.min
            it[METERS_MAX_KEY] = config.max
            it[METERS_INTERVAL_KEY] = config.interval
        }
    }

    companion object {
        private val UNIT_KEY = stringPreferencesKey("distance_unit")
        private val FEET_MIN_KEY = doublePreferencesKey("range_feet_min")
        private val FEET_MAX_KEY = doublePreferencesKey("range_feet_max")
        private val FEET_INTERVAL_KEY = doublePreferencesKey("range_feet_interval")
        private val METERS_MIN_KEY = doublePreferencesKey("range_meters_min")
        private val METERS_MAX_KEY = doublePreferencesKey("range_meters_max")
        private val METERS_INTERVAL_KEY = doublePreferencesKey("range_meters_interval")
    }
}
