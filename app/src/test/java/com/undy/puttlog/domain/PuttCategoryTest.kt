package com.undy.puttlog.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class PuttCategoryTest {

    @Test
    fun `short putts are under 11 feet`() {
        assertEquals(PuttCategory.SHORT, categorize(0.0))
        assertEquals(PuttCategory.SHORT, categorize(10.9))
    }

    @Test
    fun `c1x is 11 to 33 feet inclusive`() {
        assertEquals(PuttCategory.C1X, categorize(11.0))
        assertEquals(PuttCategory.C1X, categorize(20.0))
        assertEquals(PuttCategory.C1X, categorize(33.0))
    }

    @Test
    fun `c2 is 34 to 66 feet inclusive`() {
        assertEquals(PuttCategory.C2, categorize(33.1))
        assertEquals(PuttCategory.C2, categorize(50.0))
        assertEquals(PuttCategory.C2, categorize(66.0))
    }

    @Test
    fun `c3 is beyond 66 feet`() {
        assertEquals(PuttCategory.C3, categorize(66.1))
        assertEquals(PuttCategory.C3, categorize(150.0))
    }
}
