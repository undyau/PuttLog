package com.undy.puttlog.domain

/**
 * Standard disc golf putting categories (UDisc-style), based on distance from the basket.
 * SHORT (tap-ins under 11ft) are tracked but not part of the three headline stat buckets.
 */
enum class PuttCategory {
    SHORT,
    C1X,
    C2,
    C3
}

private const val C1X_MIN_FEET = 11.0
private const val C1X_MAX_FEET = 33.0
private const val C2_MAX_FEET = 66.0

fun categorize(distanceFeet: Double): PuttCategory = when {
    distanceFeet < C1X_MIN_FEET -> PuttCategory.SHORT
    distanceFeet <= C1X_MAX_FEET -> PuttCategory.C1X
    distanceFeet <= C2_MAX_FEET -> PuttCategory.C2
    else -> PuttCategory.C3
}
