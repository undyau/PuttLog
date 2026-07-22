package com.undy.puttlog.domain

import com.undy.puttlog.data.DistanceUnit
import com.undy.puttlog.data.Putt
import kotlin.math.roundToInt

data class DistanceStat(val distance: Int, val made: Int, val attempts: Int) {
    val percent: Double get() = if (attempts == 0) 0.0 else made * 100.0 / attempts
}

/**
 * Per-distance makes/attempts within a single category, rounded to whole units
 * in whichever unit the caller wants them displayed in.
 */
fun computeDistanceBreakdown(putts: List<Putt>, category: PuttCategory, unit: DistanceUnit): List<DistanceStat> =
    putts.asSequence()
        .filter { categorize(it.distanceFeet) == category }
        .groupBy { unit.fromFeet(it.distanceFeet).roundToInt() }
        .map { (distance, group) -> DistanceStat(distance, group.count { it.made }, group.size) }
        .sortedBy { it.distance }
