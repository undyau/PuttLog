package com.undy.puttlog.data

import org.junit.Assert.assertEquals
import org.junit.Test

class DistanceRangeConfigTest {

    @Test
    fun `default feet range is 12 to 36 in 3 ft steps`() {
        val distances = DistanceRangeConfig.DEFAULT_FEET.distances()
        assertEquals(listOf(12.0, 15.0, 18.0, 21.0, 24.0, 27.0, 30.0, 33.0, 36.0), distances)
    }

    @Test
    fun `default meters range is 4 to 14 in 1 m steps`() {
        val distances = DistanceRangeConfig.DEFAULT_METERS.distances()
        assertEquals(listOf(4.0, 5.0, 6.0, 7.0, 8.0, 9.0, 10.0, 11.0, 12.0, 13.0, 14.0), distances)
    }

    @Test
    fun `invalid interval yields empty list`() {
        assertEquals(emptyList<Double>(), DistanceRangeConfig(min = 10.0, max = 20.0, interval = 0.0).distances())
    }

    @Test
    fun `min greater than max yields empty list`() {
        assertEquals(emptyList<Double>(), DistanceRangeConfig(min = 20.0, max = 10.0, interval = 1.0).distances())
    }
}
