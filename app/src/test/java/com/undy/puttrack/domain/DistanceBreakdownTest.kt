package com.undy.puttrack.domain

import com.undy.puttrack.data.DistanceUnit
import com.undy.puttrack.data.Putt
import org.junit.Assert.assertEquals
import org.junit.Test

class DistanceBreakdownTest {

    private fun putt(distanceFeet: Double, made: Boolean) =
        Putt(distanceFeet = distanceFeet, made = made, timestampMillis = 0L)

    @Test
    fun `groups putts by rounded distance within category only`() {
        val putts = listOf(
            putt(20.0, true),
            putt(20.0, false),
            putt(25.0, true),
            putt(50.0, true) // different category, should be excluded
        )
        val breakdown = computeDistanceBreakdown(putts, PuttCategory.C1X, DistanceUnit.FEET)

        assertEquals(
            listOf(DistanceStat(20, 1, 2), DistanceStat(25, 1, 1)),
            breakdown
        )
    }

    @Test
    fun `converts to display unit before grouping`() {
        val putts = listOf(putt(33.0, true), putt(32.8, false))
        val breakdown = computeDistanceBreakdown(putts, PuttCategory.C1X, DistanceUnit.METERS)

        assertEquals(1, breakdown.size)
        assertEquals(10, breakdown[0].distance)
        assertEquals(2, breakdown[0].attempts)
    }

    @Test
    fun `empty when no putts in category`() {
        val breakdown = computeDistanceBreakdown(listOf(putt(5.0, true)), PuttCategory.C3, DistanceUnit.FEET)
        assertEquals(emptyList<DistanceStat>(), breakdown)
    }
}
