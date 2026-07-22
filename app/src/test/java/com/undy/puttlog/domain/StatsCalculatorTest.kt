package com.undy.puttlog.domain

import com.undy.puttlog.data.Putt
import org.junit.Assert.assertEquals
import org.junit.Test

class StatsCalculatorTest {

    private fun putt(distanceFeet: Double, made: Boolean) =
        Putt(distanceFeet = distanceFeet, made = made, timestampMillis = 0L)

    @Test
    fun `empty list yields zeroed stats`() {
        val stats = StatsCalculator.compute(emptyList())
        assertEquals(CategoryStats(0, 0), stats.overall)
        assertEquals(0.0, stats.overall.percent, 0.0)
    }

    @Test
    fun `buckets putts into correct categories`() {
        val putts = listOf(
            putt(5.0, true),   // SHORT
            putt(20.0, true),  // C1X
            putt(25.0, false), // C1X
            putt(50.0, true),  // C2
            putt(80.0, false)  // C3
        )
        val stats = StatsCalculator.compute(putts)

        assertEquals(CategoryStats(3, 5), stats.overall)
        assertEquals(CategoryStats(1, 1), stats.short)
        assertEquals(CategoryStats(1, 2), stats.c1x)
        assertEquals(CategoryStats(1, 1), stats.c2)
        assertEquals(CategoryStats(0, 1), stats.c3)
    }

    @Test
    fun `percent is computed correctly`() {
        val putts = listOf(putt(20.0, true), putt(20.0, true), putt(20.0, false), putt(20.0, false))
        val stats = StatsCalculator.compute(putts)
        assertEquals(50.0, stats.c1x.percent, 0.0)
        assertEquals(50.0, stats.overall.percent, 0.0)
    }
}
