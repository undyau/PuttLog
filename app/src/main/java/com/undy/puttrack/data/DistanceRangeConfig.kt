package com.undy.puttrack.data

/** The min/max/interval used to generate quick-entry distance buttons, in a single unit. */
data class DistanceRangeConfig(val min: Double, val max: Double, val interval: Double) {
    fun distances(): List<Double> {
        if (interval <= 0.0 || min > max) return emptyList()
        val result = mutableListOf<Double>()
        var value = min
        while (value <= max + EPSILON) {
            result.add(value)
            value += interval
        }
        return result
    }

    companion object {
        private const val EPSILON = 1e-9

        val DEFAULT_FEET = DistanceRangeConfig(min = 12.0, max = 36.0, interval = 3.0)
        val DEFAULT_METERS = DistanceRangeConfig(min = 4.0, max = 14.0, interval = 1.0)
    }
}
