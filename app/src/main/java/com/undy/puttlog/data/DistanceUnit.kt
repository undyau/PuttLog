package com.undy.puttlog.data

enum class DistanceUnit(val label: String) {
    FEET("ft"),
    METERS("m");

    fun toFeet(value: Double): Double = when (this) {
        FEET -> value
        METERS -> value / METERS_PER_FOOT
    }

    fun fromFeet(feet: Double): Double = when (this) {
        FEET -> feet
        METERS -> feet * METERS_PER_FOOT
    }

    companion object {
        private const val METERS_PER_FOOT = 0.3048
    }
}
